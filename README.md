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

- **Display**: WaveShare 1.54" Passive NFC-Powered E-Paper (200×200px)
- **Phone**: Any Android device with NFC capability (API 21+)
- **Tag Type**: NFC-A compatible displays

Get the display: [WaveShare 1.54" NFC E-Paper]


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
✅ **Multiple display sizes** - Various WaveShare e-paper formats supported  
✅ **Crisp output** - Pure black/white rendering for reliable scanning  
✅ **Configurable padding** - Adjust barcode margins via settings  
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

Access display management via Settings → Manage Displays

## Building & Installation

### Quick Build (Docker)

Requires [Docker Engine] installed with ~5GB memory and 3GB disk space.

```bash
# Download Dockerfile
curl -OL https://raw.githubusercontent.com/robberwick/PaperTap/main/Dockerfile

# Build APK
docker build --output type=local,dest=. .

# Install app-debug.apk on your Android device
```

### Android Studio

Open the project in [Android Studio] and build via Build → Build Bundle(s) / APK(s) → Build APK(s).

[Docker Engine]: https://docs.docker.com/engine/install/
[Android Studio]: https://developer.android.com/studio

## Installation

Transfer the `app-debug.apk` to your Android device and install it. You'll need to enable installation from unknown sources in your device settings.

**Requirements**: Android 5.0+ (API level 21 or higher)

## Settings

Access via the menu (⋮) in the top-right corner:

- **Display Size**: Choose from supported WaveShare displays (defaults to 1.54" 200×200)
- **Barcode Padding**: Adjust white border around barcode (0-50 pixels)
- **Favorite Journeys**: Manage saved station pairs (up to 50 favorites)
- **Manage Displays**: Label and track your NFC e-paper displays

## Known Issues

**NFC can be finnicky** - Finding the right position and distance between your phone and the e-paper display may require some experimentation. Each phone's NFC antenna is positioned differently.

**Corrupted writes** - Occasionally the NFC transfer can fail, resulting in visual noise on the display. If this happens:

1. Toggle NFC off and back on in your phone's Quick Settings
2. Re-tap the display to retry the write

**NFC radio dying** - Some Android devices experience NFC chipset failures at the system level. This appears in logs as `android.os.DeadObjectException`. Toggle NFC off/on to recover.

## Technical Details

**Built with:**

- Kotlin + Android SDK (API 21+, targets API 35)
- Room database for ticket persistence
- ML Kit Barcode Scanning for barcode detection (QR, Aztec, Data Matrix, PDF417)
- ZXing for barcode generation and rendering
- Android PdfRenderer for PDF processing
- WaveShare NFC SDK for e-paper communication
- Material Design 3 UI components

**Key technologies:**

- Room database with LiveData for reactive ticket management
- Many-to-many ticket-to-display relationship tracking
- ML Kit barcode scanning with multiple format support
- ZXing barcode generation from stored raw data
- Threshold-based image processing for crisp barcode rendering
- Foreground NFC dispatch for tag interception
- Hex UID-based display identification
- AudioTrack API for custom success/error sounds
- Kotlin coroutines for async operations
- Station code lookup with autocomplete search

## Project Origins

PaperTap is a focused fork of [joshuatz/nfc-epaper-writer], which was itself adapted by [mk-fg] and [DevPika]. This version transforms the original single-image tool into a full ticket management system with barcode extraction, metadata tracking, and library organization.

**Attribution chain:**

- Copyright (c) 2025 Rob Berwick - PaperTap (focused e-ticket version)
- Copyright (c) 2024 harinworks - Fork updates
- Copyright (c) 2024 mk-fg - Fork updates  
- Copyright (c) 2021 Joshua Tzucker - Original NFC E-Paper Writer

See [LICENSE] and [NOTICE] for complete MIT License details.

[joshuatz/nfc-epaper-writer]: https://github.com/joshuatz/nfc-epaper-writer
[mk-fg]: https://github.com/mk-fg/nfc-epaper-writer
[DevPika]: https://github.com/DevPika/nfc-epaper-writer-update
[LICENSE]: LICENSE
[NOTICE]: NOTICE

## License

MIT License - See [LICENSE] file for details.
