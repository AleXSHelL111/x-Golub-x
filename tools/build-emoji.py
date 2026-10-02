#!/usr/bin/env python3
"""Собирает app/assets/golub-emoji.js: SVG-картинки для каждого эмодзи, встречающегося в игре.

Картинки — Twemoji (https://github.com/jdecked/twemoji, графика CC-BY 4.0), пакет @twemoji/svg.
Свои картинки (например, голубь вместо 🐦) лежат в tools/emoji-overrides и имеют приоритет.
Использование: tools/build-emoji.py <папка с SVG из @twemoji/svg>
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GAME = os.path.join(ROOT, 'app', 'assets', 'index.html')
OUT = os.path.join(ROOT, 'app', 'assets', 'golub-emoji.js')
src = sys.argv[1]
OVR = os.path.join(ROOT, 'tools', 'emoji-overrides')

EMOJI = re.compile(
    '(?:[\U0001F000-\U0001FAFF☀-➿⬀-⯿⌀-⏿〰〽㊗㊙‼⁉]'
    '(?:️|‍|[\U0001F3FB-\U0001F3FF])*)+')

def name(e):
    cps = [c for c in e if c != '️'] if '‍' not in e else list(e)
    return '-'.join('%x' % ord(c) for c in cps)

text = open(GAME, encoding='utf-8').read()
found = sorted(set(EMOJI.findall(text)))
icons, missing = {}, []
for e in found:
    key = e.replace('️', '')
    f = os.path.join(OVR, name(e) + '.svg')
    if not os.path.exists(f):
        f = os.path.join(src, name(e) + '.svg')
    if not os.path.exists(f):
        missing.append(e)
        continue
    svg = open(f, encoding='utf-8').read().strip()
    icons[key] = svg
if missing:
    sys.exit('нет картинок для: ' + ' '.join(missing))

with open(OUT, 'w', encoding='utf-8') as o:
    o.write('// Сгенерировано tools/build-emoji.py. Эмодзи: Twemoji, CC-BY 4.0 (https://github.com/jdecked/twemoji)\n')
    o.write('window.GOLUB_EMOJI=' + json.dumps(icons, ensure_ascii=False, separators=(',', ':')) + ';\n')
print(len(icons), 'эмодзи ->', os.path.relpath(OUT, ROOT))
