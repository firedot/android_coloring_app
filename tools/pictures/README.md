# Picture tools

`prepare.py` turns a source line-art image into a library picture: a 2048 × 1536 grayscale PNG,
a 384 × 288 WebP thumbnail and an entry in `data/src/main/assets/pictures/catalog.json`. It
checks the T-rules of [docs/PICTURE_GUIDELINES.md](../../docs/PICTURE_GUIDELINES.md); a picture
that fails one is moved to `rejected/<section>/` with the reason in a `.txt` next to it.

It runs on the developer machine only, not in the Gradle build. Commit its output.

## Setup

```
python -m venv tools/pictures/.venv
tools/pictures/.venv/Scripts/python -m pip install -r tools/pictures/requirements.txt   # Windows
tools/pictures/.venv/bin/python -m pip install -r tools/pictures/requirements.txt       # macOS/Linux
```

`potracer` is a pure-Python port of potrace, so nothing needs compiling. A picture takes about
15 s.

## Use

```
python tools/pictures/prepare.py inbox/animals/cat_01.png
python tools/pictures/prepare.py inbox/animals/cat_01.png --section animals --slug cat_01 \
    --source-note "chatgpt-image, prompt v1, 2026-10-29"
```

- The section defaults to the source's folder and the slug to its file name.
- Picture ids are immutable once shipped: the script refuses an id that is already in the
  catalog. Give a changed picture a new slug (`cat_02`).
- `inbox/` and `rejected/` are git-ignored.

## Synthetic test pictures

```
python tools/pictures/prepare.py --synthetic
```

Draws two simple pictures per section (`*_test`) and runs them through the pipeline. They exist
so the app and the asset test have data; delete them from the catalog and the asset folders once
real pictures are in.
