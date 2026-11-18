#!/usr/bin/env python3
"""
Renders the "Meadow" design: thousands of flowers on a grey meadow at night – a black sky with a big red moon – with
depth of field like a
photo (far meadow soft, the middle sharp, blossoms right in front of the lens blurred), and the loose petals that fly
up when you press Play.

The meadow is rendered twice from the same random numbers: once in bloom and once "bare" (every flower without its
petals, only the heart and the stem). The positions, sizes and kinds of the flowers you can see clearly are written
out too, so the Play animation can lift each flower's own petals off at the right place and fade to the bare meadow
underneath – the petals really come from the flowers.

    python3 tools/meadow.py            # writes the files below

Outputs (launcher/src/assets/designs and mod-1.21.11/.../textures/gui/design)
  meadow.jpg / meadow.png              the meadow in bloom (1920x1080 launcher, 1600x900 game)
  meadow_bare.jpg / meadow_bare.png    the same meadow without petals
  meadow_flowers.json                  [x, y, size, kind, flat, rotation, petals, petal length, petal width] per flower,
                                       x/y/size as fractions of the picture's width and height
  petals.png                           petal sprite sheet: 8 petals of 64x64 in a row, petal i has the colour of kind i
The same pictures come out every time (fixed random seed).
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
W, H = 2400, 1350              # render size (downscaled for the outputs)
SS = 2                         # supersampling for the drawn shapes
HORIZON = 0.34                 # horizon height (fraction of H)
SEED = 1907
rng = random.Random(SEED)
BARE = False            # render the flowers without their petals
FLOWERS = []            # the clearly visible flowers of the last render (see main)

# flower kinds: petal colour (light, dark), centre colour, petal count, petal length/width
KINDS = [
    ((246, 246, 243), (196, 198, 200), (226, 196, 92), 13, 1.0, 0.30),     # daisy
    ((250, 250, 248), (205, 206, 210), (214, 186, 84), 9, 0.9, 0.38),      # white anemone
    ((236, 214, 222), (184, 150, 166), (220, 190, 110), 8, 1.05, 0.42),    # pale pink cosmos
    ((226, 222, 238), (170, 162, 190), (210, 200, 150), 6, 0.8, 0.46),     # lilac
    ((238, 232, 214), (190, 182, 160), (200, 160, 80), 11, 0.85, 0.34),    # cream
]
WEIGHTS = [5, 3, 2, 2, 2]


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


MOON = (0.70, 0.19, 0.062)       # centre x, y and radius as fractions of the picture (radius of the height)


def sky():
    """A black night sky, a touch of red near the horizon, faint dark clouds."""
    y = np.linspace(0, 1, H)[:, None]
    top = np.array([4, 4, 6], float)
    low = np.array([26, 15, 17], float)
    img = np.where(y < HORIZON, top + (low - top) * (y / HORIZON) ** 1.8, low)
    arr = np.broadcast_to(img.reshape(H, 1, 3), (H, W, 3)).copy()
    yy, xx = np.mgrid[0:H, 0:W]
    for _ in range(14):
        cx, cy = rng.uniform(-0.1, 1.1) * W, rng.uniform(0.02, HORIZON * 0.9) * H
        rx, ry = rng.uniform(0.15, 0.4) * W, rng.uniform(0.03, 0.08) * H
        d = ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2
        arr += (np.exp(-d * 1.6) * rng.uniform(2, 6))[..., None] * np.array([1.0, 0.55, 0.55])
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).convert('RGBA')


def moon(arr):
    """The red moon with its glow, drawn into the finished picture (float RGB array)."""
    cx, cy, r = MOON[0] * W, MOON[1] * H, MOON[2] * H
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    # a wide red glow in the sky, stronger close to the disc; the meadow below catches a little of it
    glow = np.exp(-((d / (r * 7)) ** 1.3)) * 70 + np.exp(-((d / (r * 2.2)) ** 2)) * 60
    sky_part = np.clip((HORIZON * H + 30 - yy) / 60, 0.25, 1)
    arr += (glow * sky_part)[..., None] * np.array([1.0, 0.18, 0.12])
    # the disc: deep red, brighter in the middle, darker at the rim, soft surface marks
    inside = d < r
    t = np.clip(d / r, 0, 1)
    base = np.array([214, 46, 34], float) * (1 - 0.35 * t ** 2)[..., None]
    nrng = np.random.default_rng(3)
    marks = np.zeros((H, W))
    for _ in range(9):
        mx, my = cx + nrng.uniform(-0.6, 0.6) * r, cy + nrng.uniform(-0.6, 0.6) * r
        mr = nrng.uniform(0.12, 0.32) * r
        marks += np.exp(-(((xx - mx) ** 2 + (yy - my) ** 2) / (mr * mr))) * nrng.uniform(0.15, 0.3)
    base *= (1 - np.clip(marks, 0, 0.45))[..., None]
    edge = np.clip((r - d) / 2.0, 0, 1)                    # anti-aliased rim
    arr[inside] = arr[inside] * (1 - edge[inside, None]) + base[inside] * edge[inside, None]
    return arr


def ground():
    """The meadow floor: a fine grass texture, grey-green, lighter and hazier towards the horizon."""
    y0 = int(HORIZON * H)
    gh = H - y0
    t = (np.arange(gh) / gh)[:, None]
    far = np.array([34, 26, 28], float)
    near = np.array([40, 42, 40], float)
    col = np.broadcast_to((far + (near - far) * t ** 0.65)[:, None, :], (gh, W, 3)).copy()
    nrng = np.random.default_rng(11)
    tex = np.zeros((gh, W))
    # vertical streaks, short far away and long close by
    for stretch, weight, lo, hi in ((2, 1.0, 0.0, 0.35), (5, 1.0, 0.2, 0.7), (12, 1.0, 0.55, 1.0)):
        n = nrng.normal(0, 1, (max(2, gh // stretch), W))
        st = np.array(Image.fromarray(((n * 40) + 128).clip(0, 255).astype(np.uint8)).resize((W, gh), Image.BILINEAR)).astype(float) - 128
        wgt = np.clip(1 - np.abs(t - (lo + hi) / 2) / ((hi - lo) / 2 + 0.15), 0, 1)
        tex += st * wgt * weight
    col += tex[..., None] * (0.10 + 0.22 * t)[..., None]
    # large soft patches of darker and lighter grass
    yy, xx = np.mgrid[0:gh, 0:W]
    for _ in range(30):
        cx, cy = rng.uniform(0, W), rng.uniform(0, gh)
        r = rng.uniform(0.06, 0.25) * W * (0.3 + cy / gh)
        dd = (xx - cx) ** 2 / (r * r) + (yy - cy) ** 2 / (r * r * 0.1)
        col += (np.exp(-dd) * rng.uniform(-12, 9))[..., None]
    arr = np.zeros((H, W, 4), np.uint8)
    arr[y0:, :, :3] = np.clip(col, 0, 255).astype(np.uint8)
    arr[y0:, :, 3] = 255
    return Image.fromarray(arr, 'RGBA')


def treeline(hy):
    """A far, hazy line of hills and bushes along the horizon (summed waves, heavily blurred)."""
    xs = np.arange(W)
    hgt = np.zeros(W)
    for k in range(9):
        f = rng.uniform(0.002, 0.03) * (k + 1) ** 0.6
        hgt += np.sin(xs * f + rng.uniform(0, 6.3)) * rng.uniform(4, 22) / (k + 1) ** 0.4
    hgt = 18 + hgt - hgt.min() * 0.6
    yy = np.arange(H)[:, None]
    mask = (yy > hy - hgt[None, :]) & (yy < hy + 8)
    arr = np.zeros((H, W, 4), np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2] = 14, 11, 12
    arr[..., 3] = mask * 255
    return Image.fromarray(arr, 'RGBA').filter(ImageFilter.GaussianBlur(5))


def depth_at(y):
    """0 at the horizon, 1 at the bottom edge."""
    return max(0.0, (y / H - HORIZON) / (1 - HORIZON))


def ellipse_poly(cx, cy, rx, ry, ang, n=14):
    ca, sa = math.cos(ang), math.sin(ang)
    out = []
    for i in range(n):
        t = 2 * math.pi * i / n
        ex, ey = math.cos(t) * rx, math.sin(t) * ry
        out.append(((cx + ex * ca - ey * sa) * SS, (cy + ex * sa + ey * ca) * SS))
    return out


def flower(draw, x, y, size, kind, tilt, record=False, keep_petals=False):
    light, dark, centre, n, plen, pwid = kind
    bare = BARE and not keep_petals
    # stem
    stem_h = size * rng.uniform(0.8, 1.6)
    sc = lerp((58, 54, 54), (38, 44, 38), min(1, size / 40))
    draw.line([(x * SS, y * SS), ((x + tilt * stem_h * 0.2) * SS, (y + stem_h) * SS)], fill=sc + (255,), width=max(1, int(size * 0.06 * SS)))
    # head seen from slightly above: flattened vertically
    flat = rng.uniform(0.55, 0.95)
    rot0 = rng.uniform(0, math.pi * 2)
    if record:
        FLOWERS.append([x / W, y / H, size / W, KINDS.index(kind), round(flat, 3), round(rot0, 3), n, plen, pwid])
    for i in range(n):
        a = rot0 + 2 * math.pi * i / n + rng.uniform(-0.08, 0.08)
        L = size * plen * rng.uniform(0.85, 1.1)
        Wd = size * pwid * rng.uniform(0.85, 1.1)
        px = x + math.cos(a) * L * 0.5
        py = y + math.sin(a) * L * 0.5 * flat
        # petals at the back (top) are in shade, the front ones catch the light
        lit = 0.5 + 0.5 * math.sin(a)
        c = lerp(dark, light, 0.35 + 0.65 * lit) + (255,)
        ang = math.atan2(math.sin(a) * flat, math.cos(a))
        if bare:
            continue                                   # the same random numbers, nothing drawn
        draw.polygon(ellipse_poly(px, py, L * 0.5, Wd * 0.5, ang), fill=c)
        hi = lerp(c[:3], (255, 255, 255), 0.25) + (255,)
        draw.polygon(ellipse_poly(px - math.cos(a) * L * 0.08, py - math.sin(a) * L * 0.08 * flat, L * 0.34, Wd * 0.28, ang), fill=hi)
    cr = size * (0.24 if bare else 0.2)
    draw.polygon(ellipse_poly(x, y, cr, cr * flat, 0), fill=centre + (255,))
    draw.polygon(ellipse_poly(x - cr * 0.25, y - cr * 0.25 * flat, cr * 0.45, cr * 0.45 * flat, 0), fill=lerp(centre, (255, 245, 200), 0.5) + (255,))


def flower_layer(y_from, y_to, count, size_mul=1.0, grass_count=0, record=False):
    img = Image.new('RGBA', (W * SS, H * SS), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    items = []
    for _ in range(count):
        y = rng.uniform(y_from, y_to)
        items.append(('f', y))
    for _ in range(grass_count):
        items.append(('g', rng.uniform(y_from, y_to)))
    items.sort(key=lambda it: it[1])
    for kind, y in items:
        d = depth_at(y)
        if kind == 'g':
            grass_one(dr, y, d)
            continue
        size = (1.2 + 44 * d ** 1.8) * rng.uniform(0.7, 1.25) * size_mul
        k = rng.choices(KINDS, WEIGHTS)[0]
        flower(dr, rng.uniform(-30, W + 30), y, size, k, rng.uniform(-1, 1), record=record and size >= 4)
    return img.resize((W, H), Image.LANCZOS)


def grass_one(draw, y, d):
    x = rng.uniform(-40, W + 40)
    h = (4 + 70 * d ** 1.5) * rng.uniform(0.5, 1.2)
    w = (0.5 + 3.2 * d ** 1.4) * rng.uniform(0.7, 1.3)
    lean = rng.uniform(-0.45, 0.45)
    shade = rng.uniform(-20, 16)
    base = lerp((60, 54, 56), (40, 44, 40), d ** 0.8)
    c = tuple(max(0, min(255, int(v + shade))) for v in base) + (255,)
    pts_l, pts_r = [], []
    for i in range(7):
        k = i / 6
        bx = x + lean * h * k * k
        by = y - h * k
        half = w * (1 - k) * 0.5
        pts_l.append(((bx - half) * SS, by * SS))
        pts_r.append(((bx + half) * SS, by * SS))
    draw.polygon(pts_l + pts_r[::-1], fill=c)


def blur(img, r):
    return img.filter(ImageFilter.GaussianBlur(r)) if r > 0 else img


def render(bare=False):
    global rng, BARE
    rng = random.Random(SEED)
    BARE = bare
    FLOWERS.clear()
    base = sky()
    hy = HORIZON * H
    base.alpha_composite(treeline(hy))
    base.alpha_composite(ground())
    # depth bands, far to near: (y range, flowers, grass blades, blur radius)
    bands = [
        ((hy, hy + 0.07 * H), 3600, 1500, 1.8),
        ((hy + 0.05 * H, hy + 0.20 * H), 3000, 4000, 0.8),
        ((hy + 0.18 * H, hy + 0.44 * H), 2000, 5000, 0.0),
        ((hy + 0.42 * H, H * 1.04), 700, 2600, 2.0),
    ]
    for (a, b), nf, ng, r in bands:
        layer = flower_layer(a, b, nf, grass_count=ng, record=True)
        base.alpha_composite(blur(layer, r))
    # haze over the far meadow
    arr = np.array(base).astype(float)
    y = np.arange(H)[:, None]
    haze = np.clip(1 - np.abs(y - hy) / (0.12 * H), 0, 1) ** 1.3 * 0.8
    arr[..., :3] = arr[..., :3] * (1 - haze[..., None]) + np.array([40, 20, 22]) * haze[..., None]
    # blossoms right in front of the lens: big, very soft
    front = Image.new('RGBA', (W * SS, H * SS), (0, 0, 0, 0))
    fd = ImageDraw.Draw(front)
    for _ in range(26):
        x = rng.choice([rng.uniform(-80, W * 0.22), rng.uniform(W * 0.78, W + 80)])
        flower(fd, x, rng.uniform(H * 0.86, H * 1.08), rng.uniform(70, 130), rng.choices(KINDS, WEIGHTS)[0], rng.uniform(-1, 1), keep_petals=True)
    front = blur(front.resize((W, H), Image.LANCZOS), 9)
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')
    img.alpha_composite(front)
    # grade: cool grey, soft contrast, vignette, fine grain
    arr = np.array(img.convert('RGB')).astype(float)
    yy0, xx0 = np.mgrid[0:H, 0:W]
    # the flowers are lit by the moon: pale, a little reddish, brighter on the side of the moon
    lum = arr.mean(axis=2, keepdims=True)
    arr = lum + (arr - lum) * 0.6
    light = np.exp(-(((xx0 - W * MOON[0]) / (W * 0.6)) ** 2 + ((yy0 - H * 0.55) / (H * 0.5)) ** 2))
    arr = arr * (0.4 + 0.32 * light)[..., None] * np.array([1.0, 0.8, 0.8])
    arr = moon(arr)
    yy, xx = np.mgrid[0:H, 0:W]
    v = ((xx - W / 2) / (W * 0.62)) ** 2 + ((yy - H * 0.48) / (H * 0.7)) ** 2
    arr *= (1 - np.clip(v - 0.35, 0, 1) * 0.45)[..., None]
    arr += np.random.default_rng(7).normal(0, 2.2, arr.shape)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGB')


def petals():
    """8 loose petals, 64x64 each, lit from above, for the fly-up animation: petal i has the colour and shape of kind i
    (the last three are a little narrower or wider variants of the first ones). Pointing right, so they can be turned."""
    sheet = Image.new('RGBA', (64 * 8 * SS, 64 * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(sheet)
    for i in range(8):
        light, dark, _, _, plen, pwid = KINDS[i % len(KINDS)]
        cx, cy = 32 + 64 * i, 32
        L = 54 * (0.95 if i < 5 else 0.85)
        Wd = 54 * pwid * (1.0 if i < 5 else 1.25) * 1.15
        ang = 0.0
        for s in range(6, 0, -1):
            t = s / 6
            col = lerp(light, dark, t * 0.6) + (255,)
            d.polygon(ellipse_poly(cx - 2 * t, cy + 2 * t, L * 0.5 * (0.6 + 0.4 * t), Wd * 0.5 * (0.6 + 0.4 * t), ang, 24), fill=col)
        # a fine vein
        d.line([((cx - math.cos(ang) * L * 0.4) * SS, (cy - math.sin(ang) * L * 0.4) * SS), ((cx + math.cos(ang) * L * 0.3) * SS, (cy + math.sin(ang) * L * 0.3) * SS)], fill=dark + (120,), width=SS)
    return sheet.resize((64 * 8, 64), Image.LANCZOS)


def main():
    img = render()
    flowers = sorted(FLOWERS, key=lambda f: -f[2])[:1800]
    bare = render(bare=True)
    out_l = os.path.join(ROOT, 'launcher', 'src', 'assets', 'designs')
    out_m = os.path.join(ROOT, 'mod-1.21.11', 'src', 'main', 'resources', 'assets', 'onix', 'textures', 'gui', 'design')
    os.makedirs(out_l, exist_ok=True)
    os.makedirs(out_m, exist_ok=True)
    img.resize((1920, 1080), Image.LANCZOS).save(os.path.join(out_l, 'meadow.jpg'), quality=90, optimize=True, progressive=True)
    img.resize((1600, 900), Image.LANCZOS).save(os.path.join(out_m, 'meadow.png'), optimize=True)
    bare.resize((1920, 1080), Image.LANCZOS).save(os.path.join(out_l, 'meadow_bare.jpg'), quality=90, optimize=True, progressive=True)
    bare.resize((1600, 900), Image.LANCZOS).save(os.path.join(out_m, 'meadow_bare.png'), optimize=True)
    data = json.dumps([[round(v, 5) if isinstance(v, float) else v for v in f] for f in flowers], separators=(',', ':'))
    for d in (out_l, out_m):
        with open(os.path.join(d, 'meadow_flowers.json'), 'w') as out:
            out.write(data)
    p = petals()
    # in the moonlight: as dark and reddish as the flowers they come from, a little brighter where they catch the light
    pa = np.array(p).astype(float)
    pa[..., :3] *= np.array([0.78, 0.64, 0.64])
    p = Image.fromarray(np.clip(pa, 0, 255).astype(np.uint8), 'RGBA')
    p.save(os.path.join(out_l, 'petals.png'), optimize=True)
    p.save(os.path.join(out_m, 'petals.png'), optimize=True)
    print(f'meadow, bare meadow, {len(flowers)} flowers and petals written')


if __name__ == '__main__':
    main()
