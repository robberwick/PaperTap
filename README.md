# PaperTap

**Display your e-ticket QR codes on passive NFC-powered e-ink displays.**

PaperTap is an Android application designed to extract QR codes from train tickets, event tickets, and other e-tickets, then write them to 1.54" WaveShare NFC e-paper displays. Perfect for keeping your tickets accessible without draining your phone battery.

## Why PaperTap?

- **One-tap workflow**: Share a PDF ticket → QR code automatically extracted → Tap display to write
- **Battery-free display**: The e-paper tag requires no power and lasts indefinitely
- **Always scannable**: Works at ticket gates even when your phone is dead
- **Ultra-focused**: Built specifically for the QR code ticket use case

## Target Hardware

- **Display**: WaveShare 1.54" Passive NFC-Powered E-Paper (200×200px)
- **Phone**: Any Android device with NFC capability (API 21+)
- **Tag Type**: NFC-A compatible displays

Get the display: [WaveShare 1.54" NFC E-Paper]

[WaveShare 1.54" NFC E-Paper]: https://www.waveshare.com/1.54inch-nfc-powered-e-paper-bw.htm

## Features

✅ **Automatic QR detection** - ML Kit barcode scanning finds QR codes instantly  
✅ **PDF support** - Direct sharing from email links or downloaded PDFs  
✅ **Image support** - Works with screenshots, photos, or gallery images  
✅ **Crisp output** - Threshold-based processing ensures QR codes scan reliably  
✅ **Audio feedback** - Distinct sounds for start, success, and errors  
✅ **Configurable padding** - Adjust QR code margins via settings  
✅ **Quick reflash** - One-tap button to rewrite your last ticket  

## How It Works

1. **Get your ticket** - Receive email with PDF link or download the PDF
2. **Share to PaperTap** - Tap "Share" in your browser/file app, select PaperTap
3. **Auto-extraction** - App detects and extracts the QR code automatically
4. **Tap to write** - Hold your phone to the e-paper display
5. **Done** - Hear success sound, ticket is now on the display

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

- **Display Size**: Choose from supported WaveShare displays (defaults to 1.54")
- **QR Code Padding**: Adjust white border around QR code (0-50 pixels)

## Known Issues

**NFC can be finnicky** - Finding the right position and distance between your phone and the e-paper display may require some experimentation. Each phone's NFC antenna is positioned differently.

**Corrupted writes** - Occasionally the NFC transfer can fail, resulting in visual noise on the display. If this happens:
1. Toggle NFC off and back on in your phone's Quick Settings
2. Re-tap the display to retry the write

**NFC radio dying** - Some Android devices experience NFC chipset failures at the system level. This appears in logs as `android.os.DeadObjectException`. Toggle NFC off/on to recover.
## Technical Details

**Built with:**
- Kotlin + Android SDK
- ML Kit Barcode Scanning for QR detection
- Android PdfRenderer for PDF processing
- WaveShare NFC SDK for e-paper communication
- Material Design 3 UI components

**Key technologies:**
- Threshold-based image processing for crisp QR codes
- Foreground NFC dispatch for tag interception
- AudioTrack API for custom success/error sounds
- Kotlin coroutines for async operations

## Project Origins

PaperTap is a focused fork of [joshuatz/nfc-epaper-writer], which was itself adapted by [mk-fg] and [DevPika]. This version strips away general-purpose image editing features to create a streamlined tool specifically for e-ticket QR codes.

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
