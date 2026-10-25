#!/usr/bin/env python3
"""Write a minimal PGS (Blu-ray .sup) subtitle stream.

ffmpeg has no PGS encoder, so the test media get their bitmap subtitle track from this script.
Each cue shows a white label drawn with a built-in 5x7 pixel font; no font file is needed.

Usage: make_pgs.py OUTPUT.sup WIDTH HEIGHT START:END:TEXT [START:END:TEXT ...]
Times are in seconds. Only the characters of FONT below can be drawn.
"""

import struct
import sys

FONT = {
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "G": ["01110", "10001", "10000", "10111", "10001", "10001", "01111"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    " ": ["00000"] * 7,
}
SCALE = 6
PAD = 12


def render(text):
    glyph_w, glyph_h = 5 * SCALE, 7 * SCALE
    width = PAD * 2 + len(text) * (glyph_w + SCALE) - SCALE
    height = PAD * 2 + glyph_h
    pixels = [[1] * width for _ in range(height)]  # 1 = translucent background box
    for index, char in enumerate(text):
        rows = FONT[char]
        left = PAD + index * (glyph_w + SCALE)
        for gy, row in enumerate(rows):
            for gx, bit in enumerate(row):
                if bit == "1":
                    for dy in range(SCALE):
                        for dx in range(SCALE):
                            pixels[PAD + gy * SCALE + dy][left + gx * SCALE + dx] = 2
    return width, height, pixels


def rle(pixels):
    out = bytearray()
    for row in pixels:
        x = 0
        while x < len(row):
            color = row[x]
            run = 1
            while x + run < len(row) and row[x + run] == color and run < 16383:
                run += 1
            if color != 0 and run == 1:
                out.append(color)
            elif color == 0:
                out += bytes([0, run]) if run < 64 else bytes([0, 0x40 | (run >> 8), run & 0xFF])
            else:
                out += bytes([0, 0x80 | run, color]) if run < 64 else bytes([0, 0xC0 | (run >> 8), run & 0xFF, color])
            x += run
        out += b"\x00\x00"
    return bytes(out)


def segment(pts, kind, payload):
    return b"PG" + struct.pack(">IIBH", pts, pts, kind, len(payload)) + payload


def cue(screen_w, screen_h, start, end, text, number):
    width, height, pixels = render(text)
    x = (screen_w - width) // 2
    y = screen_h - height - screen_h // 12
    start_pts, end_pts = int(start * 90000), int(end * 90000)
    window = struct.pack(">BBHHHH", 1, 0, x, y, width, height)
    data = rle(pixels)
    show = segment(start_pts, 0x16, struct.pack(">HHBHBBBB", screen_w, screen_h, 0x10, number * 2, 0x80, 0, 0, 1)
                   + struct.pack(">HBBHH", 0, 0, 0, x, y))
    show += segment(start_pts, 0x17, window)
    # Palette: 0 transparent, 1 black at ~60 %, 2 white (Y, Cr, Cb, alpha).
    show += segment(start_pts, 0x14, bytes([0, 0]) + bytes([0, 16, 128, 128, 0, 1, 16, 128, 128, 150, 2, 235, 128, 128, 255]))
    show += segment(start_pts, 0x15, struct.pack(">HBB", 0, 0, 0xC0) + (len(data) + 4).to_bytes(3, "big")
                    + struct.pack(">HH", width, height) + data)
    show += segment(start_pts, 0x80, b"")
    hide = segment(end_pts, 0x16, struct.pack(">HHBHBBBB", screen_w, screen_h, 0x10, number * 2 + 1, 0x00, 0, 0, 0))
    hide += segment(end_pts, 0x17, window)
    hide += segment(end_pts, 0x80, b"")
    return show + hide


def main(argv):
    if len(argv) < 5:
        sys.exit(__doc__)
    output, screen_w, screen_h = argv[1], int(argv[2]), int(argv[3])
    stream = bytearray()
    for number, spec in enumerate(argv[4:]):
        start, end, text = spec.split(":", 2)
        stream += cue(screen_w, screen_h, float(start), float(end), text, number)
    with open(output, "wb") as handle:
        handle.write(stream)


if __name__ == "__main__":
    main(sys.argv)
