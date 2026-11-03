---
name: CallReplay
description: Lavender Material 3 surfaces for a private native Android call library.
colors:
  primary: "#5936AD"
  on-primary: "#FFFFFF"
  primary-container: "#E9DFFF"
  on-primary-container: "#32156C"
  secondary: "#665680"
  secondary-container: "#EEE5FF"
  on-secondary-container: "#3E2D59"
  tertiary: "#246B48"
  incoming: "#087DF5"
  background: "#FAF7FF"
  surface: "#FFFBFF"
  on-surface: "#171126"
  surface-variant: "#EEE7F7"
  on-surface-variant: "#554B66"
  surface-container-lowest: "#FFFFFF"
  surface-container-low: "#FAF7FF"
  surface-container: "#F5EFFF"
  surface-container-high: "#EEE5FF"
  surface-container-highest: "#E9DFFF"
  outline: "#81728F"
  outline-variant: "#E5DCF3"
  dark-primary: "#CDB4FF"
  dark-on-primary: "#2D1458"
  dark-primary-container: "#42305F"
  dark-on-primary-container: "#EADDFF"
  dark-secondary: "#D1BFEB"
  dark-on-secondary: "#362744"
  dark-secondary-container: "#3B2D50"
  dark-on-secondary-container: "#EADDFF"
  dark-tertiary: "#8BD6AC"
  dark-on-tertiary: "#003920"
  dark-incoming: "#80BAFF"
  dark-background: "#15111D"
  dark-surface: "#211A2D"
  dark-on-surface: "#F3ECFF"
  dark-surface-variant: "#392E48"
  dark-on-surface-variant: "#CCC0DD"
  dark-surface-container-lowest: "#110D17"
  dark-surface-container-low: "#1B1525"
  dark-surface-container: "#251D32"
  dark-surface-container-high: "#30253F"
  dark-surface-container-highest: "#3A2D4B"
  dark-outline: "#9B8AAE"
  dark-outline-variant: "#493B5C"
typography:
  body-large:
    fontFamily: "Android default sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
    letterSpacing: "0.5sp"
rounded:
  settings-card: "16dp"
  recording-card: "18dp"
  pill: "50%"
spacing:
  row-gap: "8dp"
  control-gap: "12dp"
  row-inset: "16dp"
  page-gutter: "20dp"
  player-gutter: "24dp"
  player-section: "28dp"
components:
  recording-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.recording-card}"
    padding: "12dp 8dp"
  settings-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.settings-card}"
    padding: "14dp 16dp"
  player-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.pill}"
    size: "76dp"
  wave-medallion:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.primary}"
    rounded: "{rounded.pill}"
    size: "112dp"
---

# Design System: CallReplay

## Overview

**Creative North Star: "The approved lavender reference"**

The user-supplied twelve-screen reference dated 2026-11-04 governs the visual direction. Native Jetpack Compose and Material 3 provide structure and interaction; lavender surfaces, purple controls and rounded recording rows carry the identity. This records the implemented system, including its separate dark scheme.

The library is compact and readable. Task pages retain a shared top app bar, settings use bordered rows, and the player centers the waveform identity above playback controls. Product behavior and privacy commitments remain in PRODUCT.md.

**Key Characteristics:**

- Lavender tonal surfaces with purple primary actions.
- Rounded bordered cards and native Material controls.
- Hebrew and English text with explicit numeric reading direction.
- Theme-specific blue incoming and green outgoing indicators.

## Colors

Color roles come from the active light and dark schemes in `ui/theme/Theme.kt`; direction-specific blue is defined in `RecordingHistoryScreen.kt`. Frontmatter names without a `dark-` prefix describe the light scheme. Equal roles share values: light on-secondary and on-tertiary use on-primary; background text uses on-surface in each scheme. Error and other unspecified roles retain Material defaults.

### Primary

Purple primary controls contrast with pale lavender primary containers. Dark mode pairs a light lavender primary with deep purple containers and a dark on-primary role.

### Secondary

Muted violet secondary roles support tonal controls and selected navigation. Secondary containers remain distinct from the page background in each theme.

### Tertiary

Green tertiary marks outgoing calls. Incoming blue is a semantic accent with its own light and dark values, rather than a replacement for the primary action color.

### Neutral

Near-white lavender backgrounds and almost-white cards form the light scheme. Dark aubergine backgrounds and layered purple surfaces form the dark scheme. On-surface-variant supports metadata; outline-variant supplies quiet borders.

### Named Rules

**The Fixed Palette Rule.** Resolve colors through the active Material scheme; the implemented brand palette remains fixed rather than wallpaper-derived.

**The Direction Rule.** Incoming arrows are blue and outgoing arrows use tertiary green; their physical arrow direction remains stable in RTL.

## Typography

The implementation uses Android's default font family through Material 3 `Typography`; script fallback is platform-managed. `ui/theme/Type.kt` explicitly overrides only bodyLarge, recorded in the frontmatter. Other sizes, line heights and tracking remain the Material library defaults rather than a separately authored type scale.

### Hierarchy

- **Headline:** headlineLarge bold on splash; headlineMedium bold for the player identity; headlineSmall bold for setup and support headings.
- **Title:** titleLarge bold in task app bars; titleSmall bold for recording titles and settings rows.
- **Body:** bodyLarge for the tagline and player number; bodyMedium for explanations and editable file patterns; bodySmall for metadata and supporting text.
- **Label:** labelLarge for date groups; labelMedium for filename placeholders; labelSmall for attribution.

### Named Rules

**The Reading Direction Rule.** Follow locale for content layout while preserving LTR phone numbers, filenames, playback times and seeking; English legal content uses an LTR region.

## Layout

Use dp for layout and sp for text. The library list has 12dp horizontal content padding and an 8dp row gap; its filter strip uses 20dp horizontal padding and 12dp chip gaps. Settings index gutters are 16dp, most task pages 20dp, and the player 24dp. The player separates major control groups by 28dp.

The two primary destinations use a Material navigation bar below 600dp available width and a navigation rail at or above 600dp. Detail routes hide primary navigation. This breakpoint is implemented behavior, not a claim of completed tablet verification.

Top bars, scrolling content, system bars and keyboard use Compose inset handling. Native Material touch targets apply; the primary player action is explicitly 76dp and action-sheet rows have a 56dp minimum height. Preserve Android Back behavior and system font scaling.

## Elevation & Depth

Resting recording and settings cards use tonal surfaces and 1dp outline-variant borders rather than custom shadows. Compact navigation explicitly sets tonal elevation to zero. Sheets, dialogs, menus and other Material components retain their component defaults; there is no project-authored shadow or motion scale to extract.

## Shapes

Recording cards use an 18dp radius; settings rows use 16dp. Search fields and circular playback controls use CircleShape. Filter chips retain the native Material shape rather than a project-wide pill override. The waveform identity uses a circular medallion, or a square with radius equal to one quarter of its size on splash and About.

## Components

### Buttons

Native filled, filled-tonal, icon and text buttons retain Material enabled, disabled, pressed and focus treatments. The player uses a 76dp filled icon button with a 36dp icon. Row playback and mute use filled-tonal icon buttons; playback speed uses a filled-tonal button. Preparing playback replaces the play glyph with a progress indicator.

### Chips

Native FilterChip controls select all, incoming or outgoing calls, using Material selected and unselected treatments. A horizontally scrollable strip accommodates translated labels and larger type.

### Cards / Containers

Recording surfaces carry an outline-variant border with 8dp horizontal and 12dp vertical padding. Titles and caller metadata sit beside the direction indicator and tonal playback button. Tap opens the player; long press opens recording actions. Settings rows use 16dp horizontal and 14dp vertical padding with a 26dp primary icon.

### Inputs / Fields

Search uses a single-line circular outlined field with native Material focus treatment, back and clear actions. Filename editing uses an outlined field, explicit LTR text and an error state for blank values. Contact selection search also uses a circular outlined field; keyboard-aware task layouts keep controls reachable.

### Navigation

Material NavigationBar and NavigationRail share the two destinations and active selection. Task pages use a back affordance and titleLarge bold; the implemented reference-oriented task top bar is an LTR region with its title aligned to the end. Navigation is hidden in player and settings detail routes.

### Waveform identity and player

Five round-ended vertical strokes form WaveMark. Its player medallion is 112dp; the splash mark is an 80dp rounded square. Splash adds two overlapping primary-container waves with 0.5 and 0.72 opacity across the bottom 32% of the screen. The player seek track is 4dp with a 16dp thumb and explicit LTR progression. Seeking by ten seconds, speed, mute, sharing and overflow actions remain native controls.

## Do's and Don'ts

### Do:

- Do resolve surfaces and text through the active light or dark Material roles.
- Do preserve blue incoming, green outgoing and fixed physical direction arrows.
- Do retain native Material interactions, accessibility labels, insets and system font scaling.
- Do use the implemented navigation rail breakpoint when extending wider layouts.

### Don't:

- Don't mirror phone numbers, filenames, playback times or English legal paragraphs with Hebrew layout.
- Don't replace the approved fixed palette with wallpaper-derived dynamic color.
- Don't treat generated sidecar previews or tonal ramps as device screenshots or new application tokens.

Source evidence: `ui/theme/Theme.kt`, `ui/theme/Type.kt`, `ui/screens/CallReplayComponents.kt`, `ui/screens/RecordingHistoryScreen.kt`, and `ui/screens/CallReplaySettingsScreen.kt`. Device-test status, open review findings and device-class verification belong in the separate review artifacts, not this visual system.
