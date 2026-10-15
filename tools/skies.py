"""
Seamless cube-map skies for the Custom Sky module.

Atlas layout: 3 x 2 cells, cell i at (i % 3, i // 3). Face i is drawn by the mod exactly like
vanilla's End sky: the quad (-1,-1,-1)..(1,-1,1) on y = -1 with u along x and v along z, rotated by
  i=1: X +90°, i=2: X -90°, i=3: X 180°, i=4: Z +90°, i=5: Z -90°.
Every pixel's color is a function of its view direction only, so the faces always line up.
"""
import json
import math
import sys
import numpy as np
from PIL import Image

N = int(sys.argv[2]) if len(sys.argv) > 2 else 384
OUT = sys.argv[1]
ONLY = sys.argv[3:] if len(sys.argv) > 3 else None


def rot_x(deg):
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_z(deg):
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


ROTS = [np.eye(3), rot_x(90), rot_x(-90), rot_x(180), rot_z(90), rot_z(-90)]


def face_dirs(i):
    uv = (np.arange(N) + 0.5) / N
    u, v = np.meshgrid(uv, uv)  # rows = v, cols = u
    local = np.stack([-1 + 2 * u, -np.ones_like(u), -1 + 2 * v], axis=-1)
    d = local @ ROTS[i].T
    return d / np.linalg.norm(d, axis=-1, keepdims=True)


# ---------------------------------------------------------------------- noise

rng = np.random.default_rng(1234)
PERM = rng.permutation(4096)
VALS = rng.random(4096)


def hash3(ix, iy, iz):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791)
    return VALS[PERM[np.abs(h) % 4096] % 4096]


def smooth(t):
    return t * t * (3 - 2 * t)


def noise3(p):
    x, y, z = p[..., 0], p[..., 1], p[..., 2]
    ix, iy, iz = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64), np.floor(z).astype(np.int64)
    fx, fy, fz = smooth(x - ix), smooth(y - iy), smooth(z - iz)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                out = out + w * hash3(ix + dx, iy + dy, iz + dz)
    return out


def fbm(p, octaves=5, lacunarity=2.0, gain=0.5):
    total, amp, norm = 0, 1.0, 0
    q = p
    for _ in range(octaves):
        total = total + amp * noise3(q)
        norm += amp
        amp *= gain
        q = q * lacunarity + 17.3
    return total / norm


def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64)


def ramp(t, stops):
    """t in -1..1 (elevation); stops: [(t, hex)]"""
    out = np.zeros(t.shape + (3,))
    ts = [s[0] for s in stops]
    cs = [hexc(s[1]) for s in stops]
    t = np.clip(t, ts[0], ts[-1])
    for k in range(len(stops) - 1):
        m = (t >= ts[k]) & (t <= ts[k + 1])
        f = smooth((t[m] - ts[k]) / max(1e-6, ts[k + 1] - ts[k]))[..., None]
        out[m] = cs[k] + (cs[k + 1] - cs[k]) * f
    return out


def direction_to(az_deg, el_deg):
    a, e = math.radians(az_deg), math.radians(el_deg)
    return np.array([math.cos(e) * math.cos(a), math.sin(e), math.cos(e) * math.sin(a)])


# ---------------------------------------------------------------------- layers

def glow(d, toward, color, power, strength):
    k = np.clip((d @ toward), 0, 1) ** power * strength
    return hexc(color) * k[..., None]


def stars(d, density=0.996, scale=260, brightness=1.0, tint=None):
    cell = np.floor(d * scale).astype(np.int64)
    r = hash3(cell[..., 0], cell[..., 1], cell[..., 2])
    star = np.clip((r - density) / (1 - density), 0, 1) ** 2.2
    # keep stars point-like: only the cell centre lights up
    frac = d * scale - np.floor(d * scale) - 0.5
    dist = np.sqrt((frac ** 2).sum(axis=-1))
    star = star * np.clip(1 - dist * 2.2, 0, 1)
    col = np.array([255.0, 255.0, 255.0]) if tint is None else hexc(tint)
    return col * (star * brightness)[..., None]


def plane(d, height=0.12):
    """Cloud-plane coordinates: perspective projection onto a flat layer above the horizon."""
    y = np.clip(d[..., 1] + height, 0.02, None)
    return np.stack([d[..., 0] / y, d[..., 2] / y], axis=-1), np.clip(d[..., 1] * 6 + 0.4, 0, 1)


def clouds(d, scale, cover, seed, softness=0.18):
    xy, horizon_fade = plane(d)
    p = np.stack([xy[..., 0] * scale + seed, xy[..., 1] * scale - seed, np.full(xy.shape[:-1], seed * 0.37)], axis=-1)
    n = fbm(p, 6)
    k = np.clip((n - (1 - cover)) / softness, 0, 1)
    return k * horizon_fade * (d[..., 1] > -0.02)


def milky_way(d, axis, width=0.22, strength=1.0):
    dist = np.abs(d @ axis)
    band = np.exp(-(dist / width) ** 2)
    detail = fbm(d * 6 + 3.1, 5)
    return band * (0.4 + 0.9 * detail) * strength


def compose(d, sky, layers):
    col = sky
    for layer in layers:
        col = layer(d, col)
    return col


# ---------------------------------------------------------------------- skies

def sky_clear_day(d):
    e = d[..., 1]
    col = ramp(e, [(-1, '#5b7fa6'), (-0.05, '#a9cbe8'), (0.0, '#cfe4f5'), (0.25, '#8fc0ec'), (1, '#3f86d6')])
    col = col + glow(d, direction_to(40, 35), '#fff4dc', 10, 0.18)
    c = clouds(d, 0.55, 0.45, 3.0)
    col = col + (np.array([250, 252, 255.0]) - col) * (c * 0.85)[..., None]
    return col


def sky_golden(d):
    e = d[..., 1]
    sun = direction_to(200, 6)
    col = ramp(e, [(-1, '#5a4a52'), (-0.05, '#e7a46c'), (0.02, '#f6c88f'), (0.25, '#a7b9d6'), (1, '#4f7fc4')])
    col = col + glow(d, sun, '#ffb35c', 3, 0.55) + glow(d, sun, '#fff0c8', 30, 0.35)
    c = clouds(d, 0.6, 0.48, 7.0, 0.15)
    lit = np.clip(d @ sun, 0, 1)[..., None] ** 1.5
    cloud_col = hexc('#cdb8c6') + (hexc('#ffc48a') - hexc('#cdb8c6')) * lit
    col = col + (cloud_col - col) * (c * 0.8)[..., None]
    return col


def sky_sunset(d):
    e = d[..., 1]
    sun = direction_to(120, 2)
    col = ramp(e, [(-1, '#2c1630'), (-0.05, '#e0576b'), (0.03, '#ff9a6b'), (0.15, '#b45a9c'), (0.45, '#4b2c78'), (1, '#1c1640')])
    col = col + glow(d, sun, '#ff7a4a', 2.5, 0.6) + glow(d, sun, '#ffe2b0', 24, 0.35)
    c = clouds(d, 0.45, 0.38, 11.0, 0.12)
    lit = np.clip(d @ sun, 0, 1)[..., None] ** 2
    cloud_col = hexc('#7c2e5c') + (hexc('#ffb07a') - hexc('#7c2e5c')) * lit
    col = col + (cloud_col - col) * (c * 0.9)[..., None]
    return col


def sky_pastel(d):
    e = d[..., 1]
    col = ramp(e, [(-1, '#9b8fb0'), (-0.05, '#ffd2c4'), (0.05, '#ffe4d6'), (0.3, '#c9d6f2'), (1, '#93b1e6')])
    col = col + glow(d, direction_to(300, 4), '#fff1e6', 6, 0.22)
    c = clouds(d, 0.5, 0.4, 13.0, 0.2)
    col = col + (hexc('#fff5f2') - col) * (c * 0.7)[..., None]
    return col


def sky_overcast(d):
    e = d[..., 1]
    col = ramp(e, [(-1, '#5c6268'), (0, '#a7adb3'), (1, '#8c939a')])
    c = clouds(d, 0.9, 0.85, 17.0, 0.4)
    shade = fbm(d * 5 + 2, 4)[..., None]
    cloud_col = hexc('#c3c8cc') * (0.75 + 0.35 * shade)
    col = col + (cloud_col - col) * (c * 0.9)[..., None]
    return col


def sky_starry(d):
    e = d[..., 1]
    col = ramp(e, [(-1, '#05070f'), (-0.05, '#16213d'), (0.05, '#121b34'), (1, '#04060f')])
    axis = direction_to(30, 40)
    axis = np.cross(axis, np.array([0.3, 0.2, 0.9]))
    axis /= np.linalg.norm(axis)
    mw = milky_way(d, axis, 0.2, 1.0)
    col = col + hexc('#5d6a9a') * (mw * 0.35)[..., None]
    col = col + stars(d, 0.988, 300, 1.15) * (e > -0.05)[..., None]
    col = col + stars(d, 0.975, 520, 0.5) * (mw > 0.2)[..., None]
    return col


def sky_aurora(d):
    e = d[..., 1]
    col = ramp(e, [(-1, '#030810'), (-0.05, '#0d2033'), (0.1, '#0a1828'), (1, '#03060d')])
    col = col + stars(d, 0.991, 300, 1.0) * (e > -0.05)[..., None]
    az = np.arctan2(d[..., 2], d[..., 0])
    el = np.arcsin(np.clip(e, -1, 1))
    ring = np.stack([np.cos(az) * 1.6, np.sin(az) * 1.6, np.zeros(az.shape)], axis=-1)
    base = 0.22 + 0.22 * fbm(ring + 4.0, 3)                     # lower edge of the curtain (radians)
    strength = np.clip(fbm(ring * 1.4 + 9.0, 3) * 1.8 - 0.55, 0, 1)
    rays = 0.55 + 0.45 * fbm(np.stack([np.cos(az) * 40, np.sin(az) * 40, np.zeros(az.shape)], axis=-1), 2)
    above = el - base
    profile = np.where(above < 0, np.exp(-(above / 0.02) ** 2), np.exp(-above / 0.28))
    k = profile * strength * rays
    top_mix = np.clip(above / 0.35, 0, 1)[..., None]
    curtain = hexc('#3cf2b0') * (1 - top_mix) + hexc('#8a5cff') * top_mix
    col = col + curtain * (k * 0.8)[..., None]
    return col


def sky_nebula(d):
    col = np.zeros(d.shape)
    col += hexc('#06040f')
    a = fbm(d * 2.2 + 1.7, 6)
    b = fbm(d * 3.1 - 4.2, 6)
    col = col + hexc('#5b3bd6') * (np.clip(a - 0.45, 0, 1) * 2.4)[..., None] * 0.6
    col = col + hexc('#ff4fa3') * (np.clip(b - 0.52, 0, 1) * 2.6)[..., None] * 0.5
    col = col + hexc('#3ac7ff') * (np.clip(a * b - 0.3, 0, 1) * 2.0)[..., None] * 0.35
    col = col + stars(d, 0.993, 300, 1.0) + stars(d, 0.98, 600, 0.35)
    return col


def sky_deep_space(d):
    col = np.zeros(d.shape) + hexc('#020208')
    col = col + stars(d, 0.985, 360, 1.1) + stars(d, 0.97, 700, 0.45)
    g = direction_to(250, 30)
    core = np.clip(d @ g, 0, 1) ** 120
    col = col + hexc('#ffd9b3') * core[..., None] * 0.9 + hexc('#8fa6ff') * (np.clip(d @ g, 0, 1) ** 30)[..., None] * 0.25
    return col


def sky_blood_moon(d):
    e = d[..., 1]
    col = ramp(e, [(-1, '#120304'), (-0.05, '#4a0d0e'), (0.08, '#3a0a0c'), (1, '#0c0304')])
    col = col + glow(d, direction_to(80, 35), '#ff3b2f', 6, 0.35)
    col = col + stars(d, 0.995, 300, 0.6, '#ffd0c8') * (e > -0.05)[..., None]
    c = clouds(d, 0.6, 0.3, 23.0, 0.2)
    col = col + (hexc('#2a0608') - col) * (c * 0.8)[..., None]
    return col


def sky_cotton_candy(d):
    e = d[..., 1]
    col = ramp(e, [(-1, '#9c8fc4'), (-0.05, '#ffc6e0'), (0.05, '#ffd6ea'), (0.35, '#9fd0ff'), (1, '#6aa8ff')])
    c = clouds(d, 0.5, 0.5, 29.0, 0.16)
    tint = fbm(d * 3 + 8, 3)[..., None]
    cloud_col = hexc('#ffd1ec') * (1 - tint) + hexc('#d8e8ff') * tint
    col = col + (cloud_col - col) * (c * 0.9)[..., None]
    return col


def sky_storm(d):
    e = d[..., 1]
    col = ramp(e, [(-1, '#1a2226'), (0, '#4a5960'), (1, '#262f35')])
    c = clouds(d, 1.0, 0.9, 31.0, 0.35)
    shade = fbm(d * 7 + 5, 5)[..., None]
    cloud_col = hexc('#3a464c') * (0.6 + 0.6 * shade)
    col = col + (cloud_col - col) * (c * 0.95)[..., None]
    return col


def sky_morning_mist(d):
    e = d[..., 1]
    col = ramp(e, [(-1, '#8f9aa3'), (-0.05, '#d7dee3'), (0.06, '#e6ebee'), (0.35, '#b9cbdc'), (1, '#8eaccb')])
    col = col + glow(d, direction_to(70, 10), '#fff6e8', 8, 0.1)
    c = clouds(d, 0.35, 0.45, 37.0, 0.35)
    col = col + (hexc('#f1f3f4') - col) * (c * 0.55)[..., None]
    return col


def sky_lavender_dusk(d):
    e = d[..., 1]
    sun = direction_to(330, 1)
    col = ramp(e, [(-1, '#2a2340'), (-0.05, '#c58bb7'), (0.04, '#e7a9c2'), (0.2, '#8f78c9'), (0.6, '#3d3478'), (1, '#1d1a42')])
    col = col + glow(d, sun, '#ffc0d0', 3, 0.4)
    col = col + stars(d, 0.995, 300, 0.7) * np.clip(e * 3 - 0.6, 0, 1)[..., None]
    c = clouds(d, 0.55, 0.32, 41.0, 0.15)
    lit = np.clip(d @ sun, 0, 1)[..., None] ** 2
    cloud_col = hexc('#6d5a9c') + (hexc('#ffc4d6') - hexc('#6d5a9c')) * lit
    col = col + (cloud_col - col) * (c * 0.85)[..., None]
    return col


def sky_arctic(d):
    e = d[..., 1]
    col = ramp(e, [(-1, '#6d8fae'), (-0.05, '#d6ecfb'), (0.03, '#e9f6ff'), (0.2, '#7fc2f2'), (1, '#1f6fd1')])
    col = col + glow(d, direction_to(160, 25), '#ffffff', 12, 0.25)
    # thin, streaky cirrus
    xy, fade = plane(d, 0.08)
    p = np.stack([xy[..., 0] * 0.25, xy[..., 1] * 2.2, np.full(xy.shape[:-1], 3.3)], axis=-1)
    streak = np.clip((fbm(p, 5) - 0.55) / 0.2, 0, 1) * fade * (e > 0)
    col = col + (hexc('#ffffff') - col) * (streak * 0.6)[..., None]
    return col


def sky_ember(d):
    e = d[..., 1]
    col = ramp(e, [(-1, '#1a0806'), (-0.05, '#a3361a'), (0.04, '#d9622a'), (0.25, '#6e2216'), (1, '#1f0a08')])
    col = col + glow(d, direction_to(210, 3), '#ff8a3d', 3, 0.5)
    c = clouds(d, 0.8, 0.6, 43.0, 0.3)
    shade = fbm(d * 6 + 1, 4)[..., None]
    smoke = hexc('#3b1610') * (0.6 + 0.8 * shade)
    col = col + (smoke - col) * (c * 0.8)[..., None]
    sparks = stars(d, 0.997, 220, 0.8, '#ffb070') * (e > 0)[..., None]
    return col + sparks


def sky_midnight_clouds(d):
    e = d[..., 1]
    moon = direction_to(40, 40)
    col = ramp(e, [(-1, '#05080f'), (-0.05, '#1b2a45'), (0.05, '#16223a'), (1, '#060a14')])
    col = col + glow(d, moon, '#9db4ff', 8, 0.35)
    col = col + stars(d, 0.993, 300, 0.9) * (e > -0.05)[..., None]
    c = clouds(d, 0.6, 0.5, 47.0, 0.18)
    lit = np.clip(d @ moon, 0, 1)[..., None] ** 3
    cloud_col = hexc('#18233a') + (hexc('#8fa3cf') - hexc('#18233a')) * lit
    col = col + (cloud_col - col) * (c * 0.9)[..., None]
    return col


def sky_emerald_night(d):
    e = d[..., 1]
    col = ramp(e, [(-1, '#020a08'), (-0.05, '#0b2a24'), (0.08, '#082019'), (1, '#02070a')])
    col = col + stars(d, 0.989, 300, 1.05) * (e > -0.05)[..., None]
    a = fbm(d * 2.5 + 11, 5)
    col = col + hexc('#1fd6a0') * (np.clip(a - 0.42, 0, 1) * 2.0 * np.clip(e * 2 + 0.2, 0, 1))[..., None] * 0.55
    col = col + glow(d, direction_to(280, 20), '#5effc8', 10, 0.12)
    return col


SKIES = {
    'clear_day': ('Clear Day', sky_clear_day),
    'golden_hour': ('Golden Hour', sky_golden),
    'sunset': ('Sunset Bloom', sky_sunset),
    'pastel_dawn': ('Pastel Dawn', sky_pastel),
    'cotton_candy': ('Cotton Candy', sky_cotton_candy),
    'overcast': ('Overcast', sky_overcast),
    'storm': ('Thunderstorm', sky_storm),
    'starry_night': ('Starry Night', sky_starry),
    'aurora': ('Aurora', sky_aurora),
    'nebula': ('Nebula', sky_nebula),
    'deep_space': ('Deep Space', sky_deep_space),
    'blood_moon': ('Blood Moon', sky_blood_moon),
    'morning_mist': ('Morning Mist', sky_morning_mist),
    'lavender_dusk': ('Lavender Dusk', sky_lavender_dusk),
    'arctic': ('Arctic Day', sky_arctic),
    'ember': ('Ember Sky', sky_ember),
    'midnight_clouds': ('Midnight Clouds', sky_midnight_clouds),
    'emerald_night': ('Emerald Night', sky_emerald_night),
}

BAYER = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5)
import os
meta = json.load(open(f'{OUT}/skies.json')) if os.path.exists(f'{OUT}/skies.json') else {}
for key, (name, fn) in SKIES.items():
    if ONLY and key not in ONLY:
        continue
    atlas = np.zeros((2 * N, 3 * N, 3))
    horizon = []
    for i in range(6):
        d = face_dirs(i)
        col = fn(d)
        r, c = divmod(i, 3)
        atlas[r * N:(r + 1) * N, c * N:(c + 1) * N] = col
        near = np.abs(d[..., 1]) < 0.05
        if near.any():
            horizon.append(col[near].mean(axis=0))
    yy, xx = np.mgrid[0:2 * N, 0:3 * N]
    atlas = atlas + BAYER[yy % 4, xx % 4][..., None] * 2.0
    Image.fromarray(np.clip(atlas, 0, 255).astype(np.uint8), 'RGB').save(f'{OUT}/{key}.png', optimize=True)
    h = np.clip(np.mean(horizon, axis=0), 0, 255).astype(int)
    meta[key] = {'name': name, 'horizon': '#%02X%02X%02X' % tuple(h)}
    print('wrote', key, meta[key])

with open(f'{OUT}/skies.json', 'w') as f:
    json.dump(meta, f, indent=1)
