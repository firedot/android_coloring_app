# Colour Fun — Architecture

This document turns the goals in [README.md](../README.md) into a concrete technical design.
It covers module layout, the drawing engine, the picture library, persistence, UI state and
testing. Decisions that diverge from or refine the README are marked **(refines README)**.

Picture authoring rules live in [PICTURE_GUIDELINES.md](PICTURE_GUIDELINES.md).

---

## 1. Guiding constraints

| Constraint | Consequence |
|---|---|
| Fully offline, no `INTERNET` permission | All pictures, textures and sounds are bundled; manifest actively strips `INTERNET` |
| Canvas fills the whole screen | Edge-to-edge, immersive mode; all controls float over the canvas as expandable buttons |
| Canvas is zoomable | Pinch-zoom and two-finger pan through a `Viewport`; all drawing logic stays in document space |
| Color never covers the outline | Outline is a separate layer composited **on top**; it is never part of the paint bitmap |
| Fill bucket must never leak | Regions are derived from the *rendered* outline bitmap |
| Undo/redo for every action | Every mutation of the paint layer goes through a command with a recorded before/after state |
| Brushes look "real" | Brushes are stamp/particle based and render into raster bitmaps, not vector paths |
| Pictures come from a curated library | Bundled, validated line-art PNGs in sections (animals, plants, various); no runtime generation |
| Min SDK 26, phones + tablets | Document sized to the screen aspect, with a memory budget sized for low-end devices |

---

## 2. Module layout **(refines README)**

The drawing engine is large, self-contained and benefits from fast JVM tests, so it is split out.
The procedural generator from earlier drafts has been **removed**. Pictures are bundled assets.

```
:app ──────▶ :drawing
  │             ▲
  └──▶ :data ───┘
```

| Module | Type | Contents | Depends on |
|---|---|---|---|
| `:drawing` | Android library | Document, viewport, layers, brushes, fill, region labeling, history, `DrawingEngine` | nothing project-internal |
| `:data` | Android library | Picture library, Room DB, artwork file store, MediaStore export, settings (DataStore) | `:drawing` (snapshot types, `RegionLabeler` for asset tests) |
| `:app` | Android application | Compose UI, floating controls, ViewModels, navigation, Hilt modules, sound/haptics | all of the above |

Rules:
- `:drawing` has no Compose dependency. It exposes `Bitmap`s and a small API; the Compose layer
  adapts it.
- `RegionLabeler` and `Viewport` math are **pure Kotlin** (no `android.*` imports). They operate on
  arrays and floats, so the JVM picture-asset test and plain unit tests can use them.
- Only `:app` knows about Hilt. Library modules expose plain constructors; `:app/di` wires them.

### Package layout

```
drawing/src/main/kotlin/com/example/colourfun/drawing/
├── document/      Document (size, pictureRect, layers), DocumentSizer
├── viewport/      Viewport (scale, offset, clamping, doc↔screen matrices)
├── layers/        PaperLayer, PaintLayer, OutlineLayer (loads line-art PNG → ALPHA_8)
├── regions/       RegionLabeler (pure), RegionMap, RegionMaskCache
├── brushes/       Brush, BrushSpec, StrokePoint, Pencil, Pen, Chalk, Spray, SoftBrush,
│                  PaintBrush, MagicPen, Eraser
├── fill/          FillOperation
├── history/       Command, StrokeCommand, FillCommand, PixelPatch, History
├── pipette/       ColorSampler
└── DrawingEngine.kt

data/src/main/kotlin/com/example/colourfun/data/
├── library/       PictureLibrary (scans the asset folders), Picture, Section, PictureRepository
├── db/            ColourFunDatabase, ArtworkEntity, ArtworkDao
├── files/         ArtworkFileStore (outline copy, paint PNG, thumbnail)
├── export/        GalleryExporter (MediaStore)
├── settings/      SettingsRepository (DataStore)
└── ArtworkRepository.kt

data/src/main/assets/pictures/
├── animals/       cat_01.png, cat_01_thumb.webp, ...
├── plants/
└── various/

app/src/main/java/com/example/colourfun/
├── ui/library/    LibraryScreen (sections + "my pictures"), LibraryViewModel
├── ui/canvas/     ColoringScreen, DrawingSurface (Compose ↔ engine adapter, gestures), CanvasViewModel
├── ui/controls/   ExpandableControl, PaletteControl, ToolsControl, UndoRedoButtons, FitButton
├── ui/icons/      PaintSplatIcon, PaintBrushIcon, tool glyphs (vector drawables)
├── ui/parentgate/ ParentGateDialog
├── ui/settings/   SettingsScreen (behind parent gate)
├── ui/theme/      incl. Palette.kt (color wheel + neutrals)
├── feedback/      SoundPlayer (SoundPool), Haptics
├── navigation/    ColourFunNavHost
└── di/

tools/pictures/    prepare.py (normalize + validate source images), README
```

---

## 3. The document, viewport and layers

### 3.1 Full-screen document **(refines README)**

The canvas covers the whole screen: edge-to-edge, drawn under display cutouts, system bars
hidden in immersive mode (swipe to reveal). Landscape only.

To fill screens of any aspect without stretching, the **document matches the screen aspect**
and the picture sits centered inside it:

- Picture area: fixed **2048 × 1536** (4:3). Every library picture has exactly this size.
- Document height `H = 1536`. Document width `W = clamp(round(H × screenAspect), 2048, 3456)`.
  A 20:9 phone gets 3413 × 1536 and a 4:3 tablet gets 2048 × 1536.
- `pictureRect` = the 2048 × 1536 area centered horizontally. The side margins are blank paper
  and belong to the background region, so filling the background colors the whole screen.
- Screens narrower than 4:3 or wider than 2.25:1 are letterboxed with a neutral backdrop.
- On `ActivityManager.isLowRamDevice()` everything is scaled by 0.75 (picture 1536 × 1152).
- `W`, `H`, `pictureRect` and the scale are stored with each artwork. If a saved artwork is reopened
  on a screen with a different aspect (multi-window, another device), the stored document is
  fit inside the screen and letterboxed. It is never resized.

Brush sizes, stamp spacing and region thresholds are defined once, in document pixels.

### 3.2 Viewport: zoom and pan **(refines README)**

`Viewport` holds `scale` and `offset` and produces the document→screen `Matrix` and its inverse.

- `scale = 1` ("fit") shows the whole document on screen. The maximum is **4×**.
- Pinch zooms around the gesture focal point. Two-finger drag pans. Panning is clamped so the
  document always covers the screen, or stays centered when it is smaller than the screen.
- When `scale > 1.05`, a **Fit** button appears and animates back to `scale = 1`.
  There is no double-tap zoom, because children double-tap by accident while filling.
- Zoom is view state only. It is never undoable, never saved, and every picture opens at fit.
- Brush sizes stay in document px, so zooming in lets the child work more precisely.

### 3.3 Layers and memory

Sizes are for the largest document (3456 × 1536 ≈ 5.3 MP):

| Layer | Representation | Size @ 3456×1536 | Notes |
|---|---|---|---|
| Paper | Tiled texture `BitmapShader` in document space | ~64 KB texture | Never stored per picture; not erasable |
| Paint | `Bitmap` ARGB_8888 | 21.2 MB | The only layer the child changes |
| Stroke scratch | `Bitmap` ARGB_8888 | 21.2 MB | The in-progress stroke; merged into Paint on stroke end |
| Outline | `Bitmap` **ALPHA_8** | 5.3 MB | From the picture PNG (`alpha = 255 − luminance`), placed at `pictureRect` |
| Region map | `ShortArray` label per pixel | 10.6 MB | Region id per pixel, `0` = outline |
| Region mask | `Bitmap` ALPHA_8 (cached, 1–2 entries) | 5.3 MB each | Mask for "stay inside the lines" |

The total is ≈ 70 MB plus the undo budget (§6), and ≈ 40 MB on low-RAM devices. This fits the
default heap on API 26 mid-range devices. If memory is tight, the first lever is making the scratch
layer dirty-rect sized.

### 3.4 Display composition

`DrawingSurface` (Compose) draws, through the viewport matrix, every frame the engine or the
viewport reports as dirty:

```
1. backdrop   only when letterboxed
2. paper      drawRect with the texture shader
3. paint      drawImage(paintBitmap)
4. stroke     drawImage(strokeScratch, alpha = brush.opacity, blendMode = brush.blendMode)
5. outline    drawImage(outlineBitmap, colorFilter = tint(Black))
```

Bitmaps are drawn with bilinear filtering. At 4× the lines get soft but stay readable.

The engine exposes `frameVersion: State<Long>` and the viewport exposes its matrix as state. The
draw lambda reads both, so only the **draw phase** re-runs during strokes and zoom.

> **Risk:** re-uploading a 21 MB bitmap texture per frame on low-end GPUs, now also during zoom.
> Measure in the "basic canvas" milestone. Escape hatch: 256 px tiles, uploading only dirty tiles
> and drawing only visible tiles. The engine API (`dirtyRect` per update) keeps this a local change.
>
> **Option (later):** for crisp outlines at high zoom, keep a vectorized SVG of each picture
> (produced by `prepare.py`) and draw the outline as paths at screen resolution. Regions still come
> from the raster.

---

## 4. Drawing engine (`:drawing`)

### 4.1 Threading

- `DrawingEngine` and all bitmaps are **confined to the main thread**. Per touch event a brush
  draws only a handful of stamps into a dirty rect, which is cheap.
- Heavy, pure computations run on `Dispatchers.Default` and hand back plain arrays:
  PNG decode, region labeling, fill pixel computation, PNG encoding. Results are applied on the
  main thread.

### 4.2 Public API (sketch)

```kotlin
class DrawingEngine(document: Document, regions: RegionMap, history: History) {
    val frameVersion: State<Long>

    fun beginStroke(spec: BrushSpec, color: Int, p: StrokePoint)   // p in document coords
    fun extendStroke(points: List<StrokePoint>)
    fun tick(frameTimeNanos: Long)                                  // dwell time, needed by Spray
    fun endStroke()
    fun cancelStroke()

    suspend fun fill(at: PointF, color: Int)
    fun sampleColor(at: PointF): Int

    fun undo(); fun redo()
    val canUndo: StateFlow<Boolean>; val canRedo: StateFlow<Boolean>

    fun snapshotPaint(): Bitmap
}
```

### 4.3 Touch and gestures **(refines README)**

`DrawingSurface` handles pointers directly in `pointerInput`, not through the ViewModel's
`StateFlow`. It maps screen points through the inverse viewport matrix.

| Situation | Behavior |
|---|---|
| One finger down, a control panel open | Collapses the panel; no paint |
| One finger | Draw stroke / fill / pipette with the current tool |
| Second finger joins during a stroke | The stroke ends: it is **discarded** if it is short (< 150 ms and < 24 dp travel), otherwise **committed**. Then gesture mode starts |
| Two fingers | Zoom/pan until **all** fingers are up. No drawing in this gesture, even if one finger remains |
| `FLAG_CANCELED` (palm, API 33+) | `cancelStroke()` |

While a drawing pointer is down, a `withFrameNanos` loop calls `tick()` so the spray can keep
building density when the finger is still.

### 4.4 Stroke pipeline

```
touch points ──▶ inverse viewport ──▶ smoothing ──▶ resampling ──▶ brush stamps ──▶ stroke scratch
                                     (1-euro)      (fixed doc px)        │
                                                                         ▼
                                                     clip to region mask (DstIn, dirty rect only)
                                                                         │
                                     on endStroke ──────────────────────▶ merge into Paint layer
                                                                         │
                                                                         ▼
                                                                 StrokeCommand → History
```

Drawing into a **scratch layer first** gives correct medium behavior:

- A single pen stroke crossing itself does not get darker.
- Pencil strokes merge at ~60 % opacity, so *separate* strokes build up darker.
- The eraser draws normally into scratch and merges with `DST_OUT`, removing only paint.

### 4.5 Brush model

```kotlin
data class StrokePoint(val x: Float, val y: Float, val timeMs: Long, val pressure: Float)
enum class BrushSize { Small, Medium, Large }       // no slider, per README
data class BrushSpec(val medium: Medium, val size: BrushSize)

interface Brush {
    val opacity: Float                     // applied at merge
    val blendMode: BlendMode               // SrcOver, or DstOut for eraser
    fun begin(ctx: StrokeContext, p: StrokePoint)
    fun segment(ctx: StrokeContext, from: StrokePoint, to: StrokePoint): RectF  // dirty rect
    fun dwell(ctx: StrokeContext, at: StrokePoint, dtMs: Long): RectF           // default: empty
}

class StrokeContext(
    val canvas: Canvas,            // over the stroke scratch bitmap
    val random: Pcg32,             // seeded per stroke → deterministic rendering
    val color: Int,
    val widthPx: Float,            // resolved from BrushSize per medium
    val paintBelow: PixelReader,
    val textures: TextureSet,
)
```

`Pcg32` is a small PRNG in `:drawing` (kotlin.random is not guaranteed stable across versions).

| Medium | Technique |
|---|---|
| Pencil | Small stamps with a grain `BitmapShader`, slight jitter; merged at 0.6 opacity |
| Pen | `Path` with round caps/joins drawn per segment; opacity 1.0 |
| Chalk | Textured stamp, random skips, then `DstIn` with paper grain so the paper shows through |
| Spray | Particles in a disc, Gaussian radius; particle count ∝ `dt` in both `segment` and `dwell` |
| Brush | Width = f(velocity), low-pass filtered; stamps with a radial-gradient soft edge |
| Paint brush | Wide soft stamps; color mixed toward `paintBelow` (~15 %); opacity and width decay with stroke length |
| Magic pen | Hue = f(distance along stroke); core line + `BlurMaskFilter` glow; random sparkle stamps |
| Eraser | Opaque round stamps; merged with `DstOut` |

Each stroke has its own seed, so a stroke renders **identically every time**. This enables
golden-image tests (§9).

### 4.6 Regions: "stay inside the lines" and fill

Regions come from the **outline bitmap** loaded from the picture PNG.

1. Threshold the outline alpha (`a > 96` ⇒ line).
2. Run 4-connected **connected-component labeling** (two-pass union-find, `RegionLabeler`) on
   non-line pixels → `RegionMap` (`ShortArray`, plus per-region bounding box and area).
   With 4-connectivity, a diagonal one-pixel gap in a line still separates regions.
3. Region `0` = outline pixels. The region touching the document border is the background,
   which includes the side margins. Pictures guarantee that no line comes near their edges
   (see guidelines), so the margins never split or merge picture regions.

**Stay inside the lines** (optional, default on): at `beginStroke`, look up the region under the
first point. `RegionMaskCache` builds an ALPHA_8 mask for that region, **dilated by half the
outline width + 1 px**, so paint tucks under the anti-aliased line. Each segment is clipped with
`DstIn` against that mask, over the segment's dirty rect only. If the stroke starts on the
outline, the nearest region is used.

**Fill bucket** = "paint every pixel of region *R* (dilated) with color *c*". It is computed off the
main thread as a pixel patch over the region's bounding box, then applied. It replaces existing
paint in the region, and it cannot leak by construction.

### 4.7 Pipette

`sampleColor` reads the Paint layer. A transparent pixel returns the paper color. A pixel on the
outline samples the nearest non-outline pixel instead. Semi-transparent paint is composited over
paper before returning.

---

## 5. Picture library **(refines README — replaces the procedural generator)**

### 5.1 Library folders

Pictures are bundled in `:data` under `assets/pictures/`. The folders are the index; there is no
catalog file to keep in sync:

```
assets/pictures/<section>/<name>.png          the picture (id "section/name")
assets/pictures/<section>/<name>_thumb.webp   its thumbnail
```

- `PictureRepository` lists the folders once (`AssetManager.list`, milliseconds even for
  thousands of files); the library grid loads thumbnails lazily as it scrolls.
- Sections are the folder names. Their icon and order come from a small table in `Section.of`
  (animals → paw, plants → leaf, various → star); a new folder such as "vehicles" shows up after
  them with the star icon until it gets its own entry.
- Sections are shown by icon, with no text, consistent with the README's minimal-text rule.
- Pictures within a section are sorted by name.
- `prepare.py` records where a picture came from in the PNG's own `Source` text chunk. It also
  prints a difficulty rating (`easy`, `medium`, `hard`, from the region count and the smallest
  region) as a guide; the app does not store or use it.
- **Picture ids and files are immutable once shipped.** A changed picture gets a new id
  (`cat_01` → `cat_02`). Saved artworks also keep their own outline copy (§7), so they stay valid.

### 5.2 Asset pipeline

```
source image (ChatGPT, any size) ──▶ tools/pictures/prepare.py ──▶ <section>/<name>.png + <name>_thumb.webp
                                       │
                                       ├─ grayscale → threshold → despeckle (remove blobs < 64 px)
                                       ├─ fit into 4:3 with white margin
                                       ├─ fill highlight spots inside solid areas (pupils)
                                       ├─ vectorize (potrace) → re-render at uniform line width
                                       ├─ validate (rules below); reject listing every failed rule
                                       └─ thumbnail 384×288 WebP
```

`prepare.py` runs on the developer machine and is not part of the Gradle build. Its output files
are committed. Re-rendering through potrace removes AI artifacts (jaggies, near-gaps, grey specks)
and normalizes the line weight across the library.

### 5.3 Picture requirements (summary)

Full rules and the ChatGPT prompt: [PICTURE_GUIDELINES.md](PICTURE_GUIDELINES.md).

| Rule | Value |
|---|---|
| Format | PNG, 8-bit grayscale, no alpha, exactly 2048 × 1536 |
| Content | Black line art on pure white; anti-aliased edges only; no grey fills, shading, hatching or text |
| Line width | Uniform 12–18 px |
| Edge margin | No line pixel within 64 px of the image border |
| Closure | Every colorable area fully closed; a gap under 40 px must not almost close an area |
| Minimum region | Every region ≥ 1500 px² (`easy` pictures: all ≥ 6000 px²) |
| Region count | 8–80 (easy 8–25, medium up to 50, hard 51–80) |
| Subject size | Bounding box covers 45–85 % of the image area |
| Solid black | Total solid fill (pupils etc.) ≤ 2 % of the image; each area ≤ 140 × 140 px |

### 5.4 Loading a picture

`NewArtworkUseCase(pictureId)`: decode PNG (background) → copy it into the artwork folder →
place it at `pictureRect` in the outline ALPHA_8 layer → label regions → create the artwork row →
open the canvas. Labeling 5 MP takes well under a second on mid-range devices. A spinner shows
meanwhile.

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

- Undo writes `before`; redo writes `after`. Both are O(dirty area), with no replay.
- Patches are stored as compressed `IntArray`s (deflate on a background thread).
- `History` keeps a **memory budget** (default 48 MB, 24 MB on low-RAM devices) and a max of
  50 steps. The oldest entries are dropped first, and a new command clears the redo stack.
- Zoom and pan are not commands.

---

## 7. Data and persistence (`:data`)

### 7.1 What is stored

```
filesDir/artworks/<artworkId>/
├── outline.png       Copy of the library picture at creation time
├── paint.png         Paint layer (lossless, full document size)
└── thumb.webp        Composite thumbnail for "my pictures"
```

```kotlin
@Entity(tableName = "artwork")
data class ArtworkEntity(
    @PrimaryKey val id: String,          // UUID
    val pictureId: String,               // e.g. "animals/cat_01"
    val docWidth: Int, val docHeight: Int,
    val pictureLeft: Int, val pictureTop: Int,
    val documentScale: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val exportedUri: String?,
)
```

The outline copy costs about 100 KB per artwork. In exchange, saved work survives library
changes and picture removal.

### 7.2 Autosave and process death

- Autosave runs debounced 2 s after the last command, and in `onStop`.
- `SavedStateHandle` holds only the current `artworkId`; on restore the ViewModel reloads files.
- Undo history is in-memory only and is lost on process death.

### 7.3 Save to the device gallery (behind parent gate)

`GalleryExporter` flattens paper + paint + outline **cropped to `pictureRect`** into a PNG, written
through MediaStore to `Pictures/Colour Fun/`.

- API 29+: `MediaStore` insert with `IS_PENDING`; no permission needed.
- API 26–28: `WRITE_EXTERNAL_STORAGE` with `android:maxSdkVersion="28"`, requested only after the
  parent gate.

### 7.4 Settings

DataStore (Preferences): `soundEnabled`, `hapticsEnabled`, `stayInsideLines`, `lastToolSpec`,
`lastColor`. Changes to anything except tool and color go through the parent gate.

---

## 8. UI layer (`:app`)

### 8.1 Navigation

```
Library (start) ──▶ Coloring(new artwork from picture)
   │      └──────▶ Coloring(existing artwork)        ["my pictures" row]
   └──▶ [ParentGate] ──▶ Settings
Coloring ──▶ home button ──▶ Library
Coloring ──▶ [ParentGate] ──▶ Save to device / Share
```

The **Library** is the start screen:
- A **"my pictures"** row comes first, with thumbnails of saved artworks (most recent first).
  Tapping one continues it.
- After it come the sections (animals, plants, various) as icon tabs, each with a grid of large
  thumbnails. Tapping one starts a new artwork. The previous work stays in "my pictures".
- Thumbnails are ≥ 160 dp. There is no text except what the parent area needs.

### 8.2 Coloring screen: full-screen canvas with floating controls

The canvas fills the screen. Controls float on top, inset by the system safe area (cutouts,
gesture bars), so the canvas reaches under them:

```
┌────────────────────────────────────────────────────────┐
│ [home]                                   [undo] [redo] │
│                                                        │
│                     canvas (full screen)               │
│                                              [fit]*    │
│ [palette ●]                                  [tools 🖌] │
└────────────────────────────────────────────────────────┘
 * only while zoomed in
```

**Expandable controls** (`ExpandableControl`):
- **Palette button**: a **paint splat** icon, tinted with the current color.
- **Tools button**: a **paint brush** icon, with a small badge showing the current tool's glyph.
- Tapping a button expands its panel with an animation (~200 ms, spring) and a soft sound.
  Tapping a choice selects it and collapses the panel. Tapping the button again, or anywhere on
  the canvas, also collapses it, and that tap does **not** paint.
- Only one panel is open at a time.
- Buttons are 72 dp, and panel items are ≥ 56 dp with ≥ 8 dp spacing.
- Icons are original vector drawables (`ui/icons/`).

**Palette panel**: the 12-color wheel in wheel order, then the neutrals, then the pipette:

| Group | Colors |
|---|---|
| Primary | Red `#E53935`, Yellow `#FDD835`, Blue `#1E63D6` |
| Secondary | Orange `#FB8C00`, Green `#43A047`, Violet `#8E24AA` |
| Tertiary | Red-orange `#F4511E`, Yellow-orange `#FFB300`, Yellow-green `#9CCC3C`, Blue-green `#00897B`, Blue-violet `#5E35B1`, Red-violet `#C2185B` |
| Neutrals | Black `#212121`, White `#FFFFFF`, Grey `#9E9E9E`, Brown `#8D5524`, Pink `#F48FB1` |
| Tool | Pipette: the next canvas tap picks a color, then the previous tool returns |

The wheel colors are laid out as a ring, or a 4 × 3 grid in wheel order on small phones, so the
color wheel itself is visible. Colors are defined once in `ui/theme/Palette.kt`.

**Tools panel**: Pencil, Pen, Chalk, Spray, Brush, Paint brush, Magic pen, Eraser, Fill bucket,
followed by a row of three size dots (S/M/L) for the selected medium. Each tool's icon shows the
current color where that makes sense.

### 8.3 ViewModel and state

```kotlin
data class ColoringUiState(
    val loading: Boolean = true,
    val tool: Tool = Tool.Draw(BrushSpec(Medium.Pencil, BrushSize.Medium)),
    val color: Int,
    val openPanel: Panel? = null,          // Palette | Tools
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val zoomedIn: Boolean = false,         // shows the Fit button
    val stayInsideLines: Boolean = true,
)

sealed interface Tool {
    data class Draw(val spec: BrushSpec) : Tool
    data object Fill : Tool
    data object Pipette : Tool
    data class Erase(val size: BrushSize) : Tool
}

sealed interface ColoringEvent {
    data class TogglePanel(val panel: Panel) : ColoringEvent
    data object ClosePanels : ColoringEvent
    data class SelectTool(val tool: Tool) : ColoringEvent
    data class SelectColor(val color: Int) : ColoringEvent
    data object Undo : ColoringEvent
    data object Redo : ColoringEvent
    data object FitToScreen : ColoringEvent
}
```

`CanvasViewModel` exposes `StateFlow<ColoringUiState>` and `onEvent(ColoringEvent)`. It owns the
`DrawingEngine` and the `Viewport`. Raw touch bypasses it (§4.3).

Use cases: `NewArtworkUseCase(pictureId)`, `LoadArtworkUseCase`, `AutosaveUseCase`,
`ExportToGalleryUseCase`.

### 8.4 Kid-friendly UI rules

- Touch targets ≥ 64 dp for primary controls and ≥ 48 dp for everything else.
- Zoom and pan are available in v1. A Fit button always returns to the whole picture.
- No dead ends: home is always one tap away, and panels collapse on any canvas tap.
- Every tap gives feedback: `SoundPool` short sounds (mutable) + light haptic tick.

### 8.5 Parent gate

A modal asking to enter digits written as words ("Type: seven, two, four"), randomized each time.
It guards settings, save to device and share.

---

## 9. Testing strategy

| Area | Kind | What |
|---|---|---|
| Picture assets | JVM unit (ImageIO + `RegionLabeler`) | Every picture in the folders: only `<name>.png` + `<name>_thumb.webp` pairs with valid names, 2048×1536 grayscale, edge margin, region count and minimum size, every known section non-empty. Runs in CI over `assets/pictures/` |
| Region labeling | JVM | Synthetic outlines: diagonal gaps don't merge regions, nested shapes, border region, side margins join the background |
| Viewport | JVM | Fit scale per aspect, focal-point zoom, pan clamping, doc↔screen round trip |
| Document sizing | JVM | `W` for 4:3, 16:10, 20:9 and 21:9 screens; low-RAM scale; letterbox cases |
| Fill | Robolectric (`@GraphicsMode(NATIVE)`) | Fill never changes pixels outside the dilated region; outline unaffected |
| Brushes | Screenshot (Roborazzi) | Each medium × size draws a fixed stroke with a fixed seed → golden PNGs |
| History | Robolectric | Random strokes/fills/undo/redo; paint equals expected after each step; budget eviction |
| Gestures | Compose UI test | Second finger: short stroke discarded, long stroke committed; panel open → tap closes without painting |
| Persistence | Instrumented | Save → kill → restore yields identical paint pixels and document size |
| UI | Compose UI tests | Panel expand/collapse, tool/color selection, parent gate, undo/redo state, library → canvas |
| Manifest | Unit/lint check | Merged manifest has no `INTERNET` permission |

Performance (Macrobenchmark, later): stroke latency, frame time while drawing and while zooming,
on a low-end API 26 device.

---

## 10. Privacy & safety enforcement

- `AndroidManifest.xml` contains
  `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />`,
  plus the merged-manifest test above.
- No analytics, crash reporting or ads SDKs in the dependency graph.
- `android:allowBackup` is limited to settings via `dataExtractionRules`; artworks stay local.
- Sharing (roadmap) uses `FileProvider` + `ACTION_SEND` behind the parent gate.
- Library pictures are reviewed by a person before they are committed (child-safe content, no
  copyrighted characters). See the guidelines.

---

## 11. Mapping to the roadmap

| Roadmap item | Architecture pieces |
|---|---|
| Project setup | Gradle modules (`:app`, `:drawing`, `:data`), version catalog, Hilt, manifest strip, immersive full-screen |
| Layered canvas | `Document` + `DocumentSizer`, paper/paint/outline layers, outline from a test PNG, display composition |
| Zoom & pan | `Viewport`, two-finger gestures, Fit button; **measure texture upload here** |
| Basic pen + undo/redo | Stroke scratch, `Pen`, `History`, gesture/stroke arbitration |
| Floating controls + palette + pipette | `ExpandableControl`, splat/brush icons, `Palette.kt`, `ColorSampler` |
| Flood fill | `RegionLabeler`, `RegionMap`, `FillCommand`, stay-inside clipping |
| All 7 mediums | `brushes/*`, merge modes, screenshot tests |
| Picture library | `prepare.py`, `PictureLibrary`, `PictureRepository`, `LibraryScreen`, asset test |
| Save with parent gate | Persistence, autosave, "my pictures", `GalleryExporter`, `ParentGateDialog` |

Build the **layered canvas and zoom before the pen**. Stroke clipping, fill, the eraser and touch
mapping all depend on the layer split and the viewport, and retrofitting them later is costly.

---

## 12. Open questions

1. **Orientation:** landscape-only (current design), or also portrait with a vertical document?
2. **Fill semantics:** should fill replace all paint in a region (current design) or only fill
   the unpainted pixels?
3. **Stay inside the lines:** default on for all ages, or a parent setting?
4. **Palette extras:** keep the five neutrals (black, white, grey, brown, pink) next to the
   12-color wheel, or the wheel only?
5. **Re-opening a picture:** a library tap always starts a new artwork (current design), or it
   continues the latest artwork of that picture?
6. **Max zoom:** is 4× enough on 10" tablets?

---

## Appendix: repository housekeeping

`.gitignore` currently ignores `*.jar`, which also ignores `gradle/wrapper/gradle-wrapper.jar`.
Add `!gradle/wrapper/gradle-wrapper.jar` when the Gradle project is created, and add standard
Android entries (`build/`, `.gradle/`, `local.properties`, `.idea/`, `*.iml`, `captures/`).
Also ignore `tools/pictures/inbox/` and `tools/pictures/rejected/` (raw sources).
