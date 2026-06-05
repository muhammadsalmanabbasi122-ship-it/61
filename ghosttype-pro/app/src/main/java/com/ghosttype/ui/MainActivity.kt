package com.ghosttype.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.ghosttype.ime.AutoTypeEngine
import com.ghosttype.ime.AutoTypeForegroundService
import com.ghosttype.ui.screens.*
import com.ghosttype.ui.theme.GhostTypeTheme

class MainActivity : ComponentActivity() {

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val name = queryDisplayName(it)
                AutoTypeEngine.loadFromUri(this@MainActivity, it, name)
            }
        }
    }

    // The "Add custom font" file picker was removed in v1.4.2.

    private val requestNotifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        val proj = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return contentResolver.query(uri, proj, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ask for POST_NOTIFICATIONS on Android 13+ (Tiramisu). Wrapped in
        // a try/catch so a buggy permission registry never blocks the
        // app from launching — worst case the user just sees no popup.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } catch (_: Throwable) { /* best-effort */ }
        }
        val initialSection = try { intent?.getStringExtra("section") } catch (_: Throwable) { null }
        try {
            setContent {
                GhostTypeTheme {
                    // v1.9 — every screen now sits behind the security
                    // + approval gate. GatedApp shows a loading splash
                    // briefly, then either the LockScreen (with the
                    // device ID + WhatsApp button) or the real AppRoot.
                    GatedApp {
                    AppRoot(
                        initialSection = initialSection,
                        onPickTxt = { safeLaunch { pickFile.launch(arrayOf("text/plain", "*/*")) } },
                        onOpenImeSettings = { safeLaunch { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } },
                        onOpenAccessibility = { safeLaunch { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } },
                        onStartAutoType = {
                            safeLaunch {
                                AutoTypeForegroundService.start(this)
                                AutoTypeEngine.start(
                                    this,
                                    com.ghosttype.utils.SettingsStore.prefs(this)
                                        .getInt(com.ghosttype.utils.SettingsStore.KEY_AT_START_LINE, 0)
                                )
                                // Mark this exact moment so the upcoming
                                // onStop (caused by our own moveTaskToBack)
                                // doesn't pause the typer we just started.
                                lastStartAtMs = System.currentTimeMillis()
                                try { moveTaskToBack(true) } catch (_: Throwable) {}
                            }
                        },
                        onResumeAutoType = {
                            safeLaunch {
                                AutoTypeEngine.resume()
                                // Same minimize trick as Start so the user
                                // lands back in the chat app after Resume.
                                lastStartAtMs = System.currentTimeMillis()
                                try { moveTaskToBack(true) } catch (_: Throwable) {}
                            }
                        },
                        onPause = { safeLaunch { AutoTypeEngine.pause() } },
                        onStop = {
                            safeLaunch {
                                AutoTypeEngine.stopAndSave(this)
                                AutoTypeForegroundService.stop(this)
                            }
                        }
                    )
                    } // GatedApp
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("GhostTypeMain", "setContent failed", t)
            // Fallback minimal UI so the app does not die silently.
            val tv = android.widget.TextView(this).apply {
                text = "GhostType Pro\n\nUI failed to initialize:\n${t.javaClass.simpleName}: ${t.message}\n\nPlease share crash log."
                setPadding(40, 80, 40, 40)
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#0D0D0D"))
            }
            setContentView(tv)
        }
    }

    private inline fun safeLaunch(block: () -> Unit) {
        try { block() } catch (t: Throwable) {
            android.util.Log.e("GhostTypeMain", "action failed", t)
        }
    }

    // ===== PAUSE-ON-CLOSE / PAUSE-ON-MINIMIZE =====
    // The user wants the auto-typer to PAUSE (preserving its queue)
    // whenever they close or minimize the app, instead of being STOPPED
    // outright. They can then come back later, hit Resume, and pick up
    // exactly where they left off.
    //
    // The tricky part: pressing Start itself triggers a programmatic
    // moveTaskToBack(true) (so the user lands back in the chat app), and
    // that fires onStop too. We use `lastStartAtMs` as a short-lived
    // window (~2 s) to distinguish "user-initiated minimize" (pause) from
    // "Start-triggered minimize" (don't pause).
    @Volatile private var lastStartAtMs = 0L

    /**
     * Hard-stop the auto-typer (cancels its coroutine, clears the queue
     * cursor) and tears down the foreground service. Used whenever the
     * user leaves GhostType outside of the Start-triggered minimize
     * window — they explicitly asked for the typer to be OFF (not just
     * paused) when they minimize the app, because a paused typer plus a
     * stale floating-pointer position was producing rogue clicks on the
     * launcher / GhostType's own UI.
     */
    private fun stopTyperIfRunning() {
        try {
            val s = AutoTypeEngine.state.value
            if (s.running) {
                AutoTypeEngine.stop()
                try { AutoTypeForegroundService.stop(this) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    /**
     * App backgrounded (home button, recent apps switch, etc.). STOP the
     * typer UNLESS this onStop is the immediate consequence of the Start
     * button having just called moveTaskToBack(true) — in that case we
     * WANT the chat app to appear so the typer can do its job.
     *
     * The user reported that a paused-but-still-resident typer would
     * occasionally fire its pointer click on the home launcher or
     * GhostType's own UI ("kya kya open ho jata hai"). Hard stop on
     * minimize eliminates that whole class of bug — they can simply hit
     * Start again from the AutoType screen to begin a new run.
     */
    override fun onStop() {
        super.onStop()
        val withinStartWindow = System.currentTimeMillis() - lastStartAtMs < 2000L
        if (withinStartWindow) return
        stopTyperIfRunning()
    }

    /**
     * App swiped from Recents. Hard-stop the typer too — same reasoning
     * as onStop above (no rogue clicks while the user thinks the app is
     * "closed").
     */
    override fun onDestroy() {
        if (isFinishing) {
            stopTyperIfRunning()
        }
        super.onDestroy()
    }

    /**
     * Coming back to GhostType's main UI also hard-stops the typer.
     * Mirrors the onStop behaviour so the AutoType screen always shows
     * a clean "stopped" state when the user reopens the app, instead of
     * a confusing half-paused queue from a previous session.
     */
    override fun onResume() {
        super.onResume()
        val withinStartWindow = System.currentTimeMillis() - lastStartAtMs < 2000L
        if (!withinStartWindow) stopTyperIfRunning()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    initialSection: String?,
    onPickTxt: () -> Unit,
    onOpenImeSettings: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onStartAutoType: () -> Unit,
    onPause: () -> Unit,
    onResumeAutoType: () -> Unit,
    onStop: () -> Unit
) {
    val tabs = listOf("Home", "Plans", "Auto-Type", "Pointer", "Clipboard", "FYT Type", "Math", "Reset", "Developer", "About")
    val initialIdx = when (initialSection) {
        "autotype" -> 2
        "pointer"  -> 3
        "clipboard" -> 4
        "fyttype" -> 5
        "math" -> 6
        "plans" -> 1
        else -> 0
    }
    var selected by rememberSaveable { mutableStateOf(initialIdx) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // ── Tasks unlock popup ────────────────────────────────────
    val ctx = LocalContext.current
    val prefs = com.ghosttype.utils.SettingsStore.prefs(ctx)
    var tasksUnlocked by remember {
        mutableStateOf(prefs.getBoolean(com.ghosttype.utils.SettingsStore.KEY_TASKS_UNLOCKED, false))
    }
    if (!tasksUnlocked) {
        com.ghosttype.ui.screens.TasksDialog(onUnlocked = { tasksUnlocked = true })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight().width(280.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "GhostType Pro",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                tabs.forEachIndexed { i, name ->
                    val ic = when (name) {
                        "Home"      -> Icons.Default.Home
                        "Plans"     -> Icons.Default.Sell
                        "Auto-Type" -> Icons.Default.PlayArrow
                        "Pointer"   -> Icons.Default.AdsClick
                        "Clipboard" -> Icons.Default.ContentPaste
                        "FYT Type"  -> Icons.Default.Speed
                        "Math"      -> Icons.Default.Star
                        "Reset"     -> Icons.Default.Refresh
                        "Developer" -> Icons.Default.Person
                        else        -> Icons.Default.Info
                    }
                    val isActive = selected == i
                    NavigationDrawerItem(
                        icon = { Icon(ic, contentDescription = name, tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = { Text(name, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal) },
                        selected = isActive,
                        onClick = { selected = i; scope.launch { drawerState.close() } },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                        )
                    )
                }
                Spacer(Modifier.weight(1f))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Theme toggle ──────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val prefs = com.ghosttype.utils.SettingsStore.prefs(LocalContext.current)
                    var uiTheme by remember { mutableStateOf(prefs.getString(com.ghosttype.utils.SettingsStore.KEY_UI_THEME, "dark") ?: "dark") }
                    val isLight = uiTheme == "light"

                    Text(
                        "Appearance",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Dark button
                        val darkBg = if (!isLight) MaterialTheme.colorScheme.primary else Color.Transparent
                        val darkText = if (!isLight) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        TextButton(
                            onClick = {
                                uiTheme = "dark"
                                prefs.edit().putString(com.ghosttype.utils.SettingsStore.KEY_UI_THEME, "dark").apply()
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = darkBg,
                                contentColor = darkText
                            ),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("🌙  Dark", style = MaterialTheme.typography.labelLarge, fontWeight = if (!isLight) FontWeight.Bold else FontWeight.Normal)
                        }

                        // Light button
                        val lightBg = if (isLight) MaterialTheme.colorScheme.primary else Color.Transparent
                        val lightText = if (isLight) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        TextButton(
                            onClick = {
                                uiTheme = "light"
                                prefs.edit().putString(com.ghosttype.utils.SettingsStore.KEY_UI_THEME, "light").apply()
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = lightBg,
                                contentColor = lightText
                            ),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("☀️  Light", style = MaterialTheme.typography.labelLarge, fontWeight = if (isLight) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Text(
                    "v${com.ghosttype.BuildConfig.VERSION_NAME}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 20.dp, bottom = 20.dp, end = 20.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color(0xFFFF8C00)
                            )
                        }
                    },
                    title = {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                            Text("GhostType Pro", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (selected) {
                    0 -> HomeScreen(onOpenImeSettings, onOpenAccessibility)
                    1 -> PlansScreen()
                    2 -> AutoTypeScreen(onPickTxt, onStartAutoType, onPause, onResumeAutoType, onStop)
                    3 -> PointerScreen()
                    4 -> ClipboardScreen()
                    5 -> com.ghosttype.ui.screens.FYTScreen()
                    6 -> com.ghosttype.ui.screens.MathScreen()
                    7 -> ResetScreen()
                    8 -> com.ghosttype.ui.screens.DeveloperScreen()
                    9 -> AboutScreen()
                }
            }
        }
    }
}
