# MJ PDF To-Do list

## 2.3.0 Targets
- [x] Add SETUP.md guide for building MJ PDF locally. ([!24](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/24))
- [x] Remove old dependencies and libraries ([!24](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/24), [!25](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/25))
- [x] Update SDK and dependencies ([!24](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/24), [!25](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/25))
- [x] Add Italian, Dutch, French, Simplified Chinese, Persian, and Polish translations. ([!26](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/26))
- [ ] Add option to change/crop PDF margins.
- [x] Fix back button breaking when "volume key to turn page" is enabled.
- [ ] Fix password-protected PDFs opened from file managers not showing the password prompt.
- [ ] Investigate PDF corruption/rendering issues after files stay open for several hours.
- [ ] Fix or verify fullscreen labels reappearing after theme changes.
- [ ] Fix or verify scroll-handle tap behavior.
- [ ] Review plaintext-session MITM risk and add a warning if applicable.
- [ ] Review `dependenciesInfo` handling for the next release.
- [ ] Lower minimum auto-scroll speed; current speed `1` is still too fast.
- [ ] Stop auto-scrolling when the user touches/interacts with the document.
- [ ] Add option to default to Text Mode.
- [ ] Add option to show/hide page numbers in the scrollbar knob.
- [ ] Show reading percentage when tapping in fullscreen mode.
- [ ] Add progress indicator for loading large files.
- [ ] Add setting to change the background color between pages.
- [ ] Make fullscreen mode truly edge-to-edge, including notch/cutout areas.

## Bugs
- [ ] Improve stability for very large files open for long sessions, especially 400MB+ files.
- [ ] Investigate missing form checkboxes when printing.

## Reading State And Persistence
- [ ] Remember per-document orientation.
- [ ] Remember per-document zoom.
- [ ] Remember per-document horizontal scroll state.
- [ ] Remember per-document zoom lock.
- [ ] Remember per-document horizontal scroll lock.
- [ ] Remember per-document auto-scroll speed.
- [ ] Add import/export for settings, reading progress, and app data.

## Navigation And Reading Flow
- [ ] Improve Go To Page behavior, to show page thumbnails.
- [ ] Add navigation history for internal links, references, and TOC jumps.
- [ ] Add ability to jump back after clicking internal PDF references.
- [ ] Add way to return to Table of Contents after navigation.
- [ ] Make TOC remember expanded/collapsed state.
- [ ] Support RTL reading direction in horizontal mode.
- [ ] Add browser-like scrolling mode.
- [ ] Add straight vertical scrolling when zoomed in.
- [ ] Improve physical-book navigation feel.

## Bookmarks And Library
- [ ] Add user bookmarks for saved page locations.
- [ ] Add search in bookmarks activity.
- [ ] Add expand/collapse all in bookmarks activity.
- [ ] Add Home page.
- [ ] Add user-controlled "mark as read" state, independent from progress.
- [ ] Add recently opened files.
- [ ] Add library sections such as folders, favorites, want-to-read, and finished.
- [ ] Add file info cards for library items.

## Fullscreen And Controls
- [ ] Show some metadata like date and time in fullscreen mode.
- [ ] Add fullscreen shortcuts for Table of Contents and Go To Page.
- [ ] Make fullscreen shortcuts/custom buttons configurable.
- [ ] Make the second top-bar button configurable instead of reload-only.

## Text Mode, Search, And Reflow
- [ ] Replace current Text Mode with a proper dedicated Text Mode page.
- [ ] Add Reflow Mode.
- [ ] Fix hard line/sentence breaks in reflow/text mode.
- [ ] Investigate WebView-based Text Mode.
- [ ] Add support for multiple fonts in Text Mode.
- [ ] Add skip-empty-pages option in Text Mode.
- [ ] Add live inline text selection.
- [ ] Add next/previous search-result navigation from the main reader.
- [ ] Improve expanding search results after filtering.

## PDF Features
- [ ] Add PDF metadata such as page size and embedded fonts.
- [ ] Add forms support.
- [ ] Add fillable forms support.
- [ ] Add handwritten/image signature support.
- [ ] Add highlight functionality.
- [ ] Add custom PDF theming.

## External Input
- [ ] Add mouse wheel support.
- [ ] Add mouse button support for next/previous page navigation.

## Development
- [ ] Add an easy way to submit translations.

## Won't do
- [ ] Add tap-for-next-page option.
- [ ] Add tap or double-tap option for toggling fullscreen mode.
- [ ] Show Android navigation bar with transparent background on tap in fullscreen mode.