# Changelog

All notable changes to Nekoread.

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
