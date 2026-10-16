#!/usr/bin/env python3
"""Static checks for Android string resources. Exits non-zero on any error.

  * every translation is well-formed XML and has exactly the keys of values/strings.xml
  * format placeholders (%1$d, %2$s ...) match the English string, in count and type
  * a '%' that is not part of a placeholder must be written '%%' (aapt2 rejects stray ones)
  * no unescaped apostrophes / quotes, no leading '@' or '?', no empty strings
  * every R.string.* used in Kotlin exists; unused keys are reported
  * values-XX folders, res/xml/locales_config.xml and LocaleHelper.tags agree
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter

ROOT = os.environ.get('DV_ROOT') or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app/src/main/res')
KT = os.path.join(ROOT, 'app/src/main/kotlin')

errors, warnings = [], []
err = errors.append
warn = warnings.append

PLACEHOLDER = re.compile(r'%(\d+)\$([a-zA-Z])')
ANY_PERCENT = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]?')


def load(path):
    """Returns {name: raw text} using the file's own text (entities resolved by the XML parser)."""
    try:
        tree = ET.parse(path)
    except ET.ParseError as e:
        err(f'{path}: not well-formed XML: {e}')
        return {}
    out = {}
    for el in tree.getroot().iter('string'):
        name = el.get('name')
        text = ''.join(el.itertext())
        if name in out:
            err(f'{path}: duplicate key {name}')
        out[name] = text
    return out


def check_string(path, name, text):
    where = f'{os.path.relpath(path, ROOT)}:{name}'
    if not text.strip():
        err(f'{where}: empty string')
    if text.startswith('@') or text.startswith('?'):
        err(f'{where}: starts with @ or ? (needs escaping)')
    # apostrophes and double quotes must be escaped in Android resources
    if re.search(r"(?<!\\)'", text):
        err(f'{where}: unescaped apostrophe')
    if re.search(r'(?<!\\)"', text):
        err(f'{where}: unescaped double quote')
    # percent handling: only %% or %N$x placeholders are allowed
    stripped = text.replace('%%', '')
    for m in re.finditer(r'%', stripped):
        rest = stripped[m.start():]
        if not re.match(r'%\d+\$[sdf]', rest):
            err(f'{where}: stray % (write %% for a literal percent sign): {text!r}')
            break


def placeholders(text):
    return Counter((n, t) for n, t in PLACEHOLDER.findall(text.replace('%%', '')))


# ---- default strings ------------------------------------------------------------------------
default_path = os.path.join(RES, 'values/strings.xml')
default = load(default_path)
for k, v in default.items():
    check_string(default_path, k, v)

# ---- translations ---------------------------------------------------------------------------
folders = sorted(glob.glob(os.path.join(RES, 'values-*')))
langs = []
for folder in folders:
    code = os.path.basename(folder)[len('values-'):]
    path = os.path.join(folder, 'strings.xml')
    if not os.path.exists(path):
        continue
    langs.append(code)
    tr = load(path)
    keys = set(default) - {'app_name'}
    missing = sorted(keys - set(tr))
    extra = sorted(set(tr) - set(default))
    if missing:
        err(f'{code}: missing {len(missing)} keys: {missing[:8]}{"..." if len(missing) > 8 else ""}')
    if extra:
        err(f'{code}: unknown keys: {extra}')
    for k, v in tr.items():
        if k not in default:
            continue
        check_string(path, k, v)
        if placeholders(v) != placeholders(default[k]):
            err(f'{code}:{k}: placeholders {dict(placeholders(v))} != English {dict(placeholders(default[k]))}')
        # overflow heuristic for short UI labels
        en = default[k]
        if len(en) <= 18 and len(v) > max(2.6 * len(en), len(en) + 14):
            warn(f'{code}:{k}: much longer than English ({len(v)} vs {len(en)}): {v!r}')

# ---- usage in Kotlin ------------------------------------------------------------------------
used = set()
for f in glob.glob(os.path.join(KT, '**/*.kt'), recursive=True):
    used |= set(re.findall(r'R\.string\.([a-z0-9_]+)', open(f, encoding='utf-8').read()))
for k in sorted(used - set(default)):
    err(f'Kotlin uses R.string.{k} but values/strings.xml does not define it')
for k in sorted(set(default) - used):
    warn(f'unused string: {k}')

# ---- language lists agree -------------------------------------------------------------------
cfg = re.findall(r'android:name="([^"]+)"', open(os.path.join(RES, 'xml/locales_config.xml'), encoding='utf-8').read())
helper = open(os.path.join(KT, 'app/dvphoto/platform/LocaleHelper.kt'), encoding='utf-8').read()
tags_line = re.search(r'val tags: List<String> = listOf\((.*?)\)', helper, re.S).group(1)
tags = [t for t in re.findall(r'"([^"]*)"', tags_line) if t]
if set(cfg) != set(langs) | {'en'}:
    err(f'locales_config.xml {sorted(cfg)} != translated folders {sorted(langs)} + en')
if set(tags) != set(cfg):
    err(f'LocaleHelper.tags {sorted(tags)} != locales_config.xml {sorted(cfg)}')
for t in tags:
    if f'"{t}" ->' not in helper and t != 'en':
        err(f'LocaleHelper.nativeName has no entry for {t}')

print(f'languages: en + {", ".join(langs)}  |  {len(default)} strings  |  {len(used)} used in code')
for w in warnings:
    print('warning:', w)
for e in errors:
    print('ERROR:', e)
sys.exit(1 if errors else 0)
