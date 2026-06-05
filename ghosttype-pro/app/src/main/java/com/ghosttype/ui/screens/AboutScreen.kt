package com.ghosttype.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghosttype.R
import com.ghosttype.security.Obf
import com.ghosttype.BuildConfig
import com.ghosttype.security.ObfConstants
private val Orange             = Color(0xFFFF8C00)
private val DarkCard           = Color(0xFF1A1A1A)
private val DarkBorder         = Color(0xFF2A2A2A)

@Composable
fun AboutScreen() {
    val ctx         = LocalContext.current
    val ownerName   = remember { Obf.decode(ctx, ObfConstants.OWNER_NAME).ifBlank { "CHAND" } }
    val ownerTeam   = remember { Obf.decode(ctx, ObfConstants.OWNER_TEAM).ifBlank { "ATF Team" } }
    val instaUrl    = remember { Obf.decode(ctx, ObfConstants.INSTAGRAM_URL) }
    val waChannel   = remember { Obf.decode(ctx, ObfConstants.WA_CHANNEL_URL) }
    val waCommunity = remember { Obf.decode(ctx, ObfConstants.WA_COMMUNITY_URL) }
    val licenseLine = remember { Obf.decode(ctx, ObfConstants.LICENSE_LINE).ifBlank { "CHAND · ATF Team. All rights reserved." } }
    val youtubeUrl  = "https://www.youtube.com/@chandtricker"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        Spacer(Modifier.height(8.dp))

        // ── Hero card ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkCard)
                .border(1.5.dp, Orange.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(vertical = 28.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Ghost icon
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Orange.copy(alpha = 0.12f))
                        .border(2.dp, Orange.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👻", fontSize = 36.sp)
                }

                Text(
                    "GhostType Pro",
                    color = Orange,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    letterSpacing = 0.5.sp
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Badge(text = "v${BuildConfig.VERSION_NAME}", bg = Orange.copy(alpha = 0.15f), textColor = Orange)
                    Badge(text = "Android 7.0+", bg = Color(0xFF2A2A2A), textColor = Color(0xFFAAAAAA))
                }

                Text(
                    "Professional Keyboard · Auto-Type Engine",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Developer card ─────────────────────────────────────
        SectionHeader("Developer")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .border(1.dp, Orange.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Orange.copy(alpha = 0.18f))
                        .border(2.dp, Orange.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        ownerName.take(1).uppercase(),
                        color = Orange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(ownerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(ownerTeam, color = Color(0xFF888888), fontSize = 13.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                        Text("Active developer", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Features card ──────────────────────────────────────
        SectionHeader("Features")

        val features = listOf(
            "⌨️" to "IME Keyboard Service",
            "🤖" to "Auto-Type Engine",
            "🎨" to "Custom Themes",
            "🔤" to "36 Built-in Fonts",
            "📋" to "Clipboard History",
            "🌐" to "Multi-language",
            "📡" to "Floating Send Pointer"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
        ) {
            features.forEachIndexed { index, (emoji, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(emoji, fontSize = 18.sp)
                    Text(
                        label,
                        color = Color(0xFFE0E0E0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text("✓", color = Orange, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
                if (index < features.lastIndex) {
                    HorizontalDivider(
                        color = DarkBorder,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        // ── Connect section ────────────────────────────────────
        SectionHeader("Connect with us")

        // WhatsApp Channel
        SocialCard(
            icon          = painterResource(R.drawable.ic_whatsapp),
            platformName  = "WhatsApp Channel",
            handle        = "@GhostType Pro",
            badgeText     = "CHANNEL",
            accentColor   = Color(0xFF25D366),
            darkAccent    = Color(0xFF128C7E),
            onClick       = { openUrl(ctx, waChannel.ifBlank { "https://whatsapp.com" }) }
        )

        // WhatsApp Community
        SocialCard(
            icon          = painterResource(R.drawable.ic_whatsapp),
            platformName  = "WhatsApp Community",
            handle        = "@ATF Team",
            badgeText     = "COMMUNITY",
            accentColor   = Color(0xFF128C7E),
            darkAccent    = Color(0xFF075E54),
            onClick       = { openUrl(ctx, waCommunity.ifBlank { "https://whatsapp.com" }) }
        )

        // Instagram
        SocialCard(
            icon          = painterResource(R.drawable.ic_instagram),
            platformName  = "Instagram",
            handle        = "@chand.tricker",
            badgeText     = "FOLLOW",
            accentColor   = Color(0xFFE1306C),
            darkAccent    = Color(0xFF833AB4),
            onClick       = { openUrl(ctx, instaUrl.ifBlank { "https://instagram.com" }) }
        )

        // YouTube
        SocialCard(
            icon          = painterResource(R.drawable.ic_youtube),
            platformName  = "YouTube",
            handle        = "@chandtricker",
            badgeText     = "SUBSCRIBE",
            accentColor   = Color(0xFFFF0000),
            darkAccent    = Color(0xFFCC0000),
            onClick       = { openUrl(ctx, youtubeUrl) }
        )

        // ── Legal ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                "© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} $licenseLine",
                color = Color(0xFF666666),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Text(
                "Does not collect or transmit typing data",
                color = Color(0xFF444444),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = Orange,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun Badge(text: String, bg: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SocialCard(
    icon: Painter,
    platformName: String,
    handle: String,
    badgeText: String,
    accentColor: Color,
    darkAccent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(accentColor.copy(alpha = 0.13f), DarkCard)
                )
            )
            .border(1.5.dp, accentColor.copy(alpha = 0.50f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Platform icon — gradient square
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor, darkAccent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = icon,
                contentDescription = platformName,
                modifier = Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(Color.White)
            )
        }

        // Name + badge + handle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    platformName,
                    color = accentColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(accentColor.copy(alpha = 0.18f))
                        .border(0.5.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        badgeText,
                        color = accentColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Text(
                handle,
                color = Color(0xFFBBBBBB),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        }

        // Circular link button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.20f))
                .border(1.dp, accentColor.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("↗", color = accentColor, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun openUrl(ctx: android.content.Context, url: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {}
}
