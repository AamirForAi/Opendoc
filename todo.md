# MJ PDF To-Do list

## 2.3.0 Targets
- [x] Add SETUP.md guide for building MJ PDF locally. ([!24](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/24))
- [x] Remove old dependencies and libraries ([!24](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/24), [!25](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/25))
- [x] Update SDK and dependencies ([!24](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/24), [!25](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/25))
- [x] Add Italian, Dutch, French, Simplified Chinese, Persian, and Polish translations. ([!26](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/26))
- [x] Add option to change/crop PDF margins.
- [x] Fix back button breaking when "volume key to turn page" is enabled.
- [ ] Investigate PDF corruption/rendering issues after files stay open for several hours.
- [x] Fix scroll-handle tap behavior.
- [x] Lower minimum auto-scroll speed; current speed `1` is still too fast.
- [x] Stop auto-scrolling when the user touches/interacts with the document.
- [x] Add option to default to Text Mode.
- [x] Add option to show/hide page numbers in the scrollbar knob.
- [x] Show reading percentage when tapping in fullscreen mode.
- [x] Make fullscreen mode truly edge-to-edge, including notch/cutout areas.
- [x] Show some metadata like time in fullscreen mode.
- [x] Add fullscreen shortcuts for Table of Contents and Go To Page.
- [x] Make fullscreen shortcuts/custom buttons configurable.
- [x] Make the second top-bar button configurable instead of reload-only.
- [ ] Remember per-document zoom.
- [x] Add way to return to Table of Contents after navigation.
- [x] Make TOC remember expanded/collapsed state.
- [ ] Remember per-document horizontal scroll lock.
- [x] Remember per-document auto-scroll speed.
- [x] Support RTL reading direction in horizontal mode.
- [x] Add search in TOC activity.
- [x] Improve text mode a little
- [ ] Investigate missing form checkboxes when printing.

## Reading State And Persistence
- [ ] Remember per-document orientation.
- [ ] Remember per-document zoom.
- [ ] Remember per-document zoom lock.
- [ ] Add import/export for settings, reading progress, and app data.

## Navigation And Reading Flow
- [ ] Improve Go To Page behavior, to show page thumbnails.
- [ ] Add navigation history for internal links, references, and TOC jumps.
- [ ] Add ability to jump back after clicking internal PDF references.
- [ ] Add browser-like scrolling mode.
- [ ] Improve physical-book navigation feel.

## Bookmarks And Library
- [ ] Add user bookmarks for saved page locations.
- [ ] Add expand/collapse all in ToC.
- [ ] Add Home page.
- [ ] Add user-controlled "mark as read" state, independent from progress.
- [ ] Add recently opened files.
- [ ] Add library sections such as folders, favorites, want-to-read, and finished.
- [ ] Add file info cards for library items.

## Text Mode, Search, And Reflow
- [x] Replace current Text Mode with a proper dedicated Text Mode page.
- [x] Add Reflow Mode.
- [ ] Fix hard line/sentence breaks in reflow/text mode?!
- [ ] Investigate WebView-based Text Mode.
- [x] Add support for multiple fonts in Text Mode.
- [ ] Add skip-empty-pages option in Text Mode.
- [x] Add live inline text selection.
- [ ] Add next/previous search-result navigation from the main reader.
- [x] Improve expanding search results after filtering.
- [ ] Search for words ignoring accents/diacritics. (`èéÈÉ=e`, `òóÒÓ=o`, `çč=c`, `ž=z`)

## PDF Features
- [ ] Add PDF metadata such as page size (mm, A4, etc) and embedded fonts.
- [ ] Add forms support.
- [ ] Add fillable forms support.
- [ ] Add handwritten/image signature support.
- [x] Add highlight functionality.
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