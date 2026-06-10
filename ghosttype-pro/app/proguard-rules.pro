# ============================================================
# GhostType Pro — R8 / ProGuard rules (v1.9)
# ============================================================
# Goals:
#   1. Strip debugging metadata so a decompiler can't easily map
#      class / method names back to source.
#   2. KEEP everything Android loads by name (manifest entries,
#      Room reflections, Compose runtime symbols).
#   3. Don't break OkHttp / Compose with overly aggressive options.
# ============================================================

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes !LocalVariableTable,!LocalVariableTypeTable,!SourceFile,!LineNumberTable

# Allow R8 to widen access modifiers when it shrinks (safe with our code).
-allowaccessmodification

# ===== Manifest entry points (loaded by name) =====
-keep class com.ghosttype.ime.GhostTypeIMEService { *; }
-keep class com.ghosttype.ime.GhostTypeAccessibilityService { *; }
-keep class com.ghosttype.ime.AutoTypeForegroundService { *; }
-keep class com.ghosttype.ime.FloatingPointerService { *; }
-keep class com.ghosttype.utils.BootReceiver { *; }
-keep class com.ghosttype.ui.MainActivity { *; }
-keep class com.ghosttype.GhostTypeApp { *; }

# ===== Security module =====
# ObfConstants is generated at build time and accessed by name from
# the keep'd Obf object — keep both intact so signature pinning + URL
# decryption still works after R8 obfuscation.
-keep class com.ghosttype.security.ObfConstants { *; }
-keep class com.ghosttype.security.Obf { public static *; }
-keep class com.ghosttype.security.SecurityGuard { public static *; }
-keep class com.ghosttype.security.Hardener { *; }
-keep class com.ghosttype.security.PastebinSecrets { *; }
-keep class com.ghosttype.security.CrashGate { *; }
-keep class com.ghosttype.security.UpdateGate { *; }
-keep class com.ghosttype.security.ApprovalGate { public *; }
-keep class com.ghosttype.security.ApprovalGate$State { *; }
-keep class com.ghosttype.security.ApprovalGate$State$* { *; }
-keep class com.ghosttype.security.ApprovalRefreshWorker { *; }
-keep class com.ghosttype.ui.BrickedActivity { *; }

# ===== Utility classes (called from GhostTypeApp / GatedApp / MainActivity) =====
-keep class com.ghosttype.utils.** { *; }
-keep class com.ghosttype.ime.AutoTypeEngine { *; }
-keep class com.ghosttype.security.DeviceId { *; }
-keep class com.ghosttype.ui.screens.** { *; }
-keep class com.ghosttype.ui.theme.** { *; }

# ===== Room (uses reflection on entities + DAOs) =====
-keep class com.ghosttype.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ===== Compose runtime (defensive) =====
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ===== OkHttp / Okio / TLS providers =====
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ===== Kotlin metadata =====
-keepclassmembers class **$Companion { *; }
-keep class kotlin.Metadata { *; }

# ===== Coroutines internals =====
-keepclassmembernames class kotlinx.** { volatile <fields>; }
