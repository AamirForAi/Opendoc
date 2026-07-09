# MJ PDF To-Do list

## 3.0.0 Targets
- [x] Add SETUP.md guide for building MJ PDF locally. ([!24](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/24))
- [x] Remove old dependencies and libraries ([!24](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/24), [!25](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/25))
- [x] Update SDK and dependencies ([!24](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/24), [!25](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/25))
- [x] Add Italian, Dutch, French, Simplified Chinese, Persian, and Polish translations. ([!26](https://gitlab.com/mudlej_android/mj_pdf_reader/-/merge_requests/26))
- [x] Add option to change/crop PDF margins.
- [x] Fix back button breaking when "volume key to turn page" is enabled.
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
- [x] Add way to return to Table of Contents after navigation.
- [x] Make TOC remember expanded/collapsed state.
- [x] Remember per-document auto-scroll speed.
- [x] Support RTL reading direction in horizontal mode.
- [x] Add search in TOC activity.
- [x] Improve text mode a little
- [x] Add ability to jump back after clicking internal PDF references.
- [x] Add forms support.
- [x] Add handwritten signature support.
- [x] Add highlight functionality.
- [x] Replace current Text Mode with a proper dedicated Text Mode page.
- [x] Add Reflow Mode.
- [x] Add support for multiple fonts in Text Mode.
- [x] Add live inline text selection.
- [x] Improve expanding search results after filtering.
- [x] Add Home page.
- [x] Add user-controlled "mark as read" state, independent from progress.
- [x] Add recently opened files.
- [x] Add library sections such as folders, favorites, want-to-read, and finished.
- [x] Add file info cards for library items.

## Others
- [ ] Add import/export for settings, reading progress, and app data.
- [ ] Improve Go To Page behavior, to show page thumbnails in the dialog.
- [ ] Add navigation history for internal links, references, and TOC jumps.
- [ ] Add browser-like scrolling mode.
- [ ] Add user bookmarks for saved page locations.
- [ ] Add next/previous search-result navigation from the main reader.
- [ ] Add mouse wheel support.
- [ ] Add mouse button support for next/previous page navigation.

## Development
- [ ] Add an easy way to submit translations.

## Won't do
- [ ] Add tap-for-next-page option.
- [ ] Add tap or double-tap option for toggling fullscreen mode.
- [ ] Show Android navigation bar with transparent background on tap in fullscreen mode.