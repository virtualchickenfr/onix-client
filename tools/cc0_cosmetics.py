"""
Onix cosmetics made from CC0 (public domain) art – see tools/cc0-art/SOURCES.md:
  - Kenney, Voxel Pack (CC0): weapons and tools, ores        https://kenney.nl
  - JoeEnderman, Assorted Minecraft Style Textures (CC0): blocks, ingots, tools
CC0 allows using, changing and selling the art without asking and without credit; Onix credits the artists anyway.

Two kinds of cosmetics come out of it:
  - back items: a 16 x 16 tool or weapon sprite as a 3D pixel model (every pixel a little cube, like a held item)
  - capes: a block texture tiled over the cape, with a darker frame
Used by tools/cosmetics.py (models, items, cape PNGs).
"""

import os

try:
    from PIL import Image
except ImportError:  # the catalog can still be built, just without these
    Image = None

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.join(HERE, 'cc0-art')

# (file, name, tier)
BACK_ITEMS = []
for mat, label, tier in [('wood', 'Wooden', 'common'), ('stone', 'Stone', 'common'), ('bronze', 'Bronze', 'common'), ('steel', 'Steel', 'rare'),
                         ('gold', 'Golden', 'epic'), ('mese', 'Mese', 'epic'), ('diamond', 'Diamond', 'legendary')]:
    for tool, tlabel in [('sword', 'Sword'), ('axe', 'Axe'), ('pick', 'Pickaxe')]:
        BACK_ITEMS.append((f'joeenderman/default_tool_{mat}{tool}.png', f'{label} {tlabel}', tier))
for mat, label, tier in [('silver', 'Silver', 'rare'), ('mithril', 'Mithril', 'legendary')]:
    for tool, tlabel in [('sword', 'Sword'), ('axe', 'Axe'), ('pick', 'Pickaxe')]:
        BACK_ITEMS.append((f'joeenderman/moreores_tool_{mat}{tool}.png', f'{label} {tlabel}', tier))
for tool, tlabel in [('sword', 'Sword'), ('axe', 'Axe'), ('pick', 'Pickaxe')]:
    BACK_ITEMS.append((f'joeenderman/nether_tool_murexium_{tool}.png' if tool != 'sword' else 'joeenderman/nether_tool_murexium_sword.png', f'Murexium {tlabel}', 'legendary'))
    BACK_ITEMS.append((f'joeenderman/nether_tool_nether{tool}.png', f'Nether {tlabel}', 'epic'))
for mat, label, tier in [('bronze', 'Bronze', 'common'), ('iron', 'Iron', 'common'), ('silver', 'Silver', 'rare'), ('gold', 'Gold', 'epic'), ('diamond', 'Diamond', 'legendary')]:
    for tool, tlabel in [('sword', 'Blade'), ('axe', 'Battle Axe'), ('pick', 'Miner Pick'), ('hammer', 'Hammer'), ('flail', 'Flail')]:
        BACK_ITEMS.append((f'kenney/{tool}_{mat}.png', f'{label} {tlabel}', tier))
BACK_ITEMS += [('kenney/bow.png', 'Hunter Bow', 'rare'), ('kenney/fishingPole.png', 'Fishing Rod', 'common')]

# (file, name, tier)
CAPE_BLOCKS = [
    ('joeenderman/default_gold_block.png', 'Gold Block', 'epic'), ('joeenderman/default_diamond_block.png', 'Diamond Block', 'legendary'),
    ('joeenderman/default_diamond_block_alt.png', 'Diamond Tiles', 'legendary'), ('joeenderman/default_obsidian.png', 'Obsidian', 'rare'),
    ('joeenderman/default_obsidian_brick.png', 'Obsidian Brick', 'rare'),
    ('joeenderman/default_copper_block.png', 'Copper Block', 'common'), ('joeenderman/xcopper_oxidized_block.png', 'Oxidized Copper', 'rare'),
    ('joeenderman/default_bronze_block.png', 'Bronze Block', 'common'), ('joeenderman/default_steel_block.png', 'Steel Block', 'common'),
    ('joeenderman/default_tin_block.png', 'Tin Block', 'common'), ('joeenderman/default_mese_block.png', 'Mese Block', 'epic'),
    ('joeenderman/default_coal_block.png', 'Coal Block', 'common'), ('joeenderman/default_cobble.png', 'Cobblestone', 'common'),
    ('joeenderman/default_mossycobble.png', 'Mossy Cobble', 'common'), ('joeenderman/granite_block.png', 'Granite', 'common'),
    ('joeenderman/granite_brick.png', 'Granite Brick', 'common'), ('joeenderman/default_desert_sandstone_brick.png', 'Sandstone Brick', 'common'),
    ('joeenderman/moreores_mithril_block.png', 'Mithril Block', 'legendary'), ('joeenderman/moreores_silver_block.png', 'Silver Block', 'rare'),
    ('joeenderman/nether_murexium_block.png', 'Murexium Block', 'legendary'), ('joeenderman/nether_brick_deep.png', 'Deep Nether Brick', 'rare'),
    ('joeenderman/crystal.png', 'Crystal', 'epic'),
    ('joeenderman/cherry blossom wood plank.png', 'Cherry Planks', 'rare'), ('joeenderman/dark wood plank.png', 'Dark Planks', 'common'),
    ('joeenderman/gold wood plank.png', 'Golden Planks', 'epic'), ('joeenderman/ebony plank.png', 'Ebony Planks', 'rare'),
    ('joeenderman/red wood plank 2.png', 'Redwood Planks', 'common'), ('joeenderman/default_junglewood.png', 'Jungle Planks', 'common'),
    ('kenney/ore_diamond.png', 'Diamond Ore', 'epic'), ('kenney/ore_emerald.png', 'Emerald Ore', 'epic'),
    ('kenney/ore_ruby.png', 'Ruby Ore', 'epic'), ('kenney/ore_gold.png', 'Gold Ore', 'rare'),
]

slug = lambda s: ''.join(ch if ch.isalnum() else '_' for ch in s.lower()).strip('_')


def sprite(path, size=16):
    im = Image.open(os.path.join(ART, path)).convert('RGBA')
    if im.size != (size, size):
        im = im.resize((size, size), Image.NEAREST)
    return im


def sprite_cubes(path):
    """Every opaque pixel as a cube (runs of one colour in a row merged), flat on the back like a carried item."""
    im = sprite(path)
    px = im.load()
    s = 0.8                      # model pixels per sprite pixel: the item is 12.8 px tall
    cubes = []
    for y in range(16):
        x = 0
        while x < 16:
            r, g, b, a = px[x, y]
            if a < 128:
                x += 1
                continue
            x2 = x
            while x2 + 1 < 16 and px[x2 + 1, y] == px[x, y]:
                x2 += 1
            col = '#%02X%02X%02X' % (r, g, b)
            # sprite x to the right = the player's left seen from behind; y down; centred on the back
            x1m, x2m = (8 - (x2 + 1)) * s, (8 - x) * s
            y1m, y2m = 6 + (y - 8) * s, 6 + (y + 1 - 8) * s
            cubes.append([round(x1m, 3), round(y1m, 3), 2.2, round(x2m, 3), round(y2m, 3), 3.0, col, 0])
            x = x2 + 1
    return cubes


def cape_png(path, out):
    """64 x 32 vanilla cape layout filled with the block texture (1:1 pixels), the front and back framed."""
    tex = sprite(path)
    cape = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    tp = tex.load()
    cp = cape.load()

    def fill(x0, y0, w, h, frame=False, ox=0, oy=0):
        for y in range(h):
            for x in range(w):
                r, g, b, a = tp[(x + ox) % 16, (y + oy) % 16]
                if a < 255:   # see-through pixels (glass) get a dark backing
                    r, g, b = [round(v * a / 255 + 24 * (1 - a / 255)) for v in (r, g, b)]
                if frame and (x == 0 or y == 0 or x == w - 1 or y == h - 1):
                    r, g, b = round(r * 0.55), round(g * 0.55), round(b * 0.55)
                cp[x0 + x, y0 + y] = (r, g, b, 255)

    fill(1, 1, 10, 16, True, 3, 0)      # outside (seen from behind)
    fill(12, 1, 10, 16, True, 3, 0)     # inside
    fill(0, 1, 1, 16)                   # sides
    fill(11, 1, 1, 16)
    fill(1, 0, 10, 1)                   # top
    fill(11, 0, 10, 1)                  # bottom
    cape.save(out)


def build(models, items, cape_dirs):
    """Adds the CC0 back items (models) and capes (textures) to the catalog under construction."""
    if Image is None or not os.path.isdir(ART):
        print('cc0 cosmetics skipped (Pillow or tools/cc0-art missing)')
        return 0
    n = 0
    for path, name, tier in BACK_ITEMS:
        if not os.path.exists(os.path.join(ART, path)):
            continue
        mid = 'cc0_' + slug(name)
        models[mid] = {'slot': 'back', 'name': name, 'tier': tier, 'anchor': 'body', 'cubes': sprite_cubes(path),
                       'anim': {'type': 'static'}, 'kind': 'back'}
        items.append({'id': mid, 'name': f'{name} (on the back)', 'slot': 'back', 'model': mid, 'palette': 'snow', 'tier': tier,
                      'tags': ['cc0', 'pixel', 'tool'], 'credit': 'Kenney (CC0)' if path.startswith('kenney') else 'JoeEnderman (CC0)'})
        n += 1
    for path, name, tier in CAPE_BLOCKS:
        if not os.path.exists(os.path.join(ART, path)):
            continue
        cid = 'cc0_cape_' + slug(name)
        for d in cape_dirs:
            os.makedirs(d, exist_ok=True)
            cape_png(path, os.path.join(d, cid + '.png'))
        items.append({'id': cid, 'name': f'{name} Cape', 'slot': 'cape', 'texture': cid, 'tier': tier,
                      'tags': ['cc0', 'block'], 'credit': 'Kenney (CC0)' if path.startswith('kenney') else 'JoeEnderman (CC0)'})
        n += 1
    return n
