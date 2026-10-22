#!/usr/bin/env python3
"""
Generates the Onix cosmetics catalog: one JSON file that the mod (rendering) and the launcher
(previews) both read, so a cosmetic looks the same everywhere.

    python3 tools/cosmetics.py            # writes both catalog files

All designs are original. The catalog describes
  - palettes:  4 colours each (base, second, accent, dark) plus 6 derived shades (indices 4-9)
  - patterns:  cape designs as a list of pixel operations on the 20 x 32 cape face, most of them
               animated (scrolling, twinkling, flickering, a sweeping sheen)
  - models:    cube models (hats, ears, face, neck, wings, back items, pets, auras) in model
               pixels (1/16 block, Y down) with an animation (bob, spin, flap, orbit …) and parts
               that move on their own inside it (blinking eyes, wagging tails, swaying ribbons, sparkles)
  - items:     model or pattern + palette = one cosmetic

Cube format: [x1, y1, z1, x2, y2, z2, colour, glow]; colour is a palette index 0-9 or "#RRGGBB".
Part format: {cubes, anim, parts?}; part animations: rot / spin (axis, pivot, amp°, speed, phase, base),
pulse (pivot, amp, speed), blink (pivot, period, dur), twinkle (pivot, speed, phase), flicker, bob, slide.
Head anchor: head is x -4..4, y -8..0, z -4..4 (front is -z).
Body anchor: body is x -4..4, y 0..12, z -2..2 (back is +z); feet are at y 24.
"""

import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = [
    os.path.join(ROOT, 'mod', 'src', 'main', 'resources', 'assets', 'onix', 'cosmetics', 'catalog.json'),
    os.path.join(ROOT, 'launcher', 'src', 'assets', 'cosmetics', 'catalog.json'),
    os.path.join(ROOT, 'mod-1.21.11', 'src', 'main', 'resources', 'assets', 'onix', 'cosmetics', 'catalog.json'),
]
CAPE_DIRS = [
    os.path.join(ROOT, 'mod', 'src', 'main', 'resources', 'assets', 'onix', 'textures', 'cosmetics', 'capes'),
    os.path.join(ROOT, 'mod-1.21.11', 'src', 'main', 'resources', 'assets', 'onix', 'textures', 'cosmetics', 'capes'),
    os.path.join(ROOT, 'launcher', 'src', 'assets', 'capes'),
]

# ---------------------------------------------------------------- palettes (base, second, accent, dark)

PALETTES = [
    ('crimson', 'Crimson', ['#C8283C', '#8E1B2C', '#FF7B7B', '#4A0E16']),
    ('ember', 'Ember', ['#F07A28', '#C24E14', '#FFC266', '#5A2208']),
    ('sunflower', 'Sunflower', ['#F5C842', '#D99A1E', '#FFF1A8', '#6B4A0C']),
    ('lime', 'Lime', ['#8BD346', '#5E9E2A', '#D8FF8A', '#2A4A12']),
    ('forest', 'Forest', ['#2F7D46', '#1E5531', '#8FD68F', '#0E2A18']),
    ('mint', 'Mint', ['#5FE0B0', '#34A882', '#C9FFEA', '#134A3A']),
    ('teal', 'Teal', ['#1FA6A6', '#137272', '#7FE6E0', '#0A3636']),
    ('ocean', 'Ocean', ['#2A6FDB', '#1B4A9E', '#8FC0FF', '#0B2050']),
    ('sky', 'Sky', ['#6EC6FF', '#3E94D6', '#E0F5FF', '#17466B']),
    ('indigo', 'Indigo', ['#4B3FC2', '#322A8A', '#A49CFF', '#17124A']),
    ('violet', 'Violet', ['#8A4FE0', '#5E30A8', '#D4BAFF', '#2A1450']),
    ('sakura', 'Sakura', ['#FF9EC8', '#E26AA2', '#FFE4F0', '#6B2848']),
    ('rose', 'Rose', ['#E0457B', '#A82858', '#FFA6C4', '#4E0F26']),
    ('peach', 'Peach', ['#FFB38A', '#E8845A', '#FFE6D6', '#6B3420']),
    ('coffee', 'Coffee', ['#8A5A3C', '#5E3A24', '#D9AE86', '#2A170C']),
    ('sand', 'Sand', ['#E2C892', '#BFA06A', '#FFF4DA', '#5E4A26']),
    ('snow', 'Snow', ['#F2F5FA', '#C9D2DE', '#FFFFFF', '#6E7A8A']),
    ('slate', 'Slate', ['#6B7686', '#4A5361', '#B4BECC', '#22272F']),
    ('onyx', 'Onyx', ['#2A2A33', '#18181E', '#8B84FF', '#0B0B0F']),
    ('gold', 'Gold', ['#F2C14E', '#C8902A', '#FFF2BC', '#6B4A10']),
    ('silver', 'Silver', ['#C7CCD6', '#9298A6', '#F4F6FA', '#4A4F5A']),
    ('toxic', 'Toxic', ['#9CFF2E', '#4CC41A', '#EDFFC2', '#1E3A08']),
    ('lava', 'Lava', ['#FF5A1F', '#B81E0A', '#FFD24A', '#3A0A04']),
    ('galaxy', 'Galaxy', ['#4A2390', '#D76D9A', '#FFC08A', '#140A30']),
    ('aurora', 'Aurora', ['#1DE9B6', '#7C4DFF', '#C6FFE4', '#0B1A3A']),
    ('candy', 'Candy', ['#FF77C8', '#7AD7FF', '#FFF07A', '#5A2A6B']),
    ('midnight', 'Midnight', ['#1E2650', '#10163A', '#9AB2FF', '#05070F']),
]
PALETTE_IDS = [p[0] for p in PALETTES]


def mix(a, b, t=0.5):
    pa, pb = int(a[1:], 16), int(b[1:], 16)
    ch = lambda s: round(((pa >> s) & 255) * (1 - t) + ((pb >> s) & 255) * t)
    return '#%02X%02X%02X' % (ch(16), ch(8), ch(0))


def palette_colours(c):
    """The 4 palette colours plus 6 in-between shades models use for detail:
    4 light base, 5 deep base, 6 light second, 7 deep second, 8 highlight, 9 near black."""
    return c + [mix(c[0], c[2]), mix(c[0], c[3]), mix(c[1], c[2]), mix(c[1], c[3]), mix(c[2], '#FFFFFF'), mix(c[3], '#000000')]
SPECIAL = {'galaxy', 'aurora', 'candy', 'lava', 'gold'}

TIERS = ['common', 'rare', 'epic', 'legendary']


def tier(base, palette):
    i = TIERS.index(base)
    if palette in SPECIAL:
        i = min(i + 1, 3)
    return TIERS[i]


def pick(index, count, stride=7, offset=0):
    """`count` palettes for a model, spread over the list and different per model."""
    start = (index * 5 + offset) % len(PALETTE_IDS)
    out = []
    k = 0
    while len(out) < count:
        pid = PALETTE_IDS[(start + k * stride) % len(PALETTE_IDS)]
        if pid not in out:
            out.append(pid)
        k += 1
    return out

# ---------------------------------------------------------------- cape patterns (20 x 32 face)
# Every pattern may be animated: 'anim' = frames and ticks per frame (20 ticks = 1 second). Ops can scroll ('mv' pixels per
# frame), twinkle ('tw' = share of stars that are off each frame), flicker ('fr'), turn ('rot' radians per frame) or show
# only on some frames ('on'). 'sheen' sweeps a band of light across the cape.

EMBLEMS = {
    'star': ['....#....', '....#....', '...###...', '#########', '.#######.', '..#####..', '..##.##..', '.##...##.', '##.....##'],
    'heart': ['.##...##.', '####.####', '#########', '#########', '.#######.', '..#####..', '...###...', '....#....'],
    'moon': ['...####..', '..##.....', '.##......', '.##......', '.##......', '.##......', '..##.....', '...####..'],
    'bolt': ['.....###', '....###.', '...###..', '..######', '.....##.', '....##..', '...##...', '..##....', '.##.....'],
    'crown': ['#...#...#', '##.###.##', '#########', '#########', '#.#.#.#.#', '#########'],
    'leaf': ['.....###', '...#####', '..######', '.######.', '.#####..', '.###....', '##......', '#.......'],
    'flower': ['...###...', '..#####..', '.###.###.', '###...###', '.###.###.', '..#####..', '...###...', '....#....', '....#....'],
    'gem': ['..#####..', '.#######.', '#########', '.#######.', '..#####..', '...###...', '....#....'],
    'paw': ['.#.....#.', '###...###', '.#.#.#.#.', '...###...', '..#####..', '.#######.', '.#######.', '..##.##..'],
    'note': ['....####', '....#..#', '....#..#', '....#..#', '....#..#', '.###.###', '####.###', '.##...#.'],
}

SHEEN = {'t': 'sheen', 'w': 3, 'step': 3, 'c': 2}
SHEEN_ANIM = {'frames': 20, 'ticks': 3}


def anim(frames, ticks):
    return {'frames': frames, 'ticks': ticks}


# shooting star for the starry cape: a short streak on the first frames only
SHOOTING = [{'t': 'rect', 'x': 15 - 3 * i, 'y': 2 + 2 * i, 'w': 3, 'h': 1, 'c': 2, 'on': [i]} for i in range(4)]
CIRCUIT_NODES = []
for i, (x, y) in enumerate([(4, 3), (11, 11), (18, 3), (4, 19), (18, 19), (11, 27)]):
    CIRCUIT_NODES.append({'t': 'disc', 'cx': x + 0.5, 'cy': y + 0.5, 'r': 1.3, 'c': 0})
    CIRCUIT_NODES.append({'t': 'rect', 'x': x, 'y': y, 'w': 1, 'h': 1, 'c': 2, 'on': [(i * 3 + k) % 20 for k in range(4)]})

PATTERNS = {
    # name, base tier, ops, animation
    'classic': ('Classic', 'common', [{'t': 'grad', 'a': 'v', 'c': [0, 1]}, {'t': 'border', 'w': 1, 'c': 3}, SHEEN], SHEEN_ANIM),
    'duo': ('Duo', 'common', [{'t': 'fill', 'c': 0}, {'t': 'rect', 'x': 10, 'y': 0, 'w': 10, 'h': 32, 'c': 1}, {'t': 'border', 'w': 1, 'c': 3}, SHEEN], SHEEN_ANIM),
    'fade': ('Fade', 'common', [{'t': 'grad', 'a': 'v', 'c': [2, 0]}, {'t': 'grad', 'a': 'v', 'c': [-1, 1], 'from': 0.55}, SHEEN], SHEEN_ANIM),
    'stripes': ('Stripes', 'common', [{'t': 'fill', 'c': 0}, {'t': 'stripes', 'a': 'h', 'w': 2, 'p': 6, 'o': 0, 'mv': -1, 'c': 1}, {'t': 'border', 'w': 1, 'c': 3}], anim(6, 3)),
    'pinstripe': ('Pinstripe', 'common', [{'t': 'fill', 'c': 1}, {'t': 'stripes', 'a': 'v', 'w': 1, 'p': 4, 'o': 1, 'mv': 1, 'c': 2}], anim(4, 4)),
    'slash': ('Slash', 'common', [{'t': 'fill', 'c': 0}, {'t': 'stripes', 'a': 'd', 'w': 3, 'p': 8, 'o': 0, 'mv': -1, 'c': 1}, {'t': 'stripes', 'a': 'd', 'w': 1, 'p': 8, 'o': 3, 'mv': -1, 'c': 2}], anim(8, 3)),
    'chevron': ('Chevron', 'rare', [{'t': 'fill', 'c': 1}, {'t': 'chevron', 'w': 3, 'p': 8, 'mv': -1, 'c': 0}, {'t': 'chevron', 'w': 1, 'p': 8, 'o': 3, 'mv': -1, 'c': 2}], anim(8, 3)),
    'checker': ('Checker', 'common', [{'t': 'checker', 's': 4, 'mv': 1, 'c': [0, 1]}, {'t': 'border', 'w': 1, 'c': 3}], anim(8, 4)),
    'polka': ('Polka', 'common', [{'t': 'fill', 'c': 0}, {'t': 'dots', 's': 6, 'r': 1.6, 'mv': 1, 'c': 2}], anim(12, 3)),
    'starry': ('Starry', 'epic', [{'t': 'grad', 'a': 'v', 'c': [3, 1]}, {'t': 'scatter', 'n': 22, 'seed': 11, 'c': 2, 'plus': 0, 'tw': 0.35},
                                  {'t': 'scatter', 'n': 4, 'seed': 29, 'c': 2, 'plus': 1, 'tw': 0.2}] + SHOOTING, anim(16, 3)),
    'waves': ('Waves', 'rare', [{'t': 'fill', 'c': 1}, {'t': 'waves', 'p': 7, 'w': 3, 'amp': 1.6, 'len': 10, 'mv': -1, 'c': 0},
                                {'t': 'waves', 'p': 7, 'w': 1, 'amp': 1.6, 'len': 10, 'o': 3, 'mv': -1, 'c': 2}], anim(7, 3)),
    'zigzag': ('Zigzag', 'rare', [{'t': 'fill', 'c': 0}, {'t': 'zigzag', 'p': 8, 'w': 2, 'amp': 3, 'mv': -1, 'c': 1}, {'t': 'zigzag', 'p': 8, 'w': 1, 'amp': 3, 'o': 4, 'mv': -1, 'c': 2}], anim(8, 3)),
    'argyle': ('Argyle', 'rare', [{'t': 'fill', 'c': 1}, {'t': 'argyle', 'sx': 10, 'sy': 14, 'c': 0}, {'t': 'stripes', 'a': 'd', 'w': 1, 'p': 10, 'o': 0, 'c': 2},
                                  {'t': 'stripes', 'a': 'a', 'w': 1, 'p': 10, 'o': 0, 'c': 2}, SHEEN], SHEEN_ANIM),
    'nordic': ('Nordic', 'common', [{'t': 'fill', 'c': 0}, {'t': 'rect', 'x': 0, 'y': 11, 'w': 20, 'h': 5, 'c': 2}, {'t': 'rect', 'x': 6, 'y': 0, 'w': 5, 'h': 32, 'c': 2},
                                    {'t': 'rect', 'x': 0, 'y': 12, 'w': 20, 'h': 3, 'c': 1}, {'t': 'rect', 'x': 7, 'y': 0, 'w': 3, 'h': 32, 'c': 1}, SHEEN], SHEEN_ANIM),
    'flames': ('Flames', 'epic', [{'t': 'grad', 'a': 'v', 'c': [3, 1]}, {'t': 'flames', 'seed': 5, 'fr': 1, 'c': [0, 2]},
                                  {'t': 'scatter', 'n': 6, 'seed': 41, 'c': 2, 'tw': 0.5}], anim(6, 2)),
    'sunburst': ('Sunburst', 'epic', [{'t': 'fill', 'c': 1}, {'t': 'rays', 'n': 14, 'cx': 10, 'cy': 9, 'rot': round(2 * (2 * 3.141592653589793 / 14) / 16, 6), 'c': 0},
                                      {'t': 'disc', 'cx': 10, 'cy': 9, 'r': 3.2, 'c': 2}], anim(16, 2)),
    'speckle': ('Speckle', 'common', [{'t': 'grad', 'a': 'd', 'c': [0, 1]}, {'t': 'noise', 'seed': 7, 'd': 0.12, 'fr': 1, 'c': 2}, {'t': 'noise', 'seed': 13, 'd': 0.08, 'c': 3}], anim(4, 5)),
    'rain': ('Rainfall', 'rare', [{'t': 'grad', 'a': 'v', 'c': [3, 1]}, {'t': 'drops', 'n': 12, 'seed': 3, 'len': 4, 'speed': 2, 'c': 0, 'c2': 2}], anim(16, 2)),
    'code': ('Code Rain', 'epic', [{'t': 'fill', 'c': 3}, {'t': 'drops', 'n': 16, 'seed': 9, 'len': 7, 'speed': 2, 'c': 1, 'c2': 2},
                                   {'t': 'drops', 'n': 8, 'seed': 17, 'len': 4, 'speed': 2, 'c': 0}], anim(16, 2)),
    'plaid': ('Plaid', 'common', [{'t': 'fill', 'c': 0}, {'t': 'stripes', 'a': 'h', 'w': 4, 'p': 10, 'c': 1}, {'t': 'stripes', 'a': 'v', 'w': 4, 'p': 10, 'c': 1},
                                  {'t': 'stripes', 'a': 'h', 'w': 1, 'p': 10, 'o': 7, 'c': 2}, {'t': 'stripes', 'a': 'v', 'w': 1, 'p': 10, 'o': 7, 'c': 2},
                                  {'t': 'border', 'w': 1, 'c': 3}, SHEEN], SHEEN_ANIM),
    'scales': ('Dragon Scales', 'epic', [{'t': 'fill', 'c': 0}, {'t': 'scales', 's': 6, 'c': 3, 'c2': 1}, dict(SHEEN, only=0)], SHEEN_ANIM),
    'nebula': ('Nebula', 'legendary', [{'t': 'fill', 'c': 3}, {'t': 'blobs', 'n': 5, 'seed': 4, 'r': 5, 'c': 1}, {'t': 'blobs', 'n': 3, 'seed': 8, 'r': 3.5, 'c': 0},
                                       {'t': 'scatter', 'n': 18, 'seed': 5, 'c': 2, 'tw': 0.4}, {'t': 'scatter', 'n': 2, 'seed': 6, 'c': 2, 'plus': 1, 'tw': 0.3}], anim(8, 4)),
    'circuit': ('Circuit', 'rare', [{'t': 'fill', 'c': 3}, {'t': 'stripes', 'a': 'h', 'w': 1, 'p': 8, 'o': 5, 'c': 1}, {'t': 'stripes', 'a': 'v', 'w': 1, 'p': 7, 'o': 3, 'c': 1},
                                    dict(SHEEN, w=2, only=1)] + CIRCUIT_NODES, anim(20, 2)),
    'storm': ('Storm', 'epic', [{'t': 'grad', 'a': 'v', 'c': [3, 1]}, {'t': 'drops', 'n': 10, 'seed': 21, 'len': 3, 'speed': 2, 'c': 0},
                                {'t': 'emblem', 'm': 'bolt', 'x': 6, 'y': 10, 'c': 2, 'outline': 0, 'on': [0, 1, 5]}], anim(16, 2)),
}
EMBLEM_NAMES = {'star': 'Star', 'heart': 'Heart', 'moon': 'Moon', 'bolt': 'Bolt', 'crown': 'Crown', 'leaf': 'Leaf',
                'flower': 'Bloom', 'gem': 'Gem', 'paw': 'Paw', 'note': 'Melody'}
for key, label in EMBLEM_NAMES.items():
    mask = EMBLEMS[key]
    w = max(len(r) for r in mask)
    h = len(mask)
    PATTERNS[f'emblem_{key}'] = (f'{label} Emblem', 'rare', [
        {'t': 'grad', 'a': 'v', 'c': [0, 1]},
        {'t': 'border', 'w': 1, 'c': 3},
        {'t': 'emblem', 'm': key, 'x': (20 - w) // 2, 'y': 9 - h // 2 + 2, 'c': 2, 'outline': 3},
        SHEEN,
    ], SHEEN_ANIM)

# ---------------------------------------------------------------- cube helpers


def box(x1, y1, z1, x2, y2, z2, c, glow=False):
    return [round(x1, 3), round(y1, 3), round(z1, 3), round(x2, 3), round(y2, 3), round(z2, 3), c, 1 if glow else 0]


def rbox(x1, y1, z1, x2, y2, z2, c, glow=False, r=None):
    """A box with chamfered edges, made of three overlapping boxes."""
    if r is None:
        r = min(x2 - x1, y2 - y1, z2 - z1) * 0.22
    if r < 0.08:
        return [box(x1, y1, z1, x2, y2, z2, c, glow)]
    return [box(x1, y1 + r, z1 + r, x2, y2 - r, z2 - r, c, glow), box(x1 + r, y1, z1 + r, x2 - r, y2, z2 - r, c, glow),
            box(x1 + r, y1 + r, z1, x2 - r, y2 - r, z2, c, glow)]


def ball(cx, cy, cz, r, c, glow=False):
    return rbox(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r, c, glow, r * 0.42)


def ring(y1, y2, inset, c, out=0.0, t=0.5):
    """A square band around the head (x/z -4..4) at height y1..y2."""
    a = 4 + out
    return [box(-a, y1, -a, a, y2, -a + t, c), box(-a, y1, a - t, a, y2, a, c),
            box(-a, y1, -a + t, -a + t, y2, a - t, c), box(a - t, y1, -a + t, a, y2, a - t, c)]


def rot90(c, times=1):
    """Turns a cube a quarter turn around the vertical axis (front → right → back → left)."""
    for _ in range(times % 4):
        c = [-c[5], c[1], c[0], -c[2], c[4], c[3], c[6], c[7]]
    return [round(v, 3) if isinstance(v, float) else v for v in c]


def all_sides(cubes):
    """Cubes designed for the front of the head, repeated on all four sides."""
    return [rot90(c, k) for k in range(4) for c in cubes]


def mcube(c):
    return [-c[3], c[1], c[2], -c[0], c[4], c[5], c[6], c[7]]


def mirror_x(cubes):
    out = []
    for c in cubes:
        out.append(c)
        out.append(mcube(c))
    return out


def A(kind, **kw):
    a = {'type': kind}
    for k, v in kw.items():
        a[k] = [round(x, 3) for x in v] if isinstance(v, (list, tuple)) else (round(v, 4) if isinstance(v, float) else v)
    return a


def P(cubes, anim, parts=None):
    """A part: cubes with their own animation inside the model."""
    d = {'cubes': cubes, 'anim': anim}
    if parts:
        d['parts'] = parts
    return d


def manim(a, dphase=0.0):
    a = dict(a)
    if 'pivot' in a:
        a['pivot'] = [-a['pivot'][0], a['pivot'][1], a['pivot'][2]]
    axis = a.get('axis', 'y')
    if a['type'] == 'rot' and axis in ('y', 'z'):
        a['amp'] = -a.get('amp', 0)
        if 'base' in a:
            a['base'] = -a['base']
    if a['type'] == 'spin' and axis in ('y', 'z'):
        a['speed'] = -a['speed']
    if a['type'] == 'slide' and axis == 'x':
        a['amp'] = -a['amp']
    if dphase:
        a['phase'] = round(a.get('phase', 0) + dphase, 4)
    return a


def mpart(p, dphase=0.0):
    d = {'cubes': [mcube(c) for c in p['cubes']], 'anim': manim(p['anim'], dphase)}
    if p.get('parts'):
        d['parts'] = [mpart(q, dphase) for q in p['parts']]
    return d


def mirror_parts(parts, dphase=0.0):
    return parts + [mpart(p, dphase) for p in parts]


W = '#FFFFFF'
BLACK = '#17151A'
EYE = '#1A1410'
GOLD = '#F2C14E'
GOLD_D = '#C8902A'
SILVER = '#DDE3EA'
SILVER_D = '#9AA3B0'
PINK = '#FFB3C7'
LEAF_G = '#4FA35A'


def glint(x, y, z, s=0.45, speed=0.11, phase=0.0, c=W):
    """A small sparkle that flashes now and then."""
    h = s / 2
    return P([box(x - h, y - h, z - h, x + h, y + h, z + h, c, True), box(x - s, y - 0.07, z - 0.07, x + s, y + 0.07, z + 0.07, c, True),
              box(x - 0.07, y - s, z - 0.07, x + 0.07, y + s, z + 0.07, c, True)],
             A('twinkle', pivot=[x, y, z], speed=speed, phase=phase))


def eye(x, y, z, s=0.55, phase=0.0, iris=EYE, period=96):
    """An eye on a front (-z) face: dark eye, small light spot, blinks now and then."""
    h = s / 2
    cubes = [box(x - h, y - h, z - 0.08, x + h, y + h, z + 0.02, iris), box(x - h * 0.15, y - h, z - 0.11, x + h, y - h * 0.15, z - 0.07, W)]
    return P(cubes, A('blink', pivot=[x, y, z], period=period, dur=5, phase=phase))


MODELS = {}


def model(mid, slot, name, base_tier, anchor, cubes, anim=None, palettes=None, kind=None, parts=None):
    MODELS[mid] = {'slot': slot, 'name': name, 'tier': base_tier, 'anchor': anchor, 'cubes': cubes,
                   'anim': anim or {'type': 'static'}, 'palettes': palettes, 'kind': kind or slot}
    if parts:
        MODELS[mid]['parts'] = parts

# ---------------------------------------------------------------- head (head is x -4..4, y -8..0, z -4..4; front is -z)


def crown():
    cubes = ring(-9.4, -8.0, 0, 0, 0.5) + ring(-8.25, -7.85, 0, 1, 0.65) + ring(-9.75, -9.4, 0, 2, 0.6)
    cubes += [box(-3.9, -9.2, -3.9, 3.9, -8.4, 3.9, '#6E1530'), box(-2.8, -10.3, -2.8, 2.8, -9.2, 2.8, '#7E1A38'),
              box(-1.6, -10.9, -1.6, 1.6, -10.3, 1.6, '#8E2242')]
    cubes += [box(-0.25, -11.6, -0.25, 0.25, -10.9, 0.25, 1)] + ball(0, -12.0, 0, 0.45, 2)
    cubes += [box(-0.15, -12.9, -0.12, 0.15, -12.2, 0.12, 2), box(-0.4, -12.65, -0.12, 0.4, -12.4, 0.12, 2)]   # little cross on the orb
    front = []
    for x, tall in [(-3.3, False), (0.0, True), (3.3, False)]:
        top = -12.5 if tall else -11.7
        front += [box(x - 0.55, -10.6, -4.5, x + 0.55, -9.6, -4.0, 0), box(x - 0.35, top + 0.6, -4.45, x + 0.35, -10.6, -4.05, 0)]
        front += ball(x, top + 0.3, -4.25, 0.32, 2)
    for x in (-1.7, 1.7):
        front += [box(x - 0.3, -9.2, -4.62, x + 0.3, -8.6, -4.48, W)]           # pearls between the gems
    cubes += all_sides(front)
    gems = [('#E8483B', '#FF9C8C'), ('#4EA8FF', '#BFE2FF'), ('#3DDC84', '#B8F5D2'), ('#4EA8FF', '#BFE2FF')]
    for k, (col, hi) in enumerate(gems):
        g = [box(-0.75, -9.25, -4.85, 0.75, -8.15, -4.5, col), box(-0.35, -9.05, -4.95, 0.15, -8.75, -4.85, hi)]
        cubes += [rot90(c, k) for c in g]
    parts = [glint(0.35, -8.9, -5.05, 0.5, 0.1, 0.0), glint(-3.3, -11.5, -4.3, 0.4, 0.12, 2.2), glint(4.3, -9.0, 0.2, 0.4, 0.09, 4.1)]
    model('crown', 'head', 'Crown', 'rare', 'head', cubes, parts=parts)


def tophat():
    cubes = rbox(-5.6, -8.75, -5.6, 5.6, -8.0, 5.6, 3, r=0.3)
    cubes += [box(-5.6, -9.1, -2.8, -4.9, -8.75, 2.8, 3), box(4.9, -9.1, -2.8, 5.6, -8.75, 2.8, 3)]          # curled brim sides
    cubes += rbox(-3.6, -15.2, -3.6, 3.6, -8.75, 3.6, 0, r=0.35)
    cubes += [box(-3.45, -15.35, -3.45, 3.45, -15.0, 3.45, 1)]                                               # lid
    cubes += ring(-10.4, -8.75, 0, 2, -0.35, 0.3)                                                             # band
    cubes += [box(-0.9, -10.3, -3.82, 0.9, -8.85, -3.7, GOLD), box(-0.45, -9.95, -3.88, 0.45, -9.2, -3.8, 3)]  # buckle
    cubes += [box(2.2, -11.6, -3.85, 3.0, -10.4, -3.62, 2), box(2.4, -12.4, -3.85, 2.8, -11.6, -3.62, LEAF_G)]  # little feather
    parts = [glint(0.6, -9.9, -3.95, 0.4, 0.1, 1.0)]
    model('tophat', 'head', 'Top Hat', 'common', 'head', cubes, parts=parts)


def halo():
    cubes = []
    r_out, r_in, y1, y2 = 4.3, 3.4, -12.6, -11.95
    for k in range(4):                         # octagon: four long sides and four corners
        side = [box(-2.2, y1, -r_out, 2.2, y2, -r_in, 2, True), box(2.2, y1, -r_out + 0.5, 3.4, y2, -r_in + 0.4, 2, True)]
        cubes += [rot90(c, k) for c in side]
    inner = []
    for k in range(4):
        inner += [rot90(c, k) for c in [box(-2.0, y1 + 0.18, -r_in - 0.05, 2.0, y2 - 0.18, -r_in + 0.25, W, True)]]
    parts = [P(inner, A('pulse', pivot=[0, -12.3, 0], amp=0.04, speed=0.2))]
    parts += [glint(x, -12.3, z, 0.45, 0.12, ph) for x, z, ph in [(3.9, -1.0, 0.0), (-1.2, 3.9, 1.6), (-3.9, 1.6, 3.1), (1.5, -3.9, 4.6)]]
    model('halo', 'head', 'Halo', 'epic', 'head', cubes, anim={'type': 'bob', 'amp': 0.5, 'speed': 0.08}, parts=parts)


def party():
    cubes = []
    steps = 8
    for i in range(steps):
        half = 3.2 - i * 0.37
        cubes.append(box(1 - half, -8 - 1.05 * (i + 1), -half, 1 + half, -8 - 1.05 * i, half, [0, 1, 0, 2][i % 4]))
    for i, (x, z) in enumerate([(-1.6, -2.95), (2.8, -2.6), (0.4, -2.2), (2.9, 1.2), (-1.4, 2.2)]):
        y = -9.0 - i * 1.4
        cubes.append(box(x - 0.3, y - 0.3, z - 0.15, x + 0.3, y + 0.3, z + 0.15, 3))
    cubes += [box(-3.05, -8.1, -0.15, -2.9, -0.4, 0.15, '#2A2A30'), box(4.9, -8.1, -0.15, 5.05, -0.4, 0.15, '#2A2A30')]   # elastic
    top = -8 - 1.05 * steps
    pompom = ball(1, top - 0.8, 0, 0.9, W) + [box(0.3, top - 1.5, -0.4, 0.9, top - 0.95, 0.2, '#F4F4F8')]
    parts = [P(pompom, A('bob', amp=0.35, speed=0.3)), glint(1.6, top - 1.6, -0.6, 0.35, 0.14, 1.0)]
    model('party', 'head', 'Party Hat', 'common', 'head', cubes, parts=parts)


def comb():
    lobes = [box(-0.5, -9.6, -3.4, 0.5, -8.0, -2.0, 0), box(-0.55, -10.6, -2.1, 0.55, -8.0, -0.6, 0), box(-0.6, -11.4, -0.7, 0.6, -8.0, 0.9, 0),
             box(-0.55, -10.7, 0.8, 0.55, -8.0, 2.3, 0), box(-0.5, -9.8, 2.2, 0.5, -8.0, 3.4, 0)]
    lobes += [box(-0.56, -10.9, -0.5, 0.56, -10.4, 0.7, 2), box(-0.51, -10.0, -1.9, 0.51, -9.6, -0.9, 2)]
    wattle = [box(-1.0, -1.5, -4.8, 1.0, 0.3, -4.0, 0), box(-0.7, 0.3, -4.75, 0.7, 1.1, -4.05, 1)]
    base = [box(-0.65, -8.25, -3.2, 0.65, -8.0, 3.2, 1)]
    parts = [P(lobes, A('rot', axis='z', pivot=[0, -8.0, 0], amp=5, speed=0.17)),
             P(wattle, A('rot', axis='x', pivot=[0, -1.5, -4.4], amp=9, speed=0.23, phase=1.2))]
    model('comb', 'head', 'Comb', 'common', 'head', base, parts=parts)


def beanie():
    cubes = rbox(-4.5, -9.4, -4.5, 4.5, -6.6, 4.5, 0, r=0.5) + rbox(-3.6, -10.2, -3.6, 3.6, -9.2, 3.6, 0, r=0.4)
    cubes += ring(-6.9, -5.5, 0, 1, 0.65, 0.6)
    for k in range(4):                                         # ribbed cuff
        cubes += [rot90(box(x, -6.95, -4.75, x + 0.35, -5.45, -4.6, 3), k) for x in (-3.6, -2.0, -0.4, 1.2, 2.8)]
    for k in range(4):                                         # knit pattern
        cubes += [rot90(box(x, y, -4.62, x + 0.5, y + 0.4, -4.5, 2), k) for x, y in [(-2.8, -8.4), (-0.6, -7.6), (1.8, -8.6), (-1.6, -9.1)]]
    pompom = ball(0, -11.1, 0, 1.15, 2) + [box(-0.6, -11.9, -1.25, 0.2, -11.4, -1.0, W)]
    parts = [P(pompom, A('bob', amp=0.3, speed=0.22))]
    model('beanie', 'head', 'Beanie', 'common', 'head', cubes, parts=parts)


def cap():
    cubes = []
    for k, col in enumerate([0, 1, 0, 1]):                     # four panels
        cubes += [rot90(box(-4.45, -9.0, -4.45, 0.0, -7.0, 0.0, col), k)]
    cubes += rbox(-3.6, -9.7, -3.6, 3.6, -8.9, 3.6, 0, r=0.4)
    cubes += [box(-4.3, -7.3, -8.2, 4.3, -6.9, -4.4, 1), box(-4.3, -6.95, -8.2, 4.3, -6.85, -4.4, 3), box(-4.35, -7.35, -8.3, 4.35, -6.85, -8.0, 0)]
    cubes += ball(0, -10.0, 0, 0.45, 2)
    cubes += [box(-1.0, -8.6, -4.6, 1.0, -7.4, -4.48, 2), box(-0.45, -8.3, -4.66, 0.45, -7.7, -4.55, 3)]   # badge
    cubes += [box(x, -8.4, z, x + 0.25, -8.15, z + 0.25, 3) for x, z in [(-3.0, -4.55), (2.75, -4.55)]]  # vents
    cubes += [box(-1.6, -7.6, 4.45, 1.6, -7.05, 4.6, 3), box(-0.4, -7.7, 4.6, 0.4, -6.95, 4.7, SILVER)]   # strap
    parts = [glint(0.6, -8.4, -4.75, 0.35, 0.1, 0.5)]
    model('cap', 'head', 'Cap', 'common', 'head', cubes, parts=parts)


def cowboy():
    cubes = rbox(-7.2, -8.6, -7.0, 7.2, -8.0, 7.0, 1, r=0.3)
    cubes += [box(-7.2, -9.6, -5.5, -6.3, -8.6, 5.5, 1), box(6.3, -9.6, -5.5, 7.2, -8.6, 5.5, 1),          # curled sides
              box(-7.4, -10.2, -3.6, -6.7, -9.4, 3.6, 1), box(6.7, -10.2, -3.6, 7.4, -9.4, 3.6, 1)]
    cubes += rbox(-3.7, -12.0, -3.7, 3.7, -8.6, 3.7, 0, r=0.4)
    cubes += [box(-3.0, -12.6, -3.1, -0.4, -12.0, 3.1, 0), box(0.4, -12.6, -3.1, 3.0, -12.0, 3.1, 0)]       # pinched crown
    cubes += ring(-9.7, -8.6, 0, 3, -0.2, 0.3)
    cubes += [box(-0.8, -9.8, -3.95, 0.8, -8.5, -3.8, SILVER), box(-0.35, -9.4, -4.02, 0.35, -8.9, -3.92, '#4EA8FF')]
    cubes += [box(x, -9.35, -3.92, x + 0.35, -9.0, -3.8, SILVER) for x in (-2.6, -1.7, 1.35, 2.25)]
    parts = [glint(0.3, -9.4, -4.1, 0.45, 0.1, 2.0)]
    model('cowboy', 'head', 'Cowboy Hat', 'rare', 'head', cubes, parts=parts)


def pointy_hat(mid, name, tier, brim, body, band, tip_col, decor, buckle):
    cubes = rbox(-6.5, -8.6, -6.5, 6.5, -8.0, 6.5, brim, r=0.3)
    for i in range(4):
        h = 3.8 - i * 0.75
        cubes.append(box(-h, -10.6 - i * 2, -h, h, -8.6 - i * 2, h, body))
    cubes += ring(-10.0, -8.6, 0, band, -0.15, 0.3)
    if buckle:
        cubes += [box(-1.0, -10.1, -3.95, 1.0, -8.5, -3.75, GOLD), box(-0.5, -9.7, -4.02, 0.5, -8.9, -3.92, band)]
    cubes += decor
    tip_mid = [box(-0.9, -19.6, -0.9, 0.9, -16.6, 0.9, body)]
    tip_end = [box(-0.6, -21.0, -0.2, 0.6, -19.6, 1.2, body), box(-0.4, -22.0, 0.6, 0.4, -21.0, 1.8, tip_col)]
    parts = [P(tip_mid, A('rot', axis='z', pivot=[0, -16.6, 0], amp=6, speed=0.09),
               [P(tip_end, A('rot', axis='x', pivot=[0, -19.6, 0], amp=10, speed=0.09, phase=-0.9, base=-12))])]
    return cubes, parts


def wizard():
    stars = []
    gl = []
    for i, (x, y, z) in enumerate([(-2.6, -11.4, -3.35), (1.4, -13.6, -2.6), (-0.8, -15.8, -1.95), (2.9, -11.0, -3.4)]):
        stars += [box(x - 0.45, y - 0.12, z - 0.1, x + 0.45, y + 0.12, z, 2, True), box(x - 0.12, y - 0.45, z - 0.1, x + 0.12, y + 0.45, z, 2, True)]
        gl.append(glint(x, y, z - 0.15, 0.4, 0.09 + i * 0.01, i * 1.7))
    moon = [box(1.6, -10.2, -3.45, 2.1, -9.0, -3.3, 2, True), box(1.2, -10.4, -3.45, 1.7, -10.0, -3.3, 2, True), box(1.2, -9.2, -3.45, 1.7, -8.8, -3.3, 2, True)]
    cubes, parts = pointy_hat('wizard', 'Wizard Hat', 'epic', 0, 0, 1, 2, stars + moon, False)
    model('wizard', 'head', 'Wizard Hat', 'epic', 'head', cubes, parts=parts + gl)


def witch():
    patch = [box(-2.6, -13.2, -2.95, -1.2, -11.8, -2.85, 1), box(-2.5, -12.6, -3.0, -1.3, -12.45, -2.9, 2)]
    cubes, parts = pointy_hat('witch', 'Witch Hat', 'rare', 3, 3, 0, 0, patch, True)
    model('witch', 'head', 'Witch Hat', 'rare', 'head', cubes, parts=parts + [glint(0.5, -9.6, -4.1, 0.4, 0.1, 0.7)])


def viking():
    cubes = rbox(-4.6, -9.2, -4.6, 4.6, -6.6, 4.6, 1, r=0.5) + rbox(-3.7, -10.0, -3.7, 3.7, -9.0, 3.7, 1, r=0.4)
    cubes += ring(-6.9, -6.2, 0, 3, 0.7, 0.55)
    cubes += [box(-0.35, -10.1, -4.7, 0.35, -6.6, 4.7, 0), box(-4.7, -9.2, -0.35, 4.7, -6.6, 0.35, 0)]           # metal straps
    cubes += [box(-0.7, -7.0, -4.95, 0.7, -2.4, -4.6, 1), box(-0.4, -2.6, -5.0, 0.4, -2.2, -4.7, 1)]             # nose guard
    for k in range(4):
        cubes += [rot90(box(x, -6.75, -4.85, x + 0.35, -6.4, -4.7, 2), k) for x in (-3.2, -1.6, 1.25, 2.85)]  # rivets
    horn = [box(4.6, -8.6, -0.9, 6.2, -7.0, 0.9, '#EFE6D2'), box(5.6, -10.4, -0.75, 6.9, -8.4, 0.75, '#EFE6D2'),
            box(6.1, -12.0, -0.6, 7.1, -10.3, 0.6, '#E2D6BC'), box(6.0, -13.2, -0.45, 6.8, -11.9, 0.45, '#CFC2A4'),
            box(5.6, -14.0, -0.3, 6.3, -13.1, 0.3, '#B8AA8C'), box(4.55, -8.5, -1.0, 5.0, -7.1, 1.0, 3)]
    cubes += mirror_x(horn)
    parts = [glint(-2.0, -9.7, -3.9, 0.35, 0.1, 1.5)]
    model('viking', 'head', 'Viking Helmet', 'rare', 'head', cubes, parts=parts)


def devil():
    horn = [box(1.7, -9.0, -1.3, 3.3, -8.0, 0.3, 0), box(2.0, -10.0, -1.15, 3.3, -9.0, 0.15, 0), box(2.4, -10.9, -1.0, 3.4, -10.0, 0.0, 1),
            box(2.8, -11.7, -0.85, 3.5, -10.9, -0.15, 1), box(3.1, -12.3, -0.7, 3.6, -11.7, -0.3, 2)]
    cubes = mirror_x(horn)
    parts = mirror_parts([glint(3.35, -12.4, -0.5, 0.35, 0.13, 0.0, '#FFD24A')], 2.0)
    model('devil', 'head', 'Little Horns', 'common', 'head', cubes, parts=parts)


def ears(mid, name, tier, outer, inner, tip=None, height=1.0, tuft=False):
    s = height
    ear = [box(1.1, -9.0, -1.0, 4.0, -8.0, 0.5, outer), box(1.5, -8.0 - 2.0 * s, -1.0, 3.7, -9.0, 0.5, outer),
           box(1.9, -8.0 - 3.0 * s, -1.0, 3.4, -8.0 - 2.0 * s, 0.5, outer), box(2.3, -8.0 - 3.8 * s, -1.0, 3.1, -8.0 - 3.0 * s, 0.5, tip if tip is not None else outer),
           box(1.8, -8.0 - 2.4 * s, -1.12, 3.3, -8.5, -0.95, inner), box(2.2, -8.0 - 3.1 * s, -1.12, 2.9, -8.0 - 2.4 * s, -0.95, inner)]
    if tuft:
        ear += [box(1.9, -9.2, -1.2, 2.6, -8.3, -1.05, W), box(2.6, -9.6, -1.2, 3.2, -8.6, -1.05, W)]
    right = P(ear, A('rot', axis='z', pivot=[2.5, -8.0, 0], amp=4, speed=0.31))
    model(mid, 'head', name, tier, 'head', [], parts=mirror_parts([right], 1.9))


def bunny_ears():
    ear = [box(1.2, -10.6, -0.65, 3.2, -8.0, 0.65, 0), box(1.7, -10.4, -0.8, 2.7, -8.6, -0.65, PINK)]
    top = [box(1.25, -15.4, -0.6, 3.15, -10.6, 0.6, 0), box(1.75, -14.9, -0.75, 2.65, -10.6, -0.6, PINK), box(1.5, -15.8, -0.5, 2.9, -15.4, 0.5, 0)]
    right = P(ear, A('rot', axis='z', pivot=[2.2, -8.0, 0], amp=3, speed=0.2),
              [P(top, A('rot', axis='x', pivot=[2.2, -10.6, 0], amp=8, speed=0.2, phase=-0.8, base=6))])
    model('bunny_ears', 'head', 'Bunny Ears', 'rare', 'head', [], parts=mirror_parts([right], 1.4))


def antlers():
    a = [box(2.0, -9.2, -0.5, 3.0, -8.0, 0.5, 1), box(2.3, -11.6, -0.45, 3.3, -9.2, 0.45, 1), box(3.3, -10.9, -0.4, 5.6, -10.1, 0.4, 1),
         box(4.8, -12.8, -0.35, 5.6, -10.9, 0.35, 1), box(5.6, -12.4, -0.3, 6.6, -11.8, 0.3, 1), box(2.5, -14.2, -0.4, 3.3, -11.6, 0.4, 1),
         box(1.2, -13.4, -0.35, 2.5, -12.6, 0.35, 1), box(3.3, -15.0, -0.3, 4.2, -14.2, 0.3, 1), box(2.6, -15.4, -0.3, 3.3, -14.2, 0.3, 1)]
    a += [box(2.3, -11.0, -0.5, 3.3, -10.6, 0.5, 3), box(2.5, -13.2, -0.45, 3.3, -12.8, 0.45, 3)]               # velvet rings
    flower = ball(4.9, -13.2, 0, 0.42, 2) + [box(4.75, -13.35, -0.5, 5.05, -13.05, -0.4, GOLD)]
    cubes = mirror_x(a)
    parts = mirror_parts([P(flower, A('pulse', pivot=[4.9, -13.2, 0], amp=0.12, speed=0.15))], 2.5)
    model('antlers', 'head', 'Antlers', 'rare', 'head', cubes, parts=parts)


def flower_crown():
    cubes = ring(-8.1, -7.5, 0, 1, 0.3, 0.45)
    cubes += all_sides([box(-3.4, -8.25, -4.6, -2.4, -7.85, -4.25, LEAF_G), box(0.9, -8.2, -4.62, 1.9, -7.8, -4.27, LEAF_G)])
    parts = []
    spots = [(-4.0, -4.0), (-1.2, -4.45), (1.8, -4.45), (4.0, -4.0), (4.45, -1.0), (4.45, 2.0), (4.0, 4.0), (1.0, 4.45), (-2.0, 4.45), (-4.0, 4.0), (-4.45, 1.0), (-4.45, -2.0)]
    for i, (x, z) in enumerate(spots):
        col = 0 if i % 2 == 0 else 2
        petals = [box(x - 0.85, -8.6, z - 0.35, x + 0.85, -8.0, z + 0.35, col), box(x - 0.35, -8.6, z - 0.85, x + 0.35, -8.0, z + 0.85, col),
                  box(x - 0.6, -8.75, z - 0.6, x + 0.6, -8.45, z + 0.6, col)]
        center = [box(x - 0.3, -8.95, z - 0.3, x + 0.3, -8.6, z + 0.3, '#FFE066')]
        if i % 3 == 0:
            parts.append(P(petals + center, A('pulse', pivot=[x, -8.3, z], amp=0.1, speed=0.12, phase=i * 0.9)))
        else:
            cubes += petals + center
    model('flower_crown', 'head', 'Flower Crown', 'rare', 'head', cubes, parts=parts)


def headband():
    cubes = ring(-6.7, -5.4, 0, 0, 0.25)
    cubes += [box(-4.3, -6.25, -4.4, 4.3, -5.85, -4.25, 2)]
    cubes += [box(-0.7, -6.7, -4.5, 0.7, -5.3, -4.3, 1), box(-0.3, -6.3, -4.6, 0.3, -5.7, -4.5, 2)]              # emblem
    cubes += [box(-1.3, -6.9, 4.2, 1.3, -5.2, 4.9, 1)]                                                            # knot
    tails = [P([box(0.3, -5.4, 4.4, 1.3, -2.4, 4.7, 0), box(0.4, -2.6, 4.45, 1.2, -2.2, 4.65, 1)], A('rot', axis='x', pivot=[0.8, -5.4, 4.6], amp=9, speed=0.16, base=12)),
             P([box(-1.3, -5.4, 4.4, -0.3, -3.0, 4.7, 0), box(-1.2, -3.2, 4.45, -0.4, -2.8, 4.65, 1)], A('rot', axis='x', pivot=[-0.8, -5.4, 4.6], amp=9, speed=0.16, phase=1.3, base=8))]
    model('headband', 'head', 'Headband', 'common', 'head', cubes, parts=tails)


def pirate_bandana():
    cubes = rbox(-4.45, -8.7, -4.45, 4.45, -6.8, 4.45, 0, r=0.4) + [box(-4.5, -7.05, -4.5, 4.5, -6.2, 4.5, 0), box(-4.55, -6.5, -4.55, 4.55, -6.2, 4.55, 1)]
    cubes += [box(x, -8.78, z, x + 0.8, -8.6, z + 0.8, 2) for x, z in [(-3, -3), (0, -2), (2.4, -3.2), (-2, 1), (1.4, 1.6), (-0.4, 3)]]
    cubes += all_sides([box(-2.6, -7.9, -4.55, -1.8, -7.1, -4.46, 2), box(1.6, -8.2, -4.55, 2.4, -7.4, -4.46, 2)])
    cubes += [box(-1.2, -7.4, 4.4, 1.2, -5.6, 5.2, 0)]
    tails = [P([box(0.2, -5.8, 4.8, 1.4, -3.2, 5.2, 0), box(0.3, -3.4, 4.85, 1.3, -3.0, 5.15, 2)], A('rot', axis='x', pivot=[0.8, -5.8, 5.0], amp=8, speed=0.15, base=10)),
             P([box(-1.4, -5.8, 4.8, -0.4, -3.6, 5.2, 0)], A('rot', axis='x', pivot=[-0.9, -5.8, 5.0], amp=8, speed=0.15, phase=1.1, base=6))]
    model('pirate_bandana', 'head', 'Head Bandana', 'common', 'head', cubes, parts=tails)


def propeller():
    cubes = []
    for k, col in enumerate([0, 1, 0, 1]):
        cubes += [rot90(box(-4.45, -9.0, -4.45, 0.0, -7.0, 0.0, col), k)]
    cubes += rbox(-3.5, -9.7, -3.5, 3.5, -8.9, 3.5, 0, r=0.4)
    cubes += [box(-4.1, -7.25, -7.2, 4.1, -6.85, -4.4, 2), box(-4.15, -7.3, -7.3, 4.15, -6.85, -7.0, 1)]
    cubes += [box(-0.35, -11.0, -0.35, 0.35, -9.6, 0.35, 3)]
    blades = [box(-4.8, -11.35, -0.6, 4.8, -10.95, 0.6, 2), box(-0.6, -11.35, -4.8, 0.6, -10.95, 4.8, 2),
              box(3.6, -11.4, -0.65, 4.8, -10.9, 0.65, 0), box(-4.8, -11.4, -0.65, -3.6, -10.9, 0.65, 1),
              box(-0.65, -11.4, 3.6, 0.65, -10.9, 4.8, 0), box(-0.65, -11.4, -4.8, 0.65, -10.9, -3.6, 1)]
    blades += ball(0, -11.3, 0, 0.55, 3)
    parts = [P(blades, A('spin', pivot=[0, 0, 0], speed=18))]
    model('propeller', 'head', 'Propeller Cap', 'rare', 'head', cubes, parts=parts)


def chef():
    cubes = rbox(-4.3, -10.0, -4.3, 4.3, -8.0, 4.3, W, r=0.3)
    for k in range(4):
        cubes += [rot90(box(x, -9.9, -4.42, x + 0.3, -8.1, -4.3, '#E6E6EC'), k) for x in (-3.2, -1.6, 0.0, 1.6, 3.2)]  # pleats
    cubes += ring(-9.0, -8.0, 0, 0, 0.45, 0.4)
    puffs = [(0, -12.4, 0, 2.6), (-2.4, -12.0, -1.6, 2.0), (2.4, -12.1, -1.4, 2.0), (-2.2, -12.2, 2.0, 2.0), (2.3, -12.0, 2.0, 2.0), (0, -14.2, 0.2, 2.1)]
    for x, y, z, r in puffs:
        cubes += ball(x, y, z, r, W)
    model('chef', 'head', 'Chef Hat', 'common', 'head', cubes)


def winter_hat():
    cubes = rbox(-4.7, -8.9, -4.7, 4.7, -7.3, 4.7, W, r=0.5)
    cubes += [box(x, -9.0, -4.8, x + 0.4, -7.2, -4.7, '#EDEDF2') for x in (-3.5, -1.5, 0.5, 2.5)]
    cubes += rbox(-4.0, -11.2, -4.0, 4.0, -8.8, 4.0, 0, r=0.5) + rbox(-3.0, -12.6, -2.6, 3.0, -11.0, 3.4, 0, r=0.4)
    cubes += ring(-10.2, -9.6, 0, 2, 0.05, 0.4)
    tail = [box(-2.0, -13.6, -0.6, 2.0, -12.4, 4.4, 0), box(-2.05, -13.2, 1.6, 2.05, -12.8, 2.2, 2)]
    tail2 = [box(-1.3, -13.3, 4.0, 1.3, -11.0, 5.8, 0)]
    pom = ball(0, -10.0, 6.4, 1.3, W)
    parts = [P(tail, A('rot', axis='x', pivot=[0, -13.0, -0.6], amp=4, speed=0.12),
               [P(tail2, A('rot', axis='x', pivot=[0, -12.8, 4.2], amp=10, speed=0.12, phase=-0.8),
                  [P(pom, A('bob', amp=0.4, speed=0.24))])])]
    model('winter_hat', 'head', 'Winter Hat', 'common', 'head', cubes, parts=parts)


def beret():
    cubes = rbox(-5.3, -9.5, -4.7, 4.5, -8.2, 5.1, 0, r=0.5) + rbox(-4.3, -10.3, -3.7, 3.5, -9.3, 4.1, 0, r=0.5)
    cubes += [box(-4.6, -8.35, -4.65, 4.6, -7.9, 4.65, 1)]
    cubes += [box(-0.3, -11.0, -0.3, 0.3, -10.3, 0.3, 3)]
    cubes += [box(2.2, -9.6, -4.75, 3.6, -8.6, -4.6, 2), box(2.6, -9.3, -4.8, 3.2, -8.9, -4.72, 3)]   # pin
    parts = [glint(2.9, -9.1, -4.9, 0.3, 0.12, 0.6)]
    model('beret', 'head', 'Beret', 'common', 'head', cubes, parts=parts)


def hair_bow():
    bow = [box(0.9, -10.1, -1.0, 2.3, -8.5, 1.0, 1)] + rbox(2.3, -11.2, -1.3, 4.9, -8.3, 0.7, 0, r=0.35) + rbox(-1.7, -11.2, -1.3, 0.9, -8.3, 0.7, 0, r=0.35)
    bow += [box(3.0, -10.6, -1.42, 4.2, -9.0, -1.28, 2), box(-1.0, -10.6, -1.42, 0.2, -9.0, -1.28, 2)]
    tails = [box(1.0, -8.5, -0.4, 1.6, -6.6, 0.2, 0), box(1.7, -8.5, -0.4, 2.3, -7.0, 0.2, 0)]
    parts = [P(bow + tails, A('rot', axis='z', pivot=[1.6, -9.3, 0], amp=4, speed=0.14))]
    model('hair_bow', 'head', 'Hair Bow', 'common', 'head', [], parts=parts)


crown()
tophat()
halo()
party()
comb()
beanie()
cap()
cowboy()
wizard()
witch()
viking()
devil()
ears('cat_ears', 'Cat Ears', 'common', 0, 2)
ears('fox_ears', 'Fox Ears', 'rare', 0, W, tip=3, height=1.25, tuft=True)
bunny_ears()
antlers()
flower_crown()
headband()
pirate_bandana()
propeller()
chef()
winter_hat()
beret()
hair_bow()

HEAD_MODELS = ['crown', 'tophat', 'halo', 'party', 'comb', 'beanie', 'cap', 'cowboy', 'wizard', 'witch', 'viking', 'devil', 'cat_ears',
               'fox_ears', 'bunny_ears', 'antlers', 'flower_crown', 'headband', 'pirate_bandana', 'propeller', 'chef', 'winter_hat', 'beret', 'hair_bow']

# ---------------------------------------------------------------- face (head anchor; the face is at z -4)


def bandana_base():
    cubes = [box(-4.45, -4.0, -4.62, 4.45, -0.6, -4.1, 0), box(-3.2, -0.6, -4.62, 3.2, 0.4, -4.1, 0), box(-1.9, 0.4, -4.62, 1.9, 1.2, -4.1, 0),
             box(-0.8, 1.2, -4.62, 0.8, 1.9, -4.1, 0)]
    cubes += [box(-4.5, -4.1, -4.66, 4.5, -3.75, -4.55, 1)]                                         # hem
    cubes += [box(-2.8, -2.6, -4.66, -1.8, -2.4, -4.6, 1), box(1.6, -1.8, -4.66, 2.8, -1.6, -4.6, 1), box(-0.6, -0.2, -4.66, 0.8, 0.0, -4.6, 1)]  # folds
    cubes += [box(-4.62, -4.0, -4.62, -4.12, -2.4, 4.62, 0), box(4.12, -4.0, -4.62, 4.62, -2.4, 4.62, 0), box(-4.12, -4.0, 4.12, 4.12, -2.4, 4.62, 0)]
    cubes += [box(-0.9, -3.7, 4.6, 0.9, -1.7, 5.3, 1)]
    tails = [P([box(0.1, -2.0, 4.9, 1.1, 0.8, 5.25, 0), box(0.2, 0.6, 4.95, 1.0, 1.0, 5.2, 1)], A('rot', axis='x', pivot=[0.6, -2.0, 5.1], amp=10, speed=0.17, base=14)),
             P([box(-1.1, -2.0, 4.9, -0.1, 0.2, 5.25, 0)], A('rot', axis='x', pivot=[-0.6, -2.0, 5.1], amp=10, speed=0.17, phase=1.2, base=9))]
    return cubes, tails


def bandanas():
    cubes, tails = bandana_base()
    model('bandana', 'face', 'Bandana', 'common', 'head', cubes, parts=tails)
    dots = [box(x, y, -4.75, x + 0.7, y + 0.7, -4.6, 2) for x, y in [(-3.4, -3.4), (-1.0, -3.0), (1.6, -3.5), (3.0, -2.2), (-2.4, -1.6), (0.4, -1.4), (2.0, -0.4), (-0.4, 0.6)]]
    model('bandana_dots', 'face', 'Dotted Bandana', 'common', 'head', cubes + dots, parts=tails)
    stripes = [box(-4.48, -3.2, -4.75, 4.48, -2.6, -4.6, 2), box(-3.2, -1.6, -4.75, 3.2, -1.0, -4.6, 2), box(-1.6, 0.4, -4.75, 1.6, 0.8, -4.6, 2)]
    model('bandana_stripes', 'face', 'Striped Bandana', 'common', 'head', cubes + stripes, parts=tails)


def ninja():
    cubes = [box(-4.5, -3.6, -4.5, 4.5, 0.3, 4.5, 0), box(-4.5, -8.3, -4.5, 4.5, -5.6, 4.5, 0), box(-4.5, -5.6, -4.5, -3.4, -3.6, -4.1, 0),
             box(-0.6, -5.6, -4.5, 0.6, -3.6, -4.1, 0), box(3.4, -5.6, -4.5, 4.5, -3.6, -4.1, 0)]
    cubes += [box(-4.62, -6.8, -4.62, 4.62, -6.0, 4.62, 1), box(-1.0, -7.0, -4.75, 1.0, -5.8, -4.6, 2), box(-0.5, -6.7, -4.82, 0.5, -6.1, -4.72, 3)]
    cubes += [box(-3.6, -2.4, -4.56, 3.6, -2.2, -4.5, 1), box(-3.0, -1.2, -4.56, 3.0, -1.0, -4.5, 1)]   # wraps
    cubes += [box(-0.8, -7.0, 4.6, 0.8, -5.8, 5.2, 1)]
    tails = [P([box(0.1, -6.4, 5.0, 1.0, -2.0, 5.3, 1)], A('rot', axis='x', pivot=[0.5, -6.4, 5.1], amp=12, speed=0.2, base=18)),
             P([box(-1.0, -6.4, 5.0, -0.1, -2.6, 5.3, 1)], A('rot', axis='x', pivot=[-0.5, -6.4, 5.1], amp=12, speed=0.2, phase=1.4, base=12))]
    model('ninja', 'face', 'Ninja Mask', 'rare', 'head', cubes, parts=tails)


def round_glasses():
    rim = [box(0.8, -5.3, -4.5, 3.6, -4.85, -4.2, 3), box(0.8, -3.15, -4.5, 3.6, -2.7, -4.2, 3), box(0.5, -4.95, -4.5, 0.95, -3.05, -4.2, 3),
           box(3.45, -4.95, -4.5, 3.9, -3.05, -4.2, 3), box(1.0, -4.85, -4.4, 3.45, -3.15, -4.32, 2),
           box(3.9, -4.5, -4.45, 4.55, -4.05, 2.0, 3), box(4.2, -4.5, 1.6, 4.55, -3.2, 2.0, 3)]
    rim += [box(1.2, -4.75, -4.45, 1.8, -4.35, -4.41, W)]                     # reflection
    cubes = mirror_x(rim) + [box(-0.55, -4.5, -4.5, 0.55, -4.05, -4.2, 3)]
    parts = mirror_parts([glint(1.4, -4.6, -4.6, 0.35, 0.09, 0.0)], 2.8)
    model('round_glasses', 'face', 'Round Glasses', 'common', 'head', cubes, parts=parts)


def shades():
    lens = [box(0.6, -5.3, -4.5, 4.0, -2.9, -4.2, 3), box(0.8, -5.1, -4.56, 3.8, -4.1, -4.5, 1), box(0.8, -4.1, -4.56, 3.8, -3.1, -4.5, 3),
            box(0.6, -5.45, -4.55, 4.0, -5.2, -4.2, 0), box(4.0, -5.1, -4.5, 4.55, -4.5, 2.0, 0)]
    cubes = mirror_x(lens) + [box(-0.6, -4.9, -4.55, 0.6, -4.4, -4.2, 0)]
    parts = [glint(-1.4, -4.8, -4.65, 0.4, 0.08, 0.0), glint(2.6, -4.8, -4.65, 0.4, 0.08, 2.4)]
    model('shades', 'face', 'Shades', 'rare', 'head', cubes, parts=parts)


def visor():
    cubes = [box(-4.4, -5.4, -4.6, 4.4, -3.4, -4.25, 0, True), box(-4.4, -5.45, -4.68, 4.4, -5.25, -4.55, 2, True),
             box(-4.65, -5.65, -4.65, -4.1, -3.15, 3.0, 3), box(4.1, -5.65, -4.65, 4.65, -3.15, 3.0, 3), box(-4.65, -5.65, -4.65, 4.65, -5.4, -4.25, 3),
             box(-4.65, -3.4, -4.65, 4.65, -3.15, -4.25, 3)]
    cubes += mirror_x([box(4.55, -5.0, -1.2, 5.0, -3.6, 0.6, 1), box(4.95, -4.6, -0.8, 5.15, -4.0, 0.2, 2, True)])
    scan = P([box(-0.35, -5.25, -4.72, 0.35, -3.55, -4.62, W, True)], A('slide', axis='x', amp=3.6, speed=0.09))
    leds = mirror_parts([P([box(4.98, -4.95, 0.3, 5.12, -4.75, 0.5, '#FF4D5E', True)], A('twinkle', pivot=[5.05, -4.85, 0.4], speed=0.25))], 1.6)
    model('visor', 'face', 'Cyber Visor', 'epic', 'head', cubes, parts=[scan] + leds)


def goggles():
    cubes = ring(-7.5, -6.4, 0, 3, 0.3)
    eye_ = [box(0.5, -8.1, -5.1, 3.5, -5.7, -4.3, 1), box(0.3, -8.3, -4.9, 3.7, -5.5, -4.3, 1), box(0.9, -7.7, -5.35, 3.1, -6.1, -5.1, 2, True),
            box(1.2, -7.5, -5.42, 1.8, -7.0, -5.36, W, True), box(3.5, -7.2, -4.7, 4.3, -6.6, -4.3, 1)]
    eye_ += [box(0.4, -8.2, -5.15, 0.75, -7.85, -5.05, SILVER), box(3.25, -8.2, -5.15, 3.6, -7.85, -5.05, SILVER)]
    cubes += mirror_x(eye_) + [box(-0.5, -7.2, -5.0, 0.5, -6.6, -4.4, 1)]
    parts = mirror_parts([glint(2.6, -7.4, -5.5, 0.35, 0.1, 0.0)], 3.0)
    model('goggles', 'face', 'Goggles', 'rare', 'head', cubes, parts=parts)


def eyepatch():
    cubes = rbox(0.9, -5.5, -4.6, 3.5, -2.9, -4.2, 0, r=0.3)
    cubes += [box(1.5, -4.6, -4.65, 2.9, -4.4, -4.58, 2), box(2.1, -5.1, -4.65, 2.3, -3.6, -4.58, 2)]              # stitched cross
    cubes += [box(-4.55, -6.6, -4.45, 1.0, -6.0, -4.2, 3), box(3.5, -4.6, -4.45, 4.55, -4.0, -4.2, 3),
              box(-4.55, -6.6, -4.45, -4.25, -6.0, 4.55, 3), box(4.25, -4.6, -4.45, 4.55, -4.0, 4.55, 3), box(-4.55, -6.6, 4.25, 4.55, -4.0, 4.55, 3)]
    model('eyepatch', 'face', 'Eyepatch', 'common', 'head', cubes)


def masquerade():
    half = [box(0.4, -5.6, -4.5, 4.2, -3.0, -4.2, 0), box(0.8, -6.4, -4.55, 2.4, -5.6, -4.3, 2), box(3.4, -6.8, -4.55, 4.6, -5.6, -4.3, 2),
            box(4.2, -5.2, -4.55, 5.0, -4.2, -4.3, 0), box(4.2, -4.6, -4.5, 4.5, -4.0, 1.0, 3)]
    half += [box(1.2, -4.7, -4.6, 3.4, -3.6, -4.52, 3)]                              # eye hole shadow
    half += [box(x, -3.15, -4.6, x + 0.3, -2.85, -4.5, GOLD) for x in (0.8, 1.8, 2.8, 3.8)]
    cubes = mirror_x(half) + [box(-0.4, -4.4, -4.6, 0.4, -3.4, -4.3, 2)]
    feathers = [P([box(4.4, -10.0, -4.6, 5.0, -6.4, -4.4, 2), box(4.5, -10.6, -4.58, 4.9, -10.0, -4.42, 0),
                   box(5.0, -9.2, -4.6, 5.6, -6.2, -4.4, 0), box(5.1, -9.8, -4.58, 5.5, -9.2, -4.42, 2)],
                  A('rot', axis='z', pivot=[4.8, -6.4, -4.5], amp=5, speed=0.12))]
    parts = feathers + [glint(0.0, -3.9, -4.75, 0.4, 0.1, 0.4)]
    model('masquerade', 'face', 'Masquerade Mask', 'epic', 'head', cubes, parts=parts)


def mustache():
    half = [box(0.0, -2.6, -4.5, 1.8, -1.8, -4.1, 0), box(1.6, -2.3, -4.5, 2.8, -1.4, -4.1, 0), box(2.6, -2.0, -4.5, 3.4, -1.3, -4.1, 0),
            box(3.1, -2.6, -4.5, 3.7, -1.7, -4.1, 0), box(3.3, -3.0, -4.5, 3.8, -2.5, -4.1, 0), box(0.2, -2.55, -4.56, 1.4, -2.3, -4.5, 2)]
    parts = [P(half, A('rot', axis='z', pivot=[0.2, -2.0, -4.3], amp=4, speed=0.21)),
             mpart(P(half, A('rot', axis='z', pivot=[0.2, -2.0, -4.3], amp=4, speed=0.21)), 0.3)]
    model('mustache', 'face', 'Mustache', 'common', 'head', [], parts=parts)


def heart_glasses():
    heart = [box(0.8, -5.4, -4.5, 1.9, -4.4, -4.2, 0), box(2.3, -5.4, -4.5, 3.4, -4.4, -4.2, 0), box(0.8, -4.4, -4.5, 3.4, -3.6, -4.2, 0),
             box(1.4, -3.6, -4.5, 2.8, -2.9, -4.2, 0), box(1.85, -2.9, -4.5, 2.35, -2.55, -4.2, 0), box(1.0, -5.2, -4.56, 1.5, -4.7, -4.5, W)]
    hp = P(heart, A('pulse', pivot=[2.1, -4.2, -4.35], amp=0.08, speed=0.3))
    frame = mirror_x([box(3.4, -4.6, -4.5, 4.5, -4.2, 2.0, 3)]) + [box(-0.8, -4.6, -4.5, 0.8, -4.2, -4.2, 3)]
    model('heart_glasses', 'face', 'Heart Glasses', 'rare', 'head', frame, parts=mirror_parts([hp], 1.0))


bandanas()
ninja()
round_glasses()
shades()
visor()
goggles()
eyepatch()
masquerade()
mustache()
heart_glasses()
FACE_MODELS = ['bandana', 'bandana_dots', 'bandana_stripes', 'ninja', 'round_glasses', 'shades', 'visor', 'goggles', 'eyepatch', 'masquerade', 'mustache', 'heart_glasses']

# ---------------------------------------------------------------- neck (body anchor, neck at y 0; front is z -2)


def scarf():
    cubes = rbox(-4.5, -0.5, -2.7, 4.5, 1.9, 2.7, 0, r=0.4)
    cubes += [box(-4.55, 0.25, -2.75, 4.55, 0.75, 2.75, 1), box(-4.55, 1.25, -2.75, 4.55, 1.5, 2.75, 2)]
    cubes += rbox(0.9, 1.4, -3.0, 3.5, 3.0, -2.2, 0, r=0.3)                                                       # knot
    tail = [box(1.2, 3.0, -2.95, 3.2, 7.0, -2.35, 0), box(1.15, 4.4, -3.0, 3.25, 5.0, -2.9, 1), box(1.15, 5.6, -3.0, 3.25, 5.9, -2.9, 2)]
    fringe = [box(x, 7.0, -2.85, x + 0.3, 8.0, -2.45, 2) for x in (1.3, 1.85, 2.4, 2.95)]
    tail2 = [box(-0.4, 3.0, -2.6, 1.4, 6.2, -2.1, 1), box(-0.35, 6.2, -2.55, 1.35, 6.9, -2.15, 2)]
    parts = [P(tail + fringe, A('rot', axis='x', pivot=[2.2, 3.0, -2.6], amp=6, speed=0.11, base=-4)),
             P(tail2, A('rot', axis='x', pivot=[0.5, 3.0, -2.4], amp=5, speed=0.11, phase=1.1, base=-3))]
    model('scarf', 'neck', 'Scarf', 'common', 'body', cubes, parts=parts)


def neck_bandana():
    cubes = rbox(-4.5, -0.3, -2.7, 4.5, 1.1, 2.7, 0, r=0.3)
    cubes += [box(-0.9, -0.4, 2.6, 0.9, 1.0, 3.3, 1)]
    tri = [box(-3.5, 1.0, -2.8, 3.5, 2.4, -2.15, 0), box(-2.3, 2.4, -2.8, 2.3, 3.6, -2.15, 0), box(-1.1, 3.6, -2.8, 1.1, 4.6, -2.15, 0),
           box(-0.4, 4.6, -2.8, 0.4, 5.1, -2.15, 0), box(-0.6, 1.4, -2.88, 0.6, 2.0, -2.78, 2), box(-1.7, 2.8, -2.88, -0.5, 3.2, -2.78, 2),
           box(0.5, 2.8, -2.88, 1.7, 3.2, -2.78, 2), box(-0.3, 3.9, -2.88, 0.3, 4.3, -2.78, 2)]
    parts = [P(tri, A('rot', axis='x', pivot=[0, 1.0, -2.5], amp=3, speed=0.13, base=-3))]
    model('neck_bandana', 'neck', 'Neck Bandana', 'common', 'body', cubes, parts=parts)


def bowtie():
    bow = [box(-0.65, 0.35, -2.85, 0.65, 1.65, -2.2, 1)] + mirror_x(rbox(0.65, -0.1, -2.75, 2.7, 2.1, -2.2, 0, r=0.3))
    bow += mirror_x([box(1.1, 0.5, -2.82, 2.2, 0.75, -2.74, 2), box(1.1, 1.35, -2.82, 2.2, 1.6, -2.74, 2)])
    parts = [P(bow, A('rot', axis='z', pivot=[0, 1.0, -2.5], amp=3, speed=0.15))]
    model('bowtie', 'neck', 'Bow Tie', 'common', 'body', [box(-4.3, -0.05, -2.3, 4.3, 0.4, 2.3, '#F4F4F8')], parts=parts)


def tie():
    collar = mirror_x([box(0.2, -0.2, -2.35, 2.6, 1.2, -2.1, '#F4F4F8')])
    blade = [box(-0.85, 0.0, -2.65, 0.85, 1.3, -2.15, 1), box(-0.75, 1.3, -2.55, 0.75, 2.6, -2.15, 0), box(-0.95, 2.6, -2.55, 0.95, 6.6, -2.15, 0),
             box(-0.75, 6.6, -2.55, 0.75, 7.6, -2.15, 0), box(-0.4, 7.6, -2.55, 0.4, 8.2, -2.15, 0)]
    blade += [box(-0.97, y, -2.6, 0.97, y + 0.45, -2.5, 2) for y in (3.0, 4.4, 5.8)]
    blade += [box(-0.6, 2.2, -2.62, 0.6, 2.5, -2.5, SILVER)]                                                     # tie clip
    parts = [P(blade, A('rot', axis='x', pivot=[0, 1.3, -2.4], amp=3, speed=0.12, base=-2))]
    model('tie', 'neck', 'Tie', 'common', 'body', collar, parts=parts)


def necklace():
    chain = []
    for x in [-3.0, -2.2, -1.4, -0.6]:
        chain.append(box(x, 0.0 + (x + 3) * 0.12, -2.4, x + 0.5, 0.35 + (x + 3) * 0.12, -2.1, 2))
    chain = mirror_x(chain)
    chain += [box(-3.4, 0.0, -2.1, -3.0, 0.4, 2.1, 2), box(3.0, 0.0, -2.1, 3.4, 0.4, 2.1, 2)]
    pend = [box(-0.15, 0.4, -2.45, 0.15, 1.6, -2.15, 2), box(-0.4, 1.4, -2.5, 0.4, 1.8, -2.15, 2)]
    pend += rbox(-1.1, 1.8, -2.7, 1.1, 4.0, -2.1, 2, r=0.35)
    pend += [box(-0.7, 2.2, -2.82, 0.7, 3.6, -2.65, 0, True), box(-0.35, 2.4, -2.88, 0.05, 2.8, -2.8, W, True)]
    core = P([box(-0.3, 2.6, -2.92, 0.3, 3.2, -2.84, 2, True)], A('pulse', pivot=[0, 2.9, -2.88], amp=0.3, speed=0.2))
    parts = [P(pend, A('rot', axis='z', pivot=[0, 0.4, -2.3], amp=5, speed=0.13), [core, glint(0.6, 2.2, -2.95, 0.35, 0.1, 1.0)])]
    model('necklace', 'neck', 'Pendant', 'rare', 'body', chain, parts=parts)


def medal():
    ribbon = [box(-2.7, -0.05, -2.45, -1.3, 3.1, -2.1, 0), box(1.3, -0.05, -2.45, 2.7, 3.1, -2.1, 1),
              box(-2.2, -0.05, -2.5, -1.8, 3.1, -2.42, 2), box(1.8, -0.05, -2.5, 2.2, 3.1, -2.42, 2)]
    hang = [box(-1.7, 2.6, -2.6, 1.7, 4.0, -2.1, 0), box(-1.75, 3.2, -2.66, 1.75, 3.45, -2.55, 2)]
    hang += rbox(-1.6, 4.0, -2.8, 1.6, 7.0, -2.1, GOLD, r=0.4)
    hang += [box(-1.15, 4.4, -2.88, 1.15, 6.6, -2.78, GOLD_D)]
    star = [(0, 4.7, 0.25, 0.6), (0, 5.4, 0.9, 0.3), (0, 6.0, 0.6, 0.25)]
    hang += [box(x - w, y, -2.95, x + w, y + h, -2.87, '#FFF2BC') for x, y, w, h in star]
    parts = [P(hang, A('rot', axis='z', pivot=[0, 2.6, -2.3], amp=4, speed=0.15), [glint(0.9, 4.8, -3.0, 0.4, 0.1, 0.8)])]
    model('medal', 'neck', 'Medal', 'rare', 'body', ribbon, parts=parts)


scarf()
neck_bandana()
bowtie()
tie()
necklace()
medal()
NECK_MODELS = ['scarf', 'neck_bandana', 'bowtie', 'tie', 'necklace', 'medal']

# ---------------------------------------------------------------- wings (right wing, mirrored; extends to +x from the pivot)
# Wing space: x runs outwards from the shoulder, y down, z backwards (the side you see from behind is +z).
# Wings are built in segments (shoulder → elbow → wrist → tips) that are nested parts, so the outer segments
# trail behind the flap; single flight feathers and finger bones are parts of their own and flutter.

FLAP = {'type': 'flap', 'pivot': [1.2, 2.0, 2.2], 'base': 28, 'range': 10, 'speed': 0.12}


def shift(cubes, dx, dy, dz=0.0):
    return [[round(c[0] + dx, 3), round(c[1] + dy, 3), round(c[2] + dz, 3), round(c[3] + dx, 3), round(c[4] + dy, 3), round(c[5] + dz, 3), c[6], c[7]]
            for c in cubes]


def feather(x, y, w, length, z, body, tip, shaft, glow=False):
    """One hanging feather (top edge at y) with a rounded tip and a shaft line on the visible (+z) side."""
    z2 = z + 0.42
    cubes = [box(x, y, z, x + w, y + length - 1.1, z2, body, glow),
             box(x + 0.1, y + length - 1.1, z, x + w - 0.1, y + length - 0.45, z2, tip, glow),
             box(x + 0.3, y + length - 0.45, z + 0.05, x + w - 0.3, y + length, z2 - 0.05, tip, glow)]
    if length > 1.6:
        cubes.append(box(x + w / 2 - 0.07, y + 0.25, z2, x + w / 2 + 0.07, y + length - 0.7, z2 + 0.05, shaft, glow))
    return cubes


def feather_row(x0, x1, y, length_at, z, body, tip, shaft, step=0.9, w=1.0, glow=False):
    """A row of overlapping feathers; every other one sits a little lower and further back."""
    out = []
    x = x0
    i = 0
    while x < x1 - 0.25:
        ww = min(w, x1 - x)
        out += feather(x, y + (0.22 if i % 2 else 0.0), ww, length_at(x), z + (0.05 if i % 2 else 0.0), body, tip, shaft, glow)
        x += step
        i += 1
    return out


def flight_feather(length, z, body, tip, shaft, glow=False):
    """A long flight feather lying along +x from its root at (0, 0)."""
    z2 = z + 0.4
    return [box(0, -0.62, z, length * 0.6, 0.68, z2, body, glow),
            box(length * 0.6, -0.52, z, length * 0.85, 0.58, z2, body, glow),
            box(length * 0.85, -0.38, z, length * 0.96, 0.4, z2, tip, glow),
            box(length * 0.96, -0.16, z + 0.05, length * 1.03, 0.16, z2 - 0.05, tip, glow),
            box(0.15, -0.07, z2, length * 0.93, 0.07, z2 + 0.05, shaft, glow)]


def feather_wing(mid, name, tier, span, drop, anim, flights=6, glints=(), rows=2, glow=False, shaft=8):
    j1 = span * 0.36
    j2 = span * 0.64
    sp = anim['speed']
    sec = lambda x: drop * (1 - 0.24 * x / j2)
    # shoulder segment: bone, two rows of coverts, inner secondaries
    seg1 = [box(0, -0.75, 0.15, j1, 0.45, 0.85, 6)] + ball(j1, -0.15, 0.5, 0.55, 6)
    seg1 += feather_row(0.1, j1, -0.45, lambda x: 2.3, 0.6, 4, 0, shaft, glow=glow)
    if rows > 1:
        seg1 += feather_row(0.5, j1, 1.0, lambda x: 2.7, 0.45, 0, 5, shaft, glow=glow)
    if rows > 2:
        seg1 += feather_row(0.2, j1, 2.6, lambda x: 2.4, 0.32, 0, 4, shaft, glow=glow)
    seg1 += feather_row(0.1, j1, 1.6, sec, 0.12, 0, 5, shaft, step=1.0, w=1.05, glow=glow)
    # elbow segment
    seg2 = [box(j1 - 0.1, -0.65, 0.15, j2, 0.35, 0.85, 6)] + ball(j2, -0.15, 0.5, 0.5, 6)
    seg2 += feather_row(j1, j2, -0.35, lambda x: 2.1, 0.6, 4, 0, shaft, glow=glow)
    if rows > 1:
        seg2 += feather_row(j1 + 0.4, j2, 0.95, lambda x: 2.5, 0.45, 0, 5, shaft, glow=glow)
    if rows > 2:
        seg2 += feather_row(j1 + 0.2, j2, 2.4, lambda x: 2.2, 0.32, 0, 4, shaft, glow=glow)
    seg2 += feather_row(j1, j2 + 0.6, 1.5, sec, 0.12, 0, 5, shaft, step=1.0, w=1.05, glow=glow)
    # wrist: a fan of long flight feathers, each fluttering a little after the one above
    fan = []
    reach = span * 0.72
    for k in range(flights):
        f = k / max(1, flights - 1)
        length = reach * (1 - 0.38 * f)
        body = 1 if k % 2 == 0 else 7
        cubes = shift(flight_feather(length, 0.05 + 0.04 * (flights - 1 - k), body, 7, shaft, glow), j2, 0.3)
        fan.append(P(cubes, A('rot', axis='z', pivot=[j2, 0.3, 0.3], base=-4 + 64 * f, amp=2.5, speed=sp * 1.4, phase=-0.45 * k)))
    alula = shift(flight_feather(2.6, 0.7, 4, 0, shaft, glow), j2 - 0.2, -0.4)
    fan.append(P(alula, A('rot', axis='z', pivot=[j2 - 0.2, -0.4, 0.8], base=-14, amp=3, speed=sp * 1.6, phase=-0.3)))
    wrist = P([], A('rot', axis='y', pivot=[j2, 0, 0.45], amp=10, speed=sp, phase=-1.8), fan + [glint(x, y, 1.05, 0.42, 0.1, i * 1.7) for i, (x, y) in enumerate(glints)])
    elbow = P(seg2, A('rot', axis='y', pivot=[j1, 0, 0.45], amp=7, speed=sp, phase=-0.9), [wrist])
    model(mid, 'back', name, tier, 'body', seg1, anim=anim, kind='wings', parts=[elbow])


feather_wing('wings_feather', 'Feather Wings', 'rare', 11.5, 6.2, dict(FLAP), flights=7)
feather_wing('wings_angel', 'Angel Wings', 'epic', 14.5, 7.8, dict(FLAP, base=24, range=8), flights=9, rows=3,
             glints=[(19.0, 1.0), (16.5, 8.5), (12.0, 12.0)], shaft=GOLD)


def membrane(x0, y0, a1, l1, a2, l2, col, edge, z=0.24, step=0.6, scales=None):
    """Membrane between two bones that start at (x0, y0) and point a1 and a2 degrees below +x, with a
    scalloped trailing edge between their tips. Optional scales: (colour, every nth strip)."""
    r1, r2 = math.radians(a1), math.radians(a2)
    x1e, x2e = l1 * math.cos(r1), l2 * math.cos(r2)
    y1e, y2e = l1 * math.sin(r1), l2 * math.sin(r2)
    out = []
    x = 0.15
    n = 0
    while x < x1e - 0.05:
        xe = min(x + step, x1e)
        xm = (x + xe) / 2
        top = xm * math.tan(r1) + 0.15
        if xm <= x2e:
            bot = xm * math.tan(r2) - 0.15
        else:
            f = (xm - x2e) / max(0.01, x1e - x2e)
            bot = y2e + (y1e - y2e) * f - 1.1 * math.sin(f * math.pi)
        if bot - top > 0.2:
            out.append(box(x0 + x, y0 + top, z, x0 + xe, y0 + bot - 0.3, z + 0.28, col))
            out.append(box(x0 + x, y0 + bot - 0.3, z, x0 + xe, y0 + bot, z + 0.28, edge))
            if scales and n % scales[1] == 1 and bot - top > 1.2:
                ym = (top + bot) / 2
                out.append(box(x0 + x + 0.1, y0 + ym - 0.25, z + 0.28, x0 + xe - 0.1, y0 + ym + 0.25, z + 0.36, scales[0]))
        x = xe
        n += 1
    return out


def finger(x0, y0, length, angle, claw, sp, k, spikes=False):
    bone = [box(x0, y0 - 0.25, 0.12, x0 + length, y0 + 0.25, 0.68, 3)] + ball(x0 + length * 0.55, y0, 0.4, 0.33, 3)
    bone += [box(x0 + length, y0 - 0.2, 0.15, x0 + length + 0.9, y0 + 0.2, 0.65, claw), box(x0 + length + 0.9, y0 - 0.1, 0.25, x0 + length + 1.4, y0 + 0.1, 0.55, claw)]
    if spikes:
        bone += [box(x0 + length * 0.3, y0 - 0.85, 0.25, x0 + length * 0.3 + 0.4, y0 - 0.25, 0.55, claw)]
    return P(bone, A('rot', axis='z', pivot=[x0, y0, 0.4], base=angle, amp=1.5, speed=sp * 1.2, phase=-0.35 * k))


def bat_wing(mid, name, tier, span, anim, dragon=False):
    sp = anim['speed']
    j = span * 0.4
    reach = span - j + 1.2
    claw = 2 if dragon else 3
    scales = (4, 3) if dragon else None
    # arm and the inner membrane down to the body
    seg1 = [box(0, -0.45, 0.1, j, 0.45, 0.72, 3)] + ball(j, 0, 0.4, 0.55, 3)
    seg1 += membrane(0, 0.2, 2, j / math.cos(math.radians(2)), 60, 12.5, 0, 5, scales=scales)
    if dragon:
        for x in [0.8, 2.2, 3.6, 5.0]:
            seg1 += [box(x, -1.25, 0.25, x + 0.55, -0.45, 0.6, 2), box(x + 0.12, -1.75, 0.3, x + 0.42, -1.25, 0.55, 2)]
    # forearm with fingers and the membranes between them
    angles = [-6, 20, 46, 74]
    lengths = [reach, reach * 0.92, reach * 0.8, reach * 0.66]
    seg2 = [box(j - 0.1, -0.4, 0.12, j + 1.6, 0.4, 0.7, 3)]
    seg2 += [box(j + 0.4, -1.9, 0.2, j + 1.0, -0.4, 0.62, 3), box(j + 0.5, -2.6, 0.28, j + 0.9, -1.9, 0.55, claw)]   # thumb claw
    for a in range(len(angles) - 1):
        seg2 += membrane(j, 0.2, angles[a], lengths[a], angles[a + 1], lengths[a + 1], 0 if a % 2 == 0 else 4, 5, scales=scales)
    seg2 += membrane(j, 0.2, angles[-1], lengths[-1], 96, reach * 0.42, 0, 5, scales=scales)
    fingers = [finger(j, 0.2, lengths[k], angles[k], claw, sp, k, spikes=dragon and k == 0) for k in range(len(angles))]
    elbow = P(seg2, A('rot', axis='y', pivot=[j, 0, 0.4], amp=12, speed=sp, phase=-1.0), fingers)
    model(mid, 'back', name, tier, 'body', seg1, anim=anim, kind='wings', parts=[elbow])


bat_wing('wings_bat', 'Bat Wings', 'rare', 12.5, dict(FLAP, speed=0.16, range=14))
bat_wing('wings_dragon', 'Dragon Wings', 'epic', 15.5, dict(FLAP, base=30, range=12, speed=0.1), dragon=True)


def strips(x0, x1, top, bot, z, fill, margin=None, mw=0.6, step=0.45, glow=False, depth=0.3):
    """A flat shape made of thin vertical strips between top(x) and bottom(x); optional darker margin."""
    out = []
    x = x0
    while x < x1 - 1e-6:
        xe = min(x1, x + step)
        xm = (x + xe) / 2
        t, b = top(xm), bot(xm)
        col = fill(xm) if callable(fill) else fill
        edge = margin is not None and (xe >= x1 - 1e-6 or x <= x0 + 1e-6)
        if b - t > 0.12:
            if margin is not None and not edge and b - t > 2.4 * mw:
                out += [box(x, t, z, xe, t + mw, z + depth, margin, glow), box(x, t + mw, z, xe, b - mw, z + depth, col, glow),
                        box(x, b - mw, z, xe, b, z + depth, margin, glow)]
            else:
                out.append(box(x, t, z, xe, b, z + depth, margin if margin is not None else col, glow))
        x = xe
    return out


def ellipse(cx, cy, rx, ry):
    top = lambda x: cy - ry * math.sqrt(max(0.0, 1 - ((x - cx) / rx) ** 2))
    bot = lambda x: cy + ry * math.sqrt(max(0.0, 1 - ((x - cx) / rx) ** 2))
    return top, bot


def spots(points, z, col, s=0.35, glow=False):
    return [box(x - s, y - s, z, x + s, y + s, z + 0.08, col, glow) for x, y in points]


def butterfly():
    top = lambda x: -0.6 - 0.62 * x
    bot = lambda x: 2.2 - 0.2 * x - 0.055 * x * x
    upper = strips(0.0, 8.8, top, bot, 0.1, lambda x: 4 if x < 2.2 else (6 if x > 6.4 else 0), margin=3, mw=0.7)
    upper += spots([(8.2, -5.2), (7.4, -4.9), (8.3, -3.9), (6.6, -4.6), (5.6, -4.0)], 0.42, W, 0.22)      # margin dots
    upper += [box(3.9, -3.3, 0.4, 6.3, -0.9, 0.48, 3), box(4.3, -2.9, 0.45, 5.9, -1.3, 0.53, 2, True), box(4.8, -2.4, 0.5, 5.4, -1.8, 0.58, W, True)]  # eyespot
    for k, (y0, slope) in enumerate([(0.0, -0.55), (0.9, -0.38), (1.6, -0.22)]):   # veins
        for i in range(8):
            x = 0.4 + i * 0.85
            upper.append(box(x, y0 + slope * x, 0.4, x + 0.85, y0 + slope * x + 0.13, 0.45, 7))
    top2 = lambda x: 2.4 + 0.15 * x
    bot2 = lambda x: 2.4 + 6.8 * math.sqrt(max(0.0, 1 - (x / 5.6) ** 2)) * (0.75 + 0.25 * math.cos(x))
    lower = strips(0.0, 5.6, top2, bot2, 0.1, lambda x: 6 if x < 1.4 else 1, margin=3, mw=0.6)
    lower += [box(1.6, 8.6, 0.1, 2.6, 11.4, 0.4, 3), box(1.75, 11.4, 0.12, 2.45, 12.2, 0.38, 2)]   # tail
    lower += [box(2.2, 4.4, 0.4, 3.8, 6.0, 0.48, 2), box(2.65, 4.85, 0.45, 3.35, 5.55, 0.53, W)] + spots([(4.6, 3.6), (4.2, 6.8), (1.0, 7.6)], 0.42, W, 0.2)
    body_ = [box(-0.35, -5.4, 0.0, 0.35, 10.6, 0.6, 3)]
    parts = [P(lower, A('rot', axis='y', pivot=[0, 3.0, 0.3], amp=10, speed=0.22, phase=-0.7))]
    model('wings_butterfly', 'back', 'Butterfly Wings', 'rare', 'body', body_ + upper, anim=dict(FLAP, base=34, range=22, speed=0.22), kind='wings', parts=parts)


def blade(length, c0, c1, w0, w1):
    """A long leaf-shaped wing panel along x: centre line c0 + c1 * x, half width w0 + w1 * sin(pi x / length)."""
    top = lambda x: c0 + c1 * x - (w0 + w1 * math.sin(math.pi * x / length))
    bot = lambda x: c0 + c1 * x + (w0 + w1 * math.sin(math.pi * x / length))
    return top, bot


def fairy():
    t, b = blade(8.0, 0.2, -1.0, 0.25, 1.9)
    upper = strips(0.0, 8.0, t, b, 0.15, 4, margin=0, mw=0.3, glow=True, depth=0.25, step=0.35)
    t2, b2 = blade(8.0, 0.2, -1.0, 0.0, 0.9)
    upper += strips(1.0, 7.4, t2, b2, 0.1, 8, glow=True, depth=0.33, step=0.35)
    upper += [box(0.3 + i * 0.7, 0.2 - 1.0 * (0.3 + i * 0.7) - 0.06, 0.42, 1.0 + i * 0.7, 0.2 - 1.0 * (0.3 + i * 0.7) + 0.06, 0.47, 2, True) for i in range(10)]
    t3, b3 = blade(5.6, 2.4, 0.8, 0.25, 1.5)
    lower = strips(0.0, 5.6, t3, b3, 0.15, 4, margin=0, mw=0.3, glow=True, depth=0.25, step=0.35)
    lower += strips(0.8, 4.8, *blade(5.6, 2.4, 0.8, 0.0, 0.7), 0.1, 8, glow=True, depth=0.33, step=0.35)
    sparkles = [glint(8.4, -8.0, 0.7, 0.45, 0.13, 0.0), glint(5.8, 7.6, 0.7, 0.4, 0.11, 1.9), glint(3.2, -4.6, 0.7, 0.35, 0.15, 3.3),
                glint(6.2, -3.4, 0.7, 0.3, 0.17, 4.4), glint(2.2, 5.6, 0.7, 0.3, 0.12, 5.6)]
    parts = [P(lower, A('rot', axis='y', pivot=[0, 3.0, 0.3], amp=12, speed=0.25, phase=-0.6))] + sparkles
    model('wings_fairy', 'back', 'Fairy Wings', 'epic', 'body', upper, anim=dict(FLAP, base=36, range=18, speed=0.25), kind='wings', parts=parts)


def mech():
    root = [box(0, 0, 0, 3.2, 2.2, 1.3, 3), box(0.4, 0.4, -0.1, 2.8, 1.8, 1.4, 1), box(3.0, 0.2, -0.05, 3.6, 7.6, 1.15, 0)]
    root += [box(0.8, 0.8, -0.2, 1.4, 1.4, -0.1, 2, True)] + [box(0.5 + 0.8 * i, 2.2, 0.3, 0.9 + 0.8 * i, 2.6, 1.0, 9) for i in range(3)]
    plates = []
    extras = []
    for i, (y1, y2, reach) in enumerate([(-0.8, 1.0, 13.6), (1.4, 3.0, 12.0), (3.4, 5.0, 10.0), (5.4, 6.8, 8.0), (7.2, 8.4, 6.0)]):
        plates += [box(3.0, y1, 0.1, reach, y2, 1.0, 1), box(3.0, y1, 0.0, reach - 0.4, y1 + 0.3, 1.05, 6),
                   box(reach - 1.0, y1 + 0.1, 0.05, reach, y2 - 0.1, 1.05, 2, True), box(3.0, y2 - 0.25, 0.0, reach - 1.0, y2, 1.05, 7)]
        plates += [box(x, y1 + 0.45, 1.0, x + 0.3, y1 + 0.75, 1.12, 8) for x in [4.2 + k * 2.2 for k in range(int((reach - 5) / 2.2))]]  # rivets
        extras.append(P([box(4.0 + i, y1 + 0.5, -0.05, 4.5 + i, y2 - 0.5, 0.1, '#4EF2FF', True)],
                        A('twinkle', pivot=[4.25 + i, (y1 + y2) / 2, 0], speed=0.2, phase=i * 1.3)))
        extras.append(P([box(reach, y1 + 0.3, 0.3, reach + 0.6, y2 - 0.3, 0.8, '#7FF7FF', True)],
                        A('flicker', pivot=[reach, (y1 + y2) / 2, 0.55], speed=0.6, phase=i * 0.9)))   # thrusters
    piston = P([box(3.4, 3.2, 1.15, 9.0, 3.6, 1.45, 8), box(8.6, 2.9, 1.1, 9.4, 3.9, 1.5, 3)], A('slide', axis='x', amp=0.6, speed=0.09, phase=-0.8))
    parts = [P(plates, A('rot', axis='y', pivot=[3.0, 0, 0.5], amp=5, speed=0.09, phase=-0.8), extras + [piston])]
    model('wings_mech', 'back', 'Mech Wings', 'epic', 'body', root, anim=dict(FLAP, base=22, range=6, speed=0.09), kind='wings', parts=parts)


def crystal():
    shards = [(0, 0, 3, 2), (2, -1.5, 6, 1.5), (5, -3.5, 8, -0.5), (7.5, -5.5, 9.5, -2.5), (9, -7.4, 10.4, -5.0), (2, 2, 7, 4), (6, 3, 10, 5),
              (9, 4, 12.5, 5.6), (11.6, 4.6, 13.6, 5.4), (2, 4.5, 5.5, 6.5), (4.5, 6, 7.5, 8), (6.6, 7.6, 8.6, 9.4), (0.6, 6.8, 3.0, 8.6)]
    inner = []
    outer = []
    for i, (x1, y1, x2, y2) in enumerate(shards):
        col = 0 if i % 2 == 0 else 2
        cube = [box(x1, y1, 0.1, x2, y2, 0.8, col, True), box(x1 + 0.3, y1 + 0.3, 0.0, x2 - 0.3, y2 - 0.3, 0.9, 8 if col == 0 else 4, True),
                box(x1 + 0.1, y1, 0.85, x1 + 0.4, y2 - 0.2, 0.95, W, True)]     # facet edge
        (inner if x1 < 4 else outer).append(P(cube, A('pulse', pivot=[(x1 + x2) / 2, (y1 + y2) / 2, 0.45], amp=0.06, speed=0.15, phase=i * 0.7)))
    glints = [glint(9.8, -7.0, 1.0, 0.45, 0.12, 0.0), glint(13.2, 5.0, 1.0, 0.45, 0.1, 2.0), glint(7.6, 9.0, 1.0, 0.4, 0.14, 4.0), glint(5.0, -2.0, 1.0, 0.35, 0.16, 5.2)]
    core = [box(0, 0.4, 0.3, 1.2, 1.6, 0.6, 2, True), box(0.2, 0.6, 0.6, 1.0, 1.4, 0.75, W, True)]
    parts = inner + [P([], A('rot', axis='y', pivot=[4.0, 0, 0.45], amp=6, speed=0.08, phase=-0.9), outer + glints)]
    model('wings_crystal', 'back', 'Crystal Wings', 'legendary', 'body', core, anim=dict(FLAP, base=26, range=7, speed=0.08), kind='wings', parts=parts)


butterfly()
fairy()
mech()
crystal()
WING_MODELS = ['wings_feather', 'wings_angel', 'wings_bat', 'wings_dragon', 'wings_butterfly', 'wings_fairy', 'wings_mech', 'wings_crystal']

# ---------------------------------------------------------------- back items (body anchor; the back is z +2)


def backpack():
    cubes = rbox(-3.7, 0.8, 2.0, 3.7, 10.6, 5.1, 0, r=0.6)
    cubes += rbox(-2.8, 5.4, 5.0, 2.8, 9.6, 6.2, 1, r=0.4)                                                      # front pocket
    cubes += [box(-2.6, 5.3, 5.95, 2.6, 5.6, 6.25, 3)] + [box(x, 5.25, 6.2, x + 0.25, 5.65, 6.3, SILVER) for x in (-2.2, -1.0, 0.2, 1.4)]
    cubes += [box(-3.8, 0.4, 2.4, 3.8, 1.6, 4.7, 1), box(-1.0, 0.0, 3.0, 1.0, 0.4, 4.2, 3)]                       # lid + handle
    cubes += [box(-3.3, 0.0, -2.25, -2.4, 9.2, 2.0, 3), box(2.4, 0.0, -2.25, 3.3, 9.2, 2.0, 3)]                   # straps
    cubes += [box(-3.4, 5.0, -2.35, -2.3, 5.7, -2.2, SILVER), box(2.3, 5.0, -2.35, 3.4, 5.7, -2.2, SILVER)]
    cubes += rbox(3.7, 4.6, 2.6, 5.0, 8.6, 4.4, '#4EA8FF', r=0.3) + [box(3.9, 4.0, 3.1, 4.8, 4.6, 3.9, W)]       # bottle
    charm = [box(-2.25, 9.6, 6.1, -2.05, 10.8, 6.3, SILVER)] + ball(-2.15, 11.2, 6.2, 0.45, 2)
    parts = [P(charm, A('rot', axis='z', pivot=[-2.15, 9.6, 6.2], amp=14, speed=0.2))]
    model('backpack', 'back', 'Backpack', 'common', 'body', cubes, parts=parts)


def jetpack():
    tank = rbox(0.6, 1.0, 2.0, 3.6, 9.4, 5.0, 1, r=0.6) + [box(0.55, 3.0, 2.2, 3.65, 3.6, 4.8, 2), box(0.55, 6.6, 2.2, 3.65, 7.2, 4.8, 0)]
    tank += [box(1.0, 0.2, 2.4, 3.2, 1.0, 4.6, 0), box(1.2, 9.4, 2.6, 3.0, 10.6, 4.4, 3), box(1.5, 10.6, 2.9, 2.7, 11.0, 4.1, SILVER_D)]
    cubes = mirror_x(tank) + [box(-0.6, 2.0, 2.0, 0.6, 8.0, 3.6, 0), box(-0.45, 3.2, 3.6, 0.45, 4.2, 3.75, SILVER)]
    cubes += [box(-3.3, 0.0, -2.25, -2.4, 8.0, 2.0, 3), box(2.4, 0.0, -2.25, 3.3, 8.0, 2.0, 3)]
    gauge = [P([box(-0.3, 3.4, 3.76, 0.1, 3.9, 3.82, '#FF4D5E', True)], A('twinkle', pivot=[-0.1, 3.65, 3.8], speed=0.3))]
    flame = [box(1.4, 11.0, 2.8, 2.8, 12.8, 4.2, 2, True), box(1.7, 12.8, 3.1, 2.5, 14.4, 3.9, 0, True), box(1.85, 14.4, 3.3, 2.35, 15.2, 3.7, 2, True)]
    flames = mirror_parts([P(flame, A('flicker', pivot=[2.1, 11.0, 3.5], speed=0.9))], 1.7)
    model('jetpack', 'back', 'Jetpack', 'epic', 'body', cubes, parts=gauge + flames)


def quiver():
    cubes = rbox(0.6, 0.0, 2.0, 3.2, 9.0, 4.6, 0, r=0.45)
    cubes += [box(0.45, 0.0, 1.85, 3.35, 1.0, 4.75, 1), box(0.45, 7.6, 1.85, 3.35, 8.4, 4.75, 1)]
    cubes += [box(0.5, y, 1.95, 0.6, y + 0.3, 4.65, 2) for y in (2.0, 3.4, 4.8, 6.2)]                         # stitching
    for i in range(4):
        x = 1.0 + i * 0.55
        top = -3.6 + (i % 2) * 0.7
        cubes += [box(x, top, 3.0, x + 0.3, 0.0, 3.3, '#C8A27A'), box(x - 0.15, top - 1.2, 2.85, x + 0.45, top, 3.45, 2 if i % 2 else 0),
                  box(x - 0.05, top - 1.5, 2.95, x + 0.35, top - 1.2, 3.35, SILVER)]
    cubes += [box(-3.4, 0.0, 1.9, 3.4, 0.8, 2.2, 3), box(-3.4, 9.0, 1.9, 1.0, 9.8, 2.2, 3)]
    model('quiver', 'back', 'Quiver', 'rare', 'body', cubes)


def sword():
    cubes = [box(-0.65, -6.0, 2.2, 0.65, 9.0, 2.8, '#DDE3EA'), box(-0.2, -5.6, 2.12, 0.2, 9.0, 2.88, SILVER_D), box(-0.4, -7.0, 2.3, 0.4, -6.0, 2.7, '#DDE3EA')]
    cubes += [box(-2.6, 9.0, 1.95, 2.6, 10.0, 3.05, 0), box(-3.0, 8.8, 2.05, -2.4, 10.2, 2.95, 1), box(2.4, 8.8, 2.05, 3.0, 10.2, 2.95, 1)]
    cubes += [box(-0.5, 9.2, 1.85, 0.5, 9.8, 1.95, 2, True)]
    cubes += [box(-0.7, 10.0, 2.1, 0.7, 13.0, 2.9, 1)] + [box(-0.75, y, 2.05, 0.75, y + 0.3, 2.95, 3) for y in (10.5, 11.3, 12.1)]
    cubes += ball(0, 13.6, 2.5, 0.75, 0)
    cubes += [box(-0.3, -4.0, 1.9, 0.3, 8.0, 2.1, 3), box(-3.2, -1.0, -2.25, -2.4, 0.0, 2.0, 3)]
    shine = P([box(-0.6, -0.6, 2.82, 0.6, 0.6, 2.88, W, True)], A('slide', axis='y', amp=6.5, speed=0.05))
    parts = [shine, glint(0, 13.6, 3.35, 0.4, 0.1, 1.0)]
    model('sword', 'back', 'Back Sword', 'epic', 'body', cubes, parts=parts)


def shield():
    rows = [(1.0, 1.8, 2.6), (1.8, 3.0, 3.4), (3.0, 8.0, 3.7), (8.0, 9.6, 3.2), (9.6, 10.8, 2.5), (10.8, 11.8, 1.6), (11.8, 12.5, 0.7)]
    cubes = [box(-w, y1, 2.0, w, y2, 2.8, 0) for y1, y2, w in rows]
    cubes += [box(-w - 0.25, y1, 2.1, -w, y2, 2.9, 1) for y1, y2, w in rows] + [box(w, y1, 2.1, w + 0.25, y2, 2.9, 1) for y1, y2, w in rows]
    cubes += [box(-2.6, 0.75, 2.1, 2.6, 1.0, 2.9, 1)]
    cubes += [box(-0.6, 2.2, 2.8, 0.6, 10.8, 3.05, 2), box(-2.8, 5.2, 2.8, 2.8, 6.4, 3.05, 2)]
    cubes += ball(0, 5.8, 3.1, 0.75, 1)
    cubes += [box(x - 0.2, y - 0.2, 2.8, x + 0.2, y + 0.2, 3.0, SILVER) for x, y in [(-2.9, 2.6), (2.9, 2.6), (-2.9, 8.8), (2.9, 8.8)]]
    parts = [glint(-1.8, 3.4, 3.2, 0.45, 0.09, 0.3)]
    model('shield', 'back', 'Back Shield', 'rare', 'body', cubes, parts=parts)


backpack()
jetpack()
quiver()
sword()
shield()
BACK_MODELS = ['backpack', 'jetpack', 'quiver', 'sword', 'shield']

# ---------------------------------------------------------------- auras (body anchor, feet at y 24)


def shape(rows, colors, scale=0.5, glow=True, depth=0.15):
    """A small flat pixel shape centred at the origin, facing forward. colors maps a row character to a colour."""
    out = []
    hgt = len(rows)
    wid = max(len(r) for r in rows)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colors:
                px = (x - wid / 2) * scale
                py = (y - hgt / 2) * scale
                out.append(box(px, py, -depth, px + scale, py + scale, depth, colors[ch], glow))
    return out


def shape_flat(rows, colors, scale=0.5, glow=True, depth=0.08):
    """Like shape(), but lying flat on the ground (x/z plane)."""
    out = []
    hgt = len(rows)
    wid = max(len(r) for r in rows)
    for z, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colors:
                px = (x - wid / 2) * scale
                pz = (z - hgt / 2) * scale
                out.append(box(px, -depth, pz, px + scale, depth, pz + scale, colors[ch], glow))
    return out


HEART = ['.##.##.', '#ww####', '#w#####', '#######', '.#####.', '..###..', '...#...']
NOTE = ['...###', '...#.#', '...#..', '...#..', '.###..', '####..', '.##...']
STAR = ['...#...', '...#...', '..###..', '#######', '.#####.', '..#.#..', '.#...#.']
RUNE = ['#####', '#.#.#', '##.##', '#.#.#', '#####']
GLYPH = ['.###.', '#...#', '#.#.#', '#...#', '.###.']
FLAKE = ['#..#..#', '.#.#.#.', '..###..', '###o###', '..###..', '.#.#.#.', '#..#..#']
LEAF = ['...##', '..###', '.#v##', '#v##.', 'v##..']
BUBBLE = ['.###.', '#w..#', '#...#', '#...#', '.###.']
SPARK = ['..#..', '..#..', '##o##', '..#..', '..#..']

ORB = ball(0, 0, 0, 0.75, 0, True)
ORB_CORE = ball(0, 0, 0, 0.45, 2, True)

model('aura_orbs', 'aura', 'Orbiting Orbs', 'rare', 'body', ORB,
      anim={'type': 'orbit', 'count': 3, 'radius': 11, 'y': 6, 'speed': 2.4, 'wobble': 2.5},
      parts=[P(ORB_CORE + [box(-0.2, -0.95, -0.2, 0.2, -0.75, 0.2, W, True)], A('pulse', amp=0.25, speed=0.3)),
             P([box(0.9, 0.2, -0.15, 1.2, 0.5, 0.15, 2, True)], A('twinkle', pivot=[1.05, 0.35, 0], speed=0.4))])
model('aura_ring', 'aura', 'Ground Ring', 'rare', 'body', [box(-0.5, -0.06, -0.5, 0.5, 0.06, 0.5, 0, True)],
      anim={'type': 'orbit', 'count': 18, 'radius': 9, 'y': 23.6, 'speed': 0.8, 'wobble': 0},
      parts=[P([box(-0.22, -0.1, -0.22, 0.22, 0.1, 0.22, 2, True)], A('pulse', amp=0.35, speed=0.25))])
model('aura_spiral', 'aura', 'Spiral', 'epic', 'body', [box(-0.5, -0.15, -0.15, 0.5, 0.15, 0.15, 2, True)],
      anim={'type': 'spiral', 'count': 12, 'radius': 9, 'y': 24, 'height': 30, 'speed': 1.6},
      parts=[P([box(-0.15, -0.5, -0.15, 0.15, 0.5, 0.15, 0, True), box(-0.15, -0.15, -0.5, 0.15, 0.15, 0.5, 0, True)], A('spin', speed=12))])
model('aura_sparks', 'aura', 'Sparks', 'rare', 'body', shape(SPARK, {'#': 2}, 0.28),
      anim={'type': 'rise', 'count': 12, 'radius': 9, 'y': 24, 'height': 30, 'speed': 0.45},
      parts=[P(shape(['o'], {'o': W}, 0.3, depth=0.2), A('twinkle', speed=0.5))])
model('aura_snow', 'aura', 'Snowfall', 'rare', 'body', [],
      anim={'type': 'fall', 'count': 12, 'radius': 10, 'y': -14, 'height': 38, 'speed': 0.3},
      parts=[P(shape(FLAKE, {'#': 2, 'o': W}, 0.22), A('spin', axis='z', speed=3))])
model('aura_embers', 'aura', 'Embers', 'epic', 'body', [],
      anim={'type': 'rise', 'count': 14, 'radius': 8, 'y': 24, 'height': 26, 'speed': 0.6},
      parts=[P(ball(0, 0, 0, 0.42, 0, True) + [box(-0.2, -0.2, -0.2, 0.2, 0.2, 0.2, 2, True)], A('flicker', speed=1.2))])
model('aura_hearts', 'aura', 'Hearts', 'rare', 'body', [],
      anim={'type': 'rise', 'count': 6, 'radius': 9, 'y': 18, 'height': 26, 'speed': 0.35},
      parts=[P(shape(HEART, {'#': 0, 'w': 2}, 0.3), A('pulse', amp=0.15, speed=0.35))])
model('aura_notes', 'aura', 'Melody', 'rare', 'body', [],
      anim={'type': 'orbit', 'count': 4, 'radius': 10, 'y': 2, 'speed': 1.6, 'wobble': 3},
      parts=[P(shape(NOTE, {'#': 0}, 0.3), A('rot', axis='z', amp=12, speed=0.3))])
model('aura_runes', 'aura', 'Rune Circle', 'epic', 'body', [],
      anim={'type': 'orbit', 'count': 8, 'radius': 10, 'y': 23.8, 'speed': 0.6, 'wobble': 0},
      parts=[P(shape_flat(RUNE, {'#': 0}, 0.4), A('spin', speed=2)),
             P(shape_flat(['#'], {'#': 2}, 0.5, depth=0.12), A('pulse', amp=0.4, speed=0.2))])
model('aura_stars', 'aura', 'Star Crown', 'legendary', 'body', [],
      anim={'type': 'orbit', 'count': 5, 'radius': 6, 'y': -11, 'speed': 1.2, 'wobble': 0.6},
      parts=[P(shape(STAR, {'#': 2}, 0.22), A('spin', speed=4)), glint(0, 0, 0, 0.35, 0.2)])
model('aura_bubbles', 'aura', 'Bubbles', 'common', 'body', [],
      anim={'type': 'rise', 'count': 8, 'radius': 9, 'y': 22, 'height': 30, 'speed': 0.3},
      parts=[P(shape(BUBBLE, {'#': 2, 'w': W}, 0.32), A('pulse', amp=0.1, speed=0.4))])
model('aura_leaves', 'aura', 'Falling Leaves', 'rare', 'body', [],
      anim={'type': 'fall', 'count': 9, 'radius': 11, 'y': -12, 'height': 36, 'speed': 0.22},
      parts=[P([], A('rot', axis='x', amp=35, speed=0.15), [P(shape(LEAF, {'#': 0, 'v': 1}, 0.32, glow=False), A('spin', axis='z', speed=2.5))])])
model('aura_runes_glyph', 'aura', 'Glyph Orbit', 'epic', 'body', [],
      anim={'type': 'orbit', 'count': 6, 'radius': 12, 'y': 8, 'speed': 1.0, 'wobble': 1.5},
      parts=[P(shape(GLYPH, {'#': 2}, 0.32), A('pulse', amp=0.12, speed=0.3)), P(shape(['#'], {'#': 0}, 0.4, depth=0.2), A('twinkle', speed=0.3))])
AURA_MODELS = ['aura_orbs', 'aura_ring', 'aura_spiral', 'aura_sparks', 'aura_snow', 'aura_embers', 'aura_hearts', 'aura_notes', 'aura_runes', 'aura_stars',
               'aura_bubbles', 'aura_leaves', 'aura_runes_glyph']

# ---------------------------------------------------------------- pets (sit on the right shoulder; body anchor)

SH = 4.6  # shoulder x
BOB = {'type': 'bob', 'amp': 0.4, 'speed': 0.25}
ORANGE = '#FF9A2E'
RED = '#E8483B'


def feet(x1, x2, z, col=ORANGE):
    return [box(x1, -0.3, z - 0.5, x1 + 0.6, 0.0, z + 0.3, col), box(x2 - 0.6, -0.3, z - 0.5, x2, 0.0, z + 0.3, col)]


def wing_pair(x1, x2, y1, y2, z1, z2, col, speed=0.4, amp=18, pivot_y=None):
    """Two little wings on the sides of a pet that flap."""
    py = pivot_y if pivot_y is not None else y1
    right = P([box(x2, y1, z1, x2 + 0.5, y2, z2, col)], A('rot', axis='z', pivot=[x2, py, (z1 + z2) / 2], amp=amp, speed=speed, base=-amp * 0.6))
    left = P([box(x1 - 0.5, y1, z1, x1, y2, z2, col)], A('rot', axis='z', pivot=[x1, py, (z1 + z2) / 2], amp=-amp, speed=speed, base=amp * 0.6))
    return [right, left]


def pet_chicken():
    body = rbox(SH, -3.0, -1.5, SH + 3.0, -0.3, 2.3, 0, r=0.5) + [box(SH + 0.2, -2.6, 2.2, SH + 2.8, -0.8, 3.0, 1), box(SH + 0.6, -3.0, 2.8, SH + 2.4, -1.6, 3.4, 1)]
    body += feet(SH + 0.6, SH + 2.4, 0.2)
    head = rbox(SH + 0.45, -5.3, -2.5, SH + 2.55, -2.8, -0.5, 0, r=0.4)
    head += [box(SH + 1.0, -4.3, -3.4, SH + 2.0, -3.6, -2.45, ORANGE), box(SH + 1.15, -3.65, -3.2, SH + 1.85, -3.4, -2.5, '#E87F1A')]
    head += [box(SH + 1.1, -6.0, -2.0, SH + 1.9, -5.2, -1.4, RED), box(SH + 1.2, -6.4, -1.4, SH + 1.8, -5.2, -0.8, RED), box(SH + 1.2, -3.5, -2.75, SH + 1.8, -2.8, -2.45, RED)]
    eyes = [eye(SH + 0.95, -4.75, -2.5, 0.4, 0.0), eye(SH + 2.05, -4.75, -2.5, 0.4, 0.0)]
    head_part = P(head, A('rot', axis='x', pivot=[SH + 1.5, -3.0, -1.5], amp=7, speed=0.25), eyes)
    parts = [head_part] + wing_pair(SH, SH + 3.0, -2.6, -0.8, -0.8, 1.8, 1, speed=0.5, amp=14)
    model('pet_chicken', 'pet', 'Chicken Buddy', 'common', 'body', body, anim=dict(BOB), palettes=['snow', 'sand', 'coffee', 'onyx'], parts=parts)


def pet_dragon():
    body = rbox(SH, -3.2, -1.2, SH + 3.0, -0.3, 2.6, 0, r=0.5) + [box(SH + 0.6, -2.6, -1.3, SH + 2.4, -0.6, -1.1, 2)]
    body += [box(SH + 0.7, -2.3, -1.35, SH + 2.3, -2.1, -1.25, 1), box(SH + 0.7, -1.5, -1.35, SH + 2.3, -1.3, -1.25, 1)]
    body += [box(SH + 1.2, -3.8, x, SH + 1.8, -3.2, x + 0.6, 2) for x in (0.0, 1.0, 2.0)]                       # back spikes
    body += feet(SH + 0.4, SH + 2.6, 0.2, 1)
    head = rbox(SH + 0.4, -5.6, -2.7, SH + 2.6, -3.2, -0.6, 0, r=0.4) + [box(SH + 0.8, -4.7, -3.8, SH + 2.2, -3.4, -2.6, 0)]
    head += [box(SH + 1.0, -4.3, -3.9, SH + 1.3, -4.0, -3.8, BLACK), box(SH + 1.7, -4.3, -3.9, SH + 2.0, -4.0, -3.8, BLACK)]
    head += [box(SH + 0.6, -6.6, -1.5, SH + 1.0, -5.5, -0.9, 2), box(SH + 2.0, -6.6, -1.5, SH + 2.4, -5.5, -0.9, 2),
             box(SH + 0.65, -7.1, -1.3, SH + 0.95, -6.6, -1.0, 3), box(SH + 2.05, -7.1, -1.3, SH + 2.35, -6.6, -1.0, 3)]
    eyes = [eye(SH + 0.85, -5.05, -2.7, 0.42, 0.0, '#FFD24A'), eye(SH + 2.15, -5.05, -2.7, 0.42, 0.0, '#FFD24A')]
    head_part = P(head, A('rot', axis='y', pivot=[SH + 1.5, -4.0, -1.0], amp=10, speed=0.08), eyes)
    tail = P([box(SH + 1.0, -1.6, 2.5, SH + 2.0, -0.8, 4.4, 0), box(SH + 1.2, -1.5, 4.4, SH + 1.8, -0.9, 5.6, 0), box(SH + 1.1, -1.9, 5.6, SH + 1.9, -0.6, 6.3, 2)],
             A('rot', axis='y', pivot=[SH + 1.5, -1.2, 2.5], amp=16, speed=0.2))
    wings = [P([box(SH + 3.0, -4.2, 0.0, SH + 5.0, -3.8, 1.8, 1), box(SH + 3.0, -3.8, 0.2, SH + 4.4, -2.6, 1.6, 0), box(SH + 4.6, -4.6, 0.6, SH + 5.0, -4.2, 1.2, 2)],
               A('rot', axis='z', pivot=[SH + 3.0, -4.0, 0.9], amp=22, speed=0.35, base=-10)),
             P([box(SH - 2.0, -4.2, 0.0, SH, -3.8, 1.8, 1), box(SH - 1.4, -3.8, 0.2, SH, -2.6, 1.6, 0), box(SH - 2.0, -4.6, 0.6, SH - 1.6, -4.2, 1.2, 2)],
               A('rot', axis='z', pivot=[SH, -4.0, 0.9], amp=-22, speed=0.35, base=10))]
    parts = [head_part, tail] + wings + [glint(SH + 2.3, -5.6, -2.9, 0.3, 0.1, 1.0)]
    model('pet_dragon', 'pet', 'Mini Dragon', 'legendary', 'body', body, anim=dict(BOB), palettes=['crimson', 'forest', 'violet', 'gold'], parts=parts)


def pet_slime():
    body = rbox(SH, -3.6, -1.6, SH + 3.4, -0.2, 1.8, 0, r=0.45) + [box(SH + 0.8, -2.8, -0.8, SH + 2.6, -1.0, 1.0, 1)]
    body += [box(SH + 0.3, -3.3, -1.68, SH + 0.9, -2.9, -1.6, W)]                                                # shine
    face = [box(SH + 1.4, -1.6, -1.68, SH + 2.0, -1.2, -1.58, EYE)]
    eyes = [eye(SH + 0.9, -2.5, -1.62, 0.6, 0.0), eye(SH + 2.5, -2.5, -1.62, 0.6, 0.0)]
    squish = P(body + face, A('pulse', pivot=[SH + 1.7, 0.0, 0.1], amp=0.05, speed=0.3), eyes)
    model('pet_slime', 'pet', 'Slime Cube', 'common', 'body', [], anim={'type': 'bob', 'amp': 0.6, 'speed': 0.3},
          palettes=['lime', 'ocean', 'rose', 'violet'], parts=[squish])


def pet_ghost():
    body = rbox(SH, -5.2, -1.4, SH + 3.0, -1.4, 1.6, 0, r=0.6) + rbox(SH + 0.4, -5.8, -1.0, SH + 2.6, -4.8, 1.2, 0, r=0.3)
    hem = [box(SH, -1.4, -1.4, SH + 0.8, -0.6, 1.6, 0), box(SH + 1.1, -1.4, -1.4, SH + 1.9, -0.8, 1.6, 0), box(SH + 2.2, -1.4, -1.4, SH + 3.0, -0.6, 1.6, 0)]
    mouth = [box(SH + 1.2, -2.8, -1.5, SH + 1.8, -2.2, -1.42, 3)]
    blush = [box(SH + 0.3, -3.1, -1.48, SH + 0.8, -2.8, -1.42, PINK), box(SH + 2.2, -3.1, -1.48, SH + 2.7, -2.8, -1.42, PINK)]
    eyes = [eye(SH + 0.9, -3.7, -1.45, 0.6, 0.0), eye(SH + 2.1, -3.7, -1.45, 0.6, 0.0)]
    parts = [P(hem, A('rot', axis='x', pivot=[SH + 1.5, -1.4, 0.1], amp=6, speed=0.3))] + eyes
    model('pet_ghost', 'pet', 'Ghost', 'rare', 'body', body + mouth + blush, anim={'type': 'bob', 'amp': 0.8, 'speed': 0.12},
          palettes=['snow', 'sky', 'mint', 'sakura'], parts=parts)


def pet_bee():
    body = rbox(SH, -3.2, -1.4, SH + 2.6, -0.8, 2.2, 0, r=0.45)
    body += [box(SH - 0.05, -3.05, -0.3, SH + 2.65, -0.95, 0.3, 3), box(SH - 0.05, -3.05, 0.9, SH + 2.65, -0.95, 1.5, 3)]
    body += [box(SH + 1.0, -2.2, 2.2, SH + 1.6, -1.8, 2.8, 3)]
    body += [box(SH + 0.6, -4.0, -1.0, SH + 0.8, -3.2, -0.8, 3), box(SH + 1.8, -4.0, -1.0, SH + 2.0, -3.2, -0.8, 3)]
    body += ball(SH + 0.7, -4.15, -0.9, 0.2, 3) + ball(SH + 1.9, -4.15, -0.9, 0.2, 3)
    eyes = [eye(SH + 0.6, -2.3, -1.45, 0.45, 0.0), eye(SH + 2.0, -2.3, -1.45, 0.45, 0.0)]
    wings = [P([box(SH + 1.4, -4.4, 0.2, SH + 3.2, -3.2, 1.2, W)], A('rot', axis='z', pivot=[SH + 1.4, -3.2, 0.7], amp=25, speed=1.2, base=-10)),
             P([box(SH - 0.6, -4.4, 0.2, SH + 1.2, -3.2, 1.2, W)], A('rot', axis='z', pivot=[SH + 1.2, -3.2, 0.7], amp=-25, speed=1.2, base=10))]
    model('pet_bee', 'pet', 'Bee', 'common', 'body', body, anim={'type': 'bob', 'amp': 0.7, 'speed': 0.5},
          palettes=['sunflower', 'gold', 'peach', 'candy'], parts=eyes + wings)


def pet_frog():
    body = rbox(SH, -2.0, -1.4, SH + 3.0, -0.2, 1.8, 0, r=0.4) + [box(SH + 0.4, -1.0, -1.48, SH + 2.6, -0.4, -1.38, 2)]
    body += [box(SH - 0.3, -0.7, -1.2, SH + 0.5, -0.1, 0.0, 0), box(SH + 2.5, -0.7, -1.2, SH + 3.3, -0.1, 0.0, 0)]   # legs
    body += [box(SH + 0.1, -2.9, -1.2, SH + 1.1, -1.8, -0.2, 0), box(SH + 1.9, -2.9, -1.2, SH + 2.9, -1.8, -0.2, 0)]
    eyes = [eye(SH + 0.6, -2.4, -1.25, 0.55, 0.0), eye(SH + 2.4, -2.4, -1.25, 0.55, 0.0)]
    throat = P([box(SH + 0.9, -0.9, -1.55, SH + 2.1, -0.3, -1.4, 2)], A('pulse', pivot=[SH + 1.5, -0.6, -1.45], amp=0.25, speed=0.35))
    model('pet_frog', 'pet', 'Frog', 'common', 'body', body, anim={'type': 'bob', 'amp': 0.9, 'speed': 0.2},
          palettes=['lime', 'forest', 'teal', 'ember'], parts=eyes + [throat])


def pet_owl():
    body = rbox(SH, -4.3, -1.2, SH + 3.0, -0.2, 1.6, 1, r=0.5) + [box(SH + 0.3, -3.2, -1.25, SH + 2.7, -0.4, -1.15, 0)]
    body += [box(SH + 0.6 + i * 0.6, -2.2 + (i % 2) * 0.5, -1.3, SH + 0.9 + i * 0.6, -1.9 + (i % 2) * 0.5, -1.22, 3) for i in range(4)]
    body += [box(SH + 0.3, -3.9, -1.35, SH + 1.4, -2.7, -1.25, 2), box(SH + 1.6, -3.9, -1.35, SH + 2.7, -2.7, -1.25, 2)]
    body += [box(SH + 1.3, -2.9, -1.45, SH + 1.7, -2.2, -1.25, '#FFB547')]
    body += [box(SH, -4.9, -0.4, SH + 0.6, -4.2, 0.4, 1), box(SH + 2.4, -4.9, -0.4, SH + 3.0, -4.2, 0.4, 1)]
    body += feet(SH + 0.6, SH + 2.4, -0.6)
    eyes = [eye(SH + 0.85, -3.3, -1.37, 0.5, 0.0, period=140), eye(SH + 2.15, -3.3, -1.37, 0.5, 0.0, period=140)]
    parts = eyes + wing_pair(SH, SH + 3.0, -3.4, -0.8, -0.6, 1.2, 1, speed=0.3, amp=8)
    model('pet_owl', 'pet', 'Owl', 'rare', 'body', body, anim=dict(BOB), palettes=['coffee', 'snow', 'slate', 'sand'], parts=parts)


def pet_penguin():
    body = rbox(SH, -4.5, -1.0, SH + 2.8, -0.2, 1.6, BLACK, r=0.5) + [box(SH + 0.4, -3.4, -1.05, SH + 2.4, -0.4, -0.95, W)]
    body += [box(SH + 0.8, -3.0, -1.3, SH + 2.0, -2.6, -1.0, '#FFB547')]
    body += [box(SH - 0.05, -2.6, -1.05, SH + 2.85, -2.0, 1.65, 0), box(SH + 2.0, -2.65, -1.12, SH + 2.6, -1.4, -1.0, 0)]   # scarf
    body += feet(SH + 0.4, SH + 2.4, -0.6, '#FFB547')
    eyes = [eye(SH + 0.9, -3.7, -1.05, 0.38, 0.0), eye(SH + 1.9, -3.7, -1.05, 0.38, 0.0)]
    flippers = wing_pair(SH, SH + 2.8, -3.0, -0.8, -0.4, 1.0, BLACK, speed=0.35, amp=16)
    model('pet_penguin', 'pet', 'Penguin', 'common', 'body', body, anim=dict(BOB), palettes=['crimson', 'ocean', 'mint', 'sunflower'], parts=eyes + flippers)


def pet_cat():
    body = rbox(SH, -2.4, -1.6, SH + 2.8, -0.2, 2.4, 0, r=0.45)
    body += [box(SH + 0.3, -0.5, -1.7, SH + 0.9, -0.1, -1.0, 2), box(SH + 1.9, -0.5, -1.7, SH + 2.5, -0.1, -1.0, 2)]  # paws
    head = rbox(SH + 0.2, -4.6, -2.2, SH + 2.6, -2.4, -0.2, 0, r=0.35)
    head += [box(SH + 0.2, -5.5, -1.5, SH + 0.9, -4.6, -0.6, 0), box(SH + 1.9, -5.5, -1.5, SH + 2.6, -4.6, -0.6, 0),
             box(SH + 0.35, -5.2, -1.58, SH + 0.75, -4.7, -1.48, PINK), box(SH + 2.05, -5.2, -1.58, SH + 2.45, -4.7, -1.48, PINK)]
    head += [box(SH + 1.2, -3.15, -2.3, SH + 1.6, -2.85, -2.2, PINK), box(SH + 0.9, -2.75, -2.28, SH + 1.9, -2.6, -2.2, 3)]
    head += [box(SH - 0.3, -3.1, -2.1, SH + 0.3, -3.0, -2.0, W), box(SH + 2.5, -3.1, -2.1, SH + 3.1, -3.0, -2.0, W)]   # whiskers
    eyes = [eye(SH + 0.8, -3.6, -2.22, 0.45, 0.0, '#3DDC84'), eye(SH + 2.0, -3.6, -2.22, 0.45, 0.0, '#3DDC84')]
    tail = P([box(SH + 1.2, -1.8, 2.4, SH + 1.8, -1.2, 4.6, 0)], A('rot', axis='y', pivot=[SH + 1.5, -1.5, 2.4], amp=20, speed=0.15),
             [P([box(SH + 1.2, -3.8, 4.0, SH + 1.8, -1.2, 4.6, 0), box(SH + 1.2, -4.3, 4.0, SH + 1.8, -3.8, 4.6, 2)],
                A('rot', axis='x', pivot=[SH + 1.5, -1.5, 4.3], amp=10, speed=0.15, phase=-0.8))])
    parts = [P(head, A('rot', axis='z', pivot=[SH + 1.4, -2.4, -1.2], amp=5, speed=0.07), eyes), tail]
    model('pet_cat', 'pet', 'Cat', 'common', 'body', body, anim=dict(BOB), palettes=['onyx', 'ember', 'snow', 'slate'], parts=parts)


def pet_fox():
    body = rbox(SH, -2.4, -1.6, SH + 2.8, -0.2, 2.4, 0, r=0.45) + [box(SH + 0.3, -2.0, -1.65, SH + 2.5, -0.4, -1.55, W)]
    body += [box(SH + 0.3, -0.5, -1.7, SH + 0.9, -0.1, -1.0, 3), box(SH + 1.9, -0.5, -1.7, SH + 2.5, -0.1, -1.0, 3)]
    head = rbox(SH + 0.2, -4.6, -2.2, SH + 2.6, -2.4, -0.2, 0, r=0.35) + [box(SH + 0.8, -3.4, -3.1, SH + 2.0, -2.4, -2.2, W)]
    head += [box(SH + 1.2, -3.5, -3.2, SH + 1.6, -3.1, -3.05, EYE)]
    head += [box(SH + 0.2, -5.7, -1.2, SH + 0.9, -4.6, -0.6, 0), box(SH + 1.9, -5.7, -1.2, SH + 2.6, -4.6, -0.6, 0),
             box(SH + 0.3, -6.1, -1.1, SH + 0.8, -5.7, -0.7, 3), box(SH + 2.0, -6.1, -1.1, SH + 2.5, -5.7, -0.7, 3)]
    eyes = [eye(SH + 0.7, -3.8, -2.22, 0.42, 0.0), eye(SH + 2.1, -3.8, -2.22, 0.42, 0.0)]
    tail = P(rbox(SH + 0.8, -2.0, 2.3, SH + 2.2, -0.4, 5.4, 0, r=0.4) + [box(SH + 0.85, -1.9, 5.4, SH + 2.15, -0.5, 6.4, W)],
             A('rot', axis='y', pivot=[SH + 1.5, -1.2, 2.4], amp=22, speed=0.2))
    parts = [P(head, A('rot', axis='z', pivot=[SH + 1.4, -2.4, -1.2], amp=6, speed=0.06), eyes), tail]
    model('pet_fox', 'pet', 'Fox', 'rare', 'body', body, anim=dict(BOB), palettes=['ember', 'snow', 'coffee', 'silver'], parts=parts)


def pet_bat():
    body = rbox(SH + 0.6, -3.0, -0.8, SH + 2.4, -1.0, 1.0, 0, r=0.4)
    body += [box(SH + 0.8, -3.7, -0.6, SH + 1.2, -3.0, -0.2, 0), box(SH + 1.8, -3.7, -0.6, SH + 2.2, -3.0, -0.2, 0)]
    body += [box(SH + 1.25, -1.6, -0.88, SH + 1.45, -1.35, -0.8, W), box(SH + 1.55, -1.6, -0.88, SH + 1.75, -1.35, -0.8, W)]
    eyes = [P([box(SH + 0.9, -2.6, -0.88, SH + 1.2, -2.3, -0.8, 2, True)], A('blink', pivot=[SH + 1.05, -2.45, -0.84], period=70, dur=4)),
            P([box(SH + 1.8, -2.6, -0.88, SH + 2.1, -2.3, -0.8, 2, True)], A('blink', pivot=[SH + 1.95, -2.45, -0.84], period=70, dur=4))]
    wings = [P([box(SH + 2.4, -3.0, -0.2, SH + 4.4, -2.2, 0.4, 1), box(SH + 2.4, -2.2, -0.15, SH + 3.8, -1.5, 0.35, 1), box(SH + 4.0, -2.2, -0.15, SH + 4.4, -1.8, 0.35, 1)],
               A('rot', axis='z', pivot=[SH + 2.4, -2.6, 0.1], amp=30, speed=0.6)),
             P([box(SH - 1.4, -3.0, -0.2, SH + 0.6, -2.2, 0.4, 1), box(SH - 0.8, -2.2, -0.15, SH + 0.6, -1.5, 0.35, 1), box(SH - 1.4, -2.2, -0.15, SH - 1.0, -1.8, 0.35, 1)],
               A('rot', axis='z', pivot=[SH + 0.6, -2.6, 0.1], amp=-30, speed=0.6))]
    model('pet_bat', 'pet', 'Bat', 'common', 'body', body, anim={'type': 'bob', 'amp': 0.9, 'speed': 0.45},
          palettes=['onyx', 'violet', 'coffee', 'midnight'], parts=eyes + wings)


def pet_drone():
    body = rbox(SH + 0.2, -3.6, -1.2, SH + 2.8, -1.4, 1.4, 1, r=0.4) + [box(SH + 0.4, -2.0, -1.3, SH + 2.6, -1.6, 1.5, 3)]
    body += [box(SH + 1.3, -4.8, -0.2, SH + 1.7, -3.6, 0.2, 3), box(SH - 0.8, -3.2, -0.4, SH + 0.2, -2.6, 0.6, 0), box(SH + 2.8, -3.2, -0.4, SH + 3.8, -2.6, 0.6, 0)]
    lens = [P([box(SH + 0.8, -3.0, -1.3, SH + 2.2, -2.0, -1.2, 2, True)], A('slide', axis='x', amp=0.25, speed=0.1)),
            P([box(SH + 1.1, -5.2, -0.4, SH + 1.9, -4.8, 0.4, '#FF4D5E', True)], A('twinkle', pivot=[SH + 1.5, -5.0, 0], speed=0.25))]
    rotors = [P([box(SH - 1.6, -3.4, -0.15, SH + 0.6, -3.25, 0.15, SILVER), box(SH - 0.65, -3.4, -1.1, SH - 0.35, -3.25, 1.1, SILVER)],
                A('spin', pivot=[SH - 0.5, 0, 0.1], speed=40)),
              P([box(SH + 2.4, -3.4, -0.15, SH + 4.6, -3.25, 0.15, SILVER), box(SH + 3.35, -3.4, -1.1, SH + 3.65, -3.25, 1.1, SILVER)],
                A('spin', pivot=[SH + 3.5, 0, 0.1], speed=-40))]
    model('pet_drone', 'pet', 'Drone', 'epic', 'body', body, anim={'type': 'bob', 'amp': 0.5, 'speed': 0.15},
          palettes=['silver', 'onyx', 'aurora', 'toxic'], parts=lens + rotors)


def pet_snail():
    shell = rbox(SH + 0.2, -3.8, -0.6, SH + 2.2, -0.9, 1.9, 0, r=0.5)
    shell += [box(SH + 0.15, -3.1, -0.68, SH + 2.25, -2.8, 1.98, 1), box(SH + 0.15, -2.2, -0.68, SH + 2.25, -1.9, 1.98, 1),
              box(SH + 0.9, -2.9, -0.7, SH + 1.5, -2.3, -0.6, 2)]
    body = [box(SH, -1.0, -2.0, SH + 2.4, -0.2, 2.2, 2), box(SH + 0.7, -2.1, -2.6, SH + 1.7, -1.0, -1.8, 2)]
    stalks = [P([box(SH + 0.75, -3.0, -2.45, SH + 0.95, -2.1, -2.25, 2)] + ball(SH + 0.85, -3.15, -2.35, 0.22, EYE),
                A('rot', axis='x', pivot=[SH + 0.85, -2.1, -2.35], amp=12, speed=0.12)),
              P([box(SH + 1.45, -3.0, -2.45, SH + 1.65, -2.1, -2.25, 2)] + ball(SH + 1.55, -3.15, -2.35, 0.22, EYE),
                A('rot', axis='x', pivot=[SH + 1.55, -2.1, -2.35], amp=12, speed=0.12, phase=1.5))]
    model('pet_snail', 'pet', 'Snail', 'common', 'body', shell + body, anim={'type': 'bob', 'amp': 0.2, 'speed': 0.1},
          palettes=['coffee', 'rose', 'teal', 'sand'], parts=stalks)


def pet_jelly():
    bell = rbox(SH, -6.2, -1.4, SH + 3.0, -4.2, 1.6, 0, r=0.5) + rbox(SH + 0.4, -6.8, -1.0, SH + 2.6, -6.0, 1.2, 0, r=0.3)
    bell += [box(SH + 0.5, -5.8, -1.48, SH + 1.0, -5.4, -1.4, W)]
    eyes = [eye(SH + 1.0, -5.0, -1.45, 0.35, 0.0), eye(SH + 2.0, -5.0, -1.45, 0.35, 0.0)]
    tentacles = []
    for i in range(4):
        x = SH + 0.3 + i * 0.8
        z = -0.2 + (i % 2) * 0.6
        tentacles.append(P([box(x, -4.2, z, x + 0.3, -2.2, z + 0.3, 2, True), box(x + 0.05, -2.2, z + 0.05, x + 0.25, -0.8, z + 0.25, 0, True)],
                           A('rot', axis='x', pivot=[x + 0.15, -4.2, z + 0.15], amp=14, speed=0.15, phase=i * 0.9)))
    pulse = P(bell, A('pulse', pivot=[SH + 1.5, -4.2, 0.1], amp=0.06, speed=0.15), eyes)
    model('pet_jelly', 'pet', 'Jellyfish', 'epic', 'body', [], anim={'type': 'bob', 'amp': 1.0, 'speed': 0.1},
          palettes=['sakura', 'sky', 'violet', 'aurora'], parts=[pulse] + tentacles)


def pet_mushroom():
    stem = rbox(SH + 0.6, -2.4, -0.8, SH + 2.4, -0.2, 1.0, '#F1E4C8', r=0.35)
    cap_ = rbox(SH - 0.2, -4.4, -1.6, SH + 3.2, -2.4, 1.8, 0, r=0.5) + rbox(SH + 0.6, -5.0, -0.8, SH + 2.4, -4.2, 1.0, 0, r=0.3)
    cap_ += [box(SH + 0.2, -4.0, -1.68, SH + 0.8, -3.4, -1.58, W), box(SH + 2.0, -4.6, -1.0, SH + 2.6, -4.4, -0.4, W), box(SH + 1.2, -3.6, -1.68, SH + 1.6, -3.2, -1.6, W)]
    cap_ += [box(SH - 0.25, -2.55, -1.65, SH + 3.25, -2.35, 1.85, '#E6D6B4')]
    face = [box(SH + 1.3, -1.2, -0.88, SH + 1.7, -1.0, -0.8, EYE)]
    eyes = [eye(SH + 1.1, -1.6, -0.85, 0.32, 0.0), eye(SH + 1.9, -1.6, -0.85, 0.32, 0.0)]
    parts = [P(cap_, A('rot', axis='z', pivot=[SH + 1.5, -2.4, 0.1], amp=4, speed=0.18))] + eyes
    model('pet_mushroom', 'pet', 'Mushroom Buddy', 'common', 'body', stem + face, anim=dict(BOB),
          palettes=['crimson', 'ember', 'violet', 'coffee'], parts=parts)


pet_chicken()
pet_dragon()
pet_slime()
pet_ghost()
pet_bee()
pet_frog()
pet_owl()
pet_penguin()
pet_cat()
pet_fox()
pet_bat()
pet_drone()
pet_snail()
pet_jelly()
pet_mushroom()
PET_MODELS = ['pet_chicken', 'pet_dragon', 'pet_slime', 'pet_ghost', 'pet_bee', 'pet_frog', 'pet_owl', 'pet_penguin', 'pet_cat', 'pet_fox', 'pet_bat', 'pet_drone',
              'pet_snail', 'pet_jelly', 'pet_mushroom']

# ---------------------------------------------------------------- items

SLOTS = [
    ('cape', 'Capes'), ('head', 'Hats & Ears'), ('face', 'Face'), ('neck', 'Neck'), ('back', 'Wings & Back'), ('aura', 'Auras'), ('pet', 'Buddies'),
    ('nametag', 'Name Styles'),
]
LEGACY_CAPES = [('onyx', 'Onyx'), ('sunrise', 'Sunrise'), ('night', 'Starry Night'), ('sakura', 'Cherry Blossom'), ('ember', 'Ember'), ('egg', 'Fried Egg')]
pal_name = {p[0]: p[1] for p in PALETTES}

items = []
for cid, name in LEGACY_CAPES:
    items.append({'id': cid, 'name': name, 'slot': 'cape', 'texture': cid, 'tier': 'rare', 'tags': ['classic', 'original']})
for pid in PATTERNS:
    label, base, _, _ = PATTERNS[pid]
    for pal in PALETTE_IDS:
        items.append({'id': f'cape_{pid}_{pal}', 'name': f'{pal_name[pal]} {label} Cape', 'slot': 'cape', 'pattern': pid, 'palette': pal, 'tier': tier(base, pal)})


def add_models(ids, count, stride=7):
    for i, mid in enumerate(ids):
        m = MODELS[mid]
        pals = m['palettes'] or (PALETTE_IDS if count is None else pick(i, count, stride))
        for pal in pals:
            items.append({'id': f'{mid}_{pal}', 'name': f"{pal_name[pal]} {m['name']}", 'slot': m['slot'], 'model': mid, 'palette': pal, 'tier': tier(m['tier'], pal)})


add_models(HEAD_MODELS, 9)
add_models(FACE_MODELS, 9)
add_models(NECK_MODELS, 12)
add_models(WING_MODELS, None)
add_models(BACK_MODELS, 9)
add_models(AURA_MODELS, 12)
add_models(PET_MODELS, 4)

# made from CC0 art by Kenney and JoeEnderman (tools/cc0_cosmetics.py)
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cc0_cosmetics
cc0_count = cc0_cosmetics.build(MODELS, items, CAPE_DIRS) if __name__ == '__main__' else 0

# name styles for your name tag (tools/nametags.py)
import nametags
LOOK_KINDS = nametags.build(items)

# ids the old selection used (cosmetics.json from earlier versions)
ALIASES = {'comb': 'comb_crimson', 'crown': 'crown_gold', 'tophat': 'tophat_onyx', 'halo': 'halo_gold', 'party': 'party_candy',
           'wings': 'wings_feather_snow', 'firewings': 'wings_feather_lava', 'shoulder_chicken': 'pet_chicken_snow'}
ids = {it['id'] for it in items}
# make sure alias targets exist even if the palette rotation did not pick them
for old, new in ALIASES.items():
    if new not in ids:
        mid, pal = new.rsplit('_', 1)
        m = MODELS[mid]
        items.append({'id': new, 'name': f"{pal_name[pal]} {m['name']}", 'slot': m['slot'], 'model': mid, 'palette': pal, 'tier': tier(m['tier'], pal)})
        ids.add(new)

# prices in coins (1 coin = 1 minute played with Onix); the first cosmetics stay free for everyone
PRICES = {'common': 60, 'rare': 150, 'epic': 400, 'legendary': 1000}
FREE = {it['id'] for it in items if 'original' in it.get('tags', [])} | set(ALIASES.values())
for it in items:
    if 'price' not in it:
        it['price'] = 0 if it['id'] in FREE else PRICES[it['tier']]

assert len(ids) == len(items), 'duplicate ids'
names = [it['name'] for it in items]
assert len(set(names)) == len(names), 'duplicate names: ' + str([n for n in names if names.count(n) > 1][:5])

catalog = {
    'version': 2,
    'slots': [{'id': s, 'name': n} for s, n in SLOTS],
    'palettes': {pid: {'name': n, 'colors': palette_colours(c)} for pid, n, c in PALETTES},
    'emblems': EMBLEMS,
    'patterns': {pid: dict({'name': v[0], 'ops': v[2]}, **({'anim': v[3]} if v[3] else {})) for pid, v in PATTERNS.items()},
    'models': {mid: {k: v for k, v in m.items() if k != 'palettes'} for mid, m in MODELS.items()},
    'aliases': ALIASES,
    'items': items,
}

if __name__ == '__main__':
    data = json.dumps(catalog, separators=(',', ':'))
    for path in OUT:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as f:
            f.write(data)
    # the launcher page loads it as a script (fetch cannot read local files)
    with open(OUT[1][:-5] + '.js', 'w') as f:
        f.write('/* generated by tools/cosmetics.py */\nwindow.ONIX_COSMETICS = ' + data + ';\n')
    # the server only needs the prices
    with open(os.path.join(ROOT, 'server', 'shop.json'), 'w') as f:
        json.dump({'version': catalog['version'], 'prices': {it['id']: it['price'] for it in items}, 'aliases': ALIASES,
                   'looks': LOOK_KINDS}, f, separators=(',', ':'))
    counts = {}
    for it in items:
        counts[it['slot']] = counts.get(it['slot'], 0) + 1
    print(f'{len(items)} cosmetics', counts, f'{len(data) // 1024} KB', f'{len(FREE)} free')
