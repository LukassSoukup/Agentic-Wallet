#!/usr/bin/env python3
"""Verify the immutable input packs in data/raw/ against their SHA-256 manifests.

Usage: python3 scripts/data_check.py [checkout]   (default: the checkout this script lives in)
Every pack (a folder in data/raw/) carries a metadata.json that lists its files with SHA-256 hashes, like the
starter pack does. A changed, missing or unlisted file is an error (exit 1): raw data is never edited. Derive new
files into data/derived/ with a script.
"""
import hashlib
import json
import sys
from pathlib import Path

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
raw = root / "data" / "raw"
entries = sorted(raw.iterdir()) if raw.is_dir() else []
packs = [p for p in entries if p.is_dir()]
errors = [f"{p.relative_to(root)}: not in a pack (data/raw/ holds only pack folders with a manifest)"
          for p in entries if not p.is_dir()]
for pack in packs:
    manifest = pack / "metadata.json"
    try:
        listed = {f["path"]: f["sha256"] for f in json.loads(manifest.read_text(encoding="utf-8"))["files"]}
    except FileNotFoundError:
        errors.append(f"{pack.relative_to(root)}: no metadata.json manifest")
        continue
    except (ValueError, KeyError, TypeError) as e:
        errors.append(f"{manifest.relative_to(root)}: unreadable manifest ({e!r})")
        continue
    for rel, sha in sorted(listed.items()):
        f = pack / rel
        if not f.is_file():
            errors.append(f"{f.relative_to(root)}: missing")
        elif hashlib.sha256(f.read_bytes()).hexdigest() != sha:
            errors.append(f"{f.relative_to(root)}: changed (its SHA-256 differs from the manifest)")
    for f in sorted(p for p in pack.rglob("*") if p.is_file()):
        rel = f.relative_to(pack).as_posix()
        if rel != "metadata.json" and rel not in listed:
            errors.append(f"{f.relative_to(root)}: not in the manifest")
for e in errors:
    print(f"ERROR {e}")
print(f"data_check: {len(packs)} pack(s), {len(errors)} errors")
sys.exit(1 if errors else 0)
