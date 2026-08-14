# NekoRead

An Android manga reader (Kotlin + Jetpack Compose + Room), inspired by Mihon / Aniyomi / Tadami.

## Real sources, not dummy data

NekoRead loads **real manga, real chapters and real page images** through a pluggable source layer:

- `app/src/main/java/com/example/data/source/MangaSource.kt` — the source interface + `SourceRegistry`
- `app/src/main/java/com/example/data/source/MangaDexSource.kt` — working source backed by the official
  [MangaDex public API](https://api.mangadex.org/docs/swagger.html) (no auth needed):
  - search + "latest" catalog → `GET /manga`
  - manga details + cover → `GET /manga/{id}`
  - chapter list → `GET /manga/{id}/feed`
  - page image URLs → `GET /at-home/server/{chapterId}`

Flow: Browse → Catalog → tap a manga → details + real chapters load and cache into Room →
tap a chapter → the reader fetches real page URLs and renders them (webtoon / LTR / RTL).

Library, reading history, read-progress and categories all work against the Room database and are
backed by the real source data once a manga has been browsed / added to the library.

## Extensions (Mihon/Aniyomi/Tadami-style)

The **Extensions** and **Extension Repos** tabs are fully real, modeled after Mihon/Aniyomi/Tadami:

- **Repos** — add any extension repo by its index URL (or repo base URL — `index.json`,
  `repo.json`, `index.min.json` and bare-array legacy formats are all auto-detected). Adding,
  refreshing and deleting a repo actually fetches and parses the repo's real index; the extension
  count and "last updated" time come from the server, never from hardcoded numbers. Every repo can
  be deleted.
- **Extensions** — install actually downloads the extension's real APK into app-private storage,
  validates it like Mihon does (APK parses, package name matches the index, and the manifest
  declares the `tachiyomi.extension` feature / `tachiyomi.extension.class` marker) and then
  activates the extension's sources. Uninstall deletes the APK and its sources.
- **Sources** — the built-in MangaDex source plus the sources that ship inside installed
  extensions. Sources backed by `mangadex.org` are browsed through the app's real MangaDex
  implementation (search → details → chapters → read, all in-app). Other sources open the real
  site in an in-app WebView (Tadami-style), so you never leave the app.

Files: `data/extension/ExtensionNetwork.kt` (repo index parsing + APK download),
`data/extension/ExtensionEngine.kt` (defaults), plus the extension DAO / repository logic.

## Adding a native source

1. Implement `MangaSource` (mirror `MangaDexSource`) for the new provider.
2. Register it in `SourceRegistry`.
3. Add a row to `ExtensionEngine.builtinSource`-style seeds so it shows up in the Sources tab.

## Build

Open the project in Android Studio (API 24+, requires network access at runtime to reach
`api.mangadex.org` / `uploads.mangadex.org` / the image CDN).

## Automated builds & releases (GitHub Actions)

- **Debug build (every push to `main`)** — `.github/workflows/build.yml` compiles the app and pushes
  `Nekoread-debug.apk` to the **`build`** branch, and also attaches it to the run's artifacts.
  Download it straight from: `https://github.com/codegeasse1/Nekoread/raw/build/Nekoread-debug.apk`
- **Release** — go to the **Actions** tab → **Release APK** → **Run workflow**, enter a version
  (e.g. `1.0.0`) and optional notes. It builds a signed release APK, creates a GitHub Release with
  the APK attached, and drops a copy on the `build` branch too.

  Signing: if you set repo secrets `KEYSTORE_BASE64` (base64 of your `.jks`), `STORE_PASSWORD` and
  `KEY_PASSWORD`, releases are signed with your real key (required for in-place updates later).
  Without secrets, each release is signed with a freshly generated key; the keystore is attached to
  that run's artifacts and the password is printed in the log.

## Release schedule

Pre-release / WIP — builds from `main`.
