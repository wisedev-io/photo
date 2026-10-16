#!/usr/bin/env python3
"""Rules watch: do the figures the app enforces still appear on the State Department's pages?

The numbers come from core/.../DvRules.kt (one copy), so when the code changes the expectations change with it.
Run monthly by .github/workflows/rules-watch.yml. Exit codes: 0 everything found, 1 something not found (a human
should read the page and update DvRules.kt, RuleCatalogue.kt and docs/RULES.md), 2 a page could not be fetched.

A miss does not prove the rule changed - the page may have been reworded - and a hit does not prove nothing else
changed. It is a tripwire for the figures, not a replacement for reading the pages (docs/RULES.md).
"""
import html
import os
import re
import sys
import time
import urllib.request
from html.parser import HTMLParser

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RULES = os.path.join(ROOT, 'core/src/main/kotlin/app/dvphoto/core/DvRules.kt')
CATALOGUE = os.path.join(ROOT, 'core/src/main/kotlin/app/dvphoto/core/RuleCatalogue.kt')


def read_figures(path=RULES):
    """The official figures, read from DvRules.kt."""
    src = open(path, encoding='utf-8').read()

    def num(name):
        m = re.search(rf'const val {name}\s*=\s*([0-9]+(?:\.[0-9]+)?)f?\b', src)
        if not m:
            raise SystemExit(f'{name} not found in {path}')
        return float(m.group(1))

    kb = re.search(r'const val MAX_FILE_BYTES\s*=\s*(\d+)\s*\*\s*1024', src)
    if not kb:
        raise SystemExit(f'MAX_FILE_BYTES not found in {path}')
    return {
        'head': (round(num('HEAD_MIN') * 100), round(num('HEAD_MAX') * 100)),
        'eye': (round(num('EYE_MIN') * 100), round(num('EYE_MAX') * 100)),
        'side': (int(num('MIN_SIDE_PX')), int(num('MAX_SIDE_PX'))),
        'kb': int(kb.group(1)),
        'months': int(num('MAX_PHOTO_AGE_MONTHS')),
    }


def read_sources(path=CATALOGUE):
    """{name: url} of the Source enum in RuleCatalogue.kt (the pages the rules cite)."""
    src = open(path, encoding='utf-8').read()
    urls = dict(re.findall(r'(\w+)\(\s*"[^"]+",\s*"(https://[^"]+)"', src))
    urls['PHOTO_REQUIREMENTS'] = re.search(r'OFFICIAL_PHOTO_URL = "([^"]+)"', open(RULES, encoding='utf-8').read()).group(1)
    return urls


# ---- what must appear on which page ------------------------------------------------------------

def _range(lo, hi):
    return rf'\b{lo}\s*(?:%|percent)?\s*(?:and|to|-|through)\s*{hi}\s*(?:%|percent)'


def expectations(f):
    """{source name: [(what, regex)]} - patterns run on lower-cased text with dashes and spaces normalised."""
    return {
        'PHOTO_REQUIREMENTS': [
            (f'head {f["head"][0]}-{f["head"][1]} % of the image height', _range(*f['head'])),
            (f'taken within the last {f["months"]} months', rf'(?:last|past)\s*(?:{f["months"]}|six)\s*months'),
        ],
        'COMPOSITION_TEMPLATE': [
            (f'head {f["head"][0]}-{f["head"][1]} %', _range(*f['head'])),
            (f'eye line {f["eye"][0]}-{f["eye"][1]} %', _range(*f['eye'])),
        ],
        'DIGITAL_IMAGE': [
            (f'{f["side"][0]} x {f["side"][0]} px', rf'\b{f["side"][0]}\s*(?:x|by)\s*{f["side"][0]}\b'),
            (f'{f["side"][1]} x {f["side"][1]} px', rf'\b{f["side"][1]}\s*(?:x|by)\s*{f["side"][1]}\b'),
            (f'{f["kb"]} kB', rf'\b{f["kb"]}\s*(?:kb|kilobytes?|kib)\b'),
            ('JPEG', r'\bjpe?g\b'),
        ],
        'PHOTO_FAQ': [
            ('face horizontally centered', r'horizontally\s+cent(?:ere|re)d'),
            ('photos not in focus are rejected', r'not\s+in\s+focus'),
        ],
    }


# ---- fetching and reading pages ----------------------------------------------------------------

class _Text(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts, self.skip = [], 0

    def handle_starttag(self, tag, attrs):
        if tag in ('script', 'style', 'noscript'):
            self.skip += 1

    def handle_endtag(self, tag):
        if tag in ('script', 'style', 'noscript') and self.skip:
            self.skip -= 1

    def handle_data(self, data):
        if not self.skip:
            self.parts.append(data)


def normalise(page):
    """Visible text of an HTML page (or of plain text), lower-cased, with dashes and spaces made uniform."""
    p = _Text()
    p.feed(page)
    text = html.unescape(' '.join(p.parts))
    text = re.sub(r'[‐-―−]', '-', text.replace(' ', ' '))
    return re.sub(r'\s+', ' ', text).lower()


def check_text(text, rules):
    """[(what, found)] for one page's normalised text."""
    return [(what, re.search(rx, text) is not None) for what, rx in rules]


def fetch(url, tries=3):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (compatible; dv-photo-rules-watch)', 'Accept': 'text/html'})
    last = None
    for i in range(tries):
        try:
            with urllib.request.urlopen(req, timeout=40) as r:
                return r.read().decode('utf-8', 'replace')
        except Exception as e:  # network errors, HTTP errors, TLS
            last = e
            time.sleep(2 * (i + 1))
    raise last


def main():
    figures = read_figures()
    urls = read_sources()
    missing, unreachable = [], []
    for name, rules in expectations(figures).items():
        url = urls[name]
        try:
            text = normalise(fetch(url))
        except Exception as e:
            print(f'COULD NOT FETCH {name}: {url}\n    {e}')
            unreachable.append(name)
            continue
        for what, found in check_text(text, rules):
            print(f'{"ok     " if found else "MISSING"}  {name}: {what}')
            if not found:
                missing.append(f'{name}: {what}')
    if missing:
        print('\nNot found on the official page - read it and update DvRules.kt, RuleCatalogue.kt, docs/RULES.md:')
        for m in missing:
            print('  -', m)
        return 1
    if unreachable:
        print('\nSome pages could not be fetched; the watch is blind for them:', ', '.join(unreachable))
        return 2
    print('\nAll expected figures found.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
