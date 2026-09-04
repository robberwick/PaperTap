# PaperTap: Refactoring, Upgrade & UX Improvement Report

**Date:** 2026-09-04

**Scope:** 30 Kotlin files (~5,000 lines), 17 layouts, menus, build config, manifest, and `res/raw` data analyzed. The NFC protocol implementation was cross-checked against the proxmark3 `cmdhfwaveshare.c` reference — the WaveShare demo-derived source the codebase itself cites. ~115 findings total, organized below by severity and theme.

## Executive summary

- Only the default 1.54" B display works end-to-end; every other WaveShare model is broken (wrong model byte, wrong dimensions, or contradictory config tables) and the display-size setting to reach them is unreachable from the UI anyway.
- Room's destructive migration (`fallbackToDestructiveMigration()`, zero `Migration` objects) guarantees silent data loss on the next schema bump.
- `AddTicketActivity`/`EditTicketActivity` are ~60% duplicated (≈330 of 466 Edit lines are copy-pasted from Add), and the divergence has already produced real bugs: Edit wipes stored station data that Add would have preserved, and validation behavior differs between the two screens.
- The add→flash user journey dead-ends: after "Ticket added!" the user is dropped on the list and must locate and tap the new ticket manually — there is no direct path into the flash flow.
- The NFC write path has unenforced tag validation (any NfcA tag gets NDEF written to it before the handshake even runs), an uncapped wait loop with no timeout on the 1.54" B path, and a rotation-unsafe threading model that can leak activities, lose flash-history writes, or double-write to a tag.
- Several correctness bugs are silent: swipe-to-delete can crash on `NO_POSITION`, undo loses display mappings on cascade delete, flash counters race under concurrent writes, and a duplicate-detection heuristic reports success when nothing was saved.
- Architecture has real duplication beyond the forms: three copy-pasted swipe-to-delete `ItemTouchHelper` blocks, three copy-pasted favorite-journey dialog implementations, and three near-identical packet-loop bodies in the NFC writer.
- Performance issues are concentrated in hot paths: `TicketAdapter` recomputes "most recent ticket" on every bind and runs an unstructured per-bind coroutine that can overwrite a rebound row with stale data; `StationLookup` parses a 2,970-entry, ~500KB JSON file on the main thread from four different activities' `onCreate`.
- The dependency and build setup is stale (deps pinned a few minor versions back, kapt instead of KSP, no CI, `minifyEnabled false` in release, a wildcard `junit:junit:4.+` version, and instrumented tests that still assert the old package name and would fail if run).
- `CLAUDE.md` and the README both describe an architecture that no longer exists — a different entry activity, a removed proprietary JAR, SharedPreferences-based persistence that Room replaced, and a display-size setting with no UI to reach it.

## Critical bugs (correctness)

| ID | Severity | Finding | Evidence | Fix |
|---|---|---|---|---|
| A1 | Critical | Display-model collapse — only default 1.54" B works; every other WaveShare model broken or unreachable. Three conflicting tables: `Constants.kt:13-39` `ScreenSizes` (9 entries) + `ScreenSizesInPixels`, vs `waveshare/DisplayConfig.kt:38-65` (8 types; comments contradict own data — index 2 labeled "2.13 V2" holds 2.9" dims; index 7 labeled "2.13 B (296x128)" but real 2.13B is 250x122). `Preferences.kt:28-31` `getScreenSizeEnum() = ScreenSizes.indexOf(size)+1` for "4.2 v.B" → 9 → `forDisplayType` (1..8) returns null → guaranteed DIMENSION_MISMATCH (`WaveShareNfcWriter.kt:65`). "2.13"" bitmap generated 250x128 (`NfcFlasher.kt:609` via `ScreenSizesInPixels`) but `DisplayConfig` expects 250x122 → `validateDimensions` always fails. Model-select byte wrong for all standard displays: `WaveShareNfcWriter.kt:153` sends `config.commandByte` (19/103/123/124/77/127 — actually frame lengths) as the `0xCD 0x00 <model>` byte; proxmark3 reference gives valid bytes as 4=2.13V2, 7=2.9, 10=4.2, 14=7.5V2, 16=2.7, 5=2.13BC, 17=7.5HD, none for 1.54B (whose path omits the byte correctly at `:301` — hence it's the only one that works). 2.7" second pass sends `redLayerData` (all zeros, single-layer) instead of the real image (`WaveShareNfcWriter.kt:252`); proxmark sends real data on second pass. Picker unreachable: `Preferences.showScreenSizePicker`/`setScreenSize` have zero callers; `preferences.xml` has no display-size entry; README documents the setting as if it exists. | `Constants.kt:13-39`, `waveshare/DisplayConfig.kt:38-65`, `Preferences.kt:28-31`, `WaveShareNfcWriter.kt:65,153,252,301`, `NfcFlasher.kt:609` | One `DisplayModel` enum (label, model byte, width, height, protocol params) driving picker + bitmap generation + write path. ListPreference wired into settings. Ship all sizes cross-checked against the proxmark3 reference; gate unverified sizes behind an experimental toggle until confirmed on hardware. |
| A2 | Critical | Destructive Room migration guarantees data loss. | `TicketDatabase.kt:8` (`version = 8, exportSchema = false`), `:26` (`fallbackToDestructiveMigration()`), zero `Migration` objects | `exportSchema = true` + `schemaLocation` + real `Migration` objects; restrict destructive fallback to debug builds only. |
| A3 | Critical | Infinite polling loop on the 1.54" B write path — never-done condition leaves the write thread alive forever. | `WaveShareNfcWriter.kt:359-366` (`while (true)` polling `0xCD 0x08` every 500ms, no attempt cap — the standard path caps at 100 attempts at `:283`; proxmark caps at 50); consequence: `mIsFlashing` stuck, `KEEP_SCREEN_ON` stuck, progress thread spins (`NfcFlasher.kt:334-345`) | Cap attempts + add a timeout, matching the standard path's 100-attempt cap. |
| A4 | Critical | Tag validation is computed but never enforced; NDEF is written to any NfcA tag before the handshake runs. | `NfcFlasher.kt:278-301` (`aarFound` only logged); `WaveShareUIDs` (`Constants.kt:5-8`) referenced nowhere; `writeNdefRecord` (`WaveShareNfcWriter.kt:101-145`) writes NTAG pages 4-14 to any tapped tag before the handshake. Proxmark requires UID exactly `FSTN10m`/`WSDZ10m`, with `FSTN10m` valid only for 1.54B. Additionally the read-compare spans 48 bytes but only 44 are written, so `contentEquals` is always false — ~11 wasted transceives and flash wear on every single write. | Enforce the UID/AAR gate with a visible reject + error sound; write NDEF only after a successful handshake; compare only the actually-written byte range. |
| A5 | Critical | Rotation during flash can leak the activity, lose flash-history writes, or double-write to a tag. | `NfcFlasher.kt:322-427` — raw `Thread` captures the Activity; a new instance sets `mIsFlashing=false` while the old write is still in flight, allowing a concurrent second write; the success-path `lifecycleScope.launch` (`recordFlashEvent`/display tracking, `:390-407`) is cancelled on destroy, silently dropping history. | Move to a ViewModel-scoped coroutine so the write survives configuration changes and history writes aren't tied to the destroyed lifecycle owner. |
| A6 | Critical | `TicketAdapter` per-bind unstructured coroutine race can display wrong data on screen. | `TicketAdapter.kt:139-186` — `CoroutineScope(Dispatchers.IO).launch` inside `bind()`, never cancelled; a stale result can overwrite a rebound row's display label; also leaks the scope. | Cancel a per-holder job on rebind, or better, fold the display label into a joined `@Transaction` query so there's no per-row async work at all. |
| A7 | High | Intent re-handled on rotation re-launches `AddTicketActivity`. | `TicketListActivity.kt:109` — `handleIncomingIntent(intent)` called unconditionally in `onCreate` | Guard with `savedInstanceState == null`, or consume the intent after first handling. |
| A8 | Medium | Wrong `NfcA` instance closed; the actually-connected tech is never closed. | `NfcFlasher.kt:320-329` connects `nfcObj`; `:348` creates a second instance `tntag`; `:413` closes `tntag` instead. No `close()` API exists on the writer. | Add a `close()` API to the writer and call it in a `finally` block. |
| A9 | High | Undo after delete loses display mappings. | Cascade delete removes mappings (`TicketDisplayMapping.kt:14-16`); undo re-inserts only the ticket (`TicketListActivity.kt:265-277`). | Snapshot and restore mappings on undo, or defer the actual delete until the snackbar timeout expires. |
| A10 | Medium | Non-atomic flash counters lose counts under concurrent flashes. | `TicketRepository.kt:89-111` — read-modify-write pattern | Use an atomic `UPDATE ... SET flashCount = flashCount + 1`. Also: `flashHistory` JSON column (`TicketEntity.kt:27`) is written but never read and grows unbounded — delete it. |
| A11 | Medium | Shared `StationAdapter` instance used for both origin and destination fields causes wrong station selection. | `ManageFavoriteJourneysActivity.kt:194-197` — one adapter/filter serving both fields | Use two separate adapter instances, matching the already-correct pattern in `AddTicketActivity.kt:416-419`. |
| A12 | Low | `!!` NPE risk on a legacy stored screen-size value. | `Preferences.kt:29,35` | Validate on read and reset to default if the stored value is no longer valid. |
| A13 | Medium | `NO_POSITION` crash risk on swipe-to-delete. | `TicketListActivity.kt:261-262`, `ManageFavoriteJourneysActivity.kt:152-153`, `ManageDisplaysActivity.kt:167-168` | Bail out early whenever `RecyclerView.NO_POSITION` is observed. |
| A14 | Medium | Duplicate-detection-after-save is broken and can report success when nothing was saved. | `AddTicketActivity.kt:611-631` — `insertTicket` returns the existing ticket's id on a duplicate; a `ticket.userLabel != label` heuristic reports "already in collection" only when labels differ, so matching labels show "Ticket added!" though nothing was added (currently unreachable in practice because an earlier check blocks it, but latent). | Return a sealed result / sentinel from `insertTicket` and delete the label-comparison heuristic entirely. |
| A15 | High | No state retention in Add/Edit across rotation. | Rotation re-runs `processDocument(intent.getStringExtra("DOCUMENT_URI"))` (`AddTicketActivity.kt:104-109`) — re-downloads/re-parses the PDF, re-shows the duplicate dialog, and can hit temp-file re-read failures. Edit re-loads from Room on rotation and **wipes any unsaved edits** (`EditTicketActivity.kt:101-103`). | Introduce a shared ViewModel holding form fields plus the extracted raw data/bitmap, surviving configuration changes. |
| A16 | High | Edit can silently erase stored station data. | `EditTicketActivity.kt:372-396` `updateJourneyDisplay` requires **both** stations to be present (Add correctly handles the one-sided case, `AddTicketActivity.kt:587-607`); a ticket with only an origin shows "Tap to set journey" even though data exists, and Save then copies `selectedOriginStation?.code` → `null`, wiping the field. `populateFields` (`EditTicketActivity.kt:118-127`) also silently drops unknown station codes not present in the lookup table. | Apply per-field logic matching Add's handling, plus a `Station(code, code)` fallback for unknown codes. |
| A17 | Medium | Journey dialog observer leak on every open. | Every dialog open registers a new `allFavorites.observe(this)` (`AddTicketActivity.kt:536`, `EditTicketActivity.kt:481`), holding the dismissed dialog's adapter/views for the Activity's entire lifetime. | Observe once in `onCreate`, or move the dialog to a `DialogFragment` using `viewLifecycleOwner`. |
| A18 | Medium | Non-cancelable `AlertDialog` shown from a coroutine risks a window leak. | `checkForDuplicateAndAlert` (`AddTicketActivity.kt:315-344`) — window leak if the activity is destroyed before the coroutine resumes | Move to a `DialogFragment`, or guard with an `isFinishing` check before showing. |

## Architecture & refactoring upgrades

### 4.1 Display-model unification (A1, B1)

One `DisplayModel` enum should replace `Constants.ScreenSizes`/`ScreenSizesInPixels` and `waveshare/DisplayConfig.kt` entirely. The field currently named `commandByte` (`WaveShareNfcWriter.kt`) is misnamed — it actually holds frame length, not a command byte — and should be split into explicit `modelByte`, `frameLength`, and other protocol fields on the new enum. The writer currently has three near-identical copy-pasted packet-loop bodies (`WaveShareNfcWriter.kt:184-201, 233-247, 250-261`) that should collapse into one generic helper; the current protocol-variant selection by magic width comparisons (`:207-217`, `:223-262`) should switch to explicit dispatch on the enum.

### 4.2 NFC write path (B2, B3)

`NfcFlasher` currently mixes a raw `Thread`, a 10ms progress poller whose result is discarded (`@Suppress("UNUSED_PARAMETER")`, `NfcFlasher.kt:478-482`), audio threads, and `lifecycleScope` coroutines. ZXing barcode generation runs on the main thread on every resume (`:222-224`), and there's a 250ms binder-polling health check (`:88-94, 437-460`). This should collapse to a single coroutine with a progress `StateFlow` fed by the writer's already-existing real 0-100 progress (`writer.progress`), a bitmap cache keyed by `(ticket, settings)`, and foreground-dispatch control driven by adapter state callbacks instead of polling. Separately, a failed flash currently leaves the display powered on: the power-off command (`0xCD 0x04`) is only sent on success (`WaveShareNfcWriter.kt:277-279`) and failure paths return without cleanup or a retry affordance — power-off should move into a `finally`, and a Retry action should be surfaced.

### 4.3 Activity/form consolidation (B4, B5)

Favorite-journey dialog logic — default label generation, the 50-entry cap, and the insert itself — is triplicated across `AddTicketActivity.kt:659-690`, `EditTicketActivity.kt:428-455`, and `ManageFavoriteJourneysActivity.kt:234-260`. This should become one `FavoriteJourneyRepository.addFavoriteWithLimit()` plus a shared ViewModel. More significantly, Add/Edit are ~60% duplicated — roughly 330 of `EditTicketActivity`'s 466 lines are copy-pasted from Add (`showJourneyDialog`: Edit `216-370` vs Add `432-585`; `showDateDialog`, `showSaveFavoriteDialog`, `updateSaveFavoriteButtonVisibility`, and `onCreate` wiring are byte-identical), and the layouts are ~95% identical. This divergence has already caused real bugs (A16, and an empty-label validation drift, see C11). Fix: a shared `TicketFormFragment` (or `BaseTicketFormActivity`) plus a `JourneyPickerDialogFragment` and an `<include>`d shared layout, removing ~350 lines and one entire layout file. Along the way: both activities use fully-qualified references instead of imports (`AddTicketActivity.kt:41`, `EditTicketActivity.kt:36`), and both carry a dead `var favoritesCount` (`AddTicketActivity.kt:529-530`, `EditTicketActivity.kt:474-475`).

### 4.4 Data layer (B7, A2, A9, A10)

`getMostRecentTicket` currently loads every ticket to find one (`TicketRepository.kt:80-83`) — needs a `LIMIT 1` query. `findDuplicate` full-scans an unindexed column (`TicketDao.kt:31`) — needs `Index("rawBarcodeData")`, `Index("addedAt")`, and a composite index on `favorite_journeys`. Repositories expose `LiveData` (`TicketRepository.kt:11`, `DisplayRepository.kt:10`, `FavoriteJourneyRepository.kt:9`) and should move to `Flow`. There's a missing FK mapping from `mapping → displays.tagUid`, currently papered over with manual cleanup (`DisplayRepository.kt:40-44`), and a dead method at `FavoriteJourneyDao.kt:31`. Combined with A2 (destructive migrations), A9 (undo losing mappings), and A10 (non-atomic counters), this is the single highest-value cluster of data-layer fixes.

### 4.5 List/adapter performance (B8, B12, B13)

`TicketAdapter` recomputes `mostRecentTicket` on every single bind (`TicketAdapter.kt:37-41`), and `TicketListActivity.onResume` calls `notifyDataSetChanged()` as a blunt-force refresh hack (`:112-115`). Fix: compute the most-recent flag once at submit time and fold display labels into the observed query rather than recomputing per row. `PdfQrExtractor` creates a new ML Kit client on every call and never closes it (`PdfQrExtractor.kt:74-76`), renders at an unbounded 3x scale (`:30-33`, risking OOM on large pages), conflates "no barcode found" with "read error" via a single `catch → null` (`:52-60`), has no page cap (a 100-page PDF renders in full), and doesn't recycle the success bitmap (`:46-48`). Fix: one long-lived client with `close()`, clamp render scale to ~2500px, use a sealed result type instead of nullable, and cap pages. `BarcodeGenerator` uses per-pixel `setPixel()` in a double loop (`:215-227`, ≈400k JNI calls on the main thread) — should batch through an `IntArray` and a single `setPixels()` call.

### 4.6 Station lookup (B11)

`StationLookup.initialize()` parses `stations.json` (2,970 entries, ~500KB, 26,760 lines) on the main thread, called from `onCreate` of four different activities (`StationLookup.kt:16-30`). `getStationName`/`getAllStations().find` are both O(n) per row bind, and this same O(n) lookup appears again directly in `EditTicketActivity.kt:120,126`. There's no error handling, so an `IOException` crashes the app outright. Fix: background lazy load feeding a `Map<String, Station>` index; also dedupe the filter logic against `StationAdapter`/`searchStations` (`StationAdapter.kt:43` additionally calls the deprecated `notifyDataSetInvalidated`).

### 4.7 Dead code & duplication inventory (B6, B9, D1, dead resources)

The swipe-to-delete `ItemTouchHelper` block is copy-pasted three times at ~75 lines each (`TicketListActivity.kt:206-282`, `ManageFavoriteJourneysActivity.kt:99-149`, `ManageDisplaysActivity.kt:135-182`) and should become one `SwipeToDeleteCallback` builder. `Preferences` holds an `Activity` reference solely for the dead screen-size picker (`Preferences.kt:14-17`); `getScreenSizeStr`/`setScreenSize`/`showScreenSizePicker` are all dead methods; `Show_Label_On_Barcode` is a dead preference key (`Constants.kt:49`); and `Constants.kt`'s `PrefKeys`/`IntentKeys`/`Preference_File_Key` are declared `var` where they should be `const val`. `Utils.kt` is entirely dead — leftover WebView/JS-injection code from an unrelated project with zero callers — and should be deleted outright (see D1).

### 4.8 Threading & lifecycle discipline (B2, B10, A5)

Beyond the NFC write path itself (4.2, A5), there's a broader pattern of redundant `withContext(Dispatchers.IO)` wrapping around Room `suspend` calls that already run on Room's own executor (`AddTicketActivity.kt:323,602`, `EditTicketActivity.kt:100,153`) — these should be removed in favor of calling suspend functions directly.

## UX & product improvements

### 5.1 Share/extract (B13, C5)

`Uri.parse(text)` never throws, so the surrounding try/catch is dead code, and arbitrary shared text can become a bogus download attempt (`TicketListActivity.kt:147-149`). The `HttpURLConnection` used to fetch shared URLs has no timeouts and never disconnects (`AddTicketActivity.kt:169-207, 185-186`), so a bad host hangs indefinitely. `barcodes[0]` is always picked rather than the largest/most-appropriate barcode (`PdfQrExtractor.kt:82`, `AddTicketActivity.kt:236-295`) — m-tickets with an outbound and return code need a user choice, or at minimum a heuristic preferring AZTEC and the largest bounding box. The manifest has duplicate `text/plain` + `text/*` intent filters (`AndroidManifest.xml:31-40`). Deprecated APIs are in active use: `getParcelableExtra`/`startActivityForResult`/`onActivityResult` (`TicketListActivity.kt:133, 188-197`, `AboutActivity.kt:53-55`). `processImage` decodes without any bounds check or downsampling, so a 12MP photo can allocate ≈48MB (`AddTicketActivity.kt:228-234`), and the source bitmap is never recycled. Separately, the Add screen has no loading state at all during the multi-second ML Kit scan of a 3x-rendered PDF — an empty preview card with no spinner — and failure is a bare Toast followed by `finish()` ("No QR code found", `AddTicketActivity.kt:159`) with no persistent explanation or retry affordance.

### 5.2 Add/edit forms (C9, C10, C11, C12 form items)

The journey dialog's `maxHeight=400dp` is ignored by the RecyclerView inside it, so the dialog grows off-screen (`dialog_journey.xml:~50`). Free-text station input silently selects nothing if it doesn't match (`AddTicketActivity.kt:471-479`) — needs validation on OK or an inline error. The OK button's visibility toggles per tab in a way that produces an inconsistent mental model (`:564-582`). The date dialog keeps the time-of-day component, so two same-day tickets can end up with different epoch values (`AddTicketActivity.kt:385-402`) — should normalize to midnight. Neither Add nor Edit track dirty state, so back/cancel silently discards edits with no confirmation. Validation is inconsistent between the two screens: Edit rejects an empty label (`EditTicketActivity.kt:146-148`) while Add silently generates a default one (`AddTicketActivity.kt:600-601`, `:340-347`), and Edit's empty-name dialog closes with no feedback at all (`:197-207`). All of this should be unified once the forms are merged (4.3).

### 5.3 Ticket list (C6, C7)

Tickets are ordered `addedAt DESC` only (`TicketDao.kt:3`) — there's no travel-date or expiry-aware sorting, no section headers, and no visual treatment for expired tickets. The empty state has actionable copy but doesn't mention that long-press edits a ticket, which is otherwise completely undiscoverable (`TicketListActivity.kt:75-84`) — there's no pencil affordance on the card and no hint anywhere. Fix: travel-date-first sorting with expired-ticket styling, plus a pencil affordance on the card and an empty-state mention of long-press-to-edit.

### 5.4 Flash flow (C1, C2, onboarding/C3)

Flash failures surface as a raw exception Toast ("FAILED to Flash :( java.io.IOException…", `NfcFlasher.kt:373-378`); null-bitmap and wrong-tech cases fail silently; the status card never actually reflects the outcome (`activity_nfc_flasher.xml:129-155`); there's no haptic feedback; success is audio-only; and a `nfc_write_dialog.xml` layout exists dead while the live UI shows only an indeterminate spinner. The target display model is also invisible on the flash screen — it's only visible via the (currently unreachable, see A1) global setting. NFC onboarding is essentially absent: `welcome_title`/`welcome_message` strings exist in `strings.xml:14-15` but are referenced by no code; the only in-flow guidance is a single 14sp status line. Fix: themed success/error status states with human-readable error copy and a "re-align and tap again" hint, haptics, a real progress bar driven by `writer.progress`, the display model surfaced in the status card, and a first-run onboarding dialog plus a persistent "hold flat against phone back, move slowly until chime" hint card.

### 5.5 Favorites (C8)

Long-press-to-delete on the favorites screen has no undo, while swipe-to-delete on the same screen does (`ManageFavoriteJourneysActivity.kt:151-169, 322-330`) — inconsistent within one screen. The 50-entry cap is checked asynchronously, creating a race with the confirmation banner (`:229-243`). The default-label detection compares against the computed default via string equality (`FavoriteJourneyAdapter.kt:49-56`), which is fragile — should persist an explicit `isDefaultLabel` flag instead.

### 5.6 Navigation & consistency (C12, C13)

A consistency sweep is needed across ~70+ hardcoded strings, spanning both layouts (e.g. "My Tickets" `activity_ticket_list.xml:37`; "Tap to set name/date/journey" `activity_add_ticket.xml:120,146,177`; dialog copy in `dialog_journey.xml:19,21,95,105`) and code (every Toast/Snackbar) — all should move to `strings.xml`. Swipe-delete background color is inconsistent: `holo_red_dark` in `TicketListActivity.kt:206` and `ManageDisplaysActivity.kt:113` vs `holo_red_light` in `ManageFavoriteJourneysActivity.kt:102` — both should become `?attr/colorError`. Card corner radius and margins are mixed (8/12/16dp radius, 4/8dp margins across `ticket_list_item.xml:11,8-9`, `activity_add_ticket.xml:53`, `activity_nfc_flasher.xml:59`, `favorite_journey_item.xml:6-7`) and should become dimen tokens. The back icon is inconsistently `@drawable/ic_arrow_back` in one place and `?attr/homeAsUpIndicator` in another (`activity_manage_favorites.xml:22`). The FAB uses the raw platform add icon (`activity_ticket_list.xml:66`). The logo's contentDescription is hardcoded English ("PaperTap Logo", `:31`) rather than localized. Two accessibility items need explicit verification before shipping: the 0.7-alpha secondary text in `activity_nfc_flasher.xml:76` and the `colorOnPrimaryContainer` (#91A8FF on #0A369D, ≈3.4:1 contrast — below WCAG AA) need computed contrast checks, and the pale #B6C4FF night-mode splash color needs visual verification. Separately, `main_menu.xml` and `navigation_drawer.xml` are both dead menus (only `R.menu.ticket_list_menu` is actually used, `TicketListActivity.kt:311`) and should be deleted; `NfcFlasher`'s `singleTask` launch mode combined with `parentActivityName` produces surprising back-stack behavior on re-flash from a fresh share, which should be verified and fixed.

### 5.7 Ideal first-run journey

> A first-time user shares an e-ticket PDF from their email or wallet app into PaperTap; the app immediately shows a progress indicator while it extracts the QR code, then presents a clear preview with the detected ticket details and a prominent "Write to display" action. The user is guided through a brief onboarding card explaining how to hold the phone against the NFC display — "hold flat against phone back, move slowly until chime" — before the first tap. On a successful write, the display screen shows a clear success state with haptic and audio confirmation, and the ticket is saved to their collection for reflashing later without needing to re-extract it. If anything goes wrong at any step — no QR found, tag rejected, write failed — the user sees plain-language guidance on what to do next, never a raw exception message.

## Dependency & build modernization

- **D1.** `Utils.kt` is entirely dead — WebView/JS-injection code from an unrelated project, zero callers. Delete.
- **D2.** `kotlinx-datetime:0.6.1` is declared but never used; dates go through `SimpleDateFormat` instead (`TicketListActivity.kt:292-296`, `TicketAdapter.kt:99,104`). Either adopt it consistently or drop the dependency.
- **D3.** `rsp6-decoder-kotlin` has zero references anywhere in the codebase; the composite-build substitution (`settings.gradle:5-13`) and the jitpack repo serve nothing. Remove both.
- **D4.** Migrate `kapt` to `KSP` for Room annotation processing — Kotlin 2.0.21 and Room 2.6.1 both support KSP.
- **D5.** Stale dependencies across the board: `core-ktx` 1.12, `appcompat` 1.6.1, `material` 1.11, `lifecycle` 2.7, `constraintlayout` 2.1.4, `preference` 1.2.1, `coroutines` 1.7.3, ML Kit 17.3.0, zxing 3.5.3 — all should be bumped. `junit:junit:4.+` is a wildcard version and should be pinned. The instrumented test package is still `com.joshuatz.nfceinkwriter` and its `applicationId` assertion would fail if actually run — rewrite or delete it. There is no CI (`.github` was absent before this report) — a build workflow should be added. There's no Gradle version catalog (`libs.versions.toml`) — worth considering given the dependency count. Release builds have `minifyEnabled false` — should be enabled with accompanying proguard rules.
- **D6.** Dead resources: layouts `nfc_write_dialog.xml`, `spinner_item.xml`, `spinner_dropdown_item.xml`; drawables `ic_menu`, `ic_nfc`, `ic_swap_horiz`; strings `cta_*`, `reflash_cta_text`, `imagePreviewText` (should be kept and wired up rather than deleted — see the `welcome_*` strings noted in 5.4/C3). The app theme is still named `Theme.NFCEInkWriter` (`values/themes.xml:3`) and should become `Theme.PaperTap`. There are ~30 debug `Log.d` calls left in, plus `e.printStackTrace()` in catch blocks that leak URIs (`TicketListActivity.kt:106-108`) — should be stripped or gated behind `BuildConfig.DEBUG`. The MIT license text is hardcoded in Kotlin (`AboutActivity.kt:30-52`) and should move to a resource.

## Documentation

`CLAUDE.md` describes an architecture that no longer exists: it names `MainActivity` as the entry point (the actual entry point is `TicketListActivity`); it describes a proprietary WaveShare JAR and the obfuscated `waveshare.feng.nfctag.activity.a` class, which was removed in commit `bef24c9`; it claims UID validation is enforced (it is computed but never enforced — see A4); it claims `DeadObjectException` recovery exists (no such code is present); it describes RSP6/`TicketData`/SharedPreferences-based persistence, which Room has since replaced entirely; it references `station_codes.json` and `fare_codes.json`, of which only `stations.json` actually exists; and it describes a 4-line ticket layout, `PickImageDialog`, and a `generated.png` flow that no longer match the current code. The README's Settings section documents a display-size setting that has no UI to reach it (see A1). Both documents need a rewrite to match the current architecture.

## Patterns worth keeping

- **Room:** the `@Volatile` double-checked singleton pattern using `applicationContext` (`TicketDatabase.kt:16-31`); the unique `tagUid` index with `IGNORE`-conflict insert (`DisplayEntity.kt:11`, `DisplayDao.kt:16`); `REPLACE` on the composite PK with `CASCADE` FK (`TicketDisplayMappingDao.kt:16`); every mutation is `suspend`; duplicate detection is already applied on insert (`TicketRepository.kt:46-57`).
- **UI:** `DiffUtil`-backed `ListAdapter` used consistently across all adapters; undo snackbars on all three delete surfaces; empty states with actionable copy; Material 3 token mapping including medium/high-contrast variants and custom `colorAppBar` attrs; `FLAG_KEEP_SCREEN_ON` held during writes; barcode regeneration from stored raw data on resume, so settings changes apply without re-extraction; the journey picker's Favorites/Search tabs with swap and inline save-favorite; the duplicate-alert dialog that shows the existing ticket rather than just a generic message.
- **Rendering:** pure black/white output, `RGB_565`, `antiAlias=false` plus monospace labels, a white composite canvas, and intermediate bitmap recycling (`BarcodeGenerator.kt:120-123, 182-208`); `PdfRenderer` finally-close hygiene (`PdfQrExtractor.kt:62-65`); crop padding clamped to valid bounds (`:95-98`).
- **Data:** the stations dataset — 2,970 entries with CRS codes, classification, and state fields — is comprehensive and well-structured.

## Prioritized roadmap

**P0 — correctness / data-loss risk**

| ID | Finding |
|---|---|
| A1 | Display model collapse (biggest single item) |
| A2 | Destructive Room migrations |
| A3 | 1.54" B infinite poll loop |
| A4 | Tag validation not enforced |
| A5 | Rotation leak / lost history / double-write |
| A6 | Adapter per-bind coroutine race |
| A7 | Intent re-handled on rotation |
| A9 | Undo loses display mappings |
| A10 | Non-atomic flash counters |
| A14 | Duplicate-detection-after-save broken |
| A15 | No state retention in Add/Edit |
| A16 | Edit erases stored station data |

**P1 — architecture / UX**

| ID | Finding |
|---|---|
| B2–B8, B11–B14 | NFC threading, form consolidation, data layer, list/adapter perf, station lookup, extraction pipeline, barcode generation |
| C1–C6, C8–C11 | Flash feedback, onboarding, add→flash dead-end, add loading state, list sorting, favorites UX, form dirty-state/validation |

**P2 — polish / dependencies / docs**

| ID | Finding |
|---|---|
| B9–B10 | Dead prefs/methods, redundant `withContext` |
| C7 | Long-press-to-edit discoverability |
| C12–C13 | String/style consistency sweep, dead menus |
| D1–D6 | Dependency cleanup, KSP migration, dead resources, CI |
| §E | CLAUDE.md / README rewrite |

**Coarse effort estimates** (S = hours, M = 1-2 days, L = multi-day):

- A1: M-L (protocol table + settings UI + hardware verification). A2: M. A3: S. A4: S-M. A5: M (ViewModel refactor). A6+A8: M (joined query). A7: S. A9: S. A10: S. A14: S. A15: M. A16: S.
- B5 (Add/Edit merge): L, but removes ~350 lines net. B6: S. B7: M. B11: S-M. B12–B13: M. B14: S-M.
- C1: M. C3–C5: M combined. C12 (strings sweep): M, mechanical.

**Every NFC protocol item (A1, A3, A4, A5, A8, B1, B2, B3) requires on-device/hardware verification before being considered fixed** — none of the corrections above can be confirmed by code inspection alone.
