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

## Adding another source

1. Implement `MangaSource` (mirror `MangaDexSource`) for the new provider.
2. Register it in `SourceRegistry`.
3. Add an entry to `ExtensionEngine.defaultSources` so it shows up in the Sources tab.

Note: Mihon/Aniyomi-style extension repositories (`mihon-extensions`, `keiyoushi`, ...) ship
installable APK extensions for many third-party (often scanlation) sites. Loading those is a
large runtime feature and those sites are frequently against their ToS — NekoRead deliberately
does not do that. Only sources with their own implementation in `data/source/` are real.

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
