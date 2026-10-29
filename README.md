# 🎨 Colour Fun

A playful, child-friendly Android coloring app. Children pick a picture from a bundled library and color it full screen, using a range of drawing tools that look and behave like the real thing.

> **Status:** 🚧 Early development. Features and structure may change.

---

## Table of Contents

- [Features](#features)
- [Drawing Mediums](#drawing-mediums)
- [Pictures](#pictures)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Testing](#testing)
- [Design Principles for Kids](#design-principles-for-kids)
- [Privacy & Child Safety](#privacy--child-safety)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Features

- **Full-screen canvas.** The picture fills the whole screen, edge to edge; all controls float on top.
- **Zoom and pan.** Pinch to zoom in on small areas (up to 4×), two fingers to move around, a Fit button to see the whole picture again.
- **Expandable controls.** Two big floating buttons open their panels when tapped and close after a choice:
  - **Palette** (paint-splat icon): all 12 colors of the color wheel (3 primary, 3 secondary, 6 tertiary) plus black, white, grey, brown and pink, and a color picker (pipette).
  - **Tools** (paint-brush icon): pencil, pen, chalk, spray, brush, paint brush, magic pen, eraser and fill bucket, each in three sizes.
- **Picture library.** Children choose a picture from sections (Animals, Plants, Various). All pictures are bundled, hand-checked line art that works with the fill bucket. Started pictures appear in "My pictures" to continue later.
- ↩️ **Undo / Redo.** Mistakes are easy to fix.
- 💾 **Save to gallery.** Keep finished pictures, with a parent gate.
- 📴 **Works fully offline.** No account, no ads, no internet needed.

---

## Drawing Mediums

Each medium is a separate `Brush` implementation with its own stroke rendering logic.

| Medium | Look | Behavior |
|---|---|---|
| **Pencil** ✏️ | Thin, slightly grainy line | Fixed narrow width, light texture, mild transparency so layers build up darker |
| **Pen** 🖊️ | Crisp, solid line | Fully opaque, constant width, smooth edges. Good for details |
| **Chalk** 🖍️ | Rough, dusty, broken line | Textured stamp with random gaps; shows the "paper" grain underneath |
| **Spray** 💨 | Soft cloud of dots | Random particle scattering within a radius; density grows the longer you hold in one spot |
| **Brush** 🖌️ | Medium, soft-edged stroke | Width varies with stroke speed (slow = thicker); soft, feathered edges |
| **Paint brush** 🎨 | Thick, wet paint | Wide stroke, colors blend slightly where strokes overlap, paint "runs out" over a long stroke |
| **Magic pen** ✨ | Rainbow / sparkle line | Color cycles along the stroke (hue shift), with a subtle sparkle/glow effect |

### Common rules for all mediums

- Color always stays **inside the outline layer**. The black lines are never painted over.
- Each tool has **2–3 preset sizes** (small / medium / large) instead of a slider, so it is easy for small hands.
- All strokes are recorded as commands so they support **undo/redo**.

---

## Pictures

Library pictures are black-and-white line art made to strict rules (closed shapes, thick lines,
no tiny areas). See [docs/PICTURE_GUIDELINES.md](docs/PICTURE_GUIDELINES.md) for the rules and
the image-generation prompt.

---

## Tech Stack

| Area | Technology |
|---|---|
| Language | [Kotlin](https://kotlinlang.org/) |
| UI | [Jetpack Compose](https://developer.android.com/jetpack/compose) |
| Drawing | Compose `Canvas` + Android `Bitmap` / `Canvas` for the paint layer |
| Architecture | MVVM + unidirectional data flow |
| DI | [Hilt](https://dagger.dev/hilt/) |
| Async | Kotlin Coroutines & Flow |
| Storage | Room (saved artworks metadata), MediaStore (exported images) |
| Testing | JUnit, Compose UI tests, Robolectric |
| Build | Gradle (Kotlin DSL), version catalogs |
| Min SDK | 26 (Android 8.0) |
| Target SDK | Latest stable |

---

## Architecture

The canvas is built from **three stacked layers**:

```
┌─────────────────────────┐
│  3. Outline layer        │  ← black line art (always on top, never painted)
├─────────────────────────┤
│  2. Paint layer          │  ← everything the child draws
├─────────────────────────┤
│  1. Paper layer          │  ← white/textured background
└─────────────────────────┘
```

A **region mask** generated from the outline is used to keep strokes inside the shape where the stroke started (optional "stay inside the lines" mode).

```
UI (Compose) ──▶ ViewModel ──▶ Use cases ──▶ Drawing engine / Picture library / Repository
      ▲                │
      └──── UiState ◀──┘
```

---

## Project Structure

```
colour-fun/
├── app/
│   └── src/main/java/com/fireworm/colourfun/
│       ├── ui/
│       │   ├── canvas/          # Drawing screen, canvas composable
│       │   ├── palette/         # Color palette & pipette UI
│       │   ├── tools/           # Medium selector
│       │   ├── gallery/         # Saved pictures
│       │   └── theme/           # Colors, typography, shapes
│       ├── drawing/
│       │   ├── brushes/         # Pencil, Pen, Chalk, Spray, Brush, PaintBrush, MagicPen
│       │   ├── fill/            # Flood fill algorithm
│       │   ├── history/         # Undo/redo command stack
│       │   └── layers/          # Paper, paint and outline layers
│       ├── data/                # Room DB, repositories
│       └── di/                  # Hilt modules
├── app/src/main/assets/
│   ├── pictures/                # Library line art and thumbnails, one folder per section
│   └── textures/                # Chalk, paper, pencil grain textures
├── docs/                        # Design notes, screenshots
└── README.md
```

---

## Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (latest stable)
- JDK 17
- An Android device or emulator (a tablet is recommended for testing)

### Clone and run

```bash
git clone https://github.com/<your-username>/colour-fun.git
cd colour-fun
```

1. Open the project in **Android Studio**.
2. Let Gradle sync.
3. Select a device or emulator.
4. Press **Run ▶**.

### Build from the command line

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew installDebug         # Install on a connected device
```

---

## Testing

```bash
./gradlew test                 # Unit tests
./gradlew connectedAndroidTest # Instrumented / UI tests
./gradlew lint                 # Static analysis
```

Key areas to cover:

- Flood fill stays inside closed regions
- Undo/redo restores the exact previous state
- Every library picture follows the line-art rules (closed regions, no tiny areas)
- Each brush renders consistently at all sizes

---

## Design Principles for Kids

- **Big touch targets.** At least 48dp, and larger for primary controls.
- **Minimal text.** Icons and colors instead of words, so pre-readers can use it.
- **No dead ends.** Every screen has an obvious way back.
- **Instant feedback.** Every tap does something visible, with gentle sounds that can be muted.
- **No failure states.** There is no "wrong" way to color.
- **Parent gate.** Settings, saving and sharing sit behind a simple adult check.

---

## Privacy & Child Safety

- ❌ No ads
- ❌ No in-app purchases
- ❌ No analytics or tracking
- ❌ No internet permission required
- ✅ All pictures are bundled with the app, and artworks are stored locally on the device
- ✅ Aims to meet Google Play's **Families Policy** and **COPPA** / **GDPR-K** requirements

---

## Roadmap

1. Project setup
2. Layered, full-screen canvas
3. Zoom and pan
4. Basic pen + undo/redo
5. Floating palette and tools + pipette
6. Fill bucket
7. All 7 mediums
8. Picture library (sections, first pictures)
9. Save with parent gate

---

## Contributing

Contributions are welcome!

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/spray-brush`
3. Commit your changes: `git commit -m "Add spray brush"`
4. Push the branch: `git push origin feature/spray-brush`
5. Open a Pull Request

Please follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) and include tests for new drawing tools or library pictures.

---

## License

This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.

---

<p align="center">Made with ❤️ for little artists</p>
