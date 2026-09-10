# Changelog

All notable changes to Nekoread.

## [2.2.7] - 2026-09-09

### Reader performance (comix lag — take eight)
- **Page changes no longer recompose the whole reader.** The last log showed the UI thread blocked for 260-430ms on nearly every page bind, while a lone decode of the same pages cost 14-70ms — so the time was going into the compose/layout pass, not image decoding. `currentPage` was being read at the top of the reader composable (as the progress-save effect's key, and as an argument to the chrome), which makes every page change invalidate the entire reader recompose scope — one large unskippable subtree that re-runs the webtoon `AndroidView` update, every argument expression and every `remember`. Progress saving now observes the page via a snapshot flow (read inside a coroutine, not during composition) and the chrome reads the page through provider lambdas evaluated in its own scope, so a page flip updates the pill and nothing else.
- **`WebtoonRecyclerView.scaleTo` no longer forces a layout pass on every call** — it now requests a layout only when the view's own height actually changed. Continuous mode calls this on config updates, and the unconditional layout request re-measured and re-laid-out the page frames even when nothing about the scale had changed.
- **Diagnostics now measure the frame phases.** New `frame total=… input= anim= layout= draw= sync= cmd= swap=` lines (only for frames over 100ms) name which phase is eating the frame — layout/draw in the app process, or sync/cmd/swap waiting on the GPU — so the next report tells us whether we are CPU-recomposing or render-thread/GPU bound instead of leaving us to infer it.
- The periodic `memcache` line now also prints live heap (`heap=<used>/<max>MB`) next to the cache size, so the next log shows whether the stalls coincide with GC pressure from a pinned memory cache.

### Reader performance (comix lag — take seven)
- **The reader's diagnostics no longer cost frames.** Every page event was logged by opening, writing and closing the log file **on the calling thread** — usually the main thread inside the page-bind callback — and by rebuilding a 120-line snapshot and recomposing the on-screen overlay **on every single line**. During a fling that is dozens of synchronous disk writes and string builds per second on the UI thread, which is scroll jank in its own right and also inflates the reported `decoded …ms` times (the bind's success callback is delayed by however long the UI thread was blocked, so a 50ms decode can *report* as 600ms). Lines now go to a background writer thread in batches, and overlay updates are coalesced to about two per second.
- **The loop's bookkeeping maps are no longer Compose snapshot state.** `webtoonDownloaded` / `webtoonInFlight` / `downloadFailed` were `mutableStateMapOf` (snapshot state) written on every download start/finish — pure main-thread snapshot overhead, since only the prewarm loop ever reads them. They are plain maps now.
- **Page-decode concurrency is capped at the two the device can take.** Take six gave the memory-warm-ups their own dispatcher so a bind could never queue behind one, but that let **three** decodes run at once — and the dx9 log shows exactly what that costs: three decodes landing together took 644-722ms each, versus ~50ms for a lone decode (and 120ms each when the same three ran without a fling competing). Warms share the binds' two-wide dispatcher again, with at most one warm at a time, so a bind always has a free slot and the pool is never oversubscribed.
- **Warm-ups pause during a scroll gesture.** While you are dragging/flinging, both decode slots belong to the pages actually entering the viewport; the moment the gesture settles the decode-ahead window refills. A page that enters the viewport mid-gesture cold-decodes in ~50ms, so nothing is starved.
- The log now also records main-thread stalls (`ui stall …ms`) measured from the reader loop's own tick gap, so the next report can tell UI-thread jank apart from slow page decodes instead of both showing up as a big `decoded` time.

### Reader performance (comix lag — take six)
- **Page binds no longer queue behind background warm-ups.** The warm and the on-screen bind shared one small decode dispatcher, so during a fling a page scrolling in had to wait behind whatever warm decodes were in flight and its own decode was reported as 300-680ms, even though a lone decode of the same page costs ~60ms. Warm-ups now run on their own one-at-a-time dispatcher, so a bind is never blocked by a warm (and total decode concurrency stays bounded: 2 binds + 1 warm).
- **The decode-ahead window now grows to cover a fling.** It was a fixed 4 pages, which the log showed a fast fling still outran (pages 16-19 entered together and cold-decoded ~380ms each). The depth is now derived from the memory-cache capacity — roughly half the cache, one decoded page each, clamped to 4-8 — so a big-heap device gets a long runway while a small-heap one can't overflow the cache and thrash. The warm tick is also faster (50ms idle / 90ms while scrolling) so the window refills the moment you pause.
- **Memory cache given a floor** so a small-heap device still holds the whole decode-ahead window instead of evicting the nearest pages (a 128MB heap at the old 40% held only ~13 pages).
- The diagnostics log now prints the memory-cache pressure (`memcache max=…MB size=…MB`) every couple of seconds, and each warm miss records its decoded size — so the next log pinpoints whether pages are still being evicted.

### Reader performance (comix lag — take five)
- **The decoded page is now sized to fit the page cache.** A 1080-wide page is ~2.0M pixels (~7.9MB as a hardware bitmap), so on this device the cache held only ~4-6 of them — fewer than the pages kept warm ahead of you plus the ones on screen. The warm window therefore evicted itself and every page you scrolled onto had to be decoded cold from disk: the log showed 300-680ms per page (and ~420ms each when three landed at once) instead of the ~1ms cache hit it should be. Short pages are now decoded at a bounded ~1.0M-pixel budget (~4MB, sampled slightly — comparable to the ~800px sources that always scrolled smoothly), so the whole warm window fits the cache and scroll-ins go back to being instant hits. Pages already at or below the budget are untouched, and the on-screen display size is unchanged.
- **Page decodes no longer stampede each other.** At most two page decodes run at once, so a fling pulling several pages in can't have a burst of 4MB decodes all slow to ~400ms from contention.
- **The warm-up runs again, paced to the gesture.** Gating it off during a scroll was the wrong call: the log showed it then almost never ran, so nothing stayed warm. It now keeps filling the window continuously — ticking fast while you read, slower while a drag/fling is in progress so the scroll keeps the CPU.
- **Binds no longer touch the disk on the main thread.** The page's dimensions come from the download metadata instead of up to three bounds-decode file reads per bind.

### Reader performance (comix lag — take four)
- **The memory warm-up no longer decodes while you scroll**: warming ran continuously, and even though each warm was a small 20-80ms background decode (binds were already 1ms cache hits), on a low-end device that background decode load during a drag/fling is what still hitch-scrolled. The warm now runs only in the pauses between scrolls (when you're reading a page) — the read-pause is long enough to fill the pages-ahead window, and the scroll gesture itself has the CPU to itself.
- **Far flings no longer outrun the pre-download window**: the window was 20 pages ahead, so a fast fling past it (the log showed a jump from page 14 to 40 waiting ~525ms at bind for the download) hit a blank page. The window is now 40 pages ahead, so the pipeline stays in front of a fling.

### Reader performance (comix lag — take three)
- **Long manhwa strips now decode ONCE, whole, at the display width** and are shown as a single stable bitmap — no more per-chunk region decoding / recycling while you scroll, which was the remaining jank even after the chunked-view rewrite. The render thread tiles the single software bitmap and caches its textures, so a fling through a long strip is now just one cached image being drawn each frame.
- **Fixed the black bars breaking long strips**: the old chunked view drew each chunk into fixed 2048px slots while the chunk's real displayed height was smaller (power-of-two sampling almost always overshot the view width), leaving a solid black gap between every chunk — the "images are breaking" bars. Tall strips no longer use the chunked view at all except for pathological mega-strips, whose chunk slots are now equal and seamless (each chunk tiles its exact display slot, so there are never gaps).
- **Chunked fallback (mega-strips >40MB) is stable now**: decoded chunks are kept for the page's lifetime (no re-decode/upload churn when you scroll back), memory is bounded by recycling only the chunks farthest from the viewport, per-chunk failures no longer blank the page unless they happen where you're looking, and region decoding (which Android can only do into software bitmaps — the old "hardware chunk" attempt silently fell back to software every time) is done explicitly as ARGB_8888/RGB_565.

## [2.2.6] - 2026-09-09

### Reader performance
- **Tall webtoon strips (long manhwa pages) no longer fling through the subsampling view's on-demand tile decoding** — the remaining comix scroll lag. Long pages (height > 3x width) are now decoded as display-width chunks by a dedicated chunked view: only the chunks near the viewport decode (nearest the eye first), chunks that scroll far away are recycled, chunks are hardware (GPU) bitmaps on Android 8+, and all decode/window management runs off the draw path behind a global decode cap. Scrolling through long strips no longer pays per-frame tile-decode stalls, and a page never holds the whole strip in memory.
- **Modern-format pages (AVIF / JPEG-XL / HEIF) now decode as hardware bitmaps too** on the short-page path, closing the same texture-upload churn for sources that serve those formats (with an automatic software fallback).

## [2.2.5c] - 2026-09-09

### Reader performance
- **Short webtoon pages now decode as hardware (GPU) bitmaps**: previously every page was a software bitmap that the render thread had to re-upload to the GPU on its first draw — with several full-width pages entering the viewport during a fling that upload churn was the remaining scroll jank even when images were already preloaded. Hardware bitmaps draw for free and are still memory-cached, so the preload warm still hits. Border-cropped pages keep the software path (the crop decoder reads pixels back).

### Layout
- **Removed the double status-bar inset on the tab headers**: the main scaffold already pushes each screen below the status bar, but the floating header pill added a second inset inside itself — leaving a dead band of empty space above "Library (0)", "History & Updates", the "Search …" bar and the Settings header. Headers now hug the top of the screen, so more content fits.

## [2.2.5] - 2026-09-09

### Page bar
- The page-skipping bar now shows **one dot per page** (like Yomi) — you can see at a glance how many images the chapter has and exactly how much a tap/drag on the bar will skip.

### Reader fixes
- **Fixed the "Enhance" button doing nothing**: the contrast/saturation boost is now applied at draw time to the whole page — including pages that render through the subsampling decoder (paged modes and tall webtoon strips), where it previously had no effect. Toggling Enhance now updates the current pages instantly, in both paged and webtoon modes.
- **Fixed jittery webtoon scrolling on heavy sources (comix)**: pages now pre-size to their true strip height from cached metadata before entering the viewport, so the list no longer snaps/jumps as images decode while you scroll. The preload window was widened (20 pages ahead) and more pages download in parallel, so slow descrambled sources finish before a page scrolls into view.

### Settings
- About text now credits the **Mihon and Aniyomi** extension system, and a tappable **Developer: codegeasse1** link opens the GitHub profile.

## [2.2.4] - 2026-09-07

### New reader
- Brand-new reading engine: smooth, fast manga and manhwa reading with crisp page rendering and on-device page caching (pages download once, then scroll instantly â no re-fetching).
- **Long-strip (continuous vertical) mode** for webtoons/manhwa: pages flow together into one continuous strip you just scroll through.
- Page-by-page modes: left-to-right, right-to-left, and vertical paging.
- **Continuous reading across chapters**: scroll to the bottom and the next chapter loads automatically; jump with the next-chapter arrow and scrolling back up returns you to the previous chapter.
- Chapter navigation buttons (prev/next) that skip duplicate chapters from multi-scanlator sources.

### Reading controls & comfort
- **New "Enhance" button** in the bottom bar (next to Settings): boosts contrast and color so pages pop â applied instantly, no lag.
- Gestures: pinch to zoom, double-tap to zoom, configurable tap zones, page transitions.
- Real-time color options: grayscale, inverted colors, custom brightness, and color filters.
- Image quality settings (50/75/100%) to trade sharpness for smoother scrolling on heavy chapters.
- Auto-scroll, gap/smart-scale options, and long-strip border cropping.

### UI tweaks
- Redesigned compact top bars across the app.
- Improved search bars (Browse, Library, catalog search).
- Tighter, cleaner library and settings headers.

### Changes & fixes
- Border cropping is now **off by default**.
- Various reader stability and scrolling fixes.

## [Earlier] - 2.2.3 and before

- Initial public builds: extension source repos, library, chapter tracking, and the first reader.
