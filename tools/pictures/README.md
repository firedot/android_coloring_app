# Picture tools

`prepare.py` turns a source line-art image (e.g. from ChatGPT) into a library picture: a
2048 × 1536 grayscale PNG and a 384 × 288 WebP thumbnail in
`data/src/main/assets/pictures/<section>/`. The app lists those folders, so there is no index to
edit. It checks the T-rules of [docs/PICTURE_GUIDELINES.md](../../docs/PICTURE_GUIDELINES.md); a
picture that fails is moved to `rejected/<section>/` with every failed rule in a `.txt` next to it.

It runs on the developer machine only, not in the Gradle build. Commit its output.

## Setup

```
python -m venv tools/pictures/.venv
tools/pictures/.venv/Scripts/python -m pip install -r tools/pictures/requirements.txt   # Windows
tools/pictures/.venv/bin/python -m pip install -r tools/pictures/requirements.txt       # macOS/Linux
```

`potracer` is a pure-Python port of potrace, so nothing needs compiling. A picture takes about
15 s.

## Adding a picture

1. Save the image as `tools/pictures/inbox/<section>/<name>.png`: section `animals`, `plants`
   or `various`; name in lowercase letters, digits and `_` (`kitten_01`).
2. Run the script on that one file:
   ```
   tools/pictures/.venv/Scripts/python tools/pictures/prepare.py tools/pictures/inbox/animals/kitten_01.png
   ```
3. `ok ...`: the picture and thumbnail are in the assets. `REJECTED ...`: fix what it lists, move
   the image back from `rejected/` to `inbox/` and run again.
4. `./gradlew installDebug`, check it on the device, commit the `.png` and `_thumb.webp`.

- The script takes one image file, never a folder, and refuses files already in the assets.
- The section defaults to the source's folder and the name to its file name (`--section`,
  `--slug` override them). `--source-note` records where it came from in the PNG's metadata.
- Picture names are immutable once shipped: the script refuses a name that already exists. Give
  a changed picture a new name (`kitten_02`).
- A new section is a new folder in the assets; add its icon in `Section.of`.
- `inbox/` and `rejected/` are git-ignored.

## Synthetic test pictures

```
tools/pictures/.venv/Scripts/python tools/pictures/prepare.py --synthetic
```

Draws two simple pictures per section (`*_test`) and runs them through the pipeline. They exist
so the app and the asset test have data; delete their files once real pictures are in.
