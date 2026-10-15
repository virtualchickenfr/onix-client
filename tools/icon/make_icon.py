"""Draws the Onix app icon (faceted gem) in three colours: python3 make_icon.py → onix-icon-*.png (512 x 512).
The silver one is the app icon (launcher/build/icon.png, mod assets/onix/icon.png)."""
import math
from PIL import Image, ImageDraw, ImageFilter

S = 2048

def hexpts(cx, cy, r, rot=-90):
    return [(cx + r * math.cos(math.radians(rot + 60 * k)), cy + r * math.sin(math.radians(rot + 60 * k))) for k in range(6)]

def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))

def hexc(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))

def icon(light, base, deep, glow, bg1='#141B1E', bg2='#080B0D', name='icon'):
    L, B, D, G = map(hexc, (light, base, deep, glow))
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    # rounded square background with a soft vertical gradient
    bg = Image.new('RGBA', (S, S))
    c1, c2 = hexc(bg1), hexc(bg2)
    dr = ImageDraw.Draw(bg)
    for y in range(S):
        dr.line([(0, y), (S, y)], fill=mix(c1, c2, y / S) + (255,))
    mask = Image.new('L', (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.225), fill=255)
    im.paste(bg, (0, 0), mask)
    # glow
    cx, cy = S / 2, S / 2 + S * 0.01
    R, r = S * 0.30, S * 0.125
    gl = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(gl).polygon(hexpts(cx, cy, R * 1.05), fill=G + (150,))
    gl = gl.filter(ImageFilter.GaussianBlur(S * 0.07))
    gm = Image.new('L', (S, S), 0)
    gm.paste(mask, (0, 0))
    im = Image.alpha_composite(im, Image.composite(gl, Image.new('RGBA', (S, S), (0, 0, 0, 0)), gm))
    # faceted gem: 6 facets, light from the top right
    outer = hexpts(cx, cy, R)
    inner = hexpts(cx, cy, r)
    shade = [0.0, 0.25, 0.55, 0.95, 0.75, 0.35]   # facet k between vertex k and k+1 (k=0 at the top)
    d = ImageDraw.Draw(im)
    for k in range(6):
        t = shade[k]
        col = mix(L, B, t / 0.5) if t <= 0.5 else mix(B, D, (t - 0.5) / 0.5)
        quad = [outer[k], outer[(k + 1) % 6], inner[(k + 1) % 6], inner[k]]
        d.polygon(quad, fill=col + (255,))
    # thin highlight lines on the facet edges
    for k in range(6):
        d.line([outer[k], inner[k]], fill=mix(L, (255, 255, 255), 0.5) + (90,), width=int(S * 0.004))
    d.line(outer + [outer[0]], fill=mix(L, (255, 255, 255), 0.6) + (120,), width=int(S * 0.005))
    # dark centre with an inner bevel
    d.polygon(inner, fill=(14, 18, 21, 255))
    d.polygon(hexpts(cx, cy, r * 0.82), fill=(20, 26, 30, 255))
    d.line(inner + [inner[0]], fill=mix(D, (0, 0, 0), 0.4) + (255,), width=int(S * 0.006))
    # sparkle at the top right vertex
    sx, sy = outer[1][0] - S * 0.01, outer[1][1] - S * 0.02
    sp = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sp)
    a, b = S * 0.075, S * 0.012
    sd.polygon([(sx, sy - a), (sx + b, sy - b), (sx + a, sy), (sx + b, sy + b), (sx, sy + a), (sx - b, sy + b), (sx - a, sy), (sx - b, sy - b)], fill=(255, 255, 255, 255))
    halo = sp.filter(ImageFilter.GaussianBlur(S * 0.012))
    im = Image.alpha_composite(im, halo)
    im = Image.alpha_composite(im, sp)
    out = im.resize((512, 512), Image.LANCZOS)
    out.save(f'{name}.png')
    return out

variants = [
    ('mint', '#E2FFF4', '#4FE3B0', '#0C5A47', '#4FE3B0'),
    ('violet', '#ECEAFF', '#8B84FF', '#2A2470', '#8B84FF'),
    ('silver', '#F4F5F8', '#A7ADBA', '#3A3E48', '#B8C2D6'),
]
imgs = [icon(l, b, d, g, name=f"onix-icon-{n}") for n, l, b, d, g in variants]
sheet = Image.new('RGBA', (512 * 3 + 80, 512 + 40), (24, 26, 30, 255))
for i, im in enumerate(imgs):
    sheet.alpha_composite(im, (20 + i * 532, 20))
sheet.save('variants.png')
# small sizes check (how it looks in the Modrinth list)
small = Image.new('RGBA', (3 * 120, 120), (255, 255, 255, 255))
for i, im in enumerate(imgs):
    small.alpha_composite(im.resize((96, 96), Image.LANCZOS), (12 + i * 120, 12))
small.save('small.png')
