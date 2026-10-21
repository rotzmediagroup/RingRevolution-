#!/usr/bin/env python3
"""Verify complete locale coverage, placeholder parity, XML and declared Android languages."""
from pathlib import Path
from generate import RES, LANGS, DIRS, read, placeholders
import xml.etree.ElementTree as ET

base = read(RES/'values/strings.xml')
for lang in LANGS + ['de', 'nl']:
    path = RES/('values-' + DIRS.get(lang, lang))/'strings.xml'
    root = ET.parse(path).getroot()
    strings = read(path)
    assert len(strings) == len(root), (lang, 'duplicate keys')
    assert strings.keys() == base.keys(), (lang, 'coverage')
    for key in base:
        assert strings[key].strip(), (lang, key, 'blank')
        assert placeholders(strings[key]) == placeholders(base[key]), (lang, key, 'placeholders')
for file in RES.rglob('*.xml'): ET.parse(file)
print(f'PASS: {len(base)} strings × 11 languages, valid XML and formatting placeholders.')
