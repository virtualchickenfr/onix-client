#!/usr/bin/env python3
"""
Builds the Onix website in English (website/*.html) and German (website/de/*.html). website/src/<lang>/<page>.html
holds each page's content; this script wraps it in the shared frame (head, navigation with the language switch,
footer, scripts). The module list on features.html comes from website/data/modules.tsv (made from a dev test log with
--modules <latest.log>); German names and descriptions come straight from the mod's translation.

    python3 tools/build_website.py                       # build the pages
    python3 tools/build_website.py --modules latest.log  # refresh website/data/modules.tsv first
"""
import html
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SITE = os.path.join(ROOT, 'website')
REPO = 'https://github.com/virtualchickenfr/onix-client'
DE_JSON = os.path.join(ROOT, 'mod-1.21.11', 'src', 'main', 'resources', 'assets', 'onix', 'lang_onix', 'de.json')

FILES = ['index.html', 'features.html', 'download.html', 'news.html', 'help.html']

LANGS = {
    'en': {
        'dir': '',
        'root': '',
        'menu': {'index.html': 'Home', 'features.html': 'Features', 'download.html': 'Download', 'news.html': "What's new", 'help.html': 'Help'},
        'titles': {
            'index.html': ('Onix Client', 'Onix is a free Minecraft client with {n} modules, a minimap, motion blur, more FPS and a launcher for Windows, macOS, Linux and Android.'),
            'features.html': ('Onix Features', 'Every Onix module: HUD, combat, visuals, camera, chat, utility and performance – with search.'),
            'download.html': ('Onix Download', 'Download Onix for Windows, macOS, Linux and Android, with install steps.'),
            'news.html': ("Onix – What's new", "What's new in the latest Onix versions."),
            'help.html': ('Onix Help', 'Answers to common questions and help when something does not work.'),
        },
        'menu_label': 'Menu',
        'notice': 'Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.',
        'switch': ('Deutsch', 'de/'),
        'categories': {
            'HUD': ('HUD', 'Displays on your screen, free to move and resize.'),
            'COMBAT': ('Combat', 'For PvP: displays, hit feedback and comfort – no cheats.'),
            'VISUAL': ('Visuals', 'Effects, skies, particles and everything that changes how the game looks.'),
            'CAMERA': ('Camera', 'Zoom, freelook, perspectives and field of view.'),
            'CHAT': ('Chat', 'Tools for the chat.'),
            'UTILITY': ('Utility', 'Small helpers for everyday play.'),
            'PERFORMANCE': ('Performance', 'More FPS and fewer stutters.'),
        },
        'all': 'All',
        'category_label': 'Category',
    },
    'de': {
        'dir': 'de',
        'root': '../',
        'menu': {'index.html': 'Start', 'features.html': 'Funktionen', 'download.html': 'Download', 'news.html': 'Neuigkeiten', 'help.html': 'Hilfe'},
        'titles': {
            'index.html': ('Onix Client', 'Onix ist ein kostenloser Minecraft-Client mit {n} Modulen, Minimap, Motion Blur, mehr FPS und einem Launcher für Windows, macOS, Linux und Android.'),
            'features.html': ('Onix Funktionen', 'Alle Module von Onix: HUD, Kampf, Optik, Kamera, Chat, Werkzeuge und Leistung – mit Suche.'),
            'download.html': ('Onix Download', 'Onix für Windows, macOS, Linux und Android herunterladen, mit Installationsanleitung.'),
            'news.html': ('Onix Neuigkeiten', 'Was in den letzten Versionen von Onix neu ist.'),
            'help.html': ('Onix Hilfe', 'Antworten auf häufige Fragen und Hilfe bei Problemen mit Onix.'),
        },
        'menu_label': 'Menü',
        'notice': 'Kein offizielles Minecraft-Produkt. Nicht von Mojang oder Microsoft genehmigt oder mit ihnen verbunden.',
        'switch': ('English', '../'),
        'categories': {
            'HUD': ('HUD', 'Anzeigen auf dem Bildschirm, frei verschiebbar und skalierbar.'),
            'COMBAT': ('Kampf', 'Für PvP: Anzeigen, Treffer-Feedback und Komfort – ohne Cheats.'),
            'VISUAL': ('Optik', 'Effekte, Himmel, Partikel und alles, was das Spiel anders aussehen lässt.'),
            'CAMERA': ('Kamera', 'Zoom, Freelook, Perspektiven und Sichtfeld.'),
            'CHAT': ('Chat', 'Werkzeuge für den Chat.'),
            'UTILITY': ('Werkzeuge', 'Kleine Helfer für den Alltag im Spiel.'),
            'PERFORMANCE': ('Leistung', 'Mehr FPS und weniger Ruckler.'),
        },
        'all': 'Alle',
        'category_label': 'Kategorie',
    },
}
CATEGORY_ORDER = ['HUD', 'COMBAT', 'VISUAL', 'CAMERA', 'CHAT', 'UTILITY', 'PERFORMANCE']


def frame(lang, file, body, count):
    L = LANGS[lang]
    root = L['root']
    title, description = L['titles'][file]
    description = description.replace('{n}', str(count))
    nav = '\n'.join(
        f'      <a href="{f}"{" aria-current=\"page\"" if f == file else ""}{" class=\"nav-cta\"" if f == "download.html" else ""}>{L["menu"][f]}</a>'
        for f in ['features.html', 'news.html', 'help.html', 'download.html'])
    other_label, other_prefix = L['switch']
    other_lang = 'de' if lang == 'en' else 'en'
    switch = f'      <a class="lang" href="{other_prefix}{file}" hreflang="{other_lang}" lang="{other_lang}">{other_label}</a>'
    scene = (f'\n  <script src="{root}js/skin3d.js"></script>\n  <script src="{root}js/fall.js"></script>'
             if 'id="scene"' in body else '')
    links = ' · '.join(f'<a href="{f}">{L["menu"][f]}</a>' for f in FILES)
    return f'''<!doctype html>
<html lang="{lang}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <title>{html.escape(title)}</title>
  <meta name="description" content="{html.escape(description)}">
  <meta property="og:title" content="{html.escape(title)}">
  <meta property="og:description" content="{html.escape(description)}">
  <meta property="og:image" content="{root}img/menu.jpg">
  <meta name="theme-color" content="#071d23">
  <link rel="alternate" hreflang="{other_lang}" href="{other_prefix}{file}">
  <link rel="icon" href="{root}img/favicon.png">
  <link rel="stylesheet" href="{root}style.css">
</head>
<body class="page-{file[:-5]}" data-root="{root}">
  <header class="nav">
    <a class="brand" href="index.html"><img src="{root}img/icon.png" alt="" width="28" height="28"><span>Onix</span></a>
    <button class="menu-btn" type="button" aria-label="{L['menu_label']}" aria-expanded="false" aria-controls="site-nav">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16"/></svg>
    </button>
    <nav id="site-nav">
{nav}
{switch}
    </nav>
  </header>

{body.strip()}

  <footer>
    <div class="foot-brand"><img src="{root}img/icon.png" alt="" width="22" height="22"><span>Onix Client</span></div>
    <p class="foot-links">{links} · <a href="{REPO}">GitHub</a> · <a href="{other_prefix}{file}" lang="{other_lang}">{other_label}</a></p>
    <p>{L['notice']}</p>
  </footer>
{scene}
  <script src="{root}site.js"></script>
</body>
</html>
'''


def modules_from_log(log):
    """Reads the '[devtest] module' lines (id, category, English name, German name, English and German description)."""
    rows = []
    for line in open(log, encoding='utf-8', errors='replace'):
        i = line.find('[devtest] module\t')
        if i < 0:
            continue
        parts = line[i:].rstrip('\n').split('\t')[1:]
        if len(parts) == 6:
            rows.append(parts)
    if not rows:
        sys.exit('no "[devtest] module" lines in ' + log)
    with open(os.path.join(SITE, 'data', 'modules.tsv'), 'w', encoding='utf-8') as out:
        out.write('id\tcategory\tname_en\tname_de\tdesc_en\tdesc_de\n')
        for r in rows:
            out.write('\t'.join(r) + '\n')
    print(f'{len(rows)} modules')


def load_modules():
    path = os.path.join(SITE, 'data', 'modules.tsv')
    rows = [l.rstrip('\n').split('\t') for l in open(path, encoding='utf-8')][1:]
    de = json.load(open(DE_JSON, encoding='utf-8'))
    for r in rows:
        r[3] = de.get('name.' + r[0], r[3])
        r[5] = de.get('desc.' + r[0], r[5])
    return rows


def module_list(rows, lang):
    L = LANGS[lang]
    name_i, desc_i, other_i = (2, 4, 3) if lang == 'en' else (3, 5, 2)
    by_cat = {}
    for r in rows:
        by_cat.setdefault(r[1], []).append(r)
    chips = [f'<button class="chip active" data-cat="all" type="button">{L["all"]} <span>{len(rows)}</span></button>']
    groups = []
    for key in CATEGORY_ORDER:
        mods = sorted(by_cat.get(key, []), key=lambda r: r[name_i].lower())
        if not mods:
            continue
        name, about = L['categories'][key]
        chips.append(f'<button class="chip" data-cat="{key}" type="button">{name} <span>{len(mods)}</span></button>')
        # the other language's name too, so a search finds a module by either name
        cards = '\n'.join(
            f'<li data-en="{html.escape(r[other_i].lower())}"><b>{html.escape(r[name_i])}</b><span>{html.escape(r[desc_i])}</span></li>'
            for r in mods)
        groups.append(f'''    <section class="mod-group" data-cat="{key}">
      <h2>{name} <span class="count">{len(mods)}</span></h2>
      <p class="group-about">{about}</p>
      <ul class="mod-list">
{cards}
      </ul>
    </section>''')
    return '\n'.join(chips), '\n'.join(groups)


def main():
    if '--modules' in sys.argv:
        modules_from_log(sys.argv[sys.argv.index('--modules') + 1])
    rows = load_modules()
    for lang, L in LANGS.items():
        chips, groups = module_list(rows, lang)
        out_dir = os.path.join(SITE, L['dir'])
        os.makedirs(out_dir, exist_ok=True)
        for file in FILES:
            body = open(os.path.join(SITE, 'src', lang, file), encoding='utf-8').read()
            body = (body.replace('{{MODULE_COUNT}}', str(len(rows))).replace('{{MODULE_CHIPS}}', chips)
                    .replace('{{MODULE_GROUPS}}', groups).replace('{{ROOT}}', L['root'])
                    .replace('{{CATEGORY_LABEL}}', L['category_label']))
            if '{{' in body:
                sys.exit(f'{lang}/{file}: unknown placeholder {re.search(r"{{[^}]*}}", body).group(0)}')
            with open(os.path.join(out_dir, file), 'w', encoding='utf-8') as out:
                out.write(frame(lang, file, body, len(rows)))
    print('built', ', '.join(FILES), 'in English and German', f'({len(rows)} modules)')


if __name__ == '__main__':
    main()
