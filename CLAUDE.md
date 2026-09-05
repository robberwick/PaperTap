# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PaperTap is an Android app that extracts barcodes (QR, Aztec, Data Matrix, PDF417) from e-tickets — PDFs, images, or direct URLs — stores them in a Room-backed ticket library with optional journey metadata, and writes them to WaveShare NFC-powered e-paper displays.

**Key technologies:**

- Kotlin + Android SDK (minSdk 21, targets/compiles SDK 35)
- ML Kit Barcode Scanning for barcode detection
- Android PdfRenderer for PDF processing
- ZXing (`core`) for barcode regeneration from stored raw data
- Room 2.6.1 for persistence (schema v9, exported)
- Material Design 3 UI (`Theme.PaperTap`), light + dark
- Kotlin coroutines, `LiveData`, and `StateFlow`
- Clean-room WaveShare NFC protocol implementation (no proprietary SDK)

## Build Commands

### Standard Build

```bash
# Build the debug APK
bash gradlew assembleDebug

# Output location
app/build/outputs/apk/debug/papertap-debug.apk

# Lint and unit tests (same tasks CI runs)
bash gradlew lint
bash gradlew testDebugUnitTest
```

Requires JDK 17. The toolchain is Gradle 8.13, AGP 8.13.2, Kotlin 2.0.21; Room uses kapt (not KSP).

### Debug Signing

The debug signing key is committed at `keystore/debug.keystore` (store password and key password `android`, alias `androiddebugkey`), pinned in `app/build.gradle`. This keeps local debug builds and CI artifacts on the same signature, so a fresh APK installs over an existing install.

### CI

`.github/workflows/ci.yaml` runs on every push and pull request: `lint`, `testDebugUnitTest`, `assembleDebug`, then uploads the Room schemas (`app/schemas`) and `papertap-debug.apk` as artifacts.

### Android Studio

Open the project and build via Build → Build Bundle(s) / APK(s) → Build APK(s).

### Docker (caveat)

The `Dockerfile` at the repo root still builds the **upstream mk-fg project** (fetched at a pinned commit), not PaperTap. It does not produce a PaperTap APK — use the Gradle or Android Studio paths above.

## Architecture

### Core Flow

1. **TicketListActivity**: Launcher and entry point (`singleTop`, splash theme)
   - Receives `ACTION_SEND` intents (`image/*`, `application/pdf`, `text/*` — browsers share PDF links as http/https text), and `ACTION_VIEW` for `application/pdf`
   - The + FAB opens the system document picker (`ActivityResultContracts.OpenDocument`, MIME types `image/*` and `application/pdf`)
   - Every route resolves a `Uri` and starts `AddTicketActivity` with a `DOCUMENT_URI` extra
   - Share intents are handled only when `savedInstanceState == null`, so rotation must not re-launch AddTicketActivity; re-delivery while running goes through `onNewIntent`
   - Ticket list via `TicketAdapter` backed by `TicketRepository.allTickets` (`LiveData<List<TicketWithDisplays>>`); tap a ticket → `NfcFlasher`, long-press → `EditTicketActivity`, swipe left → delete with undo

2. **AddTicketActivity**: Extraction + metadata
   - Extraction pipeline: `http`/`https` URIs are downloaded to a temp file (15s connect / 30s read timeout) and treated as PDFs; `application/pdf` goes to `PdfQrExtractor`; images are sampled down to ≤2000px and scanned with ML Kit
   - Duplicate detection: `TicketRepository.findByBarcodeData` alerts before saving
   - Optional metadata: custom label (default "Ticket <date>"), travel date, origin/destination stations (autocomplete via `StationLookup`/`StationAdapter`, or favorite journeys), all stored on the `TicketEntity`

3. **PdfQrExtractor**: PDF/image scanning utility (Kotlin `Closeable`)
   - Renders up to 20 PDF pages, scale up to 3x capped at a 2500px max dimension
   - ML Kit formats: `QR_CODE`, `AZTEC`, `DATA_MATRIX`, `PDF417`
   - Crops the detected barcode with configurable padding; returns a sealed `BarcodeExtractionResult` (`Success`/`NoBarcode`/`Error`)

4. **NfcFlasher**: NFC writing activity (`launchMode="singleTask"`)
   - Regenerates the barcode bitmap from the ticket's stored raw data via `BarcodeGenerator` (ZXing) at the selected `DisplayModel`'s exact resolution, on `Dispatchers.Default`; optional text labels (station codes, travel date) under the barcode per settings
   - Foreground dispatch (NFC-A tech list + NDEF filter), enabled in `onResume`, disabled in `onPause`
   - On tag discovery, validates via `WaveShareTagValidator`, then hands off to `NfcFlashViewModel`
   - Audio feedback: start = system notification sound; success = Mario coin (B5 988Hz → E6 1319Hz); error = sad trombone (C4 261.6Hz → A3 220Hz → F3 174.6Hz); generated with `AudioTrack` at 44.1kHz in static mode, with fade-in/out envelopes to prevent clicking

5. **NfcFlashViewModel**: The write itself
   - `AndroidViewModel`; the write runs in a `viewModelScope` coroutine on `Dispatchers.IO`, so it survives configuration changes
   - Publishes a `StateFlow<FlashState>` (`Idle` / `Writing(progress)` / `Success` / `Error`); progress is polled from `WaveShareNfcWriter.progress` every 100ms
   - On success, records usage in Room: `DisplayRepository.getOrCreateDisplay`, `TicketRepository.addDisplayToTicket` (enforces one-ticket-per-display), `DisplayRepository.recordUsage`, `TicketRepository.recordFlashEvent`

### WaveShare NFC Writer

`waveshare/WaveShareNfcWriter.kt` is a clean-room reimplementation of the WaveShare NFC e-paper protocol, cross-checked against proxmark3's `cmdhfwaveshare.c` (derived from WaveShare's ST25R3911B NFC demo). There is **no proprietary SDK dependency**; the legacy JAR still sitting in `app/libs/waveshare-nfc/NFC.jar` is unreferenced by the Gradle build — do not use it.

- Connects via `NfcA` with the timeout overridden from the 700ms default to 1200ms for reliable writes
- `WriteResult`: `SUCCESS`, `DIMENSION_MISMATCH` (bitmap must match the model's resolution in either orientation), `COMMUNICATION_ERROR`
- Protocol behaviour is driven entirely by the `DisplayModel` enum: model-select byte, bytes per packet, packet count, rotation, trailing padding (7.5" HD), ready-poll delay
- The 1.54" B has a special two-pass path (red plane transmitted first, 50 packets per pass); both completion loops are attempt-capped (51 polls for 1.54" B, 100 for the standard path) so a write can never hang forever
- Image packing is 1bpp: `bytesPerRow = ceil(width / 8)`, and the packed size must equal `dataBytesPerPacket * packetCount` (enforced with `require`). Dual-layer models (1.54" B, 2.13" B) expect pure black/white pixels; single-layer models use luminance thresholding at 128 (`0.299*R + 0.587*G + 0.114*B`)

### NFC Tag Validation

`WaveShareTagValidator` (object, returns sealed `TagValidationResult`) rejects a tag unless:

1. **Tech type**: supports `android.nfc.tech.NfcA`
2. **UID**: 7-byte ASCII UID in the whitelist (`WSDZ10m` standard models, `FSTN10m` for the 1.54" B)
3. **Model match**: the UID must match the selected display model — `FSTN10m` only writes when the 1.54" B is selected, `WSDZ10m` for anything else
4. **AAR**: for NDEF-discovered tags, an Android Application Record `android.com:pkg` = `waveshare.feng.nfctag` must be present

On success the validator returns a hex tracking UID (`UID:XX:XX:...`) used as the Room display identifier.

### DisplayModel

`waveshare/DisplayModel.kt` is the single source of truth for every supported WaveShare NFC e-paper model: preference value, label, physical resolution, model-select byte, packet layout, protocol (`SINGLE_PASS`, `BLANK_THEN_BLACK`, `DUAL_LAYER`, `ONE_54_B`), rotation, and ready-poll delay.

- Only `ONE_54_B` (1.54" B, 200×200) is hardware-verified (`isHardwareVerified`); `DisplayModel.selectable()` hides every other model unless the experimental setting is on
- Default preference value is `"1.54_b"`
- There is no index-based model selection — the old 1-indexed SDK enum is gone; always go through the enum

### Ticket Storage (Room)

`database/TicketDatabase.kt` — Room database, **version 9**, `exportSchema = true` (schema checked in at `app/schemas/com.robberwick.papertap.database.TicketDatabase/9.json`).

- Entities: `TicketEntity` (`rawBarcodeData`, ML Kit `barcodeFormat` constant, `userLabel`, station codes, `travelDate`, `addedAt`, `lastFlashedAt`, `flashCount`), `FavoriteJourneyEntity`, `DisplayEntity` (unique `tagUid`, `userLabel`, `useCount`, `lastUsedAt`), `TicketDisplayMapping` (junction table with composite PK and CASCADE FKs to both sides)
- `MIGRATION_8_9` is the only registered migration; `fallbackToDestructiveMigration()` is applied **debug builds only** — release builds fail loudly on a missing migration. When bumping the schema: write a real `Migration`, bump the version, and commit the exported schema JSON
- Repositories: `TicketRepository` (`allTickets` LiveData of `TicketWithDisplays`, `deleteWithMappings`/`restoreDeleted` snapshot for swipe-undo, `addDisplayToTicket` removes the display from other tickets first, `recordFlashEvent`), `DisplayRepository` (`getOrCreateDisplay`, `recordUsage`), `FavoriteJourneyRepository` (50-favorite cap)
- `TicketRepository.insertTicket` returns the **existing ticket's id** when the barcode is a duplicate, rather than failing — callers detect this by comparing labels

### Settings

`SettingsActivity` hosts a `PreferenceFragmentCompat` over `res/xml/preferences.xml`. Preference keys live in `PrefKeys` (`Constants.kt`); the shared preferences file is `"Preferences"`.

- **Display model** (`Display_Size`): `ListPreference`, default `"1.54_b"`; entries built from `DisplayModel.selectable(...)` in code
- **Show experimental display models** (`Experimental_Display_Models`): `SwitchPreferenceCompat`, off by default; reveals unverified models
- **Edge padding** (`Qr_Padding`): 0–50 pixels, default 5
- **Show Station Codes** / **Show Travel Date**: render text labels beneath the barcode on the NFC image

The `Preferences` wrapper maps legacy display-size strings to `DisplayModel` values and clamps any non-experimental selection back to the 1.54" B.

### Shared Components

- `SwipeToDeleteCallback`: the single `ItemTouchHelper.SimpleCallback` (left swipe, 0.5 threshold, error-color background with delete icon) used by the ticket list, `ManageDisplaysActivity`, and `ManageFavoriteJourneysActivity`
- `BarcodeGenerator` (`object`): ZXing-based regeneration with optional `BarcodeLabel`s beneath the barcode
- `StationLookup`: singleton over `res/raw/stations.json` (2,970 entries, ~500KB); `initialize()` starts a daemon-thread parse guarded by a `CountDownLatch`, safe to call from every activity's `onCreate`

### Theming

The app theme is `Theme.PaperTap` (Material 3, light + dark variants in `values/` and `values-night/`), with `Theme.PaperTap.Splash` wrapping `androidx.core:core-splashscreen`. See `THEMING_GUIDE.md` for the color-role mapping.

### Logging

Every `Log.d` call in the app is gated by `if (BuildConfig.DEBUG)` (`buildFeatures { buildConfig true }` keeps the flag available); `Log.e` always fires. Keep this convention in new code.

## Common Gotchas

**NFC**:

- Each phone's NFC antenna is positioned differently — finding the sweet spot requires experimentation.
- Corrupted writes show as visual noise on the display. Fix: toggle NFC off/on and retry.
- NfcA's default timeout (700ms) is too short for these displays — the writer overrides it to 1200ms.
- Only the 1.54" B is verified on real hardware; other models are experimental and may fail silently (wrong model byte, wrong packet layout).
- Read progress by polling `WaveShareNfcWriter.progress` from a separate coroutine (the ViewModel polls at 100ms) — never from the write thread.

**Room**:

- Release builds have no destructive fallback — a schema bump without a matching `Migration` will crash real users. Bump, migrate, export, commit.
- `insertTicket` returning an existing duplicate's id is intentional; don't "fix" it to throw without updating the Add flow's duplicate handling.

**Gradle/Dependencies**:

- Requires Java 17 for compilation.
- Room still uses kapt; don't switch to KSP as a drive-by change.
- JitPack is declared as a repository but nothing currently resolves from it.

**Station data**:

- `stations.json` parses on a background thread; accessors briefly block on a latch if called before the load completes.

## IDE Integration

Always use the `jetbrains-index` MCP server when applicable for:

- **Finding references** — Use `ide_find_references` instead of grep/search
- **Go to definition** — Use `ide_find_definition` for accurate navigation
- **Renaming symbols** — Use `ide_refactor_rename` for safe, project-wide renames
- **Type hierarchy** — Use `ide_type_hierarchy` to understand class relationships
- **Finding implementations** — Use `ide_find_implementations` for interfaces/abstract classes
- **Diagnostics** — Use `ide_diagnostics` to check for code problems

The IDE's semantic understanding is far more accurate than text-based search. Prefer IDE tools over grep, ripgrep, or manual file searching when working with code symbols.