# PaperTap

**Manage and display your e-ticket barcodes on passive NFC-powered e-ink displays.**

PaperTap is an Android application that extracts barcodes from train tickets, event tickets, and other e-tickets, stores them in a personal library, and writes them to WaveShare NFC e-paper displays. Perfect for keeping your tickets accessible without draining your phone battery.

## Why PaperTap?

- **Ticket library**: Build a collection of your tickets with custom labels and metadata
- **Battery-free display**: The e-paper tag requires no power and lasts indefinitely
- **Always scannable**: Works at ticket gates even when your phone is dead
- **Flexible workflow**: Add tickets from PDFs, images, or direct URLs
- **Organized travel**: Track origin/destination stations and travel dates

## Target Hardware

- **Display**: WaveShare 1.54" Passive NFC-Powered E-Paper (200×200px, black/white/red) — the only model verified on real hardware
- **Phone**: Any Android device with NFC capability (API 21+)
- **Tag Type**: NFC-A compatible displays

Other WaveShare NFC e-paper models can be selected in Settings behind an experimental toggle, but they have not been verified on hardware and may not update correctly.

Get the display: [WaveShare 1.54" NFC-Powered e-Paper]

## Features

### Barcode Detection & Storage

✅ **Automatic barcode detection** - ML Kit finds QR codes, Aztec codes, Data Matrix, and PDF417 barcodes  
✅ **PDF support** - Share from email, browser, or file manager  
✅ **Image support** - Works with screenshots, photos, or gallery images  
✅ **URL downloads** - Share PDF links directly from browsers  
✅ **Ticket library** - Store multiple tickets with custom labels  
✅ **Duplicate detection** - Alerts you if a ticket is already saved

### Metadata & Organization

✅ **Station autocomplete** - Search and select origin/destination stations  
✅ **Travel dates** - Associate travel dates with tickets  
✅ **Custom labels** - Name your tickets for easy identification  
✅ **Favorite journeys** - Save frequently used station pairs for quick selection  
✅ **Ticket editing** - Update labels, stations, and dates anytime

### Display & Writing

✅ **Multiple barcode formats** - Supports QR Code, Aztec, Data Matrix, PDF417  
✅ **Crisp output** - Pure black/white rendering for reliable scanning  
✅ **Configurable padding** - Adjust barcode margins via settings  
✅ **Optional image text** - Render station codes or the travel date beneath the barcode
✅ **Audio feedback** - Distinct sounds for start, success, and errors  
✅ **Quick reflash** - Tap any ticket to write to display  
✅ **Display tracking** - See which display each ticket is currently on  
✅ **Display management** - Label your displays for easy identification

### Management

✅ **Swipe to delete** - Remove tickets with undo option  
✅ **Long-press to edit** - Quick access to ticket metadata editor  
✅ **Manage displays** - Label, track, and organize your NFC displays

## How It Works

### Adding a Ticket

1. **Get your ticket** - Receive email with PDF link or download the PDF/image
2. **Share to PaperTap** - Tap "Share" in your browser/file app and select PaperTap
   - Or open PaperTap and tap the + button to pick a file
3. **Barcode extraction** - App automatically detects and extracts the barcode
4. **Add metadata** (optional) - Enter custom label, select stations, set travel date
5. **Save** - Ticket is added to your library

### Writing to Display

1. **Select ticket** - Tap any ticket in your library
2. **Tap NFC display** - Hold your phone to the e-paper tag
3. **Wait for audio** - Success sound confirms write is complete
4. **Done** - Your ticket is now displayed and scannable

### Managing Tickets

- **Edit**: Long-press any ticket to update its metadata
- **Delete**: Swipe left on a ticket (with undo option)
- **Reflash**: Simply tap a ticket to write it again
- **Favorites**: Save common station pairs for faster ticket entry
- **Track displays**: See which display(s) each ticket is currently on

### Managing Displays

- **Auto-registration**: Displays are automatically registered when first used
- **Label displays**: Give your displays friendly names like "Home Badge" or "Work Display"
- **Track usage**: See when each display was last used and how many times
- **Clear labels**: Reset a display name back to its hex UID
- **Delete displays**: Remove displays you no longer use

Access display management via the menu (⋮) → Manage Displays

## Building & Installation

### Gradle (recommended)

Requires JDK 17 and the Android SDK (compileSdk 35):

```bash
# Build the debug APK
bash gradlew assembleDebug

# Output
app/build/outputs/apk/debug/papertap-debug.apk
```

Lint and unit tests use the same tasks CI runs:

```bash
bash gradlew lint
bash gradlew testDebugUnitTest
```

### Android Studio

Open the project in [Android Studio] and build via Build → Build Bundle(s) / APK(s) → Build APK(s).

### CI Artifacts

Every push and pull request runs [GitHub Actions CI] (lint, unit tests, assemble), which publishes the debug APK and the exported Room schema as artifacts.

### Docker (not for PaperTap)

The `Dockerfile` in this repo still builds the upstream [mk-fg] project at a pinned commit — it does **not** produce a PaperTap APK. Use the Gradle or Android Studio builds above.

## Installation

Transfer the `papertap-debug.apk` to your Android device and install it. You'll need to enable installation from unknown sources in your device settings.

Debug builds are signed with the debug keystore committed to this repo, so a newly built APK installs over an existing PaperTap debug install without uninstalling first.

**Requirements**: Android 5.0+ (API level 21 or higher)

## Settings

Access via the menu (⋮) → Settings in the top-right corner:

- **Display model**: Which WaveShare NFC e-paper to write to. Defaults to the hardware-verified 1.54" B (200×200, black/white/red)
- **Show experimental display models**: Off by default. When enabled, the display-model picker lists the other WaveShare models (2.13", 2.7", 2.9", 4.2", 7.5", 7.5" HD, 2.13" B) — these have not been verified on hardware and the display may not update correctly
- **Edge Padding**: White border around the barcode (0–50 pixels, default 5)
- **Show Station Codes**: Display origin → destination station codes on the NFC image
- **Show Travel Date**: Display the travel date on the NFC image

Also available from the menu (⋮):

- **Favorite Journeys**: Manage saved station pairs (up to 50 favorites)
- **Manage Displays**: Label and track your NFC e-paper displays

## Known Issues

**NFC can be finnicky** - Finding the right position and distance between your phone and the e-paper display may require some experimentation. Each phone's NFC antenna is positioned differently.

**Corrupted writes** - Occasionally the NFC transfer can fail, resulting in visual noise on the display. If this happens:

1. Toggle NFC off and back on in your phone's Quick Settings
2. Re-tap the display to retry the write

**NFC radio dying** - Some Android devices experience NFC chipset failures at the system level. This appears in logs as `android.os.DeadObjectException`. Toggle NFC off/on to recover.

**Experimental display models** - Only the 1.54" B display is verified against real hardware. Other models are implemented from the WaveShare protocol but untested; if one fails to update, switch back to the default model.

## Technical Details

**Built with:**

- Kotlin + Android SDK (minSdk 21, targets API 35)
- Room database for ticket persistence
- ML Kit Barcode Scanning for barcode detection (QR, Aztec, Data Matrix, PDF417)
- ZXing for barcode generation and rendering
- Android PdfRenderer for PDF processing
- Clean-room WaveShare NFC protocol implementation for e-paper communication (no proprietary SDK)
- Material Design 3 UI components

**Key technologies:**

- Room database (schema v9, exported and versioned in `app/schemas`) with LiveData for reactive ticket management
- Many-to-many ticket-to-display relationship tracking
- ML Kit barcode scanning with multiple format support
- ZXing barcode generation from stored raw data
- Threshold-based image processing for crisp barcode rendering
- ViewModel-scoped coroutines with StateFlow for rotation-safe NFC writes
- Foreground NFC dispatch for tag interception
- Hex UID-based display identification
- AudioTrack API for custom success/error sounds
- Station code lookup with autocomplete search

## Project Origins

PaperTap is a focused fork of [joshuatz/nfc-epaper-writer], which was itself adapted by [mk-fg] and [DevPika]. This version transforms the original single-image tool into a full ticket management system with barcode extraction, metadata tracking, and library organization.

**Attribution chain:**

- Copyright (c) 2025 Rob Berwick - PaperTap (focused e-ticket version)
- Copyright (c) 2024 harinworks - Fork updates
- Copyright (c) 2024 mk-fg - Fork updates  
- Copyright (c) 2021 Joshua Tzucker - Original NFC E-Paper Writer

See [LICENSE] and [NOTICE] for complete MIT License details.

[WaveShare 1.54" NFC-Powered e-Paper]: https://www.waveshare.com/1.54inch-nfc-powered-e-paper-bw.htm
[Android Studio]: https://developer.android.com/studio
[GitHub Actions CI]: https://github.com/robberwick/PaperTap/actions
[mk-fg]: https://github.com/mk-fg/nfc-epaper-writer
[joshuatz/nfc-epaper-writer]: https://github.com/joshuatz/nfc-epaper-writer
[DevPika]: https://github.com/DevPika/nfc-epaper-writer-update
[LICENSE]: LICENSE
[NOTICE]: NOTICE

## License

MIT License - See [LICENSE] file for details.