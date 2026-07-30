#!/usr/bin/env python3
from pathlib import Path
import re, sys

root = Path(__file__).resolve().parents[1]
errors = []

rules = {
    "ComponentName": "android.content.ComponentName",
}

for path in (root / "app/src/main/java").rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    imports = set(re.findall(r"^import\s+([\w.]+);", text, re.MULTILINE))
    body = re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.DOTALL)
    body = re.sub(r'"(?:\\.|[^"\\])*"', '""', body)
    body = re.sub(r"^\s*(?:package|import)\s+.*?;\s*$", "", body, flags=re.MULTILINE)

    for simple, full in rules.items():
        if re.search(rf"\b{simple}\b", body) and full not in imports:
            errors.append(f"{path.relative_to(root)}: missing import {full};")

if errors:
    print("JAVA-IMPORT-CHECK FAILED")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print("JAVA-IMPORT-CHECK: PASS")
