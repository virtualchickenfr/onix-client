#!/usr/bin/env python3
"""
Release notes for GitHub from the website's changelog (website/src/en/news.html and website/src/de/news.html):
what's new in English and German, the downloads, and how to install.

    python3 tools/release_notes.py 1.1.28 > notes.md
"""
import html
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def entry(lang, version):
    text = open(os.path.join(ROOT, 'website', 'src', lang, 'news.html'), encoding='utf-8').read()
    for li in re.findall(r'<li>\s*<div class="cl-head">(.*?)</ul>\s*</li>', text, re.S):
        if re.search(r'<h2>\s*' + re.escape(version) + r'\s*</h2>', li):
            items = re.findall(r'<li>(.*?)</li>', li, re.S)
            out = []
            for it in items:
                it = re.sub(r'<b>(.*?)</b>', r'**\1**', it, flags=re.S)
                it = re.sub(r'<[^>]+>', '', it)
                out.append('- ' + html.unescape(' '.join(it.split())))
            return '\n'.join(out)
    return None


def main():
    version = sys.argv[1].lstrip('v')
    en, de = entry('en', version), entry('de', version)
    parts = []
    if en:
        parts += ["## What's new", en, '']
    if de:
        parts += ['## Neu in dieser Version', de, '']
    if not parts:
        parts += ['Small fixes and improvements.', '']
    parts += [
        '## Download',
        '| System | File |',
        '|---|---|',
        f'| Windows | `Onix-Client-Setup-{version}.exe` |',
        '| macOS | `.dmg` (Apple chip: `arm64`) |',
        f'| Linux | `Onix-Client-{version}.AppImage` |',
        '| Android | `Onix-Android.apk` |',
        '',
        'Already have Onix? The launcher updates itself – nothing to do.',
        '',
        'Only the mod: pick the `onix-client-…+<Minecraft version>.jar` for your Minecraft version '
        '(`onix-client-' + version + '.jar` is 1.21.1) and put it into the `mods` folder of a Fabric installation together with Fabric API.',
        '',
        'More on [the website](https://onix-client.vercel.app) · [What\'s new](https://onix-client.vercel.app/news) · [Help](https://onix-client.vercel.app/help)',
    ]
    print('\n'.join(parts))


if __name__ == '__main__':
    main()
