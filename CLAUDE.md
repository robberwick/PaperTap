# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PaperTap is an Android app that extracts QR codes from e-tickets (PDFs or images) and writes them to WaveShare NFC-powered e-paper displays. It's a focused fork designed specifically for e-ticket QR codes.

**Key technologies:**

- Kotlin + Android SDK (API 21+, targets API 35)
- ML Kit Barcode Scanning for QR/Aztec code detection
- Android PdfRenderer for PDF processing
- WaveShare NFC SDK (proprietary JAR in libs/waveshare-nfc/NFC.jar)
- Material Design 3 UI
- rsp6-decoder-kotlin library for UK rail ticket decoding

## Build Commands

### Standard Build

```bash
# Build APK via Gradle
bash gradlew build

# Output location
app/build/outputs/apk/debug/papertap-debug.apk
```

### Docker Build

```bash
# Build using Docker (requires ~5GB memory, 3GB disk)
docker build --output type=local,dest=. .

# Output: app-debug.apk in current directory
```

### Android Studio

Build → Build Bundle(s) / APK(s) → Build APK(s)

## Architecture

### Core Flow

1. **MainActivity**: Entry point, handles sharing from other apps
   - Receives PDFs/images via Android Share intents
   - Extracts QR codes automatically using ML Kit
   - Falls back to manual cropping if auto-detection fails
   - Converts images to pure black/white (threshold-based, no dithering)
   - Saves processed image to internal storage as `generated.png`

2. **PdfQrExtractor**: PDF processing utility
   - Renders PDF pages at 3x resolution for better QR detection
   - Scans all pages for QR/Aztec codes using ML Kit
   - Crops detected codes with configurable padding
   - Attempts RSP6 decoding for UK rail tickets

3. **NfcFlasher**: NFC writing activity
   - Uses foreground dispatch system to intercept NFC tags
   - Validates tag UID against known WaveShare identifiers
   - Writes bitmap to e-paper display via WaveShare SDK
   - Provides audio feedback (Mario coin = success, sad trombone = error)
   - Includes NFC health checking loop to recover from `DeadObjectException`

### Key Components

**TicketData**: Data class for decoded rail ticket information

- Stores origin/destination, travel date/time, fare type, class
- JSON serialization for SharedPreferences persistence
- Only populated for UK rail tickets (RSP6 Aztec codes)

**Preferences**: SharedPreferences wrapper

- Screen size selection (1.54" B model is default)
- QR code padding (0-50 pixels, default 5)
- Ticket data persistence

**Constants.kt**: Centralized configuration

- WaveShare UID whitelist: `["WSDZ10m", "FSTN10m"]`
- Screen size enumeration (matches WaveShare SDK indexing)
- Resolution map for each display model

### WaveShare SDK Integration

The SDK is a proprietary JAR with obfuscated classes. Integration happens in two places:

1. **NfcFlasher.kt**: Direct usage of SDK class `waveshare.feng.nfctag.activity.a`
   - `a.a(nfcObj)` - Initialize NFC connection
   - `a.a(screenSizeEnum, bitmap)` - Send bitmap to display
   - `a.c` - Read progress percentage (0-100)

2. **WaveShareHandler.kt**: Wrapper interface (currently unused)
   - Provides cleaner API over obfuscated SDK
   - Consider using this for future refactoring

**Important**: WaveShare SDK enum is 1-indexed (1 = 2.13", 8 = 1.54" B), array is 0-indexed.

### NFC Tag Validation

Tags are validated using multiple checks:

1. **Tech type**: Must be `android.nfc.tech.NfcA`
2. **UID check**: Must match known WaveShare UIDs (currently relaxed for NDEF tags)
3. **AAR check**: For NDEF-discovered tags, validates Android Application Record payload is `"waveshare.feng.nfctag"`

### Image Processing

QR codes must be pure black/white for reliable e-paper display:

- Threshold: 128 (middle grey)
- Luminance calculation: `0.299*R + 0.587*G + 0.114*B`
- No dithering (preserves crisp QR edges)
- Final scaling matches target display resolution exactly

### Barcode Detection

ML Kit supports multiple 2D barcode formats:

- QR_CODE, AZTEC, DATA_MATRIX, PDF417
- High resolution rendering (PDF at 3x, images up to 2000px)
- Bounding box extraction with configurable padding
- UK rail tickets use AZTEC format with RSP6 encoding

## Common Gotchas

**NFC Issues**:

- `DeadObjectException`: Android NFC chipset failure at system level. App includes auto-recovery via polling loop that re-enables foreground dispatch.
- Corrupted writes: Visual noise on display. Solution: Toggle NFC off/on, retry.
- Each phone's NFC antenna is positioned differently - finding the sweet spot requires experimentation.

**WaveShare SDK**:

- Default timeout (700ms) is too short - override to 1200ms
- Screen size parameter is 1-indexed, not 0-indexed
- Progress reading is unreliable during multi-threaded flashing

**Gradle/Dependencies**:

- Uses JitPack for rsp6-decoder-kotlin (main-SNAPSHOT)
- Requires Java 17 for compilation
- WaveShare JAR is not in Maven - must be in libs/ directory

## File Structure Notes

**Station/Fare Lookups**:

- `app/src/main/res/raw/station_codes.json` - NLC code → station name mapping
- `app/src/main/res/raw/fare_codes.json` - UK rail fare codes
- Loaded lazily by StationLookup and FareCodeLookup singletons

**Intent Handling**:

- MainActivity registers for SEND intents (image/*, application/pdf, text/*)
- ACTION_VIEW support for direct URL opens (e.g., browser "Open with" feature)
- NfcFlasher uses launchMode="singleTask" to avoid multiple instances

**Audio Feedback**:

- Start: System notification sound
- Success: Mario coin (B5 988Hz → E6 1319Hz)
- Error: Sad trombone (C4 261.6Hz → A3 220Hz → F3 174.6Hz)
- Generated with AudioTrack, 44.1kHz, with fade-in/out envelopes to prevent clicking

## IDE Integration

Always use the `jetbrains-index` MCP server when applicable for:

- **Finding references** — Use `ide_find_references` instead of grep/search
- **Go to definition** — Use `ide_find_definition` for accurate navigation
- **Renaming symbols** — Use `ide_refactor_rename` for safe, project-wide renames
- **Type hierarchy** — Use `ide_type_hierarchy` to understand class relationships
- **Finding implementations** — Use `ide_find_implementations` for interfaces/abstract classes
- **Diagnostics** — Use `ide_diagnostics` to check for code problems

The IDE's semantic understanding is far more accurate than text-based search. Prefer IDE tools over grep, ripgrep, or manual file searching when working with code symbols.
