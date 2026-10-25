#!/usr/bin/env python3
"""Checks that the versioned OpenAPI matches the one a server serves.

Jellyfin picks the description of a `$ref` property differently from one start to the next
(« Gets or sets the user. » or « Class UserDto. »), so descriptions are left out of the comparison:
paths, operations, parameters and schemas must match exactly.

Usage: check-openapi.py VERSIONED.json SERVED.json
"""
import json
import sys


def strip(node):
    if isinstance(node, dict):
        return {key: strip(value) for key, value in node.items() if key != "description"}
    if isinstance(node, list):
        return [strip(value) for value in node]
    return node


def main(versioned_path, served_path):
    with open(versioned_path, encoding="utf-8") as handle:
        versioned = strip(json.load(handle))
    with open(served_path, encoding="utf-8") as handle:
        served = strip(json.load(handle))
    if versioned == served:
        print("OpenAPI matches (descriptions ignored).")
        return 0
    for section in ("info", "paths", "components"):
        left, right = versioned.get(section, {}), served.get(section, {})
        if left != right:
            keys = set(left) | set(right) if isinstance(left, dict) else set()
            for key in sorted(keys):
                if left.get(key) != right.get(key):
                    print(f"differs: {section}/{key}")
    return 1


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:3]))
