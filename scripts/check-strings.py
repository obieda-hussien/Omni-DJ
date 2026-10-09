#!/usr/bin/env python3
"""Catch missing translations and mismatched Android format arguments before building."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
res = root / "app/src/main/res"
read = lambda path: {e.attrib["name"]: "".join(e.itertext()) for e in ET.parse(path).getroot() if e.tag == "string"}
base = read(res / "values/strings.xml")
pattern = re.compile(r"%(?:\d+\$)?[dsf]")
for path in res.glob("values-*/strings.xml"):
    translated = read(path)
    assert base.keys() == translated.keys(), f"Missing or extra keys: {path}"
    for name in base:
        assert pattern.findall(base[name]) == pattern.findall(translated[name]), f"Format mismatch: {name}"
referenced = set()
for path in (root / "app/src/main/java").rglob("*.kt"):
    source = path.read_text()
    referenced.update(re.findall(r"R\.string\.(\w+)", source))
    assert not re.search(r'\bText\(\s*"[^"\n]+"', source), f"Hardcoded UI Text in {path}"
assert referenced <= base.keys(), f"Undefined strings: {referenced - base.keys()}"
print(f"Validated {len(base)} resource strings and {len(referenced)} UI references.")
