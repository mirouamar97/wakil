#!/usr/bin/env python3
"""CI check: (1) every string key exists in values/, values-ar/, values-fr/ and is non-empty;
(2) no hard-coded user-visible text in Kotlin UI code. Run: python3 scripts/check_i18n.py"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
SRC = ROOT / "app" / "src" / "main" / "java"
LANGS = {"en": "values", "ar": "values-ar", "fr": "values-fr"}


def load(folder: str) -> dict:
    path = RES / folder / "strings.xml"
    if not path.exists():
        return {}
    keys = {}
    for el in ET.parse(path).getroot():
        if el.tag in ("string", "plurals", "string-array") and el.get("translatable") != "false":
            keys[el.get("name")] = "".join(el.itertext()).strip()
    return keys


def main() -> int:
    errors = []
    if not RES.exists():
        print("i18n: no app resources yet, nothing to check")
        return 0
    tables = {lang: load(folder) for lang, folder in LANGS.items()}
    all_keys = set().union(*[set(t) for t in tables.values()])
    for lang, table in tables.items():
        for key in sorted(all_keys - set(table)):
            errors.append(f"missing key '{key}' in {LANGS[lang]}/strings.xml")
        for key, value in table.items():
            if not value:
                errors.append(f"empty value for '{key}' in {LANGS[lang]}/strings.xml")

    # Hard-coded text in Compose: Text("..."), contentDescription = "...", label/placeholder lambdas with literals.
    pattern = re.compile(r'(Text\(\s*(text\s*=\s*)?"[^"$]*[A-Za-z؀-ۿ][^"]*"|contentDescription\s*=\s*"[^"]+"|title\s*=\s*"[^"]*[A-Za-z][^"]*")')
    if SRC.exists():
        for kt in SRC.rglob("*.kt"):
            for i, line in enumerate(kt.read_text(encoding="utf-8").splitlines(), 1):
                if line.strip().startswith("//"):
                    continue
                if pattern.search(line):
                    errors.append(f"hard-coded UI text: {kt.relative_to(ROOT)}:{i}")

    if errors:
        print("i18n check FAILED:")
        for e in errors:
            print(" -", e)
        return 1
    print(f"i18n check ok: {len(all_keys)} keys in {len(LANGS)} languages")
    return 0


if __name__ == "__main__":
    sys.exit(main())
