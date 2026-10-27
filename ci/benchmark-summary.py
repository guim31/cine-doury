#!/usr/bin/env python3
"""Prints the results of NavigationBenchmarks (Macrobenchmark JSON) as a Markdown table.

Usage: benchmark-summary.py LABEL DIR (the folder pulled from the device, searched recursively)
"""
import json
import pathlib
import sys

label, folder = sys.argv[1], pathlib.Path(sys.argv[2])
files = sorted(folder.rglob("*benchmarkData.json"))
print(f"\n### Benchmarks: {label}\n")
if not files:
    print("No results (see the instrumentation output).")
    sys.exit(0)
print("| Measure | Metric | Median | P90 | P99 | Min | Max | Runs |")
print("|---|---|---|---|---|---|---|---|")
for file in files:
    for bench in json.loads(file.read_text())["benchmarks"]:
        name = bench["name"]
        for metric, values in sorted(bench.get("metrics", {}).items()):
            runs = len(values.get("runs", []))
            print(f"| {name} | {metric} | {values['median']:.1f} | | | {values['minimum']:.1f} | {values['maximum']:.1f} | {runs} |")
        for metric, values in sorted(bench.get("sampledMetrics", {}).items()):
            runs = sum(len(r) for r in values.get("runs", []))
            print(
                f"| {name} | {metric} | {values['P50']:.1f} | {values['P90']:.1f} | {values['P99']:.1f} "
                f"| | | {runs} frames |"
            )
