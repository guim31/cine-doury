#!/usr/bin/env python3
"""Draws the Android TV launcher banner (320 x 180 dp, xhdpi) from the embedded Inter font.

Usage: make_banner.py OUTPUT.png   (needs Pillow; the output is versioned, rerun only to change it)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT = ROOT / "core/designsystem/src/main/res/font/inter_bold.ttf"
W, H = 640, 360
BACKGROUND = (11, 12, 15)
ACCENT = (242, 193, 78)
TEXT = (242, 243, 245)

image = Image.new("RGB", (W, H), BACKGROUND)
# A soft projector beam, from a lamp at the top right towards the bottom left.
beam = Image.new("L", (W, H), 0)
ImageDraw.Draw(beam).polygon([(W - 36, 28), (250, H + 40), (W + 60, H + 40)], fill=120)
beam = beam.filter(ImageFilter.GaussianBlur(28))
image.paste(Image.new("RGB", (W, H), ACCENT), mask=beam.point(lambda v: v * 46 // 120))
ImageDraw.Draw(image).ellipse((W - 48, 16, W - 24, 40), fill=ACCENT)
text = ImageDraw.Draw(image)
font = ImageFont.truetype(str(FONT), 76)
text.text((56, H - 168), "Ciné", font=font, fill=TEXT)
text.text((56, H - 96), "Doury", font=font, fill=ACCENT)
image.save(sys.argv[1], optimize=True)
