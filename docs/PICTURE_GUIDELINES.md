# Colour Fun — Picture Guidelines

Rules for every picture in the in-app library. `tools/pictures/prepare.py` enforces the
measurable ones and the CI asset test re-checks them. A person checks the rest before committing.

---

## 1. Why these rules exist

| Rule | Reason in the app |
|---|---|
| Closed shapes, no near-gaps | The fill bucket fills a region exactly. A gap makes the fill run into the next area |
| Thick, uniform lines | Lines must stay clearly visible under paint and when zoomed out on a small phone. Thin lines create fill leaks |
| No grey, shading or hatching | Anything that isn't line or white becomes an unpaintable pixel or a tiny region |
| No tiny regions | A child's finger can't hit them, and the fill seems to "do nothing" |
| White margin, nothing touching edges | The picture is centered in a wider screen. Lines reaching the edge would split or merge the background with the side margins |
| 4:3, fixed size | Every picture shares one document geometry (2048 × 1536) |

---

## 2. Technical requirements (final asset)

| # | Requirement | Value | Checked by |
|---|---|---|---|
| T1 | File format | PNG, 8-bit grayscale, no alpha channel | script + CI |
| T2 | Size | Exactly **2048 × 1536 px** (4:3 landscape) | script + CI |
| T3 | Colors | Black lines on pure white. Only anti-aliasing greys on line edges; no grey areas | script |
| T4 | Line width | Uniform, **12–18 px** (target 14) | script (after re-render) |
| T5 | Edge margin | No line pixel within **64 px** of any border | script + CI |
| T6 | Closure | Each area meant to be colored is fully enclosed. An open line end closer than **40 px** to another line must not almost close an area (the probe bridges the gap and checks whether that adds a region; loose whiskers side by side are fine) | script (gap probe) + review |
| T7 | Minimum region | Each region ≥ **1500 px²** (about 39 × 39 px). Small white spots inside solid areas (pupil highlights) are filled automatically | script + CI |
| T8 | Region count | **8–80** colorable regions | script + CI |
| T9 | Subject size | Bounding box of all lines covers **45–85 %** of the image area | script |
| T10 | Solid black | Filled black areas (pupils, nostrils) ≤ **2 %** of the image; none larger than 140 × 140 px | script |
| T11 | Specks | No isolated blobs < 64 px (removed automatically) | script |
| T12 | Thumbnail | 384 × 288 WebP, generated | script |
| T13 | File size | Typically 50–200 KB per PNG | review |

Difficulty is derived from T8 and T7: **easy** 8–25 regions, all ≥ 6000 px²; **medium** up to 50
regions (or ≤ 25 with smaller ones); **hard** 51–80.

---

## 3. Content and style requirements

**Style**
- Friendly, cute cartoon style for ages 3–7: rounded shapes, big heads, big eyes, smiling faces.
- Bold outline only: no shading, cross-hatching, stippling, fur or texture strokes, gradients or
  shadows.
- Simple detail: few, large areas. A detail is drawn only if it is a closed shape worth coloring
  (spots, stripes, petals) or a short open line far from other lines (a smile).
- The same line weight and level of detail across the whole library.

**Composition**
- One clear subject, or a small group, centered, in landscape framing.
- It floats on white. A small closed ground patch, pot or water puddle under the subject is fine.
  Ground lines, horizons or sky must not run to the edges.
- No frame, border, background pattern or scenery that fills the page.

**Never**
- Text, letters, numbers, signatures, watermarks or logos.
- Copyrighted or trademarked characters, brands or recognizable products.
- Scary, violent or sad content: weapons, blood, teeth-baring monsters, crying.
- Realistic human faces. If people appear, they are simple cartoons.
- Photographic or 3D rendering.

**Sections (v1)**
- `animals`: pets, farm, wild, sea, birds, bugs.
- `plants`: flowers, trees, fruit, vegetables, potted plants, mushrooms.
- `various`: vehicles, houses, food, toys, space, weather, objects.
- Aim for ≥ 12 pictures per section, with a mix of easy and medium.

**Provenance**
- Record the generator and prompt version in the catalog's `source` field.
- Check that the image tool's terms allow commercial use of outputs.

---

## 4. Workflow

```
1. Generate in ChatGPT with the prompt below (landscape, 1536 × 1024).
2. Save the download to tools/pictures/inbox/<section>/<slug>.png
3. python tools/pictures/prepare.py inbox/<section>/<slug>.png --section <section>
      → normalizes, re-renders lines, validates, writes assets + thumbnail + catalog entry
      → on failure: moves the file to rejected/ with a reason (e.g. "region 14 = 620 px²")
4. Review the output PNG at 100 % zoom using the checklist (section 6).
5. Commit the asset, thumbnail and catalog.json change.
```

Common fixes when a picture is rejected: ask ChatGPT to "remove the small details", "make all
lines thicker and close every gap", or "move the subject away from the edges".

---

## 5. ChatGPT image prompt

Use with ChatGPT's image generation. Generate **one picture per message**. For the first picture
of a session, paste the full prompt. For later pictures in the same chat, use the short
follow-up, so the style stays consistent.

### 5.1 Full prompt

```
Create a children's colouring-book page as an image.

SUBJECT: {SUBJECT}

FORMAT
- Landscape, 1536 × 1024.
- Pure black lines on a pure white background. Nothing else: no grey, no colour,
  no shading, no gradients, no shadows, no hatching, no texture, no fur strokes.

LINES
- Thick, bold, uniform outlines, the same thickness everywhere (about 1% of the image width).
- Every shape is completely closed, with no gaps anywhere. Lines must meet cleanly.
- Don't let lines almost touch: either they join, or there is clearly wide space between them.

STYLE
- Cute, friendly cartoon for children aged 3–7: rounded shapes, big head,
  big eyes, a happy smile.
- Very simple: between 10 and 40 areas to colour, every area at least as large
  as a child's fingertip. Leave out small details.
- Small solid black areas only for pupils; nothing else filled black.

COMPOSITION
- One subject, centred, filling about 70% of the image.
- Empty white margin on every side; nothing touches or comes near the edges.
- No background scenery, no horizon or ground line across the image.
  A small closed ground patch, pot or puddle directly under the subject is fine.

NEVER
- No text, letters, numbers, signature, watermark, logo, frame or border.
- No copyrighted characters or brands. Nothing scary or sad.
```

### 5.2 Follow-up prompt (same chat)

```
Next page in exactly the same style, line thickness, simplicity and margins as the
previous image. Same rules: pure black lines on white, no shading, all shapes
closed, nothing near the edges, no text, landscape.

SUBJECT: {SUBJECT}
```

### 5.3 Fix-up prompts

- *Too detailed:* "Simplify this: remove small details so there are fewer, bigger areas to
  colour. Keep the same style."
- *Grey or shading:* "Redraw with pure black outlines only on white. Remove all grey, shading
  and texture."
- *Gaps:* "Redraw with thicker lines and make sure every shape is fully closed with no gaps."
- *Touches edges:* "Make the subject smaller and centred with a wide white margin on all sides."

### 5.4 Subject ideas

| animals | plants | various |
|---|---|---|
| a happy cat sitting | a sunflower with two leaves | a small car |
| a puppy with floppy ears | a tulip in a pot | a rocket with round windows |
| an elephant | an apple tree on a little grass patch | a house with a door and two windows |
| a lion with a big round mane | a cactus in a pot | a steam train engine |
| a turtle | a mushroom | a castle with three towers |
| an owl on a branch | a strawberry with leaves | a cupcake with a cherry |
| a fish with big fins | a rose | a hot air balloon |
| a butterfly with patterned wings | a palm tree on a small island | a sailboat on a little wave |
| a giraffe | a carrot with leaves | a friendly robot |
| a rabbit | a pumpkin with a leaf | a snowman with a scarf |
| a teddy-bear-style bear | a daisy bouquet | an ice cream cone |
| a frog on a lily pad | a cherry pair on a twig | a rainbow with two clouds |

For an easy variant, add "very simple, for a 3-year-old, at most 15 areas" to the subject line.

---

## 6. Review checklist (before committing)

- [ ] `prepare.py` passed with no warnings.
- [ ] Looks friendly and clearly reads as the subject at thumbnail size.
- [ ] No hidden text or letters, no signature-like squiggles in the corners.
- [ ] No near-gaps where a child would expect a closed area (open the PNG at 100 %).
- [ ] No recognizable copyrighted character or brand.
- [ ] Style and line weight match the existing pictures in the section.
- [ ] Catalog entry has the right section, difficulty and source.
