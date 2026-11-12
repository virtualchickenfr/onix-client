#!/usr/bin/env python3
"""
Builds the Onix website: website/src/<page>.html holds each page's content, this script wraps it in the shared frame
(head, navigation, footer, scripts) and writes website/<page>.html. The module list on funktionen.html comes from
website/data/modules.tsv (made from a dev test log with --modules <latest.log>).

    python3 tools/build_website.py                       # build the pages
    python3 tools/build_website.py --modules latest.log  # refresh website/data/modules.tsv first
"""
import html
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SITE = os.path.join(ROOT, 'website')
REPO = 'https://github.com/virtualchickenfr/onix-client'

# file, menu label, <title>, description
PAGES = [
    ('index.html', 'Start', 'Onix Client',
     'Onix ist ein kostenloser Minecraft-Client mit über 180 Modulen, Minimap, Motion Blur, mehr FPS und einem Launcher für Windows, macOS, Linux und Android.'),
    ('funktionen.html', 'Funktionen', 'Onix Funktionen',
     'Alle Module von Onix: HUD, Kampf, Optik, Kamera, Chat, Werkzeuge und Leistung – mit Suche.'),
    ('download.html', 'Download', 'Onix Download',
     'Onix für Windows, macOS, Linux und Android herunterladen, mit Installationsanleitung.'),
    ('neuigkeiten.html', 'Neuigkeiten', 'Onix Neuigkeiten',
     'Was in den letzten Versionen von Onix neu ist.'),
    ('hilfe.html', 'Hilfe', 'Onix Hilfe',
     'Antworten auf häufige Fragen und Hilfe bei Problemen mit Onix.'),
]

CATEGORIES = [
    ('HUD', 'HUD', 'Anzeigen auf dem Bildschirm, frei verschiebbar und skalierbar.'),
    ('COMBAT', 'Kampf', 'Für PvP: Anzeigen, Treffer-Feedback und Komfort – ohne Cheats.'),
    ('VISUAL', 'Optik', 'Effekte, Himmel, Partikel und alles, was das Spiel anders aussehen lässt.'),
    ('CAMERA', 'Kamera', 'Zoom, Freelook, Perspektiven und Sichtfeld.'),
    ('CHAT', 'Chat', 'Werkzeuge für den Chat.'),
    ('UTILITY', 'Werkzeuge', 'Kleine Helfer für den Alltag im Spiel.'),
    ('PERFORMANCE', 'Leistung', 'Mehr FPS und weniger Ruckler.'),
]


def frame(file, label, title, description, body):
    nav = '\n'.join(
        f'      <a href="{f}"{" aria-current=\"page\"" if f == file else ""}{" class=\"nav-cta\"" if f == "download.html" else ""}>{l}</a>'
        for f, l, _, _ in sorted(PAGES, key=lambda p: p[0] == 'download.html') if f != 'index.html')
    scene = '\n  <script src="js/skin3d.js"></script>\n  <script src="js/fall.js"></script>' if 'id="scene"' in body else ''
    return f'''<!doctype html>
<html lang="de">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <title>{html.escape(title)}</title>
  <meta name="description" content="{html.escape(description)}">
  <meta property="og:title" content="{html.escape(title)}">
  <meta property="og:description" content="{html.escape(description)}">
  <meta property="og:image" content="img/menu.jpg">
  <meta name="theme-color" content="#071d23">
  <link rel="icon" href="img/favicon.png">
  <link rel="stylesheet" href="style.css">
</head>
<body class="page-{file[:-5]}">
  <header class="nav">
    <a class="brand" href="index.html"><img src="img/icon.png" alt="" width="28" height="28"><span>Onix</span></a>
    <button class="menu-btn" type="button" aria-label="Menü" aria-expanded="false" aria-controls="site-nav">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16"/></svg>
    </button>
    <nav id="site-nav">
{nav}
    </nav>
  </header>

{body.strip()}

  <footer>
    <div class="foot-brand"><img src="img/icon.png" alt="" width="22" height="22"><span>Onix Client</span></div>
    <p class="foot-links">{' · '.join(f'<a href="{f}">{l}</a>' for f, l, _, _ in PAGES)} · <a href="{REPO}">GitHub</a></p>
    <p>Kein offizielles Minecraft-Produkt. Nicht von Mojang oder Microsoft genehmigt oder mit ihnen verbunden.</p>
  </footer>
{scene}
  <script src="site.js"></script>
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


def module_list():
    path = os.path.join(SITE, 'data', 'modules.tsv')
    rows = [l.rstrip('\n').split('\t') for l in open(path, encoding='utf-8')][1:]
    # German names and descriptions straight from the mod's translation (newer than the last dev test's list)
    import json
    de = json.load(open(os.path.join(ROOT, 'mod-1.21.11', 'src', 'main', 'resources', 'assets', 'onix', 'lang_onix', 'de.json'), encoding='utf-8'))
    for r in rows:
        r[3] = de.get('name.' + r[0], r[3])
        r[5] = de.get('desc.' + r[0], r[5])
    by_cat = {}
    for r in rows:
        by_cat.setdefault(r[1], []).append(r)
    out = []
    chips = ['<button class="chip active" data-cat="all" type="button">Alle <span>' + str(len(rows)) + '</span></button>']
    for key, name, about in CATEGORIES:
        mods = sorted(by_cat.get(key, []), key=lambda r: r[3].lower())
        if not mods:
            continue
        chips.append(f'<button class="chip" data-cat="{key}" type="button">{name} <span>{len(mods)}</span></button>')
        cards = '\n'.join(
            f'        <li data-search="{html.escape((r[2] + " " + r[3] + " " + r[4] + " " + r[5]).lower())}">'
            f'<b>{html.escape(r[3])}</b><span>{html.escape(r[5])}</span></li>' for r in mods)
        out.append(f'''    <section class="mod-group" data-cat="{key}">
      <h2>{name} <span class="count">{len(mods)}</span></h2>
      <p class="group-about">{about}</p>
      <ul class="mod-list">
{cards}
      </ul>
    </section>''')
    return len(rows), '\n'.join(chips), '\n'.join(out)


def main():
    if '--modules' in sys.argv:
        modules_from_log(sys.argv[sys.argv.index('--modules') + 1])
    count, chips, groups = module_list()
    for file, label, title, description in PAGES:
        body = open(os.path.join(SITE, 'src', file), encoding='utf-8').read()
        body = body.replace('{{MODULE_COUNT}}', str(count)).replace('{{MODULE_CHIPS}}', chips).replace('{{MODULE_GROUPS}}', groups)
        if '{{' in body:
            sys.exit(f'{file}: unknown placeholder {re.search(r"{{[^}]*}}", body).group(0)}')
        with open(os.path.join(SITE, file), 'w', encoding='utf-8') as out:
            out.write(frame(file, label, title, description, body))
    print('built', ', '.join(p[0] for p in PAGES), f'({count} modules)')


if __name__ == '__main__':
    main()
