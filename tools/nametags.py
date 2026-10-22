"""
Name tag cosmetics (tools/cosmetics.py adds them to the catalog):
  - name styles (slot "nametag"): colours your name above your head and in the tab list
  - tags (slot "tag"): a short tag in front of your name, preset ("[PRO]", "★") or your own text (custom tags)
Other Onix players see both (the server checks you own them, see server/cloudflare).

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

# (text, look, tier); one or two symbols are drawn without brackets
TAGS = [
    ('★', look('solid', ['#FFD84D']), 'common'),
    ('♥', look('solid', ['#FF5C8A']), 'common'),
    ('✦', look('solid', ['#B46CFF']), 'common'),
    ('☀', look('solid', ['#FFB13D']), 'common'),
    ('☾', look('solid', ['#9AB8FF']), 'common'),
    ('❄', look('solid', ['#BDEBFF']), 'common'),
    ('♪', look('solid', ['#4DE8FF']), 'common'),
    ('✿', look('solid', ['#FF9AE6']), 'common'),
    ('⚔', look('solid', ['#E0E6EE']), 'rare'),
    ('☠', look('solid', ['#D0D0D8']), 'rare'),
    ('♛', look('shine', ['#FFC23D', '#FFF6D0']), 'epic'),
    ('GG', look('solid', ['#3DDC84']), 'common'),
    ('W', look('solid', ['#5AA9FF']), 'common'),
    ('AFK', look('solid', ['#9AA0AA']), 'common'),
    ('NOOB', look('solid', ['#8CFF5A']), 'common'),
    ('PVP', look('solid', ['#FF4D4D']), 'common'),
    ('BUILDER', look('solid', ['#FFB13D']), 'common'),
    ('MINER', look('solid', ['#C9D1D9']), 'common'),
    ('CHICKEN', look('gradient', ['#FFFFFF', '#FFD84D']), 'rare'),
    ('PRO', look('gradient', ['#2E8BFF', '#4DFFE1']), 'rare'),
    ('SWEAT', look('gradient', ['#4DE8FF', '#2E8BFF']), 'rare'),
    ('BOSS', look('gradient', ['#FF2D2D', '#FFB13D']), 'rare'),
    ('CHILL', look('gradient', ['#00F5A0', '#00D9F5']), 'rare'),
    ('KING', look('shine', ['#FFC23D', '#FFF6D0']), 'epic'),
    ('QUEEN', look('shine', ['#FF7AD9', '#FFE6F7']), 'epic'),
    ('OG', look('shine', ['#E0A21E', '#FFF6D0'], bold=True), 'epic'),
    ('ONIX', look('wave', ['#5B57A0', '#8B84FF', '#C5C2FF'], bold=True), 'epic'),
    ('TRYHARD', look('wave', ['#FF2D2D', '#FF8A3D', '#FFE14D']), 'epic'),
    ('LEGEND', look('rainbow', [], bold=True), 'legendary'),
    ('GOAT', look('wave', ['#FFB800', '#FFFFFF', '#FFB800'], bold=True), 'legendary'),
]

# your own text; (name, look, tier, price)
CUSTOM_TAGS = [
    ('Custom Tag', look('solid', ['#FFFFFF']), 'rare', 300),
    ('Custom Gradient Tag', look('wave', ['#FF7AD9', '#7AD7FF']), 'epic', 600),
    ('Custom Gold Tag', look('shine', ['#E0A21E', '#FFF6D0'], bold=True), 'epic', 800),
    ('Custom Rainbow Tag', look('rainbow', [], bold=True), 'legendary', 1200),
]

# what a custom tag may say (checked again by the mod and the server)
CUSTOM_MAX = 12


def build(items):
    """Appends the name styles and tags to the catalog's items. Returns {id: 'nametag' | 'tag' | 'custom'} for the server."""
    kinds = {}
    for name, lk, tier in NAME_STYLES:
        iid = 'name_' + slug(name)
        items.append({'id': iid, 'name': f'{name} Name', 'slot': 'nametag', 'tier': tier, 'look': lk, 'tags': ['name', 'nametag', lk['type']]})
        kinds[iid] = 'nametag'
    for text, lk, tier in TAGS:
        symbol = not any(ch.isalnum() for ch in text)
        iid = 'tag_' + (slug(text) or 'sym_%04x' % ord(text[0]))
        items.append({'id': iid, 'name': f'{text} Tag' if symbol else f'[{text}] Tag', 'slot': 'tag', 'tier': tier, 'look': lk,
                      'tagText': text, 'tags': ['tag', 'nametag', lk['type']]})
        kinds[iid] = 'tag'
    for name, lk, tier, price in CUSTOM_TAGS:
        iid = 'tag_' + slug(name)
        items.append({'id': iid, 'name': name, 'slot': 'tag', 'tier': tier, 'look': lk, 'custom': True, 'price': price,
                      'tags': ['tag', 'nametag', 'custom', 'own text', lk['type']]})
        kinds[iid] = 'custom'
    return kinds
