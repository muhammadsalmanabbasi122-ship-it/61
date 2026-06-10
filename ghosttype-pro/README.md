# GhostType Pro

> **PROPRIETARY & CONFIDENTIAL** — See [LICENSE](LICENSE) and [AI.md](AI.md).
> AI training, modification, and redistribution are strictly prohibited.

Custom Android Keyboard (IME) APK with Auto-Type Engine, themes, smart dictionary, macros, clipboard manager, multi-language (English / Urdu / Arabic), and **auto-saved sentences**.

- Language: Kotlin + Jetpack Compose (Material 3)
- Min SDK: 24 (Android 7.0) — Target SDK: 34 (Android 14)
- Architecture: MVVM + Room DB + Coroutines + WorkManager
- 100% offline (no internet permission)

## Build APK on GitHub (no PC needed)

This repo includes a GitHub Actions workflow that builds the APK in the cloud:

1. Push this folder (and the repo's `.github/workflows/android-build.yml`) to a GitHub repository.
2. Open the repo on github.com → **Actions** tab.
3. The workflow `Build GhostType Pro APK` will run automatically. You can also trigger it manually via **Run workflow**.
4. When the run finishes, scroll down to **Artifacts** and download `GhostTypePro-debug-apk.zip`.
5. Extract → install `app-debug.apk` on your phone.

## Build APK locally (Android Studio / CLI)

```bash
cd ghosttype-pro
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

## Enabling the keyboard

1. Install the APK.
2. Phone → Settings → Language & input → On-screen keyboards → Manage → enable **GhostType Pro**.
3. In any text field, long-press space → switch to **GhostType Pro**.
4. For Auto-Type into other apps: Settings → Accessibility → **GhostType Pro** → enable.

## Modules

1. IME Keyboard Service (QWERTY + symbols + numbers, long-press alternates, haptic + sound feedback)
2. Auto-Type Engine (.txt loader, 1–60 s delay, pause / resume / stop / loop / start-from-line, foreground notification, direct + paste modes)
3. Word suggestions & dictionary (built-in + user learning + next-word prediction)
4. Theme engine (6 built-in themes, key opacity / borders, custom font import)
5. Custom font system (.ttf/.otf, bold/italic, size slider)
6. Clipboard manager (auto-saves last 50 copies, pin, search)
7. Multi-language (English QWERTY, Urdu Phonetic, Arabic — Globe key cycles)
8. Settings app (Compose UI with all controls)
9. **Auto-Save Sentences** — every time you finish a sentence (`.`, `?`, `!`, `۔` or focus loss), it's automatically stored and ranked by usage. Browse / copy / delete from the Sentences tab.
