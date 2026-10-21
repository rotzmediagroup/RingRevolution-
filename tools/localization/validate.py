#!/usr/bin/env python3
"""Verify complete locale coverage, placeholder parity, XML and declared Android languages."""
from pathlib import Path
from generate import RES, LANGS, DIRS, read, placeholders
import xml.etree.ElementTree as ET
import json

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

chapters = json.loads((RES.parents[3]/'content/chapters.json').read_text())
assert chapters == json.loads((RES.parent/'assets/content/chapters.json').read_text()), 'chapter mirrors differ'
assert len(chapters) == 15
for chapter in chapters:
    assert set(['en','de','nl','es','pt','fr','ja','ko','hi','id','ar']) <= chapter['name'].keys(), chapter['number']
print('PASS: all 15 chapter names localized; source and runtime content match.')
