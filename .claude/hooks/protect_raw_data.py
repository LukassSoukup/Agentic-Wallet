#!/usr/bin/env python3
"""PreToolUse guard: data/raw/ is immutable input. Blocks Edit/Write/NotebookEdit under it (exit 2).

Bash writes aren't covered; scripts/data_check.py catches any change to data/raw/ in CI.
"""
import json
import os
import sys
from pathlib import Path

event = json.load(sys.stdin)
raw = (event.get("tool_input") or {}).get("file_path") or (event.get("tool_input") or {}).get("notebook_path")
if raw:
    project = Path(os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or ".").resolve()
    path = Path(raw).expanduser()
    path = (path if path.is_absolute() else Path(event.get("cwd") or project) / path).resolve()
    # A worktree under .claude/worktrees/ is inside the project too, so this covers its data/raw/ as well.
    if project in path.parents and "/data/raw/" in "/" + path.relative_to(project).as_posix():
        print("data/raw/ is immutable input. Write derived data to data/derived/ via a script.", file=sys.stderr)
        sys.exit(2)
