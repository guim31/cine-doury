#!/usr/bin/env python3
"""Writes the pictures of ci/emulator-icons.sh from the log of its job (lines « ICON-PNG name base64 »).

Usage: emulator-icons-decode.py LOG OUT_DIR
"""
import base64
import pathlib
import re
import sys

log, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
for name, data in re.findall(r"ICON-PNG (\S+\.png) ([A-Za-z0-9+/=]+)", log.read_text()):
    (out / name).write_bytes(base64.b64decode(data))
    print(out / name)
