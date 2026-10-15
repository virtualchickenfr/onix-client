"""Turns the important lines of a failed Gradle build into GitHub annotations (::error::)."""
import sys

lines = open(sys.argv[1], encoding='utf-8', errors='replace').read().splitlines()
keep = []
for i, line in enumerate(lines):
    if ': error:' in line or line.startswith('e: ') or 'What went wrong' in line or 'Could not ' in line or 'FAILED' in line:
        block = [line] + lines[i + 1:i + 3] if ': error:' in line else lines[i:i + 4]
        keep.append('\n'.join(block))
if not keep:
    keep = lines[-60:]
text = '\n'.join(dict.fromkeys(keep))
total = sum(1 for l in lines if ': error:' in l)
chunks, cur = [], ''
for part in text.split('\n'):
    if len(cur) + len(part) > 3500:
        chunks.append(cur)
        cur = ''
    cur += part + '\n'
chunks.append(cur)
esc = lambda s: s.replace('%', '%25').replace('\r', '').replace('\n', '%0A')
for n, c in enumerate(chunks[:9], 1):
    print(f'::error title=Build errors {n}/{min(len(chunks), 9)} ({total} compiler errors)::{esc(c)}')
