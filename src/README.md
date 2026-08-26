# Nexo — AI coding copilot chat with full internet access

A dark-themed chat app where the AI assistant can **search the web** and **read live pages**
to answer questions (especially current/verifiable facts: library versions, docs, news).

## How it works

- `main.pjs` holds all app logic (pjs arrow-function syntax — note: multi-line template
  literals are NOT supported in pjs; use array-of-lines + `.join("\n")`).
- `index.html` is the UI (header, message list, suggestion chips, input).
- `generateText` (ai-text-plugin) with a streaming `onChunk` handler.
- `superFetch` (super-fetch-plugin) does CORS-free fetches.

## Internet access — the marker protocol

The model is prompted to emit tool markers, each on its own line, when it needs facts:

```
[[SEARCH:query]]
[[FETCH:https://example.com/page]]
```

`runAgent()` loops: generate → scan the streamed text for markers → run the tools →
feed results back → continue generation (max 7 rounds). This is the classic
"pseudo tool-calling" loop used in Perchance.

- Search: Brave (`search.brave.com`, `div.snippet[data-type='web']`) primary, with
  Bing (`li.b_algo`, `cite` for clean URLs) and Mojeek (`a.title`) fallbacks. All three
  rate-limit/captcha under heavy load, hence the fallback chain.
- Fetch: reads any URL, extracts readable text (JSON → pretty-printed, HTML → stripped of
  script/style/nav etc.), truncated to ~14KB.
- Images: `[[IMAGES:query]]` → `bingImages()` scrapes `a.iusc` elements from Bing Images
  (the JSON `m` attribute yields `murl`/`turl`/`t`/`purl`); the 8 thumbnails (Bing CDN
  `tse*.mm.bing.net`, hotlink-friendly) render as a `.gallery` grid in the chat bubble,
  each linking to the full-size image. `needsImages()` can force a round-1
  `startWith: "[[IMAGES:"` for explicit "show me images of X" requests. Galleries persist
  with the chat via a `[[GALLERY:json]]` tag appended to the stored message
  (`stripGalleryTag()` removes it from the model's context and re-renders it on reload).

## Live viewer count

Uses the server-plugin. The server script (first `<script type="text/x-server-plugin">`
element in index.html) keeps a `Set` of connected conn ids, and on every join/leave
publishes the count to the `presence` pubsub topic (`conn.subscribe("presence")` on open).
Clients show it as the green-dot pill in the header ("N online"). `initPresence()` in
main.pjs handles reconnect with capped exponential backoff; `4403` closes permanently
(offline, no reconnect loop). Works in the unsaved editor via the local emulator
(this tab counts as 1); saved, it counts real live users. Note the editor preview is
single-document, so "N online" only exceeds 1 after the generator is saved.

## Speed design

Three layers keep answers fast while keeping internet access real:

1. **Instant date/time fast-path** — `dateAnswer()` in `sendMessage()` detects pure
   date/day/time questions and answers straight from `new Date()` in ~3ms, no LLM, no search.
2. **TODAY line injection** — every prompt gets `TODAY: <current date | local time>` right
   before the TASK (dynamic, near the end, so it never breaks the prefix cache), and the
   system prompt tells the model it never needs to search for the date/time.
3. **Deterministic search trigger** — `needsSearch()` scans the user's message for
   "current info" signals (latest/version/release date/news/price/20xx+release...) and when
   it fires, `runAgent` round 1 is forced to open with `startWith: "[[SEARCH:"` so the model
   must search immediately (no deliberation round). Everything else answers in one fast LLM
   call; the system prompt softens tool use to "only when it truly adds value".

Result: plain questions ~4s, current-version questions ~7s (search + cached-prefix answer),
date questions ~instant. Tool markers also run in parallel (capped at 3) and the round cap
is 5.

## Conversation state

- **Multi-chat with persistence.** All conversations live in `window.__nexo.chats` (array of
  chat objects `{id, title, createdAt, updatedAt, messages, summary, ...}`) with
  `window.__nexo.activeId` pointing at the active one. `agentState()` returns the active chat
  object (so the message log/summary/compacting logic is unchanged per-chat).
- Persistence: the whole chats array is saved to `localStorage` (`nexo_chats_v1`) on every
  mutation (user msg, bot reply, new chat, delete, switch) and loaded in `initApp`.
  Nothing is deleted unless the user explicitly deletes a chat (`deleteChat()` with confirm).
  `newChat()` starts a fresh conversation without touching old ones.
- Prefix-cache-friendly prompts: fixed system prompt first, then optional summary block,
  then the verbatim append-only message log, then the TASK at the end.
- `maybeCompact()` folds old messages into a rolling summary when the prompt nears the
  token budget (background, never awaited by the user).

## UI features

- Streaming replies with typing indicator; status pill shows "Searching/Fetching …".
- Quick mode (terse answers), Internet toggle (disable tools), Stop, Copy, Regenerate.
- Chat history sidebar: "New chat" button (header + sidebar), click to switch chats,
  per-chat delete (✕). On desktop the sidebar is fixed; on ≤760px it slides in over an
  overlay, toggled by the ☰ button. "New chat" is also always in the header.
- Markdown renderer (`renderMarkdown`): headings, bold/italic, inline code, fenced code
  blocks, lists, blockquotes, links (links/HTML are escaped before injection).
- Responsive: flex column app filling 100dvh, chips wrap, tested at 390px + desktop.

## GitHub sync

The octocat button in the header opens a "Commit to GitHub" modal (`githubLoad` /
`githubSave` / `githubCommit` / `doGithubCommit` in main.pjs). It pushes the
generator's source to a GitHub repo via the git-data API (blob → tree → commit →
ref) using the user's personal access token.

- Files committed are listed in `githubFiles()` — currently `main.pjs`,
  `index.html`, `src/README.md`. Add new `src/` files there.
- The page reads its own source through Perchance's APIs (main.pjs via
  `api/getGeneratorsAndDependencies`, index.html via `api/getGeneratorHtml`
  through superFetch, src/ via plain relative fetch) — these return the SAVED
  version, so the modal blocks committing while `window.generatorIsUnsaved`
  is true (hit Save first).
- The token is stored in `localStorage` (`nexo_github_v1`) on the user's device
  only — it must never go into main.pjs/index.html (public source).
- Handles empty repos (creates the initial ref) and preserves unrelated files in
  the repo's tree.
- Owner/Repo fields tolerate full pasted URLs (e.g. `https://github.com/user/repo`):
  `githubNormalizeOwnerRepo()` extracts the pieces. If Owner is left blank (or holds
  a non-username link), the commit resolves the authenticated username via `GET /user`.
- `doGithubCommit` never overwrites a stored token with an empty input (only the
  dedicated clear-token button removes it), so tokens survive reloads.

## Note

ai-text-plugin shows an ad for non-logged-in users. Chat history is per-device
(localStorage) — could be extended to kv-plugin later for cross-device sync.
