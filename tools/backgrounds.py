"""Procedural title-screen backgrounds: layered, block-stepped landscapes with atmospheric depth."""
import math
import sys
import numpy as np
from PIL import Image

W, H = 1280, 720
OUT = sys.argv[1]
ONLY = sys.argv[2:] if len(sys.argv) > 2 else None

BAYER = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5)


def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64)


def lerp(a, b, t):
    return a + (b - a) * t


def smooth(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)


def gradient(stops):
    """stops: list of (y 0..1, hex). Returns HxWx3."""
    ys = np.linspace(0, 1, H)
    out = np.zeros((H, 3))
    for i in range(len(stops) - 1):
        y0, c0 = stops[i]
        y1, c1 = stops[i + 1]
        m = (ys >= y0) & (ys <= y1)
        t = smooth((ys[m] - y0) / max(1e-6, y1 - y0))[:, None]
        out[m] = lerp(hexc(c0), hexc(c1), t)
    out[ys < stops[0][0]] = hexc(stops[0][1])
    out[ys > stops[-1][0]] = hexc(stops[-1][1])
    return np.repeat(out[:, None, :], W, axis=1)


def value_noise_1d(n, scale, seed, octaves=4):
    rng = np.random.default_rng(seed)
    x = np.arange(n) / scale
    total = np.zeros(n)
    amp, freq, norm = 1.0, 1.0, 0.0
    for _ in range(octaves):
        pts = rng.random(int(n / scale * freq) + 3)
        xi = x * freq
        i0 = np.floor(xi).astype(int)
        f = smooth(xi - i0)
        total += amp * lerp(pts[i0], pts[i0 + 1], f)
        norm += amp
        amp *= 0.5
        freq *= 2.0
    return total / norm


def noise_2d(h, w, scale, seed, octaves=4):
    rng = np.random.default_rng(seed)
    total = np.zeros((h, w))
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        s = scale / (2 ** o)
        gh, gw = int(h / s) + 3, int(w / s) + 3
        grid = rng.random((gh, gw))
        ys = np.arange(h) / s
        xs = np.arange(w) / s
        y0 = np.floor(ys).astype(int)
        x0 = np.floor(xs).astype(int)
        fy = smooth(ys - y0)[:, None]
        fx = smooth(xs - x0)[None, :]
        a = grid[y0][:, x0]
        b = grid[y0][:, x0 + 1]
        c = grid[y0 + 1][:, x0]
        d = grid[y0 + 1][:, x0 + 1]
        total += amp * lerp(lerp(a, b, fx), lerp(c, d, fx), fy)
        norm += amp
        amp *= 0.5
    return total / norm


def glow(img, cx, cy, radius, color, strength=1.0, power=2.0):
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / radius
    k = np.clip(1 - d, 0, 1) ** power * strength
    return img + (hexc(color)[None, None, :] - img) * k[..., None] * 0 + hexc(color)[None, None, :] * k[..., None]


def disc(img, cx, cy, r, color, soft=1.5):
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    k = np.clip((r - d) / soft + 0.5, 0, 1)[..., None]
    return lerp(img, hexc(color)[None, None, :], k)


def stars(img, count, seed, top=0.0, bottom=0.6, bright=1.0):
    rng = np.random.default_rng(seed)
    for _ in range(count):
        x = rng.integers(0, W)
        y = int(rng.uniform(top, bottom) ** 1.4 * H) if bottom > top else 0
        b = rng.random() ** 3 * 255 * bright
        fade = 1 - y / (bottom * H + 1)
        b *= max(0.15, fade)
        size = 2 if rng.random() > 0.93 else 1
        img[y:y + size, x:x + size] = np.maximum(img[y:y + size, x:x + size], b)
    return img


def terrain_layer(img, base, amp, scale, seed, block, color, haze_color=None, haze=0.0, rim=0.0, octaves=4, tilt=0.0,
                  ridge=False, shade=0.0, snow=None, invert=False):
    """A block-stepped silhouette. base/amp in fractions of H (base = mean surface line from the top)."""
    n = value_noise_1d(W + block, scale, seed, octaves)
    if ridge:
        n = (1 - np.abs(2 * n - 1)) ** 1.6
    heights = (base - (n - 0.5) * 2 * amp + np.linspace(-tilt, tilt, W + block)) * H
    cols = np.arange(W)
    qx = (cols // block) * block
    top = np.round(heights[qx] / block) * block
    col = hexc(color)
    if haze_color is not None:
        col = lerp(col, hexc(haze_color), haze)
    yy = np.arange(H)[:, None]
    mask = (yy <= top[None, :]) if invert else (yy >= top[None, :])
    depth = np.abs(yy - top[None, :]).astype(float)
    layer = np.broadcast_to(col, (H, W, 3)).copy()
    if shade > 0:
        # darker towards the foot of the layer, row by row (no vertical streaks)
        start = (base - amp) * H
        k = 1 - shade * np.clip((yy - start) / (H * 0.3), 0, 1)
        layer = layer * np.broadcast_to(k, (H, W))[..., None]
    if snow is not None:
        snow_col, _, line = snow
        jag = value_noise_1d(W + block, 40, seed + 99, 2)[qx]
        line_px = np.round((line * H + (jag - 0.5) * block * 7) / block) * block
        line_px = np.minimum(line_px, top + block * 4)[None, :]
        cap = yy < line_px
        snow_rgb = hexc(snow_col) if haze_color is None else lerp(hexc(snow_col), hexc(haze_color), haze * 0.5)
        k = np.clip((line_px - yy) / (block * 3), 0.6, 1.0)
        snow_layer = lerp(layer, np.broadcast_to(snow_rgb, (H, W, 3)), k[..., None])
        layer[cap] = snow_layer[cap]
    if rim > 0:
        rim_mask = depth < max(1, block // 6)
        layer[rim_mask] = lerp(layer[rim_mask], np.array([255, 255, 255.0]), rim)
    img[mask] = layer[mask]
    return img, top


def trees(img, top, seed, block, color, density=0.15, kind='spruce', min_h=3, max_h=7, trunk_color=None):
    rng = np.random.default_rng(seed)
    col = hexc(color)
    x = 0
    while x < W:
        if rng.random() < density:
            h = rng.integers(min_h, max_h + 1) * block
            base = int(top[min(W - 1, x)])
            if kind == 'spruce':
                levels = h // block
                for i in range(levels):
                    width = max(1, (levels - i) // 2 + 1) * block
                    y1 = base - (i + 1) * block
                    img[max(0, y1):max(0, y1 + block), max(0, x - width // 2):min(W, x + width // 2 + block)] = col
                img[max(0, base - h - block):base, x:x + block] = col
            else:  # oak / blossom: trunk + layered canopy
                trunk = hexc(trunk_color) if trunk_color else col
                img[max(0, base - h):base, x:x + block] = trunk
                cw = block * int(rng.integers(2, 4))
                ch = block * int(rng.integers(2, 4))
                cy = base - h - ch + block
                img[max(0, cy):max(0, cy + ch), max(0, x - cw):min(W, x + cw + block)] = col
                img[max(0, cy - block):max(0, cy), max(0, x - cw + block):min(W, x + cw)] = col
            x += block * rng.integers(2, 5)
        else:
            x += block
    return img


def haze_band(img, y_center, height, color, strength):
    ys = np.arange(H)
    k = np.exp(-((ys - y_center * H) / (height * H)) ** 2) * strength
    return lerp(img, hexc(color)[None, None, :], k[:, None, None])


def vignette(img, strength=0.35):
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2) / math.sqrt(2)
    k = (1 - strength * d ** 2)[..., None]
    return img * k


def aurora(img, seed, colors, y0=0.12, y1=0.45, strength=0.9):
    xs = np.arange(W)
    center_n = value_noise_1d(W, 420, seed, 3)
    ys = np.arange(H)[:, None] / H
    center = (y0 + (y1 - y0) * center_n)[None, :]
    intensity = (value_noise_1d(W, 260, seed + 3, 3) ** 1.5)[None, :]
    rays = 0.75 + 0.25 * value_noise_1d(W, 9, seed + 5, 2)[None, :]
    below = np.clip(1 - (ys - center) / 0.035, 0, 1) * (ys >= center)
    above = np.exp(-((center - ys) / 0.16) ** 2) * (ys < center)
    k = (below + above) * intensity * rays * strength
    c0, c1 = hexc(colors[0]), hexc(colors[1])
    t = np.clip((center - ys) / 0.18, 0, 1)[..., None]
    col = lerp(c0[None, None, :], c1[None, None, :], t)
    return img + col * k[..., None] * 0.75


def water(img, horizon, color, reflect_from=None, seed=1):
    """Everything below the horizon becomes water reflecting the sky above."""
    hy = int(horizon * H)
    src = img[max(0, 2 * hy - H):hy][::-1].copy()
    rows = H - hy
    src = src[:rows] if len(src) >= rows else np.pad(src, ((0, rows - len(src)), (0, 0), (0, 0)), mode='edge')
    rng = np.random.default_rng(seed)
    out = src.copy()
    for r in range(rows):
        shift = int(math.sin(r * 0.35 + rng.random() * 0.4) * (1 + r * 0.04))
        out[r] = np.roll(src[r], shift, axis=0)
    t = np.linspace(0.35, 0.8, rows)[:, None, None]
    img[hy:] = lerp(out, hexc(color)[None, None, :], t)
    return img


def light_path(img, horizon, cx, color, seed, width=0.04):
    rng = np.random.default_rng(seed)
    hy = int(horizon * H)
    c = hexc(color)
    for y in range(hy + 2, H, 3):
        depth = (y - hy) / (H - hy)
        spread = W * width * (0.4 + depth * 2.2)
        for _ in range(int(3 + depth * 9)):
            x = int(cx + rng.normal(0, spread))
            w = int(rng.integers(4, 14 + int(depth * 30)))
            a = rng.uniform(0.25, 0.85) * (1 - depth * 0.5)
            x0, x1 = max(0, x - w // 2), min(W, x + w // 2)
            img[y:y + 2, x0:x1] = lerp(img[y:y + 2, x0:x1], c[None, None, :], a)
    return img


def finish(img, name, vign=0.35):
    img = vignette(img, vign)
    yy, xx = np.mgrid[0:H, 0:W]
    img = img + BAYER[yy % 4, xx % 4][..., None] * 2.0
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')
    out.save(f'{OUT}/{name}.png', optimize=True)
    out.save(f'{OUT}/{name}.jpg', quality=88)
    print('wrote', name)


# ---------------------------------------------------------------------- scenes

def scene_dusk():
    img = gradient([(0, '#1b1530'), (0.35, '#4b2a58'), (0.55, '#c0587a'), (0.66, '#f4a26b')])
    img = glow(img, W * 0.68, H * 0.6, 520, '#ff9a6b', 0.45)
    img = disc(img, W * 0.68, H * 0.6, 46, '#ffd9a8')
    img = stars(img, 160, 4, 0, 0.35, 0.6)
    haze = '#e48a7c'
    img, _ = terrain_layer(img, 0.60, 0.07, 260, 11, 8, '#7c3f63', haze, 0.45)
    img, _ = terrain_layer(img, 0.68, 0.06, 220, 12, 12, '#56304f', haze, 0.25)
    img, _ = terrain_layer(img, 0.77, 0.05, 180, 13, 16, '#3a2140', haze, 0.1)
    img, t = terrain_layer(img, 0.86, 0.04, 160, 14, 20, '#1d1225')
    img = trees(img, t, 3, 10, '#140b19', 0.12, 'spruce')
    finish(img, 'dusk')


def scene_aurora():
    img = gradient([(0, '#02040c'), (0.45, '#081628'), (0.7, '#12304a')])
    img = stars(img, 900, 21, 0, 0.7, 1.0)
    img = aurora(img, 3, ['#3df5b4', '#6a5cff'], 0.2, 0.36, 1.0)
    img, _ = terrain_layer(img, 0.6, 0.12, 170, 31, 8, '#b9d2e6', '#16334b', 0.55, ridge=True, shade=0.35,
                           snow=('#f2f8ff', 14, 0.58))
    img, _ = terrain_layer(img, 0.7, 0.1, 150, 32, 12, '#7f9bb3', '#10283b', 0.5, ridge=True, shade=0.45,
                           snow=('#dfeaf5', 18, 0.66))
    img, t = terrain_layer(img, 0.84, 0.05, 170, 33, 16, '#0c1520', shade=0.3)
    img = trees(img, t, 9, 8, '#060b12', 0.22, 'spruce', 4, 9)
    finish(img, 'aurora')


def scene_golden():
    img = gradient([(0, '#7aa6d6'), (0.45, '#f2c890'), (0.62, '#ffb46b')])
    img = glow(img, W * 0.25, H * 0.5, 600, '#ffcf8a', 0.5)
    img = disc(img, W * 0.25, H * 0.5, 38, '#fff1cf')
    haze = '#f6c58c'
    img, _ = terrain_layer(img, 0.58, 0.05, 300, 41, 8, '#8c9a6a', haze, 0.6)
    img, _ = terrain_layer(img, 0.66, 0.05, 240, 42, 12, '#6f8a4e', haze, 0.4)
    img, t = terrain_layer(img, 0.76, 0.05, 200, 43, 16, '#4f6f35', haze, 0.2, rim=0.12)
    img = trees(img, t, 5, 8, '#3a5428', 0.08, 'oak', 3, 5)
    img, t = terrain_layer(img, 0.88, 0.04, 160, 44, 20, '#2f4521', rim=0.1)
    img = trees(img, t, 6, 12, '#22331a', 0.06, 'oak', 3, 6)
    finish(img, 'golden_hour', 0.25)


def scene_sea():
    img = gradient([(0, '#040816'), (0.45, '#0f1f42'), (0.6, '#2a3f72')])
    img = stars(img, 500, 51, 0, 0.5, 0.9)
    img = glow(img, W * 0.6, H * 0.26, 360, '#8fa8ff', 0.3)
    img = disc(img, W * 0.6, H * 0.26, 28, '#eef2ff')
    img, _ = terrain_layer(img, 0.585, 0.025, 120, 52, 8, '#0f1c38', ridge=True)
    img, _ = terrain_layer(img, 0.6, 0.012, 90, 53, 8, '#0b1630')
    img = water(img, 0.6, '#081128', seed=3)
    img = light_path(img, 0.6, W * 0.6, '#cfdcff', 4)
    finish(img, 'midnight_sea')


def scene_pines():
    img = gradient([(0, '#9fb3bf'), (0.6, '#d7dfe2')])
    haze = '#d7dfe2'
    for i, (base, col, blk, hz) in enumerate([(0.52, '#6f8796', 8, 0.65), (0.6, '#577081', 10, 0.5), (0.68, '#40586a', 12, 0.35),
                                              (0.77, '#2c4252', 14, 0.2), (0.88, '#172a36', 18, 0.0)]):
        img, t = terrain_layer(img, base, 0.03, 220, 61 + i, blk, col, haze, hz)
        img = trees(img, t, 70 + i, blk, col if hz == 0 else '#' + ''.join(f'{int(v):02x}' for v in lerp(hexc(col), hexc(haze), hz)),
                    0.35, 'spruce', 3, 8)
        img = haze_band(img, base - 0.02, 0.05, haze, 0.25)
    finish(img, 'misty_pines', 0.2)


def scene_cherry():
    img = gradient([(0, '#e9a9c4'), (0.5, '#f8cfda'), (0.68, '#ffe9e4')])
    img = glow(img, W * 0.72, H * 0.4, 300, '#ffffff', 0.3)
    img = disc(img, W * 0.72, H * 0.4, 26, '#fff8f4')
    haze = '#f8d7df'
    img, _ = terrain_layer(img, 0.6, 0.06, 240, 81, 8, '#b4769a', haze, 0.55, ridge=True)
    img, t = terrain_layer(img, 0.7, 0.05, 220, 82, 10, '#8d5a7c', haze, 0.35, shade=0.2)
    img = trees(img, t, 83, 8, '#f0a7c4', 0.07, 'oak', 2, 4, trunk_color='#6b3f58')
    img, t = terrain_layer(img, 0.83, 0.035, 180, 84, 14, '#4f3348', shade=0.35)
    img = trees(img, t, 85, 12, '#ff9fc4', 0.06, 'oak', 2, 4, trunk_color='#3a2333')
    finish(img, 'cherry_grove', 0.22)


def scene_dunes():
    img = gradient([(0, '#d98f55'), (0.45, '#f0c086'), (0.6, '#fbe0b4')])
    img = glow(img, W * 0.5, H * 0.46, 380, '#fff0cc', 0.45)
    img = disc(img, W * 0.5, H * 0.46, 40, '#fff8e8')
    haze = '#f3cd98'
    layers = [(0.6, '#e2a868', 8, 0.5), (0.68, '#d48f53', 12, 0.35), (0.78, '#bd7840', 16, 0.15), (0.9, '#975a2a', 22, 0)]
    for i, (base, col, blk, hz) in enumerate(layers):
        img, _ = terrain_layer(img, base, 0.05, 300, 91 + i, blk, col, haze, hz, rim=0.18, octaves=2, ridge=True, shade=0.3)
    finish(img, 'dunes', 0.25)


def scene_nether():
    img = gradient([(0, '#160404'), (0.55, '#4a0e08'), (0.8, '#8a1f0c')])
    n = noise_2d(H, W, 120, 5, 4)
    img = img * (0.85 + 0.3 * n[..., None])
    haze = '#c2391a'
    img, _ = terrain_layer(img, 0.56, 0.12, 160, 101, 10, '#5a140c', haze, 0.45, ridge=True, shade=0.3)
    img, _ = terrain_layer(img, 0.68, 0.1, 140, 102, 14, '#3a0b07', haze, 0.25, ridge=True, shade=0.4)
    # lava sea
    hy = int(0.86 * H)
    lava = noise_2d(H - hy, W, 30, 8, 3)
    base = lerp(hexc('#ff6a14'), hexc('#ffc23a'), lava[..., None] ** 2)
    img[hy:] = base
    img = haze_band(img, 0.86, 0.06, '#ff7a1f', 0.55)
    img, t = terrain_layer(img, 0.84, 0.04, 120, 103, 18, '#1d0604', shade=0.2)
    # ceiling with glowstone
    img, ceil = terrain_layer(img, 0.1, 0.07, 140, 104, 16, '#3a0e09', invert=True, ridge=True)
    rng = np.random.default_rng(14)
    for _ in range(9):
        x = int(rng.integers(0, W - 40))
        y = int(ceil[x]) - 4
        for bx in range(int(rng.integers(1, 4))):
            for by in range(int(rng.integers(1, 3))):
                img[y + by * 12:y + by * 12 + 12, x + bx * 12:x + bx * 12 + 12] = hexc('#ffd37a') * (0.9 + 0.1 * rng.random())
        img = glow(img, x + 18, y + 10, 90, '#ff9a3a', 0.25)
    finish(img, 'nether', 0.4)


def scene_end():
    img = gradient([(0, '#04020a'), (0.6, '#120a22'), (1, '#1b0f30')])
    img = stars(img, 1100, 111, 0, 1.0, 0.8)
    n = noise_2d(H, W, 260, 9, 4)
    img = img + (np.clip(n - 0.45, 0, 1) * 2.2)[..., None] * hexc('#7b4bd6') * 0.3
    rng = np.random.default_rng(12)
    islands = [(0.5, 0.66, 230, 1.0), (0.16, 0.5, 90, 0.55), (0.83, 0.45, 110, 0.6), (0.32, 0.36, 50, 0.4), (0.7, 0.3, 40, 0.35)]
    for k, (fx, fy, r, light) in enumerate(islands):
        cx, cy, blk = fx * W, fy * H, 8 if r < 100 else 12
        under = value_noise_1d(int(2 * r) + blk * 2, 30, 200 + k, 3)
        top_n = value_noise_1d(int(2 * r) + blk * 2, 60, 300 + k, 2)
        for x in range(int(cx - r), int(cx + r), blk):
            if x < 0 or x >= W:
                continue
            u = (x - cx) / r
            depth = r * 0.7 * max(0.0, 1 - u * u) ** 0.8 * (0.6 + 0.8 * under[int(x - cx + r)])
            top = cy - r * 0.08 * top_n[int(x - cx + r)]
            y0 = int(round(top / blk) * blk)
            y1 = int(round((cy + depth) / blk) * blk)
            for y in range(y0, y1, blk):
                f = (y - y0) / max(1, y1 - y0)
                col = lerp(hexc('#e6e4ad'), hexc('#4f4a33'), min(1, f * 1.4)) * light
                img[y:y + blk, x:x + blk] = col
            img[y0:y0 + 2, x:x + blk] = hexc('#fbf9d0') * light
        if k == 0:
            for px in (-0.55, -0.2, 0.25, 0.6):
                x = int(cx + px * r)
                hgt = int(rng.uniform(0.35, 0.6) * H * 0.5)
                top = int(cy - hgt)
                img[top:int(cy), x:x + 24] = hexc('#1b1028')
                img[top:int(cy), x:x + 3] = hexc('#3d2a57')
                img = glow(img, x + 12, top - 6, 30, '#d9b8ff', 0.6)
    finish(img, 'the_end', 0.45)


def scene_glacier():
    img = gradient([(0, '#3f86cf'), (0.5, '#a9d6f3'), (0.62, '#e4f4fd')])
    img = glow(img, W * 0.8, H * 0.16, 320, '#ffffff', 0.4)
    haze = '#d6edfb'
    img, _ = terrain_layer(img, 0.52, 0.14, 160, 121, 8, '#a7c9e2', haze, 0.45, ridge=True, shade=0.25, snow=('#ffffff', 16, 0.5))
    img, _ = terrain_layer(img, 0.64, 0.12, 140, 122, 12, '#7ba8cb', haze, 0.25, ridge=True, shade=0.35, snow=('#f4fbff', 22, 0.6))
    img, _ = terrain_layer(img, 0.78, 0.03, 150, 123, 16, '#e9f5fc', rim=0.3, shade=0.15)
    img = water(img, 0.82, '#2f6e9e', seed=5)
    finish(img, 'glacier', 0.22)


def scene_space():
    img = gradient([(0, '#020208'), (1, '#070716')])
    img = stars(img, 1500, 131, 0, 1.0, 1.0)
    n = noise_2d(H, W, 300, 13, 5)
    n2 = noise_2d(H, W, 180, 14, 4)
    a = np.clip(n - 0.42, 0, 1) * 2.4
    b = np.clip(n2 - 0.5, 0, 1) * 2.5
    img = img + a[..., None] * hexc('#3a5bff') * 0.45 + b[..., None] * hexc('#ff4fa3') * 0.35
    # planet with terminator
    cx, cy, r = W * 0.78, H * 0.72, 210
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    inside = d < r
    light = np.clip(((xx - cx) * -0.6 + (yy - cy) * -0.8) / r + 0.35, 0, 1)
    bands = 0.85 + 0.15 * np.sin((yy - cy) / r * 14 + noise_2d(H, W, 40, 2, 2) * 3)
    col = hexc('#d07b52')[None, None, :] * (light * bands)[..., None]
    img[inside] = col[inside]
    rim = np.exp(-((d - r) / 6) ** 2) * light * 1.2
    img = img + rim[..., None] * hexc('#ffb38a')
    finish(img, 'deep_space', 0.4)


def scene_lavender():
    img = gradient([(0, '#272654'), (0.45, '#7461b0'), (0.62, '#e7b2c6')])
    img = glow(img, W * 0.35, H * 0.6, 420, '#ffc9d6', 0.35)
    img = stars(img, 120, 141, 0, 0.3, 0.5)
    haze = '#c6a3cf'
    img, _ = terrain_layer(img, 0.6, 0.05, 240, 142, 8, '#7a67a2', haze, 0.5, ridge=True)
    img, _ = terrain_layer(img, 0.68, 0.025, 220, 143, 10, '#56487f', haze, 0.3)
    # field: perspective rows of blocky lavender bushes
    rng = np.random.default_rng(5)
    horizon = 0.7
    y = horizon * H
    row = 0
    while y < H:
        depth = (y - horizon * H) / (H * (1 - horizon))
        blk = int(4 + depth * 22)
        gap = int(blk * 0.6)
        img[int(y):int(y) + blk] = lerp(hexc('#3c3266'), hexc('#2a2148'), depth)[None, None, :]
        x = -int(rng.integers(0, blk * 2))
        col = lerp(hexc('#a88ae6'), hexc('#7d5cc9'), depth)
        while x < W:
            w = blk * int(rng.integers(2, 5))
            tone = col * rng.uniform(0.82, 1.08)
            yi = int(y + rng.integers(-1, 2) * max(1, blk // 4))
            img[yi - blk // 2:yi + blk // 2, max(0, x):min(W, x + w)] = tone
            img[yi - blk:yi - blk // 2, max(0, x + blk // 2):min(W, x + w - blk // 2)] = tone * 1.08
            x += w + gap + int(rng.integers(0, blk))
        y += blk * 1.15
        row += 1
    finish(img, 'lavender', 0.3)


SCENES = {
    'dusk': scene_dusk, 'aurora': scene_aurora, 'golden_hour': scene_golden, 'midnight_sea': scene_sea,
    'misty_pines': scene_pines, 'cherry_grove': scene_cherry, 'dunes': scene_dunes, 'nether': scene_nether,
    'the_end': scene_end, 'glacier': scene_glacier, 'deep_space': scene_space, 'lavender': scene_lavender,
}

for key, fn in SCENES.items():
    if ONLY and key not in ONLY:
        continue
    fn()
