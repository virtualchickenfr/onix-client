'use strict';

/* Your skin as a 3D figure on the home page: every texel of the skin is a small square on the player's boxes, drawn
   back to front on a canvas (no WebGL library needed). Every launcher start picks another pose; the figure breathes
   and sways a little while the home page is open. */

const Skin3D = (() => {
  const POSES = {
    // degrees: rotations around x (forward/back), y (turn), z (sideways); positive z lifts an arm away from the body
    idle: { head: [0, 0, 0], ra: [0, 0, 6], la: [0, 0, 6], rl: [0, 0, 0], ll: [0, 0, 0] },
    wave: { head: [-6, -10, 4], ra: [0, 0, 150], la: [0, 0, 6], rl: [0, 0, 0], ll: [0, 0, 0], wave: true },
    hero: { head: [-10, 0, 0], ra: [0, 0, 28], la: [0, 0, 28], rl: [0, 0, 7], ll: [0, 0, 7] },
    walk: { head: [0, 8, 0], ra: [32, 0, 4], la: [-32, 0, 4], rl: [-30, 0, 0], ll: [30, 0, 0] },
    victory: { head: [-14, 0, 0], ra: [0, 0, 155], la: [0, 0, 155], rl: [0, 0, 4], ll: [0, 0, 4] },
    point: { head: [0, -18, 0], ra: [-70, 0, 40], la: [0, 0, 6], rl: [0, 0, 2], ll: [0, 0, 2] },
    relaxed: { head: [6, -8, 3], ra: [14, 0, 4], la: [14, 0, 4], rl: [-6, 0, 2], ll: [8, 0, 3] },
    cool: { head: [-4, 18, 0], ra: [-20, 0, -16], la: [-20, 0, -16], rl: [0, 0, 0], ll: [-8, 0, -6] },
    // the Play transition: arms up, legs apart, falling (not picked as a home pose)
    fall: { head: [-22, 0, 0], ra: [-12, 0, 148], la: [-12, 0, 148], rl: [16, 0, 12], ll: [-10, 0, 12], flail: true },
    // the sunken transition: floating backwards in the water, arms spread softly up towards the light (one higher than
    // the other), legs loose and a little apart, head tilted back looking up
    sunken: { head: [-34, 0, 0], ra: [-120, 0, 40], la: [-78, 0, 52], rl: [-50, 0, 12], ll: [-28, 0, 18], drift: true },
  };
  const POSE_NAMES = Object.keys(POSES).filter((p) => p !== 'fall' && p !== 'sunken');

  const rad = (d) => (d * Math.PI) / 180;
  function rot([rx, ry, rz]) {
    const [a, b, c] = [rad(rx), rad(ry), rad(rz)];
    const [ca, sa, cb, sb, cc, sc] = [Math.cos(a), Math.sin(a), Math.cos(b), Math.sin(b), Math.cos(c), Math.sin(c)];
    // R = Rz * Ry * Rx
    return [
      cc * cb, cc * sb * sa - sc * ca, cc * sb * ca + sc * sa,
      sc * cb, sc * sb * sa + cc * ca, sc * sb * ca - cc * sa,
      -sb, cb * sa, cb * ca,
    ];
  }
  const apply = (m, [x, y, z]) => [m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z];

  /** Boxes of the player: size, position (top-left-front corner), pivot, skin uv, overlay uv, overlay growth. */
  function parts(slim) {
    const aw = slim ? 3 : 4;
    return {
      head: { size: [8, 8, 8], pos: [-4, -8, -4], pivot: [0, 0, 0], uv: [0, 0], ov: [32, 0], grow: 0.5 },
      body: { size: [8, 12, 4], pos: [-4, 0, -2], pivot: [0, 0, 0], uv: [16, 16], ov: [16, 32], grow: 0.25 },
      ra: { size: [aw, 12, 4], pos: [-4 - aw, 0, -2], pivot: [-5, 2, 0], uv: [40, 16], ov: [40, 32], grow: 0.25 },
      la: { size: [aw, 12, 4], pos: [4, 0, -2], pivot: [5, 2, 0], uv: [32, 48], ov: [48, 48], grow: 0.25 },
      rl: { size: [4, 12, 4], pos: [-4, 12, -2], pivot: [-2, 12, 0], uv: [0, 16], ov: [0, 32], grow: 0.25 },
      ll: { size: [4, 12, 4], pos: [0, 12, -2], pivot: [2, 12, 0], uv: [16, 48], ov: [0, 48], grow: 0.25 },
    };
  }

  /**
   * The six faces of a box as texel grids: region in the skin (u, v, columns, rows) and how a texel (i, j) maps onto the
   * box (corner + i * along-x-of-face + j * along-y-of-face), with the outward normal. The front faces -z (towards you).
   */
  function faces(w, h, d, u, v) {
    return [
      { reg: [u + d, v + d, w, h], o: [0, 0, 0], a: [1, 0, 0], b: [0, 1, 0], n: [0, 0, -1] },              // front
      { reg: [u + 2 * d + w, v + d, w, h], o: [w, 0, d], a: [-1, 0, 0], b: [0, 1, 0], n: [0, 0, 1] },        // back
      { reg: [u, v + d, d, h], o: [0, 0, d], a: [0, 0, -1], b: [0, 1, 0], n: [-1, 0, 0] },                  // right (-x)
      { reg: [u + d + w, v + d, d, h], o: [w, 0, 0], a: [0, 0, 1], b: [0, 1, 0], n: [1, 0, 0] },            // left (+x)
      { reg: [u + d, v, w, d], o: [0, 0, d], a: [1, 0, 0], b: [0, 0, -1], n: [0, -1, 0] },                  // top
      { reg: [u + d + w, v, w, d], o: [0, h, 0], a: [1, 0, 0], b: [0, 0, 1], n: [0, 1, 0] },                // bottom
    ];
  }

  function pixels(img) {
    const c = document.createElement('canvas');
    c.width = 64;
    c.height = 64;
    const g = c.getContext('2d');
    g.drawImage(img, 0, 0);
    if (img.height === 32) {
      // old 64×32 skins: mirror the right arm and leg for the left side
      g.save();
      g.scale(-1, 1);
      g.drawImage(c, 0, 16, 16, 16, -32, 48, 16, 16);
      g.drawImage(c, 40, 16, 16, 16, -48, 48, 16, 16);
      g.restore();
    }
    return g.getImageData(0, 0, 64, 64).data;
  }

  const LIGHT = (() => { const l = [-0.35, -0.7, -0.62]; const n = Math.hypot(...l); return l.map((x) => x / n); })();

  /** Draws the figure. t in seconds for the idle motion; opts.view = [rx, ry, rz] turns the whole figure. */
  function draw(canvas, px, slim, poseName, t, opts = {}) {
    const ctx = canvas.getContext('2d');
    const W = canvas.width;
    const H = canvas.height;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, W, H);
    const pose = POSES[poseName] || POSES.idle;
    const breathe = Math.sin(t * 1.6);
    const sway = Math.sin(t * 0.5);
    const view = rot(opts.view || [-10, 24 + sway * 6, 0]);
    const quads = [];
    const P = parts(slim);
    for (const [key, part] of Object.entries(P)) {
      let r = key === 'body' ? [0, 0, 0] : pose[key].slice();
      if (key === 'ra' || key === 'la') {
        r[2] += breathe * 2.5;
        if (pose.wave && key === 'ra') r[2] -= Math.sin(t * 7) * 14;
        if (pose.flail) r[2] += Math.sin(t * 5 + (key === 'la' ? 1.4 : 0)) * 10;
        if (pose.drift) {
          // floating: two slow, unrelated waves per arm, so it never looks like a loop
          const ph = key === 'la' ? 2.3 : 0;
          r[0] += Math.sin(t * 0.9 + ph) * 8 + Math.sin(t * 2.1 + ph * 1.7) * 3;
          r[2] += Math.sin(t * 0.7 + ph) * 6;
        }
        if (key === 'la') r[2] = -r[2]; // the left arm lifts to the other side
      }
      if (pose.flail && (key === 'rl' || key === 'll')) r[0] += Math.sin(t * 4.2 + (key === 'll' ? 2 : 0)) * 14;
      if (pose.drift && (key === 'rl' || key === 'll')) {
        // a slow, lazy kick: the legs move against each other, a little apart and together
        const ph = key === 'll' ? Math.PI : 0;
        r[0] += Math.sin(t * 0.8 + ph) * 7 + Math.sin(t * 1.9 + ph) * 2;
        r[2] += Math.sin(t * 0.6 + ph * 0.5) * 3;
      }
      if (key === 'll') r[2] = -r[2];
      if (key === 'head') r = [r[0] + breathe * 1.2, r[1], r[2]];
      if (pose.drift && key === 'head') r = [r[0] + Math.sin(t * 0.5) * 4, r[1] + Math.sin(t * 0.37) * 7, r[2] + Math.sin(t * 0.43) * 3];
      const R = rot(r);
      for (const layer of [0, 1]) {
        const g = layer ? part.grow : 0;
        const [w, h, d] = part.size;
        const [u, v] = layer ? part.ov : part.uv;
        const origin = [part.pos[0] - g, part.pos[1] - g, part.pos[2] - g];
        const sx = (w + 2 * g) / w;
        const sy = (h + 2 * g) / h;
        const sz = (d + 2 * g) / d;
        for (const f of faces(w, h, d, u, v)) {
          const nrm = apply(view, apply(R, f.n));
          if (nrm[2] >= -0.02) continue; // facing away
          const shade = Math.max(0.62, Math.min(1.08, 0.74 + 0.38 * -(nrm[0] * LIGHT[0] + nrm[1] * LIGHT[1] + nrm[2] * LIGHT[2])));
          const [ru, rv, cols, rows] = f.reg;
          for (let j = 0; j < rows; j++) {
            for (let i = 0; i < cols; i++) {
              const k = ((rv + j) * 64 + ru + i) * 4;
              const alpha = px[k + 3];
              if (alpha < (layer ? 20 : 1)) continue;
              const corner = (di, dj) => {
                const local = [
                  (f.o[0] + f.a[0] * (i + di) + f.b[0] * (j + dj)) * sx + origin[0],
                  (f.o[1] + f.a[1] * (i + di) + f.b[1] * (j + dj)) * sy + origin[1],
                  (f.o[2] + f.a[2] * (i + di) + f.b[2] * (j + dj)) * sz + origin[2],
                ];
                const p = apply(R, [local[0] - part.pivot[0], local[1] - part.pivot[1], local[2] - part.pivot[2]]);
                return apply(view, [p[0] + part.pivot[0], p[1] + part.pivot[1] + (key === 'head' ? breathe * 0.15 : 0), p[2] + part.pivot[2]]);
              };
              const q = [corner(0, 0), corner(1, 0), corner(1, 1), corner(0, 1)];
              quads.push({
                q,
                z: (q[0][2] + q[1][2] + q[2][2] + q[3][2]) / 4 - layer * 0.01,
                c: `rgba(${Math.min(255, Math.round(px[k] * shade))},${Math.min(255, Math.round(px[k + 1] * shade))},${Math.min(255, Math.round(px[k + 2] * shade))},${(alpha / 255).toFixed(3)})`,
              });
            }
          }
        }
      }
    }
    quads.sort((a, b) => b.z - a.z);
    // fit: the figure is about 32 units tall (+ raised arms)
    const s = Math.min(W / 32, H / 46);
    const ox = W / 2;
    const oy = H / 2 - 8 * s;
    ctx.lineJoin = 'round';
    for (const { q, c } of quads) {
      ctx.fillStyle = c;
      ctx.beginPath();
      ctx.moveTo(ox + q[0][0] * s, oy + q[0][1] * s);
      ctx.lineTo(ox + q[1][0] * s, oy + q[1][1] * s);
      ctx.lineTo(ox + q[2][0] * s, oy + q[2][1] * s);
      ctx.lineTo(ox + q[3][0] * s, oy + q[3][1] * s);
      ctx.closePath();
      ctx.fill();
      // close the hairline gaps between neighbouring texels
      ctx.strokeStyle = c;
      ctx.lineWidth = 0.7;
      ctx.stroke();
    }
  }

  /** A pose different from the last start's. */
  function nextPose() {
    let last = null;
    try { last = localStorage.getItem('onix.pose'); } catch { /* storage off */ }
    const options = POSE_NAMES.filter((p) => p !== last);
    const pick = options[Math.floor(Math.random() * options.length)];
    try { localStorage.setItem('onix.pose', pick); } catch { /* storage off */ }
    return pick;
  }

  return { draw, pixels, nextPose, POSES: POSE_NAMES };
})();
