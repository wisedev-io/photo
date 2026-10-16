#!/usr/bin/env python3
"""Builds the README images in docs/screenshots/ from the headless renders in harness/build/screenshots/.

    ./gradlew -p harness desktopTest      # renders every screen (and every language) to PNG
    python3 tools/make_screenshots.py     # needs Pillow

The renders are 2x; the README images are half size. Panels are cropped to what fits a phone screen.
"""
import os
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit('Pillow is required: pip install pillow')

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'harness/build/screenshots')
DST = os.path.join(ROOT, 'docs/screenshots')
BG = (11, 15, 20)  # Pal.Ink

# interface languages in the order of LocaleHelper.tags; the English render has no suffix
LANGS = [None, 'ru', 'uz', 'ar', 'fr', 'es', 'uk', 'fa', 'tr', 'ne', 'am', 'pt', 'bn']


def load(name, scale):
    path = os.path.join(SRC, name + '.png')
    if not os.path.exists(path):
        sys.exit(f'missing {path}: run ./gradlew -p harness desktopTest first')
    im = Image.open(path).convert('RGB')
    return im.resize((round(im.width * scale), round(im.height * scale)), Image.LANCZOS)


def top(im, height):
    """The top [height] pixels, padded with the background colour if the render is shorter."""
    out = Image.new('RGB', (im.width, height), BG)
    out.paste(im.crop((0, 0, im.width, min(height, im.height))), (0, 0))
    return out


def save(im, name):
    os.makedirs(DST, exist_ok=True)
    im.save(os.path.join(DST, name), optimize=True)
    print(name, im.size)


# single screens (412 px wide)
single = {
    'camera.png': ('camera_good', 892),
    'home.png': ('home', 950),
    'name.png': ('name_ok', 950),
    'problems.png': ('result_fail', 1150),
    'saved.png': ('saved', 950),
    'settings.png': ('settings', 750),
}
for out, (src, height) in single.items():
    save(top(load(src, 0.5), height), out)

# overview strip: home, camera, name & save, problems, saved
strip = ['home', 'camera_good', 'name_ok', 'result_fail', 'saved']
panels = [top(load(n, 0.5), 892) for n in strip]
gap = 16
canvas = Image.new('RGB', (len(panels) * 412 + (len(panels) - 1) * gap, 900), BG)
for i, p in enumerate(panels):
    canvas.paste(p, (i * (412 + gap), 4))
save(canvas, 'overview.png')

# the camera screen in every language (two rows)
cells = [top(load('lang_camera' + (f'_{l}' if l else ''), 0.375), 402) for l in LANGS]
per_row = 7
rows = (len(cells) + per_row - 1) // per_row
w, h = cells[0].size
sheet = Image.new('RGB', (per_row * w, rows * h), BG)
for i, c in enumerate(cells):
    sheet.paste(c, ((i % per_row) * w, (i // per_row) * h))
save(sheet, 'languages.png')
