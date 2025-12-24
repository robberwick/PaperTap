# PaperTap Theming Guide

This guide explains how the app's theming system works and how to make common changes.

## Overview

PaperTap uses **Material Design 3** with proper light and dark theme support. The theming is centralized and semantic, meaning:
- Colors are defined once in `colors.xml`
- Themes map these colors to semantic roles (primary, surface, background, etc.)
- Layouts use theme attributes instead of hardcoded colors
- Dark mode works automatically based on system settings

## File Structure

```
app/src/main/res/
├── values/
│   ├── colors.xml          # Color palette definitions
│   └── themes.xml          # Light theme configuration
└── values-night/
    └── themes.xml          # Dark theme configuration
```

## Quick Changes

### Changing Brand Colors

Edit `app/src/main/res/values/colors.xml`:

```xml
<color name="blue_dark">#007fc6</color>      <!-- Main brand color -->
<color name="blue_medium">#00b4ff</color>    <!-- Lighter variant -->
<color name="blue_light">#d1f5ff</color>     <!-- Very light tint -->
<color name="blue_lighter">#e8f9ff</color>   <!-- Lightest tint -->
<color name="blue_darkest">#394855</color>   <!-- Darkest variant -->
```

**Example:** To change the brand color from blue to green:
1. Change `blue_dark` to your main green (`#00AA66`)
2. Change `blue_medium` to a lighter green (`#00CC7A`)
3. Change `blue_light` to a very light green (`#D0F5E8`)
4. Keep the existing color names (renaming not required)

### Changing Background Colors

#### Light Theme
Edit `app/src/main/res/values/themes.xml`:

```xml
<!-- Background color (main screen background) -->
<item name="android:colorBackground">@color/blue_lighter</item>
```

Change `@color/blue_lighter` to any color from `colors.xml` or a hex value like `#FFFFFF`

#### Dark Theme
Edit `app/src/main/res/values-night/themes.xml`:

```xml
<!-- Background color (main screen background) -->
<item name="android:colorBackground">@color/blue_darkest</item>
```

### Changing Toolbar Color

Edit the `colorPrimary` in both theme files:

**Light:** `app/src/main/res/values/themes.xml`
```xml
<item name="colorPrimary">@color/blue_dark</item>
```

**Dark:** `app/src/main/res/values-night/themes.xml`
```xml
<item name="colorPrimary">@color/blue_medium</item>
```

### Changing Card Colors

Cards use `colorSurface`. Edit in both theme files:

**Light:**
```xml
<item name="colorSurface">@color/white</item>
```

**Dark:**
```xml
<item name="colorSurface">#FF1E1E1E</item>
```

## Material Design 3 Color System

### Semantic Color Roles

| Role | Purpose | Example |
|------|---------|---------|
| `colorPrimary` | Main brand color | Toolbar, FABs, prominent buttons |
| `colorOnPrimary` | Text/icons on primary | White text on blue toolbar |
| `colorPrimaryContainer` | Tinted containers | Selected items, chips |
| `colorOnPrimaryContainer` | Text on primary containers | Dark text on light blue background |
| `colorSurface` | Card/dialog backgrounds | White cards in light mode |
| `colorOnSurface` | Text on surfaces | Black text on white cards |
| `colorBackground` | Screen backgrounds | Main app background |
| `colorOnBackground` | Text on backgrounds | Body text |

### How It Works

**Light Theme Example:**
```xml
<item name="colorPrimary">@color/blue_dark</item>        <!-- #007fc6 -->
<item name="colorOnPrimary">@color/white</item>          <!-- #FFFFFF -->
```
→ Toolbar is blue with white icons/text

**Dark Theme Example:**
```xml
<item name="colorPrimary">@color/blue_medium</item>      <!-- #00b4ff -->
<item name="colorOnPrimary">@color/blue_darkest</item>   <!-- #394855 -->
```
→ Toolbar is lighter blue with dark icons/text

## Common Scenarios

### 1. Change from Blue to Red Theme

**Step 1:** Add red colors to `colors.xml`:
```xml
<color name="red_dark">#C62828</color>
<color name="red_medium">#EF5350</color>
<color name="red_light">#FFCDD2</color>
<color name="red_lighter">#FFEBEE</color>
<color name="red_darkest">#8E0000</color>
```

**Step 2:** Update light theme (`values/themes.xml`):
```xml
<item name="colorPrimary">@color/red_dark</item>
<item name="colorPrimaryContainer">@color/red_light</item>
<item name="colorOnPrimaryContainer">@color/red_darkest</item>
<item name="android:colorBackground">@color/red_lighter</item>
```

**Step 3:** Update dark theme (`values-night/themes.xml`):
```xml
<item name="colorPrimary">@color/red_medium</item>
<item name="colorPrimaryContainer">@color/red_dark</item>
<item name="colorOnPrimaryContainer">@color/red_light</item>
<item name="android:colorBackground">@color/red_darkest</item>
```

### 2. Use Pure White/Black Backgrounds

**Light theme** - Pure white background:
```xml
<item name="android:colorBackground">#FFFFFFFF</item>
```

**Dark theme** - Pure black background:
```xml
<item name="android:colorBackground">#FF000000</item>
```

### 3. Disable Dark Mode

If you want to force light mode only:

1. Delete `app/src/main/res/values-night/themes.xml`
2. Or copy `values/themes.xml` to `values-night/themes.xml`

## Best Practices

### ✅ DO:
- Define colors in `colors.xml`
- Use semantic color roles in themes
- Test changes in both light and dark modes
- Use theme attributes in layouts (`?attr/colorPrimary`)

### ❌ DON'T:
- Hardcode colors directly in layouts (`android:textColor="@color/blue_dark"`)
- Use color values in layouts (`android:background="#007fc6"`)
- Modify theme files without testing both light/dark
- Break the Material Design color system

## Testing Your Changes

1. **Build the app:**
   ```bash
   bash gradlew assembleDebug
   ```

2. **Test both themes:**
   - Light mode: Settings → Display → Light theme
   - Dark mode: Settings → Display → Dark theme

3. **Check these screens:**
   - Main activity (with and without ticket)
   - Settings screen
   - NFC flashing screen
   - About screen

## Troubleshooting

### Problem: Text is unreadable
**Cause:** Wrong `colorOn*` pairing
**Solution:** Ensure `colorOnX` contrasts with `colorX`:
```xml
<!-- Good - High contrast -->
<item name="colorSurface">@color/white</item>
<item name="colorOnSurface">@color/blue_darkest</item>

<!-- Bad - Low contrast -->
<item name="colorSurface">@color/blue_light</item>
<item name="colorOnSurface">@color/blue_medium</item>
```

### Problem: Settings screen has wrong colors
**Cause:** PreferenceScreen uses surface colors
**Solution:** Check `colorSurface` and `colorOnSurface` in themes

### Problem: Dark mode looks like light mode
**Cause:** `values-night/themes.xml` is identical to light theme
**Solution:** Ensure dark theme uses appropriate dark colors

## Material Design Resources

- [Material Design 3 Color System](https://m3.material.io/styles/color/overview)
- [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/)
- [Color Tool](https://material.io/resources/color/)

## Current Color Palette

PaperTap uses a blue/tan color scheme:

**Blue (Primary):**
- `blue_darkest`: #394855 (Dark text, dark backgrounds)
- `blue_dark`: #007fc6 (Toolbar, primary actions)
- `blue_medium`: #00b4ff (Accents, dark mode primary)
- `blue_light`: #d1f5ff (Light containers)
- `blue_lighter`: #e8f9ff (Light backgrounds)

**Tan (Secondary):**
- `tan_darkest`: #703900
- `tan_dark`: #8c7357
- `tan_light`: #ffeccc

**Neutrals:**
- `white`: #FFFFFF
- `black`: #FF000000

---

*Last updated: December 2025*
