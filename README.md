<div align="center">

# NekoRead

**A fast, modern manga & manhwa reader for Android.** Browse catalogs, read chapters, and keep a library — all through pluggable sources that run inside the app.

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://github.com/codegeasse1/Nekoread)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://github.com/codegeasse1/Nekoread)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://github.com/codegeasse1/Nekoread)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-339933?logo=android&logoColor=white)](https://github.com/codegeasse1/Nekoread)
[![Download](https://img.shields.io/badge/Download-APK-success)](https://github.com/codegeasse1/Nekoread/raw/build/Nekoread-debug.apk)

</div>

---

## About

NekoRead is a free, open-source manga reader for Android, written in **Kotlin** with **Jetpack Compose** and **Room**.

It loads manga, chapters and page images through a pluggable source layer. Browse, search, read and track everything against live source data.

## Features

### Reading
- **Webtoon long-strip reader** with automatic chapter continuation — keep scrolling and the next chapter loads seamlessly
- **Vertical with gaps**, **vertical-paged**, **left-to-right** and **right-to-left** reading modes
- **Fit screen / fit width / fit height** page fitting
- **OLED black**, dark, cream and white reader backgrounds
- **Fast page loading** — nearby pages are prefetched into memory so scrolling feels instant
- **Immersive mode** — status and navigation bars hide while reading and reappear when you tap
- **Resume where you left off**, tracked per chapter and per page

### Sources & extensions
- **Built-in MangaDex source** using the official public API (no account needed)
- **Extension repositories** — add community repos by index URL (`index.json`, `repo.json`, `index.min.json` and legacy formats are auto-detected)
- **Extensions install as APKs** into app-private storage and their sources go online instantly — uninstalling removes them cleanly
- **In-app browsing** — search, catalog, details, chapters and reading all run inside the app through the extension engine
- **Cloudflare helper** — a built-in site-verification dialog for sources behind Cloudflare walls

### Library
- Add manga to your library from any source
- Reading history and **read progress**
- **Categories** to organize your collection
- Everything backed by a local **Room** database

## Download

Get the latest debug build:

[**⬇ Download Nekoread-debug.apk**](https://github.com/codegeasse1/Nekoread/raw/build/Nekoread-debug.apk)

> **Requires Android 7.0 (API 24) or newer.** When your browser asks, allow installing apps from unknown sources.
>
> APKs are signed with NekoRead's signing key, so you can update in place once installed.

## Getting started

1. Install the APK and open **NekoRead**.
2. Browse the built-in MangaDex catalog — or add an extension repository and install the sources you want.
3. Tap a manga → pick a chapter → read. Your progress is saved automatically.

## Building from source

Open the project in **Android Studio** (JDK 17, API 36 SDK). The app needs network access at runtime to reach MangaDex and your installed extension sources.

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Adding a native source

1. Implement `MangaSource` (mirror `MangaDexSource`) for the new provider.
2. Register it in `SourceRegistry`.
3. Add a row to the `ExtensionEngine` built-in-source seeds so it shows up in the Sources tab.

## Releases

- **Debug builds** — every push to `main` triggers the GitHub Actions build, which compiles the app and pushes `Nekoread-debug.apk` to the **`build`** branch (also attached to the run's artifacts).
- **Release builds** — run the **Release APK** workflow from the Actions tab with a version number to build a signed APK and publish a GitHub Release.

## Privacy

- All browsing and reading happens **directly between your device and each source's servers**.
- Your library, history and progress stay **on your device** in a local database.
- **No accounts, no analytics, no tracking.**
