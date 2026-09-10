# Changelog

All notable changes to Nekoread.

## [2.2.7] - 2026-09-09

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
