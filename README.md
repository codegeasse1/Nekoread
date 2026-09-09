# Nekoread — automatic builds

This branch holds APK files produced by GitHub Actions. Install by downloading
the `.apk` onto your Android device and opening it (allow "install from unknown sources").

- `Nekoread-debug.apk` — latest debug build from `main`. Auto-updated on every push.
- `build-error.log` — present when the latest build failed (contains the Gradle error).

Latest release APKs (if any) appear here too, but the official release downloads
are on the Releases page: https://github.com/codegeasse1/Nekoread/releases

If the repo has the real signing key configured (KEYSTORE_BASE64 + STORE_PASSWORD +
KEY_PASSWORD secrets), the debug APK is signed with that stable key and updates install
in-place. Without them it's signed with a throwaway debug key (reinstall to update).
