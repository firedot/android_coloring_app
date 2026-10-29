#!/usr/bin/env python3
"""Turns a source line-art image into a library picture for Colour Fun.

    python tools/pictures/prepare.py inbox/animals/cat_01.png --section animals
    python tools/pictures/prepare.py --synthetic

Steps: grayscale -> threshold -> despeckle -> fit into 2048 x 1536 with a white margin ->
skeleton -> re-stroke at a uniform width -> potrace -> anti-aliased render -> validate the
T-rules of docs/PICTURE_GUIDELINES.md -> write the PNG, a 384 x 288 WebP thumbnail and the
catalog entry. A picture that fails a rule is moved to rejected/ with the reason next to it.

--synthetic draws two simple test pictures per section with the helpers below and runs them
through the same pipeline, so the app and the asset test have data before real pictures exist.
"""

from __future__ import annotations

import argparse
import datetime
import json
import math
import shutil
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import potrace
from PIL import Image, ImageDraw, ImageFilter

TOOL_DIR = Path(__file__).resolve().parent
REPO_DIR = TOOL_DIR.parent.parent
ASSETS_DIR = REPO_DIR / "data" / "src" / "main" / "assets" / "pictures"
CATALOG = ASSETS_DIR / "catalog.json"
REJECTED_DIR = TOOL_DIR / "rejected"

# The shared picture geometry and the T-rules (PICTURE_GUIDELINES.md section 2).
WIDTH, HEIGHT = 2048, 1536
THUMB_SIZE = (384, 288)
FIT_MARGIN = 96                 # where the subject is placed; T5 only demands 64
EDGE_MARGIN = 64                # T5
LINE_WIDTH = 14                 # T4 target
LINE_WIDTH_RANGE = (12, 18)     # T4
LINE_ALPHA_THRESHOLD = 96       # same as RegionLabeler: a pixel with alpha above it is line
GAP_MIN = 40                    # T6
REGION_MIN = 1500               # T7
REGION_MIN_EASY = 6000          # T7
REGION_COUNT = (8, 80)          # T8
SUBJECT_COVER = (0.45, 0.85)    # T9
SOLID_MAX_FRACTION = 0.02       # T10
SOLID_MAX_SIDE = 140            # T10
SPECK_MAX = 64                  # T11
SUPERSAMPLE = 2                 # render scale for anti-aliasing

SECTIONS = [
    {"id": "animals", "icon": "paw", "order": 0},
    {"id": "plants", "icon": "leaf", "order": 1},
    {"id": "various", "icon": "star", "order": 2},
]
SYNTHETIC_SOURCE = "synthetic test picture (prepare.py --synthetic)"


class Rejected(Exception):
    """Rules failed; the message (one line per failure) is written next to the rejected file."""


# --- Connected components -------------------------------------------------------------------


@dataclass
class Components:
    """Connected components of a boolean mask, computed over horizontal runs."""

    height: int
    width: int
    run_y: np.ndarray
    run_start: np.ndarray
    run_end: np.ndarray        # exclusive
    run_label: np.ndarray      # 0-based component per run
    count: int
    area: np.ndarray = field(init=False)

    def __post_init__(self):
        self.area = np.bincount(self.run_label, weights=self.run_end - self.run_start, minlength=self.count).astype(np.int64)

    def touches_border(self) -> np.ndarray:
        touch = np.zeros(self.count, dtype=bool)
        edge = (self.run_y == 0) | (self.run_y == self.height - 1) | (self.run_start == 0) | (self.run_end == self.width)
        touch[self.run_label[edge]] = True
        return touch

    def bounds(self) -> np.ndarray:
        """Per component: left, top, right, bottom (exclusive)."""
        b = np.zeros((self.count, 4), dtype=np.int64)
        b[:, 0] = self.width
        b[:, 1] = self.height
        np.minimum.at(b[:, 0], self.run_label, self.run_start)
        np.minimum.at(b[:, 1], self.run_label, self.run_y)
        np.maximum.at(b[:, 2], self.run_label, self.run_end)
        np.maximum.at(b[:, 3], self.run_label, self.run_y + 1)
        return b

    def mask_of(self, keep: np.ndarray) -> np.ndarray:
        """The pixels of the components where `keep` (one bool per component) is true."""
        out = np.zeros((self.height, self.width), dtype=bool)
        for y, s, e in zip(self.run_y[keep[self.run_label]], self.run_start[keep[self.run_label]], self.run_end[keep[self.run_label]]):
            out[y, s:e] = True
        return out

    def label_at(self, x: int, y: int) -> int:
        """The component containing (x, y), or -1."""
        hit = np.flatnonzero((self.run_y == y) & (self.run_start <= x) & (self.run_end > x))
        return int(self.run_label[hit[0]]) if hit.size else -1


def components(mask: np.ndarray, eight: bool) -> Components:
    """Labels `mask` with 4- or 8-connectivity."""
    h, w = mask.shape
    padded = np.zeros((h, w + 2), dtype=np.int8)
    padded[:, 1:-1] = mask
    diff = np.diff(padded, axis=1)
    ys_s, xs_s = np.nonzero(diff == 1)
    ys_e, xs_e = np.nonzero(diff == -1)
    order_s = np.lexsort((xs_s, ys_s))
    order_e = np.lexsort((xs_e, ys_e))
    run_y, run_start, run_end = ys_s[order_s], xs_s[order_s], xs_e[order_e]
    n = run_y.size
    parent = list(range(n))

    def find(a: int) -> int:
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    row_first = np.searchsorted(run_y, np.arange(h + 1))
    reach = 1 if eight else 0
    starts, ends = run_start.tolist(), run_end.tolist()
    for y in range(1, h):
        a, a_end = row_first[y - 1], row_first[y]
        b, b_end = row_first[y], row_first[y + 1]
        while a < a_end and b < b_end:
            if starts[a] < ends[b] + reach and starts[b] < ends[a] + reach:
                ra, rb = find(a), find(b)
                if ra != rb:
                    parent[max(ra, rb)] = min(ra, rb)
            if ends[a] < ends[b]:
                a += 1
            else:
                b += 1
    roots = np.array([find(i) for i in range(n)], dtype=np.int64)
    _, labels = np.unique(roots, return_inverse=True)
    count = int(labels.max()) + 1 if n else 0
    return Components(h, w, run_y, run_start, run_end, labels.astype(np.int64), count)


# --- Morphology -----------------------------------------------------------------------------


def disk_offsets(radius: float) -> list[tuple[int, int]]:
    r = int(math.ceil(radius))
    return [(dx, dy) for dy in range(-r, r + 1) for dx in range(-r, r + 1) if dx * dx + dy * dy <= radius * radius]


def shifted(mask: np.ndarray, dx: int, dy: int) -> np.ndarray:
    out = np.zeros_like(mask)
    h, w = mask.shape
    out[max(dy, 0):h + min(dy, 0), max(dx, 0):w + min(dx, 0)] = mask[max(-dy, 0):h - max(dy, 0), max(-dx, 0):w - max(dx, 0)]
    return out


def dilate(mask: np.ndarray, radius: float) -> np.ndarray:
    out = mask.copy()
    for dx, dy in disk_offsets(radius):
        out |= shifted(mask, dx, dy)
    return out


def erode(mask: np.ndarray, radius: float) -> np.ndarray:
    return ~dilate(~mask, radius)


def skeleton(mask: np.ndarray) -> np.ndarray:
    """Zhang-Suen thinning to one-pixel-wide lines."""
    img = np.pad(mask, 1).astype(np.uint8)
    while True:
        changed = False
        for step in (0, 1):
            p2, p3, p4 = img[:-2, 1:-1], img[:-2, 2:], img[1:-1, 2:]
            p5, p6, p7 = img[2:, 2:], img[2:, 1:-1], img[2:, :-2]
            p8, p9 = img[1:-1, :-2], img[:-2, :-2]
            ring = [p2, p3, p4, p5, p6, p7, p8, p9, p2]
            neighbours = sum(ring[:8])
            transitions = sum(((ring[i] == 0) & (ring[i + 1] == 1)).astype(np.uint8) for i in range(8))
            if step == 0:
                c = (p2 * p4 * p6 == 0) & (p4 * p6 * p8 == 0)
            else:
                c = (p2 * p4 * p8 == 0) & (p2 * p6 * p8 == 0)
            remove = (img[1:-1, 1:-1] == 1) & (neighbours >= 2) & (neighbours <= 6) & (transitions == 1) & c
            if remove.any():
                img[1:-1, 1:-1][remove] = 0
                changed = True
        if not changed:
            return img[1:-1, 1:-1].astype(bool)


def neighbour_count(skel: np.ndarray) -> np.ndarray:
    img = np.pad(skel, 1).astype(np.uint8)
    return sum(img[1 + dy:img.shape[0] - 1 + dy, 1 + dx:img.shape[1] - 1 + dx]
               for dy in (-1, 0, 1) for dx in (-1, 0, 1) if (dx, dy) != (0, 0))


def endpoints(skel: np.ndarray) -> list[tuple[int, int]]:
    """Skeleton pixels with exactly one neighbour: open line ends."""
    ys, xs = np.nonzero(skel & (neighbour_count(skel) == 1))
    return list(zip(xs.tolist(), ys.tolist()))


def prune(skel: np.ndarray, length: int) -> np.ndarray:
    """Removes side branches shorter than `length` (thinning spurs) and keeps real line ends.

    Ends are eaten back `length` steps, then the surviving ends grow back along the original
    skeleton, so only branches that vanished completely stay removed.
    """
    pruned = skel.copy()
    for _ in range(length):
        pruned &= neighbour_count(pruned) > 1
    grow = pruned & (neighbour_count(pruned) == 1)
    for _ in range(length):
        grow = dilate(grow, 1.5) & skel & ~pruned | grow
    return pruned | grow


def line_width(ink: np.ndarray) -> float:
    """Typical stroke width: ink area per pixel of centre line."""
    return float(ink.sum() / max(1, skeleton(ink).sum()))


# --- Pipeline -------------------------------------------------------------------------------


def load_gray(path: Path) -> np.ndarray:
    """Luminance 0..255, with any transparency flattened onto white."""
    image = Image.open(path)
    if image.mode in ("RGBA", "LA", "PA", "P"):
        image = image.convert("RGBA")
        white = Image.new("RGBA", image.size, (255, 255, 255, 255))
        image = Image.alpha_composite(white, image)
    return np.asarray(image.convert("L"), dtype=np.uint8)


def despeckle(ink: np.ndarray) -> np.ndarray:
    """T11: drops ink blobs and fills white pinholes smaller than SPECK_MAX pixels."""
    blobs = components(ink, eight=True)
    ink = blobs.mask_of(blobs.area >= SPECK_MAX)
    holes = components(~ink, eight=False)
    return ink | holes.mask_of(holes.area < SPECK_MAX)


def fit_to_page(gray: np.ndarray, ink: np.ndarray) -> np.ndarray:
    """Crops to the drawing, scales it into the page inside FIT_MARGIN and centres it; returns ink."""
    ys, xs = np.nonzero(ink)
    if xs.size == 0:
        raise Rejected("the image has no lines")
    left, right, top, bottom = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
    cleaned = np.where(ink, gray, 255).astype(np.uint8)[top:bottom, left:right]
    scale = min((WIDTH - 2 * FIT_MARGIN) / (right - left), (HEIGHT - 2 * FIT_MARGIN) / (bottom - top))
    w, h = max(1, round((right - left) * scale)), max(1, round((bottom - top) * scale))
    resized = np.asarray(Image.fromarray(cleaned).resize((w, h), Image.LANCZOS))
    page = np.full((HEIGHT, WIDTH), 255, dtype=np.uint8)
    x0, y0 = (WIDTH - w) // 2, (HEIGHT - h) // 2
    page[y0:y0 + h, x0:x0 + w] = resized
    return page < 128


def solid_areas(ink: np.ndarray, width: float = LINE_WIDTH_RANGE[1]) -> np.ndarray:
    """Filled black areas (pupils): what survives an opening wider than lines of `width`."""
    radius = width * 0.65 + 2
    return ink & dilate(erode(ink, radius), radius + 1)


def fill_highlights(ink: np.ndarray) -> tuple[np.ndarray, int]:
    """Fills small white spots inside solid areas, like the highlight dots in pupils.

    A spot is a white region under REGION_MIN that lies mostly inside a solid area once filled.
    Such spots would be regions too small to colour; small regions between lines are left alone
    so the T7 check still reports them. Returns the new ink and the number of spots filled.
    """
    holes = components(~ink, eight=False)
    small = holes.area < REGION_MIN
    if not small.any():
        return ink, 0
    spots = holes.mask_of(small)
    solid = solid_areas(ink | spots, line_width(ink))
    inside = np.zeros(holes.count)
    for label, y, start, end in zip(holes.run_label, holes.run_y, holes.run_start, holes.run_end):
        if small[label]:
            inside[label] += solid[y, start:end].sum()
    highlight = small & (inside > holes.area / 2)
    return ink | holes.mask_of(highlight), int(highlight.sum())


def restroke(ink: np.ndarray) -> np.ndarray:
    """Lines redrawn along their centre at LINE_WIDTH; solid areas kept as they are."""
    width = line_width(ink)
    solid = solid_areas(ink, width)
    # Thinning all the ink keeps lines continuous where they merge into something wide; smoothing
    # first keeps jagged edges from sprouting branches in the skeleton.
    smooth = Image.fromarray(ink.astype(np.uint8) * 255).filter(ImageFilter.GaussianBlur(width / 4))
    centre = prune(skeleton(np.asarray(smooth) >= 128), round(width))
    return dilate(centre, LINE_WIDTH / 2) | solid


def render(mask: np.ndarray) -> np.ndarray:
    """Traces `mask` with potrace and renders the curves anti-aliased; returns luminance."""
    paths = potrace.Bitmap(~mask).trace(turdsize=SPECK_MAX // 4, alphamax=1.0, opticurve=True, opttolerance=0.2)
    s = SUPERSAMPLE
    canvas = Image.new("L", (WIDTH * s, HEIGHT * s), 255)
    draw = ImageDraw.Draw(canvas)
    for curve in paths:
        points = [(curve.start_point.x * s, curve.start_point.y * s)]
        current = curve.start_point
        for segment in curve.segments:
            if segment.is_corner:
                points.append((segment.c.x * s, segment.c.y * s))
                points.append((segment.end_point.x * s, segment.end_point.y * s))
            else:
                points.extend(bezier(current, segment.c1, segment.c2, segment.end_point, s))
            current = segment.end_point
        # Potrace lists each outline before the holes inside it.
        draw.polygon(points, fill=0 if curve._path.sign else 255)
    return np.asarray(canvas.resize((WIDTH, HEIGHT), Image.BOX), dtype=np.uint8)


def close_pinholes(lum: np.ndarray) -> np.ndarray:
    """Turns white specks trapped inside a line by the anti-aliased render into line."""
    line = (255 - lum.astype(np.int32)) > LINE_ALPHA_THRESHOLD
    holes = components(~line, eight=False)
    pinholes = holes.area < SPECK_MAX
    if not pinholes.any():
        return lum
    return np.where(holes.mask_of(pinholes), 0, lum).astype(np.uint8)


def bezier(p0, p1, p2, p3, s: int, steps: int = 12) -> list[tuple[float, float]]:
    out = []
    for i in range(1, steps + 1):
        t = i / steps
        u = 1 - t
        x = u ** 3 * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t ** 3 * p3.x
        y = u ** 3 * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t ** 3 * p3.y
        out.append((x * s, y * s))
    return out


# --- Validation -----------------------------------------------------------------------------


@dataclass
class Report:
    regions: int
    difficulty: str
    line_width: float
    cover: float
    solid_fraction: float


def validate(lum: np.ndarray) -> Report:
    """Checks the T-rules on the final picture; raises Rejected listing every failure."""
    if lum.shape != (HEIGHT, WIDTH):                                            # T2
        raise Rejected(f"T2 size {lum.shape[1]}x{lum.shape[0]}, expected {WIDTH}x{HEIGHT}")
    failures = []
    line = (255 - lum.astype(np.int32)) > LINE_ALPHA_THRESHOLD
    ink = lum < 128

    grey = (lum > 8) & (lum < 247)                                              # T3
    stray = grey & ~dilate(ink, 2)
    if stray.any():
        failures.append(f"T3 grey pixels away from the lines: {int(stray.sum())}")

    edge = np.zeros_like(line)                                                  # T5
    edge[:EDGE_MARGIN, :] = edge[-EDGE_MARGIN:, :] = True
    edge[:, :EDGE_MARGIN] = edge[:, -EDGE_MARGIN:] = True
    if (line & edge).any():
        failures.append(f"T5 lines within {EDGE_MARGIN} px of the border")

    solid = solid_areas(ink)                                                    # T10
    solid_fraction = solid.sum() / solid.size
    if solid_fraction > SOLID_MAX_FRACTION:
        failures.append(f"T10 solid black covers {solid_fraction:.1%} of the image (max {SOLID_MAX_FRACTION:.0%})")
    if solid.any():
        for l, t, r, b in components(solid, eight=True).bounds():
            if r - l > SOLID_MAX_SIDE or b - t > SOLID_MAX_SIDE:
                failures.append(f"T10 solid area of {r - l}x{b - t} px at ({l}, {t}) (max {SOLID_MAX_SIDE}x{SOLID_MAX_SIDE})")

    strokes = ink & ~solid                                                      # T4
    centre = skeleton(strokes)
    line_width = strokes.sum() / max(1, centre.sum())
    if not LINE_WIDTH_RANGE[0] <= line_width <= LINE_WIDTH_RANGE[1]:
        failures.append(f"T4 line width {line_width:.1f} px (expected {LINE_WIDTH_RANGE[0]}-{LINE_WIDTH_RANGE[1]})")

    failures += probe_gaps(line, centre)                                        # T6

    regions = components(~line, eight=False)                                    # T7, T8
    count = regions.count
    if not REGION_COUNT[0] <= count <= REGION_COUNT[1]:
        failures.append(f"T8 {count} regions (expected {REGION_COUNT[0]}-{REGION_COUNT[1]})")
    # Few regions, all of them big, is easy; few regions with some small ones counts as medium.
    if count <= 25 and regions.area.min() >= REGION_MIN_EASY:
        difficulty = "easy"
    else:
        difficulty = "medium" if count <= 50 else "hard"
    bounds = regions.bounds()
    for i in np.flatnonzero(regions.area < REGION_MIN):
        l, t, r, b = bounds[i]
        failures.append(f"T7 region at ({l}, {t})-({r}, {b}) = {regions.area[i]} px² (min {REGION_MIN})")

    ys, xs = np.nonzero(line)                                                   # T9
    cover = (xs.max() + 1 - xs.min()) * (ys.max() + 1 - ys.min()) / (WIDTH * HEIGHT) if xs.size else 0.0
    if not SUBJECT_COVER[0] <= cover <= SUBJECT_COVER[1]:
        failures.append(f"T9 the drawing covers {cover:.0%} of the image (expected {SUBJECT_COVER[0]:.0%}-{SUBJECT_COVER[1]:.0%})")

    if failures:
        raise Rejected("\n".join(failures))
    return Report(count, difficulty, float(line_width), float(cover), float(solid_fraction))


def probe_gaps(line: np.ndarray, centre: np.ndarray) -> list[str]:
    """T6: an open line end closer than GAP_MIN to another line must not almost close an area.

    Around each end, the line it belongs to is the component containing it inside a small
    window. If another line in the window is closer than GAP_MIN, the gap is bridged with a line
    and the picture relabelled: if that adds a region, a child would see a closed area that the
    fill leaks out of. Gaps that close nothing (loose whiskers side by side) are fine. Returns one
    message per near-gap.
    """
    failures = []
    reach = GAP_MIN + LINE_WIDTH
    regions = None
    for x, y in endpoints(centre):
        l, t = max(0, x - reach), max(0, y - reach)
        window = line[t:y + reach + 1, l:x + reach + 1]
        parts = components(window, eight=True)
        own = parts.label_at(x - l, y - t)
        others = parts.mask_of(np.arange(parts.count) != own)
        oy, ox = np.nonzero(others)
        if oy.size == 0:
            continue
        distances = np.sqrt((ox + l - x) ** 2 + (oy + t - y) ** 2)
        nearest = int(distances.argmin())
        distance = distances[nearest] - LINE_WIDTH / 2
        if distance >= GAP_MIN:
            continue
        if regions is None:
            regions = components(~line, eight=False).count
        bridged = Image.fromarray(line.astype(np.uint8) * 255)
        ImageDraw.Draw(bridged).line([(x, y), (int(ox[nearest] + l), int(oy[nearest] + t))], fill=255, width=LINE_WIDTH)
        if components(np.asarray(bridged) < 128, eight=False).count > regions:
            failures.append(f"T6 open line end at ({x}, {y}) is {max(0.0, distance):.0f} px from another line "
                            f"and almost closes an area (min gap {GAP_MIN})")
    return failures


# --- Output ---------------------------------------------------------------------------------


def load_catalog() -> dict:
    if CATALOG.exists():
        return json.loads(CATALOG.read_text(encoding="utf-8"))
    return {"version": 1, "sections": SECTIONS, "pictures": []}


def save_catalog(catalog: dict) -> None:
    catalog["pictures"].sort(key=lambda p: (section_order(catalog, p["section"]), p["id"]))
    CATALOG.parent.mkdir(parents=True, exist_ok=True)
    CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def section_order(catalog: dict, section: str) -> int:
    return next((s["order"] for s in catalog["sections"] if s["id"] == section), len(catalog["sections"]))


def prepare(source: Path, section: str, slug: str, provenance: str, replace_synthetic: bool = False) -> Report:
    """Runs the pipeline and writes the asset, thumbnail and catalog entry; raises Rejected."""
    catalog = load_catalog()
    if section not in {s["id"] for s in catalog["sections"]}:
        raise Rejected(f"unknown section '{section}'; add it to catalog.json first")
    picture_id = f"{section}/{slug}"
    existing = next((p for p in catalog["pictures"] if p["id"] == picture_id), None)
    if existing and not (replace_synthetic and existing["source"] == SYNTHETIC_SOURCE):
        # Shipped pictures are immutable: a changed picture gets a new id.
        raise Rejected(f"'{picture_id}' is already in the catalog; use a new slug (e.g. {slug}_02)")

    gray = load_gray(source)
    ink = despeckle(gray < 128)
    ink = fit_to_page(gray, ink)
    ink, highlights = fill_highlights(despeckle(ink))
    lum = close_pinholes(render(restroke(ink)))
    report = validate(lum)

    out_dir = ASSETS_DIR / section
    out_dir.mkdir(parents=True, exist_ok=True)
    png = out_dir / f"{slug}.png"
    thumb = out_dir / f"{slug}_thumb.webp"
    Image.fromarray(lum, mode="L").save(png, optimize=True)                     # T1
    Image.fromarray(lum, mode="L").resize(THUMB_SIZE, Image.LANCZOS).save(thumb, "WEBP", quality=85)  # T12

    entry = {
        "id": picture_id,
        "section": section,
        "file": f"{section}/{png.name}",
        "thumb": f"{section}/{thumb.name}",
        "difficulty": report.difficulty,
        "regions": report.regions,
        "source": provenance,
    }
    catalog["pictures"] = [p for p in catalog["pictures"] if p["id"] != picture_id] + [entry]
    save_catalog(catalog)

    size_kb = png.stat().st_size / 1024
    print(f"ok  {picture_id}: {report.regions} regions ({report.difficulty}), line {report.line_width:.1f} px, "
          f"cover {report.cover:.0%}, solid {report.solid_fraction:.2%}, {size_kb:.0f} KB")
    if highlights:
        print(f"    filled {highlights} highlight spot(s) inside solid areas")
    if not 50 <= size_kb <= 200:                                                # T13
        print(f"    note: {size_kb:.0f} KB is outside the usual 50-200 KB; check it by eye")
    return report


def reject(source: Path, section: str, slug: str, reason: str) -> None:
    target = REJECTED_DIR / section
    target.mkdir(parents=True, exist_ok=True)
    shutil.move(str(source), target / source.name)
    (target / f"{slug}.txt").write_text(reason + "\n", encoding="utf-8")
    reasons = "\n".join(f"    - {line}" for line in reason.splitlines())
    print(f"REJECTED {section}/{slug}:\n{reasons}\n    moved to {target / source.name}", file=sys.stderr)


# --- Synthetic test pictures ----------------------------------------------------------------


class Sketch:
    """Drawing helpers for synthetic sources: black outlines on white, drawn back to front."""

    def __init__(self, width: int = 1536, height: int = 1024, line: int = 10):
        self.image = Image.new("L", (width, height), 255)
        self.draw = ImageDraw.Draw(self.image)
        self.line = line

    def ellipse(self, cx, cy, rx, ry, fill=255):
        self.draw.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=fill, outline=0, width=self.line)

    def circle(self, cx, cy, r, fill=255):
        self.ellipse(cx, cy, r, r, fill)

    def polygon(self, points, fill=255):
        self.draw.polygon(points, fill=fill)
        self.draw.line(points + points[:1], fill=0, width=self.line, joint="curve")
        for x, y in points:
            self.dot(x, y, self.line / 2)

    def rect(self, l, t, r, b, fill=255):
        self.draw.rectangle((l, t, r, b), fill=fill, outline=0, width=self.line)

    def line_(self, points):
        self.draw.line(points, fill=0, width=self.line, joint="curve")
        for x, y in (points[0], points[-1]):
            self.dot(x, y, self.line / 2)

    def arc(self, cx, cy, rx, ry, start, end):
        self.draw.arc((cx - rx, cy - ry, cx + rx, cy + ry), start, end, fill=0, width=self.line)

    def dot(self, cx, cy, r):
        self.draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=0)


def petal_ring(s: Sketch, cx, cy, count, distance, r):
    for i in range(count):
        a = 2 * math.pi * i / count
        s.circle(cx + distance * math.cos(a), cy + distance * math.sin(a), r)


def cat(s: Sketch):
    s.polygon([(560, 300), (600, 110), (720, 250)])                 # ears
    s.polygon([(976, 300), (936, 110), (816, 250)])
    s.ellipse(768, 520, 330, 280)                                   # head
    s.polygon([(605, 270), (612, 190), (670, 255)], fill=255)       # inner ears
    s.polygon([(931, 270), (924, 190), (866, 255)], fill=255)
    s.ellipse(650, 470, 70, 80)                                     # eyes with pupils
    s.ellipse(886, 470, 70, 80)
    s.dot(660, 485, 14)
    s.dot(896, 485, 14)
    s.polygon([(718, 580), (818, 580), (768, 645)])                 # nose
    s.arc(768, 640, 110, 90, 30, 150)                               # smile


def fish(s: Sketch):
    s.polygon([(1060, 500), (1300, 330), (1300, 690)])              # tail
    s.ellipse(720, 500, 380, 240)                                   # body
    s.polygon([(640, 270), (760, 120), (860, 280)])                 # top fin
    s.line_([(830, 275), (790, 500), (830, 725)])                   # stripes
    s.line_([(970, 312), (940, 500), (970, 688)])
    s.circle(520, 450, 60)                                          # eye
    s.dot(530, 460, 16)
    for x, y, r in ((230, 270, 50), (160, 130, 42)):                # bubbles
        s.circle(x, y, r)


def flower(s: Sketch):
    s.rect(745, 560, 791, 900)                                      # stem
    s.ellipse(590, 760, 170, 60)                                    # leaves
    s.ellipse(946, 720, 170, 60)
    petal_ring(s, 768, 380, 8, 170, 95)                             # petals
    s.circle(768, 380, 120)                                         # centre


def mushroom(s: Sketch):
    s.ellipse(768, 860, 420, 70)                                    # grass patch
    s.rect(660, 470, 876, 860)                                      # stem
    s.draw.pieslice((318, 150, 1218, 790), 180, 360, fill=255, outline=0, width=s.line)  # cap
    for x, y, r in ((560, 360, 60), (768, 280, 70), (980, 360, 60), (660, 190 + 230, 40)):
        s.circle(x, y, r)


def house(s: Sketch):
    s.rect(960, 140, 1050, 330)                                     # chimney
    s.polygon([(370, 430), (768, 120), (1166, 430)])                # roof
    s.rect(420, 430, 1116, 900)                                     # walls
    s.rect(700, 620, 836, 900)                                      # door
    for l in (480, 920):                                            # windows with four panes
        s.rect(l, 500, l + 156, 660)
        s.line_([(l + 78, 500), (l + 78, 660)])
        s.line_([(l, 580), (l + 156, 580)])
    s.circle(800, 770, 12, fill=0)                                  # door knob


def rocket(s: Sketch):
    s.polygon([(620, 600), (380, 880), (660, 820)])                 # fins
    s.polygon([(916, 600), (1156, 880), (876, 820)])
    s.polygon([(690, 840), (768, 990), (846, 840)])                 # flame
    s.ellipse(768, 520, 180, 360)                                   # body
    s.line_([(605, 300), (931, 300)])                               # nose band
    s.circle(768, 470, 80)                                          # windows
    s.circle(768, 680, 55)


SYNTHETIC = {
    "animals": {"cat_test": cat, "fish_test": fish},
    "plants": {"flower_test": flower, "mushroom_test": mushroom},
    "various": {"house_test": house, "rocket_test": rocket},
}


def synthetic() -> int:
    failures = 0
    with tempfile.TemporaryDirectory() as tmp:
        for section, pictures in SYNTHETIC.items():
            for slug, draw in pictures.items():
                sketch = Sketch()
                draw(sketch)
                source = Path(tmp) / f"{slug}.png"
                sketch.image.save(source)
                try:
                    prepare(source, section, slug, SYNTHETIC_SOURCE, replace_synthetic=True)
                except Rejected as e:
                    failures += 1
                    print(f"FAILED {section}/{slug}:\n{e}", file=sys.stderr)
                    REJECTED_DIR.mkdir(parents=True, exist_ok=True)
                    sketch.image.save(REJECTED_DIR / f"failed_{slug}.png")
    return 1 if failures else 0


# --- Command line ---------------------------------------------------------------------------


def main() -> int:
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source", nargs="?", type=Path, help="source image, e.g. inbox/animals/cat_01.png")
    parser.add_argument("--section", help="animals, plants or various (default: the source's folder)")
    parser.add_argument("--slug", help="picture name (default: the source's file name)")
    parser.add_argument("--source-note", dest="provenance",
                        default=f"chatgpt-image, prompt v1, {datetime.date.today().isoformat()}",
                        help="provenance recorded in the catalog's source field")
    parser.add_argument("--synthetic", action="store_true", help="(re)generate the synthetic test pictures")
    args = parser.parse_args()

    if args.synthetic:
        return synthetic()
    if args.source is None:
        parser.error("give a source image or --synthetic")
    source = args.source if args.source.exists() else TOOL_DIR / args.source
    if not source.exists():
        parser.error(f"{args.source} not found")
    section = args.section or source.parent.name
    slug = args.slug or source.stem
    try:
        prepare(source, section, slug, args.provenance)
    except Rejected as e:
        reject(source, section, slug, str(e))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
