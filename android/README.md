# Onix for Android

The Onix app for Android phones: it runs Minecraft: Java Edition with Fabric and the Onix mod.

It is **Zalith Launcher** (https://github.com/ZalithLauncher/ZalithLauncher, a PojavLauncher fork, GPL-3.0) with
the Onix changes on top. This folder only holds those changes; `apply.py` fetches Zalith at the commit in
`zalith.commit` and applies them:

| What | Where |
|---|---|
| App name "Onix", package `dev.onix.android` (installs next to Zalith), Onix icon, version from `launcher/package.json` | `apply.py` |
| First start: creates the game version **Onix** (Minecraft 1.21.11 + newest stable Fabric Loader, own folder) | `overlay/…/onix/OnixSetup.kt` |
| Before every start of a Fabric version: newest Onix mod for that Minecraft version (GitHub releases) and Fabric API (Modrinth) | `OnixSetup.syncMods` |
| App updates: `releases/latest/download/onix-android.json` → dialog → download → Android installer | `overlay/…/onix/OnixUpdate.kt` |

## Building

GitHub Actions (`.github/workflows/android.yml`) builds two APKs on every push that touches `android/`:
`all` (every processor) and `arm64-v8a` (almost every phone from the last years, smaller). On `main` (or when the
workflow is started by hand) they are added to the release of the current launcher version together with
`onix-android.json`, so installed apps find the update.

Locally: `python3 android/apply.py`, then in `android/build/zalith`:
`./gradlew jre_lwjgl3glfw:build` and `./gradlew ZalithLauncher:assembleRelease -Darch=arm64` (Android SDK + NDK 25.2.9519653, JDK 17).

## Signing

Android only installs an update that is signed with the same key as the installed app. Without the secrets below
the APK is signed with Zalith's public debug key – fine for testing, but anybody could sign an "update" with it.
For real releases create a key once and keep it forever:

    keytool -genkeypair -v -keystore onix.jks -alias onix -keyalg RSA -keysize 4096 -validity 36500

and add these repository secrets: `ONIX_ANDROID_KEYSTORE` (the file as base64: `base64 -w0 onix.jks`),
`ONIX_ANDROID_KEYSTORE_PASSWORD`, `ONIX_ANDROID_KEY_ALIAS` (`onix`). Switching from the debug key to your own key
means installing the app once more by hand (uninstall first – export worlds you want to keep).

## License

The app is GPL-3.0 like Zalith Launcher. Whoever gets the APK must be able to get its source: this folder plus
the Zalith commit above (the repository is public).
