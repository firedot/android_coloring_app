# Colour Fun — Architecture

This document turns the goals in [README.md](../README.md) into a concrete technical design.
It covers module layout, the drawing engine, the animal generator, persistence, UI state and
testing. Decisions that diverge from or refine the README are marked **(refines README)**.

---

## 1. Guiding constraints

These come straight from the README and drive most decisions below:

| Constraint | Consequence |
|---|---|
| Fully offline, no `INTERNET` permission | Everything (generator, textures, sounds) is bundled; manifest actively strips `INTERNET` |
| Color never covers the outline | Outline is a separate layer composited **on top**; it is never part of the paint bitmap |
| Fill bucket must never leak | Regions are derived from the *rendered* outline, not from vector math |
| Undo/redo for every action | Every mutation of the paint layer goes through a command with a recorded before/after state |
| Brushes look "real" | Brushes are stamp/particle based and render into raster bitmaps, not vector paths |
| Reproducible pictures from a seed | Generator is pure, deterministic and owns its own PRNG |
| Min SDK 26, phones + tablets | Fixed-size document, scaled to the screen; memory budget sized for low-end devices |

---

## 2. Module layout **(refines README)**

The README shows a single `:app` module. The generator and drawing engine are large,
self-contained and benefit from fast JVM tests, so they are split out. Feature modules are
not worth it at this app size.

```
:app ───────────────▶ :drawing ─────▶ :generator
  │                      ▲                ▲
  └──────────▶ :data ────┘────────────────┘
```

| Module | Type | Contents | Depends on |
|---|---|---|---|
| `:generator` | **Pure Kotlin/JVM** | PRNG, geometry model, parts/templates, `AnimalGenerator`, path-data parser | nothing (kotlinx-serialization only) |
| `:drawing` | Android library | Layers, brushes, fill, region labeling, history, `DrawingEngine` | `:generator` (for `LineArt` model) |
| `:data` | Android library | Room DB, artwork file store, MediaStore export, settings (DataStore) | `:generator`, `:drawing` (for snapshot types) |
| `:app` | Android application | Compose UI, ViewModels, navigation, Hilt modules, sound/haptics | all of the above |

Rules:
- `:generator` must not import `android.*`. This keeps it testable with plain JUnit across
  thousands of seeds in seconds.
- `:drawing` has no Compose dependency. It exposes `Bitmap`s and a small API; the Compose layer
  adapts it. This keeps the engine usable from Robolectric tests and from an export worker.
- Only `:app` knows about Hilt. Library modules expose plain constructors; `:app/di` wires them.

### Package layout

```
generator/src/main/kotlin/com/example/colourfun/generator/
├── random/        Pcg32 (own PRNG — kotlin.random is not guaranteed stable across versions)
├── geometry/      Vec2, Transform, PathShape (move/line/cubic/close), PathDataParser
├── parts/         Part, PartCategory, Anchor, PartLibrary (loads JSON)
├── templates/     BodyTemplate, Slot, QuadrupedTemplate, BirdTemplate, FishTemplate, BugTemplate
├── scene/         Scene elements (grass, sun, clouds, water)
├── LineArt.kt     Output model
└── AnimalGenerator.kt

drawing/src/main/kotlin/com/example/colourfun/drawing/
├── document/      Document (size, layers), DocumentCoords
├── layers/        PaperLayer, PaintLayer, OutlineLayer, OutlineRasterizer
├── regions/       RegionMap (connected-component labeling), RegionMaskCache
├── brushes/       Brush, BrushSpec, StrokePoint, Pencil, Pen, Chalk, Spray, SoftBrush,
│                  PaintBrush, MagicPen, Eraser
├── fill/          FillOperation
├── history/       Command, StrokeCommand, FillCommand, PixelPatch, History
├── pipette/       ColorSampler
└── DrawingEngine.kt

data/src/main/kotlin/com/example/colourfun/data/
├── db/            ColourFunDatabase, ArtworkEntity, ArtworkDao
├── files/         ArtworkFileStore (paint PNG, outline JSON, thumbnail)
├── export/        GalleryExporter (MediaStore)
├── settings/      SettingsRepository (DataStore)
└── ArtworkRepository.kt

app/src/main/java/com/example/colourfun/
├── ui/canvas/     ColoringScreen, DrawingSurface (Compose ↔ engine adapter), CanvasViewModel
├── ui/palette/    PaletteBar, PipetteButton
├── ui/tools/      ToolBar, SizePicker
├── ui/gallery/    GalleryScreen, GalleryViewModel
├── ui/parentgate/ ParentGateDialog
├── ui/settings/   SettingsScreen (behind parent gate)
├── ui/theme/
├── feedback/      SoundPlayer (SoundPool), Haptics
├── navigation/    ColourFunNavHost
└── di/
```

---

## 3. The document and its layers

### 3.1 Fixed document space

All drawing happens in a **fixed logical document** of **2048 × 1536 px** (4:3, landscape-first).
The screen shows it fit-centered via a `Matrix` (document → view); touches are mapped back with
its inverse. Benefits:

- Pictures look identical on every device and in exports.
- Brush sizes, stamp spacing and region thresholds are defined once, in document pixels.
- Undo patches and saved files have a stable size.

On devices reporting `ActivityManager.isLowRamDevice()` the document is scaled to 0.75
(1536 × 1152). The scale is stored with each artwork.

### 3.2 Layers and memory

| Layer | Representation | Size @ 2048×1536 | Notes |
|---|---|---|---|
| Paper | Tiled texture `BitmapShader` | ~64 KB texture | Never stored per picture; not erasable |
| Paint | `Bitmap` ARGB_8888 | 12.6 MB | The only layer the child changes |
| Stroke scratch | `Bitmap` ARGB_8888 | 12.6 MB | The in-progress stroke; merged into Paint on stroke end |
| Outline | `Bitmap` **ALPHA_8** | 3.1 MB | Black-only, so alpha is enough; tinted black when drawn |
| Region map | `ShortArray` label per pixel | 6.3 MB | Region id per pixel, `0` = outline |
| Region mask | `Bitmap` ALPHA_8 (cached, 1–2 entries) | 3.1 MB each | Mask for "stay inside the lines" |

Total ≈ 40 MB plus the undo budget (§6). This fits comfortably in the default heap on API 26
devices.

### 3.3 Display composition

`DrawingSurface` (Compose) draws, every frame the engine reports as dirty:

```
1. paper      drawRect with the texture shader
2. paint      drawImage(paintBitmap)
3. stroke     drawImage(strokeScratch, alpha = brush.opacity, blendMode = brush.blendMode)
              (DstOut for the eraser, so the preview matches the final result)
4. outline    drawImage(outlineBitmap, colorFilter = tint(Black))
```

The engine exposes a `frameVersion: State<Long>`. The draw lambda reads it, so only the **draw
phase** re-runs on each stroke update — no recomposition.

> **Risk:** re-uploading a 12.6 MB bitmap texture per frame on low-end GPUs. Measure in the
> "basic canvas" milestone. Escape hatch: split Paint/Scratch into 256 px tiles and upload only
> dirty tiles. The engine API (`dirtyRect` per update) is designed so this is a local change.

---

## 4. Drawing engine (`:drawing`)

### 4.1 Threading

- `DrawingEngine` and all bitmaps are **confined to the main thread**. Per touch event a brush
  draws only a handful of stamps into a dirty rect, which is cheap.
- Heavy, pure computations run on `Dispatchers.Default` and hand back plain arrays:
  outline rasterization, region labeling, fill pixel computation, PNG encoding.
  Results are applied on the main thread.
- This avoids locking and tearing without a dedicated render thread. If profiling later shows
  jank, the engine can move behind a single-threaded dispatcher without changing its API.

### 4.2 Public API (sketch)

```kotlin
class DrawingEngine(document: Document, regions: RegionMap, history: History) {
    val frameVersion: State<Long>

    fun beginStroke(spec: BrushSpec, color: Int, p: StrokePoint)
    fun extendStroke(points: List<StrokePoint>)   // includes MotionEvent historical points
    fun tick(frameTimeNanos: Long)                 // dwell time, needed by Spray
    fun endStroke()
    fun cancelStroke()                             // second finger / palm → discard

    suspend fun fill(at: PointF, color: Int)
    fun sampleColor(at: PointF): Int               // pipette

    fun undo(); fun redo()
    val canUndo: StateFlow<Boolean>; val canRedo: StateFlow<Boolean>

    fun snapshotPaint(): Bitmap                    // for save/export
}
```

Touch input is **not** routed through the ViewModel's `StateFlow` — at 120 Hz with historical
points that would add allocation and latency. `DrawingSurface` calls the engine directly from
`pointerInput`; the ViewModel only owns tool/color/size selection and the engine instance.
While a pointer is down, a `withFrameNanos` loop calls `tick()` so the spray can keep building
density when the finger is still.

### 4.3 Stroke pipeline

```
touch points ──▶ smoothing ──▶ resampling ──▶ brush stamps ──▶ stroke scratch
                (1-euro filter)  (fixed spacing          │
                                  in doc px)             ▼
                                              clip to region mask (DstIn, dirty rect only)
                                                         │
                         on endStroke ──────────────────▶ merge into Paint layer
                                                          (brush opacity + blend mode)
                                                         │
                                                         ▼
                                                 StrokeCommand → History
```

Drawing into a **scratch layer first** gives correct medium behavior for free:

- A single pen stroke crossing itself does not get darker (scratch is drawn opaque).
- Pencil strokes merge at ~60 % opacity, so *separate* strokes build up darker, as the README asks.
- The eraser draws normally into scratch and merges with `DST_OUT`, removing only paint.

### 4.4 Brush model

```kotlin
data class StrokePoint(val x: Float, val y: Float, val timeMs: Long, val pressure: Float)

enum class BrushSize { Small, Medium, Large }       // no slider, per README

data class BrushSpec(val medium: Medium, val size: BrushSize)

interface Brush {
    val opacity: Float                     // applied at merge
    val blendMode: BlendMode               // SrcOver, or DstOut for eraser
    fun begin(ctx: StrokeContext, p: StrokePoint)
    fun segment(ctx: StrokeContext, from: StrokePoint, to: StrokePoint): RectF  // returns dirty rect
    fun dwell(ctx: StrokeContext, at: StrokePoint, dtMs: Long): RectF           // default: empty
}

class StrokeContext(
    val canvas: Canvas,            // over the stroke scratch bitmap
    val random: Pcg32,             // seeded per stroke → deterministic rendering
    val color: Int,
    val widthPx: Float,            // resolved from BrushSize per medium
    val paintBelow: PixelReader,   // read-only view of the paint layer (PaintBrush blending)
    val textures: TextureSet,
)
```

Per-medium implementation notes:

| Medium | Technique |
|---|---|
| Pencil | Small stamps with a grain `BitmapShader`, slight jitter; merged at 0.6 opacity |
| Pen | `Path` with round caps/joins drawn per segment; opacity 1.0 |
| Chalk | Textured stamp, random skips (`random.nextFloat() < gap`), then `DstIn` with paper grain so the paper shows through |
| Spray | Particles in a disc, Gaussian radius; particle count ∝ `dt` in both `segment` and `dwell` |
| Brush | Width = f(velocity), low-pass filtered; stamps with a radial-gradient soft edge |
| Paint brush | Wide soft stamps; color mixed toward `paintBelow` average under the stamp (~15 %); opacity and width decay with stroke length ("runs out") |
| Magic pen | Hue = f(distance along stroke); core line + `BlurMaskFilter` glow; random sparkle stamps |
| Eraser | Opaque round stamps; merged with `DstOut` |

Because each stroke has its own seed, a stroke renders **identically every time**. This enables
golden-image tests (§9) and keeps undo/redo free of visual surprises.

### 4.5 Regions: "stay inside the lines" and fill

Regions come from the **rendered outline bitmap**, not from vector geometry. Overlapping shapes
(e.g. a head over a body) create new visible areas that geometry alone does not describe; the
raster is ground truth for what the child sees.

1. Threshold the outline alpha (`a > 96` ⇒ line).
2. Run 4-connected **connected-component labeling** (two-pass union-find) on non-line pixels
   → `RegionMap` (`ShortArray`, plus per-region bounding box and area).
   4-connectivity guarantees that a diagonal one-pixel gap in a line still separates regions.
3. Region `0` = outline pixels; the region touching the document border = background.

**Stay inside the lines** (optional, default on): at `beginStroke`, look up the region under the
first point. The `RegionMaskCache` builds an ALPHA_8 mask for that region, **dilated by half the
outline width + 1 px** so paint tucks under the anti-aliased line and no white halo remains.
Each stroke segment is clipped with `DstIn` against that mask, over the segment's dirty rect only.
Starting a stroke on the outline itself picks the nearest region.

**Fill bucket** = "paint every pixel of region *R* (dilated) with color *c*". Computed off the main
thread as a pixel patch over the region's bounding box, then applied. It replaces whatever paint is
in the region, which matches child expectations ("make the ear blue"). Classic pixel flood fill is
not needed because the regions are already known, and it cannot leak by construction.

### 4.6 Pipette

`sampleColor` reads the Paint layer at the point. If the pixel is transparent it returns the paper
color; if it is on the outline it samples the nearest non-outline pixel instead of returning black.
Semi-transparent paint is composited over paper before returning, so the picked color matches what
is on screen.

---

## 5. Animal generator (`:generator`)

### 5.1 Pipeline

```
seed ─▶ Pcg32 ─▶ pick template ─▶ fill slots with parts ─▶ vary proportions ─▶ scene ─▶ validate ─▶ LineArt
                                                                                  │
                                                         fails? ──▶ attempt+1 ────┘ (max 8, then curated fallback)
```

- `generate(seed: Long): LineArt` is pure and deterministic. Attempt *n* uses
  `Pcg32(seed, stream = n)`, so retries are also reproducible.
- A small set of **curated known-good seeds** is bundled as the last resort, so the child always
  gets a picture.

### 5.2 Parts and templates

Parts are drawn by hand in a vector editor and exported as SVG, then converted (a small Gradle
task in `buildSrc`/`build-logic`) into JSON in `assets/parts/`. A runtime SVG parser is not
needed; only SVG path-data strings are parsed by `PathDataParser` in `:generator`.

```json
{
  "id": "ear_floppy_01",
  "category": "ear",
  "compatibleWith": ["quadruped"],
  "paths": ["M 0 0 C 12 -30 40 -30 48 0 Z"],
  "anchors": { "attach": [24, 0] },
  "zOrder": 2,
  "mirrorable": true,
  "scaleRange": [0.85, 1.2]
}
```

A `BodyTemplate` defines **slots** — named attach points on the body with allowed categories,
count, mirroring and z-order:

```kotlin
data class Slot(
    val name: String,              // "head", "ear_left", "leg_front", "tail", ...
    val category: PartCategory,
    val anchor: Vec2,              // relative to the body part's anchors
    val mirrorOf: String? = null,  // ear_right mirrors ear_left
    val optional: Boolean = false,
)
```

Proportions are varied within each part's `scaleRange` and per-template limits (e.g. head : body
ratio between 0.45 and 0.7 — big heads read as "cute"). Patterns (spots, stripes) are closed
shapes clipped to the body outline.

### 5.3 Output model

```kotlin
data class LineArt(
    val width: Int, val height: Int,
    val shapes: List<OutlineShape>,       // in back-to-front order
    val strokeWidth: Float,               // document px, >= 10 so lines are thick and never leak
    val seed: Long, val generatorVersion: Int,
)

data class OutlineShape(val path: PathShape, val occludes: Boolean = true)  // every PathShape is closed
```

### 5.4 Rendering outlines with occlusion

`OutlineRasterizer` (in `:drawing`) draws shapes back-to-front into the ALPHA_8 outline bitmap.
For each shape it first draws the **fill with `CLEAR`** (erasing lines of shapes behind it), then
its stroke. The result contains only *visible* lines — the body line does not cut through a head
that overlaps it — and the region labeling in §4.5 then sees exactly the picture the child sees.

### 5.5 Validation (run on every generated picture)

After rasterizing and labeling (cheap, already needed for the canvas):

- All shapes are within the document with a margin.
- **No tiny regions:** every region except outline ≥ ~1500 px² (document units). Slivers from
  overlaps fail the attempt.
- Region count within a sane range (e.g. 8–80).
- The animal's bounding box covers 45–85 % of the document.

The same checks run in CI over 10 000 seeds (§9).

---

## 6. Undo / redo

Every paint mutation is a `Command`:

```kotlin
sealed interface Command {
    val bounds: Rect
    val before: PixelPatch     // pixels of Paint inside bounds before the command
    val after: PixelPatch      // ... and after
}
class StrokeCommand(..., val spec: BrushSpec, val color: Int, val seed: Long, val points: FloatArray) : Command
class FillCommand(..., val region: Int, val color: Int) : Command
```

- Undo writes `before` into the Paint layer; redo writes `after`. Both are O(dirty area) and
  independent of brush randomness or the number of earlier strokes — no full replay.
- Patches are stored as compressed `IntArray`s (deflate on a background thread; most of a stroke's
  bounding box is unchanged and compresses very well).
- `History` keeps a **memory budget** (default 48 MB, 24 MB on low-RAM devices) and a max of
  50 steps; the oldest entries are dropped first. A new command clears the redo stack.
- Stroke metadata (`spec`, `seed`, `points`) is kept for debugging and a possible future
  "time-lapse replay" feature; it is not needed for undo.

---

## 7. Data and persistence (`:data`)

### 7.1 What is stored

```
filesDir/artworks/<artworkId>/
├── outline.json      LineArt (serialized) — the picture is restored from this, not from the seed
├── paint.png         Paint layer (lossless)
└── thumb.webp        Composite thumbnail for the gallery grid
```

```kotlin
@Entity(tableName = "artwork")
data class ArtworkEntity(
    @PrimaryKey val id: String,          // UUID
    val seed: Long,
    val generatorVersion: Int,
    val documentScale: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val exportedUri: String?,            // set after "save to gallery"
)
```

**Outlines are stored, not regenerated from the seed.** Any change to parts or templates would
change what an old seed produces and misalign the saved paint. The seed and `generatorVersion`
are kept for debugging and "make another like this".

### 7.2 Autosave and process death

- The current picture is autosaved (debounced 2 s after the last command, and in `onStop`)
  into the same artwork folder. A child never loses work when the app is backgrounded or killed.
- `SavedStateHandle` holds only the current `artworkId`; on restore the ViewModel reloads files.
- Undo history is in-memory only and is lost on process death (acceptable for this audience).

### 7.3 Save to the device gallery (behind parent gate)

`GalleryExporter` flattens paper + paint + outline into a PNG and writes it through MediaStore
to `Pictures/Colour Fun/`.

- API 29+: `MediaStore` insert with `IS_PENDING`; no permission needed.
- API 26–28: requires `WRITE_EXTERNAL_STORAGE` declared with `android:maxSdkVersion="28"`,
  requested only at this moment, after the parent gate.

### 7.4 Settings

DataStore (Preferences): `soundEnabled`, `hapticsEnabled`, `stayInsideLines`, `lastToolSpec`,
`lastColor`. Changes to anything except tool/color go through the parent gate.

---

## 8. UI layer (`:app`)

### 8.1 Navigation

```
Coloring (start) ──▶ Gallery ──▶ Coloring(artworkId)
     │
     └──▶ [ParentGate] ──▶ Settings
                     └──▶ Save to device / Share
```

The app opens straight into **Coloring** with a new animal — no menu screen, consistent with
"minimal text" and "no dead ends". A big "new animal" button starts a fresh picture (autosave
keeps the old one in the in-app gallery).

### 8.2 ViewModel and state

```kotlin
data class ColoringUiState(
    val loading: Boolean = true,
    val tool: Tool = Tool.Draw(BrushSpec(Medium.Pencil, BrushSize.Medium)),
    val color: Int,
    val palette: List<Int>,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val stayInsideLines: Boolean = true,
)

sealed interface Tool {
    data class Draw(val spec: BrushSpec) : Tool
    data object Fill : Tool
    data object Pipette : Tool
    data class Erase(val size: BrushSize) : Tool
}

sealed interface ColoringEvent {
    data class SelectTool(val tool: Tool) : ColoringEvent
    data class SelectColor(val color: Int) : ColoringEvent
    data object Undo : ColoringEvent
    data object Redo : ColoringEvent
    data object NewAnimal : ColoringEvent
}
```

`CanvasViewModel` exposes `StateFlow<ColoringUiState>` and `onEvent(ColoringEvent)`
(unidirectional data flow). It owns the `DrawingEngine` for the current picture. As noted in
§4.2, raw touch input bypasses the ViewModel and goes to the engine directly.

Use cases (thin, in `:app/domain` or the ViewModel package):
`NewPictureUseCase` (generate → rasterize → label → create artwork),
`LoadPictureUseCase`, `AutosaveUseCase`, `ExportToGalleryUseCase`.

### 8.3 Kid-friendly UI rules (from README, made concrete)

- Touch targets ≥ 64 dp for primary controls, ≥ 48 dp for everything.
- Tool bar and palette on the screen edges; the canvas never scrolls or zooms in v1
  (zoom/pan is a later, parent-toggled feature).
- Multi-touch: a second finger **cancels** the current stroke (`cancelStroke`) instead of
  drawing a stray line; palm rejection uses `MotionEvent.FLAG_CANCELED` on API 33+.
- Every tap gives feedback: `SoundPool` short sounds (mutable) + light haptic tick.

### 8.4 Parent gate

A modal asking to enter digits written as words ("Type: seven, two, four"), randomized each time.
Pre-readers cannot pass it; adults pass it in seconds. Guards: settings, save to device, share.

---

## 9. Testing strategy

| Area | Kind | What |
|---|---|---|
| Generator | JVM unit, property-based | 10 000 seeds: deterministic (same seed ⇒ same `LineArt`), all paths closed, validation passes, generation < 50 ms |
| Generator | Golden | A few fixed seeds serialized to JSON; any diff must bump `generatorVersion` |
| Region labeling | JVM/Robolectric | Synthetic outlines: diagonal gaps don't merge regions, nested shapes, document-border region |
| Fill | Robolectric (`@GraphicsMode(NATIVE)`) | Fill never changes pixels outside the dilated region; outline unaffected |
| Brushes | Screenshot (Roborazzi) | Each medium × size draws a fixed stroke with a fixed seed → compared to golden PNGs |
| History | Robolectric | Random sequences of strokes/fills/undo/redo; Paint bitmap equals expected after each step; budget eviction |
| Persistence | Instrumented | Save → kill → restore yields identical paint pixels |
| UI | Compose UI tests | Tool selection, parent gate, undo/redo enabled state |
| Manifest | Unit/lint check | Merged manifest has no `INTERNET` permission |

Performance checks (Macrobenchmark, later): stroke latency and frame time on a low-end API 26 device.

---

## 10. Privacy & safety enforcement

- `AndroidManifest.xml` contains
  `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />`
  so no library can merge it in, plus the merged-manifest test above.
- No analytics, crash reporting or ads SDKs in the dependency graph.
- `android:allowBackup` is limited to settings via `dataExtractionRules`; artworks stay local.
- Sharing (roadmap) uses `FileProvider` + `ACTION_SEND` behind the parent gate; nothing is sent
  by the app itself.

---

## 11. Mapping to the roadmap

| Roadmap item | Architecture pieces |
|---|---|
| Project setup | Gradle modules, version catalog, Hilt, manifest permission strip |
| Basic canvas with pen + undo/redo | `Document`, Paint + scratch layers, `Pen`, `History`, `DrawingSurface`; **measure texture upload cost here** |
| Palette + pipette | `PaletteBar`, `ColorSampler` |
| Layered canvas | Paper texture, `OutlineLayer`, display composition |
| Flood fill | `OutlineRasterizer`, `RegionMap`, `FillCommand` |
| All 7 mediums | `brushes/*`, scratch-layer merge modes, screenshot tests |
| Generator v1 | `:generator` end to end, validation, seed property tests |
| Save with parent gate | `:data`, autosave, `GalleryExporter`, `ParentGateDialog` |

Suggested order differs slightly from the README: build the **layered canvas and outline
rasterizer before the pen**, using a hand-written test outline. Stroke clipping, fill and the
eraser all depend on the layer split, and retrofitting it later is costly.

---

## 12. Open questions

1. **Orientation:** landscape-only, or support portrait with a letterboxed 4:3 document?
   (This design assumes landscape-first.)
2. **Fill semantics:** should fill replace all paint in a region (current design) or only fill
   the unpainted pixels?
3. **Stay inside the lines:** default on for all ages, or a parent setting?
4. **Parts pipeline:** who authors the SVG parts, and is a build-time SVG → JSON converter
   acceptable vs. hand-written JSON for v1?

---

## Appendix: repository housekeeping

`.gitignore` currently ignores `*.jar`, which will also ignore
`gradle/wrapper/gradle-wrapper.jar`. Add `!gradle/wrapper/gradle-wrapper.jar` when the Gradle
project is created, and add standard Android entries (`build/`, `.gradle/`, `local.properties`,
`.idea/`, `*.iml`, `captures/`).
