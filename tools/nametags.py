"""
Name styles (slot "nametag", tools/cosmetics.py adds them to the catalog): colour your name above your head and in
the tab list. Other Onix players see them too (the server checks you own them, see server/cloudflare).
(Tags in front of the name existed in 1.0.31-1.0.32 and were removed again; the server refunded them.)

Every item has a "look": {type, colors, bold, speed}
  solid    – one colour
  gradient – the colours spread over the letters
  wave     – a gradient that moves along the name
  rainbow  – every colour of the rainbow, moving
  pulse    – the whole name fades between the colours
  shine    – the first colour with a bright stripe (second colour) running over it
The mod (cosmetics/NameTags.java) and the launcher (src/js/cosmetics.js) draw the same looks.
"""

slug = lambda s: ''.join(ch if ch.isalnum() else '_' for ch in s.lower()).strip('_')


def look(kind, colors, bold=False, speed=1.0):
    out = {'type': kind, 'colors': colors}
    if bold:
        out['bold'] = True
    if speed != 1.0:
        out['speed'] = speed
    return out


# (name, look, tier)
NAME_STYLES = [
    # one colour
    ('Crimson', look('solid', ['#FF4D4D']), 'common'),
    ('Tangerine', look('solid', ['#FF9A3C']), 'common'),
    ('Sunflower', look('solid', ['#FFD84D']), 'common'),
    ('Lime', look('solid', ['#8CFF5A']), 'common'),
    ('Emerald', look('solid', ['#3DDC84']), 'common'),
    ('Aqua', look('solid', ['#4DE8FF']), 'common'),
    ('Sky', look('solid', ['#5AA9FF']), 'common'),
    ('Royal Blue', look('solid', ['#6C7BFF']), 'common'),
    ('Violet', look('solid', ['#B46CFF']), 'common'),
    ('Bubblegum', look('solid', ['#FF7AD9']), 'common'),
    ('Gold', look('solid', ['#FFC23D']), 'common'),
    ('Silver', look('solid', ['#C9D1D9']), 'common'),
    # gradients
    ('Sunset', look('gradient', ['#FF5F6D', '#FFC371']), 'rare'),
    ('Ocean', look('gradient', ['#2E8BFF', '#4DFFE1']), 'rare'),
    ('Forest', look('gradient', ['#2ECC71', '#A8FF60']), 'rare'),
    ('Candy', look('gradient', ['#FF7AD9', '#7AD7FF']), 'rare'),
    ('Lava', look('gradient', ['#FF2D2D', '#FFB13D']), 'rare'),
    ('Frost', look('gradient', ['#E0F7FF', '#5AB8FF']), 'rare'),
    ('Grape', look('gradient', ['#8E2DE2', '#FF6CF0']), 'rare'),
    ('Peach', look('gradient', ['#FF9A8B', '#FFD3A5']), 'rare'),
    ('Mint', look('gradient', ['#00F5A0', '#00D9F5']), 'rare'),
    ('Ember', look('gradient', ['#B0302A', '#FF8A3D']), 'rare'),
    ('Dusk', look('gradient', ['#6C7BFF', '#FF7AD9', '#FFC371']), 'rare'),
    # moving
    ('Aurora', look('wave', ['#3DFFB0', '#3D9BFF', '#B46CFF']), 'epic'),
    ('Fire', look('wave', ['#FF2D2D', '#FF8A3D', '#FFE14D']), 'epic'),
    ('Galaxy', look('wave', ['#4B2DFF', '#B46CFF', '#FF6CF0', '#4DE8FF']), 'epic'),
    ('Toxic', look('wave', ['#7CFF3D', '#2DFF9A', '#D4FF3D']), 'epic'),
    ('Cotton Candy', look('wave', ['#FF9AE6', '#9AD8FF']), 'epic'),
    ('Heartbeat', look('pulse', ['#FF2D55', '#FFB0C0']), 'epic'),
    ('Neon', look('pulse', ['#00FFE1', '#FF00D4'], speed=1.5), 'epic'),
    ('Golden Shine', look('shine', ['#E0A21E', '#FFF6D0']), 'epic'),
    ('Diamond Shine', look('shine', ['#38C8E8', '#FFFFFF']), 'epic'),
    ('Onix Shine', look('shine', ['#8B84FF', '#FFFFFF']), 'epic'),
    # the best ones
    ('Rainbow', look('rainbow', [], bold=True), 'legendary'),
    ('Prism', look('rainbow', [], speed=2.5), 'legendary'),
    ('Onix Royal', look('wave', ['#5B57A0', '#8B84FF', '#C5C2FF'], bold=True), 'legendary'),
    ('Inferno', look('wave', ['#FF1A1A', '#FF8A3D', '#FFE14D'], bold=True, speed=2.0), 'legendary'),
    ('Midas', look('shine', ['#FFB800', '#FFFFFF'], bold=True, speed=1.4), 'legendary'),
]

def build(items):
    """Appends the name styles to the catalog's items. Returns {id: 'nametag'} for the server."""
    kinds = {}
    for name, lk, tier in NAME_STYLES:
        iid = 'name_' + slug(name)
        items.append({'id': iid, 'name': f'{name} Name', 'slot': 'nametag', 'tier': tier, 'look': lk, 'tags': ['name', 'nametag', lk['type']]})
        kinds[iid] = 'nametag'
    return kinds
