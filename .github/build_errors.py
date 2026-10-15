"""Turns a failed Gradle build into GitHub annotations (::error::) that can be read on the run page,
and for every unknown Minecraft class or method lists what the mapped Minecraft jar really has."""
import glob
import os
import re
import subprocess
import sys

lines = open(sys.argv[1], encoding='utf-8', errors='replace').read().splitlines()
blocks = []
for i, line in enumerate(lines):
    if ': error:' in line and not line.startswith('  /'):
        blocks.append('\n'.join(lines[i:i + 5]))
if not blocks:
    blocks = ['\n'.join(lines[-60:])]
blocks = list(dict.fromkeys(blocks))

# ---- hints from the mapped Minecraft jar (named with Yarn, as the mod sees it)
jars = [j for j in glob.glob(os.path.expanduser('~/.gradle/caches/fabric-loom/**/*.jar'), recursive=True)
        if 'minecraft' in j and 'yarn' in j and 'sources' not in j]
classes = []
for j in jars:
    try:
        out = subprocess.run(['unzip', '-Z1', j], capture_output=True, text=True, timeout=60).stdout
        classes += [(j, c[:-6]) for c in out.splitlines() if c.endswith('.class') and '$' not in c]
    except Exception:
        pass


def find(simple):
    return [(j, c) for j, c in classes if c.rsplit('/', 1)[-1] == simple]


def members(simple, word):
    out = []
    for j, c in find(simple)[:2]:
        try:
            text = subprocess.run(['javap', '-cp', j, c.replace('/', '.')], capture_output=True, text=True, timeout=60).stdout
        except Exception:
            continue
        rows = [r.strip() for r in text.splitlines() if '(' in r or ';' in r]
        parts = [p.lower() for p in re.findall(r'[A-Z]?[a-z]+', word) if len(p) >= 3] or [word.lower()]
        hit = [r for r in rows if any(p in r.lower() for p in parts)]
        out.append(c.replace('/', '.') + ':\n    ' + '\n    '.join((hit or rows)[:25]))
    return out


hints = []
for b in blocks:
    m = re.search(r'import ([\w.]+)\.(\w+);', b)
    if m and 'cannot find symbol' in b:
        found = [c.replace('/', '.') for _, c in find(m.group(2))]
        hints.append(f'{m.group(1)}.{m.group(2)} -> ' + (', '.join(found) or 'no class with that name'))
    sym = re.search(r'symbol:\s+(method|variable|class)\s+(\w+)', b)
    loc = re.search(r'location:\s+(?:class|interface|variable \w+ of type)\s+([\w.<>]+)', b)
    if sym and loc and sym.group(1) != 'class':
        owner = re.sub(r'<.*', '', loc.group(1)).rsplit('.', 1)[-1]
        for h in members(owner, sym.group(2)):
            hints.append(f'{sym.group(2)} in {h}')
    elif sym and sym.group(1) == 'class':
        found = [c.replace('/', '.') for _, c in find(sym.group(2))]
        hints.append(f'class {sym.group(2)} -> ' + (', '.join(found) or 'no class with that name'))

total = sum(1 for l in lines if ': error:' in l and not l.startswith('  /'))
text = '\n\n'.join(blocks) + ('\n\n--- What the mapped Minecraft jar has ---\n' + '\n'.join(dict.fromkeys(hints)) if hints else '')
chunks, cur = [], ''
for part in text.split('\n'):
    if len(cur) + len(part) > 3800:
        chunks.append(cur)
        cur = ''
    cur += part + '\n'
chunks.append(cur)
esc = lambda s: s.replace('%', '%25').replace('\r', '').replace('\n', '%0A')
for n, c in enumerate(chunks[:9], 1):
    print(f'::error title=Build errors {n}/{min(len(chunks), 9)} ({total} compiler errors, {len(jars)} jars)::{esc(c)}')
