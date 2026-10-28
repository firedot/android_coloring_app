# 🎨 Colour Fun

A playful, child-friendly Android coloring app. Every session brings a **brand-new, randomly generated animal** to color, using a range of drawing tools that look and behave like the real thing.

> **Status:** 🚧 Early development. Features and structure may change.

---

## Table of Contents

- [Features](#features)
- [Drawing Mediums](#drawing-mediums)
- [Random Animal Generator](#random-animal-generator)
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

- 🐾 **Randomly generated animals.** No two pictures are the same, so there is always something new to color.
- 🎨 **Color palette.** Large, easy-to-tap swatches with a curated set of bright, kid-friendly colors.
- 💧 **Color picker (pipette).** Tap anywhere on the picture to pick up a color that is already there.
- 🖌️ **Seven drawing mediums.** Chalk, pencil, spray, magic pen, pen, paint brush and brush, each with its own look and behavior.
- 🪣 **Fill bucket.** Fill a closed area of the outline with a single tap.
- ↩️ **Undo / Redo.** Mistakes are easy to fix.
- 🧽 **Eraser.** Removes color without erasing the outline.
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

## Random Animal Generator

Pictures are created **procedurally** from a library of hand-designed vector parts rather than downloaded or produced by an AI model. This keeps the app small, offline and always child-safe.

### How it works

1. **Pick a seed.** A random number (or a fixed seed for reproducible pictures).
2. **Choose a body template.** For example, four-legged, bird, fish or bug.
3. **Assemble parts.** Head, ears, eyes, nose/beak, body, legs, tail and an optional pattern (spots, stripes) are chosen from compatible sets.
4. **Vary proportions.** Size, scale and position are randomized within safe limits so the result still looks like a cute animal.
5. **Add a scene (optional).** Simple background elements such as grass, sun, clouds or water.
6. **Render outlines.** Output is clean black line art made of **closed shapes**, so the fill bucket works reliably.

```
Seed ──▶ Template ──▶ Parts ──▶ Proportions ──▶ Scene ──▶ Line art (SVG/Path)
```

### Goals for generated pictures

- Thick, clear outlines (easy to see and color)
- Large areas suitable for small fingers
- Always friendly-looking: no scary features
- Every region is a closed path, so there are no "leaky" fills

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
UI (Compose) ──▶ ViewModel ──▶ Use cases ──▶ Drawing engine / Animal generator / Repository
      ▲                │
      └──── UiState ◀──┘
```

---

## Project Structure

```
colour-fun/
├── app/
│   └── src/main/java/com/example/colourfun/
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
│       ├── generator/
│       │   ├── parts/           # Vector part definitions (heads, ears, tails…)
│       │   ├── templates/       # Body templates
│       │   └── AnimalGenerator.kt
│       ├── data/                # Room DB, repositories
│       └── di/                  # Hilt modules
├── app/src/main/assets/
│   ├── parts/                   # SVG/vector animal parts
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
- The generator always produces closed, valid outlines for a range of seeds
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
- ✅ All pictures are generated and stored locally on the device
- ✅ Aims to meet Google Play's **Families Policy** and **COPPA** / **GDPR-K** requirements

---

## Roadmap

- [ ] Project setup (Kotlin, Compose, Hilt)
- [ ] Basic canvas with pen and undo/redo
- [ ] Color palette + pipette
- [ ] Layered canvas (paper / paint / outline)
- [ ] Flood fill bucket
- [ ] Implement all 7 drawing mediums
- [ ] Animal generator v1 (templates + parts)
- [ ] Save to gallery with parent gate
- [ ] Sound effects & haptics
- [ ] Tablet layout
- [ ] Stickers & stamps
- [ ] More animal templates (dinosaurs, sea creatures, insects)
- [ ] Localization (English, Bulgarian, …)

---

## Contributing

Contributions are welcome!

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/spray-brush`
3. Commit your changes: `git commit -m "Add spray brush"`
4. Push the branch: `git push origin feature/spray-brush`
5. Open a Pull Request

Please follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) and include tests for new drawing tools or generator logic.

---

## License

This project is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.

---

<p align="center">Made with ❤️ for little artists</p>
