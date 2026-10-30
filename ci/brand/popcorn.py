#!/usr/bin/env python3
"""Draws the Ciné Doury logo (a pot of popcorn) and the Android TV banner.

Usage: ci/brand/popcorn.py LilitaOne-Regular.ttf [OUT_DIR]

Lilita One comes from Google Fonts (SIL Open Font License); only the rendered images enter the
repository, never the font:
    curl -LO https://raw.githubusercontent.com/google/fonts/main/ofl/lilitaone/LilitaOne-Regular.ttf

Writes, under OUT_DIR (the repository by default):
- app/tv/src/main/res/drawable-xhdpi/banner.png (640 x 360) and drawable-xxhdpi/banner.png (960 x 540);
- docs/screenshots/logo/apres-icone.png (the launcher icon, 432 x 432, round mask as most launchers).

The shapes are those of app/tv/src/main/res/drawable/ic_launcher_foreground.xml (108 x 108 grid):
change both together. Needs Pillow.
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

BLACK = "#000000"
CREAM = "#F4F1EA"
GOLD = "#F2C14E"
PALE = "#FFE08A"
RED = "#E5484D"

# (cx, cy, r, colour): the kernels, back to front.
KERNELS = [(40, 38, 9, GOLD), (54, 32, 10, PALE), (68, 38, 9, GOLD), (47, 42, 8, PALE), (61, 42, 8, PALE)]
POT = [(31, 44), (77, 44), (71, 84), (37, 84)]
POT_STROKE = 3
STRIPES = [[(39, 44), (47, 44), (46, 84), (41, 84)], [(61, 44), (69, 44), (67, 84), (62, 84)]]
# Bounds of the drawing on the grid, stroke included.
TOP, BOTTOM, LEFT, RIGHT = 22.0, 85.5, 29.5, 78.5

SUPERSAMPLE = 4


def draw_popcorn(draw: ImageDraw.ImageDraw, x0: float, y0: float, scale: float) -> None:
    """Draws the pot with its grid point (0, 0) at (x0, y0), `scale` pixels per grid unit."""

    def p(x, y):
        return (x0 + x * scale, y0 + y * scale)

    for cx, cy, r, colour in KERNELS:
        (ax, ay), (bx, by) = p(cx - r, cy - r), p(cx + r, cy + r)
        draw.ellipse((ax, ay, bx, by), fill=colour)
    # The pot, with a round-joined stroke: the polygon, its edges and a disc on each corner.
    pot = [p(x, y) for x, y in POT]
    width = POT_STROKE * scale
    draw.polygon(pot, fill=CREAM)
    draw.line(pot + [pot[0]], fill=CREAM, width=round(width))
    for x, y in pot:
        draw.ellipse((x - width / 2, y - width / 2, x + width / 2, y + width / 2), fill=CREAM)
    for stripe in STRIPES:
        draw.polygon([p(x, y) for x, y in stripe], fill=RED)


def banner(font_path: Path, width: int, height: int) -> Image.Image:
    s = SUPERSAMPLE
    w, h = width * s, height * s
    image = Image.new("RGB", (w, h), BLACK)
    draw = ImageDraw.Draw(image)
    unit = w / 640  # the layout is thought on the 640 x 360 banner

    pot_height = 150 * unit
    scale = pot_height / (BOTTOM - TOP)
    pot_width = (RIGHT - LEFT) * scale
    font = ImageFont.truetype(str(font_path), round(88 * unit))
    lines = [("Ciné", CREAM), ("Doury", GOLD)]
    boxes = [draw.textbbox((0, 0), text, font=font) for text, _ in lines]
    margin = 44 * unit
    text_width = max(b[2] - b[0] for b in boxes)
    # The pot and the name, centred together.
    pot_left = (w - pot_width - margin - text_width) / 2
    draw_popcorn(draw, pot_left - LEFT * scale, (h - pot_height) / 2 - TOP * scale, scale)

    text_left = pot_left + pot_width + margin
    gap = 4 * unit
    heights = [b[3] - b[1] for b in boxes]
    y = (h - sum(heights) - gap) / 2
    for (text, colour), box, line_height in zip(lines, boxes, heights):
        draw.text((text_left - box[0], y - box[1]), text, font=font, fill=colour)
        y += line_height + gap
    return image.resize((width, height), Image.LANCZOS)


def icon(size: int) -> Image.Image:
    """The adaptive icon as a launcher shows it: black background, round mask, pot at 88 %."""
    s = SUPERSAMPLE
    big = size * s
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    mask = Image.new("L", (big, big), 0)
    # The visible part of an adaptive icon is the inner 72 x 72: show that, enlarged.
    ImageDraw.Draw(mask).ellipse((0, 0, big, big), fill=255)
    layer = Image.new("RGBA", (big, big), BLACK)
    d = ImageDraw.Draw(layer)
    grid = big / 72  # pixels per grid unit in the visible square
    scale = grid * 0.88
    offset = -18 * grid  # the visible square starts at (18, 18) on the grid
    draw_popcorn(d, offset + 54 * grid - 54 * scale, offset + 54 * grid - 54 * scale, scale)
    image.paste(layer, (0, 0), mask)
    return image.resize((size, size), Image.LANCZOS)


def main() -> None:
    font = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parents[2]
    res = out / "app/tv/src/main/res"
    for folder, (w, h) in {"drawable-xhdpi": (640, 360), "drawable-xxhdpi": (960, 540)}.items():
        (res / folder).mkdir(parents=True, exist_ok=True)
        banner(font, w, h).save(res / folder / "banner.png", optimize=True)
    shots = out / "docs/screenshots/logo"
    shots.mkdir(parents=True, exist_ok=True)
    icon(432).save(shots / "apres-icone.png", optimize=True)


if __name__ == "__main__":
    main()
