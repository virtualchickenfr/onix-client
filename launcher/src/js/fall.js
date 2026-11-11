'use strict';

/*
 * The Play transition, filmed like a shot: the camera follows your Minecraft character as it falls through an
 * evening sky (clouds rushing past in layers, motion blur), hits the sea (a crown of droplets, foam, shock rings,
 * a short shake and flash) and dives after it: god rays from the surface, drifting specks, rising bubbles, the
 * character tinting blue as it sinks into the dark. About 3.4 seconds; skipped for reduced motion.
 */
const Fall = (() => {
  const DURATION = 3800;
  const IMPACT = 1.25; // seconds until the figure hits the water
  let running = null;

  const clamp01 = (x) => Math.min(1, Math.max(0, x));
  const ease = (x) => 1 - Math.pow(1 - clamp01(x), 3);
  const rnd = (seed) => { const x = Math.sin(seed * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };

  /** Scene time: a short slow motion around the moment the figure breaks the surface. */
  function slowMotion(real) {
    const a = IMPACT - 0.12;
    const len = 0.75;      // real seconds of slow motion
    const rate = 0.35;     // scene speed during it
    if (real < a) return real;
    if (real < a + len) return a + (real - a) * rate;
    return a + len * rate + (real - a - len);
  }

  function plainSkin() {
    const px = new Uint8ClampedArray(64 * 64 * 4);
    for (let i = 0; i < 64 * 64; i++) {
      const y = Math.floor(i / 64);
      const shade = y >= 16 && y < 48 ? 190 : 214;
      px.set([shade, shade, shade + 10, 255], i * 4);
    }
    return px;
  }

  function play(skin) {
    if (running) return running;
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return Promise.resolve();
    const px = (skin && skin.px) || plainSkin();
    const slim = !!(skin && skin.slim);

    const layer = document.createElement('div');
    layer.className = 'fall-layer';
    const canvas = document.createElement('canvas');
    layer.append(canvas);
    document.body.append(layer);
    const g = canvas.getContext('2d');
    const figure = document.createElement('canvas');
    figure.width = 360;
    figure.height = 460;
    const tinted = document.createElement('canvas');
    tinted.width = 360;
    tinted.height = 460;
    const tg = tinted.getContext('2d');
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const trail = [];
    const start = performance.now();

    running = new Promise((resolve) => {
      function frame(now) {
        const p = (now - start) / DURATION;
        const t = slowMotion((now - start) / 1000);
        const W = Math.round(innerWidth * dpr);
        const H = Math.round(innerHeight * dpr);
        if (canvas.width !== W || canvas.height !== H) {
          canvas.width = W;
          canvas.height = H;
        }
        const U = H / 100; // one "unit" = 1 % of the height

        // ---------------------------------------------------------------- the shot: world y grows downwards
        const SURFACE = 150 * U;                                // where the sea is
        const START = -20 * U;
        const accel = 2 * (SURFACE - START) / (IMPACT * IMPACT);
        const vImpact = accel * IMPACT;
        let figY;
        if (t < IMPACT) figY = START + 0.5 * accel * t * t;
        else {
          const s = t - IMPACT;
          const k = 3.2;                                       // the water brakes the fall, then a slow sink
          figY = SURFACE + (vImpact / k) * (1 - Math.exp(-k * s)) + 5 * U * s;
        }
        const camY = figY - 42 * U;                             // the camera keeps the figure a bit above the middle
        const surfaceY = SURFACE - camY;                        // the sea on screen
        const under = clamp01((t - IMPACT) / 0.35);             // the camera is under water
        const depth = clamp01((figY - SURFACE) / (60 * U));    // how deep the figure is
        const speed = t < IMPACT ? accel * t : vImpact * Math.exp(-3.2 * (t - IMPACT));
        const shakeK = clamp01(1 - (t - IMPACT) / 0.35) * (t > IMPACT ? 1 : 0);
        const shake = shakeK * 1.2 * U;

        g.setTransform(1, 0, 0, 1, Math.sin(t * 70) * shake, Math.cos(t * 57) * shake);
        g.globalAlpha = clamp01(p / 0.06);
        g.globalCompositeOperation = 'source-over';

        // ---------------------------------------------------------------- sky and clouds (above the sea)
        if (surfaceY > -10 * U) {
          const sky = g.createLinearGradient(0, surfaceY - 160 * U, 0, surfaceY);
          sky.addColorStop(0, '#0d1230');
          sky.addColorStop(0.55, '#3b3a72');
          sky.addColorStop(0.85, '#b06a7f');
          sky.addColorStop(1, '#ffb27a');
          g.fillStyle = sky;
          g.fillRect(-W, -H, W * 3, surfaceY + H);
          const sun = g.createRadialGradient(W * 0.5, surfaceY + 2 * U, 0, W * 0.5, surfaceY, 70 * U);
          sun.addColorStop(0, 'rgba(255,214,160,0.75)');
          sun.addColorStop(0.15, 'rgba(255,170,120,0.35)');
          sun.addColorStop(1, 'rgba(255,150,120,0)');
          g.fillStyle = sun;
          g.fillRect(0, surfaceY - 80 * U, W, 80 * U);
          // clouds in three layers: far ones crawl, near ones rush past
          for (let i = 0; i < 26; i++) {
            const z = 0.35 + (i % 3) * 0.32;
            const wy = SURFACE - (12 + rnd(i) * 150) * U;
            const y = wy - camY * z - START * (1 - z);           // far layers move slower (parallax)
            const x = rnd(i + 9) * W;
            const r = (10 + rnd(i + 3) * 18) * U * z;
            if (y < -r || y > surfaceY + r * 0.2) continue;
            const c = g.createRadialGradient(x, y, 0, x, y, r);
            const warm = clamp01(1 - (surfaceY - y) / (90 * U));
            c.addColorStop(0, `rgba(${255},${200 + 30 * (1 - warm)},${200 - 40 * warm},${0.16 + 0.12 * z})`);
            c.addColorStop(1, 'rgba(255,220,220,0)');
            g.fillStyle = c;
            g.beginPath();
            g.ellipse(x, y, r * 1.8, r * 0.55, 0, 0, Math.PI * 2);
            g.fill();
          }
          // wind streaks while falling fast
          const wind = clamp01(speed / vImpact) * (1 - under);
          for (let i = 0; i < 40; i++) {
            const x = rnd(i * 3.1) * W;
            const len = (6 + rnd(i) * 16) * U * wind;
            const y = ((rnd(i * 7.7) * H * 1.6 - camY * (1.4 + rnd(i) * 0.8)) % (H * 1.6) + H * 1.6) % (H * 1.6) - 0.3 * H;
            if (y > surfaceY) continue;
            g.fillStyle = `rgba(255,255,255,${0.08 * wind})`;
            g.fillRect(x, y, Math.max(1, dpr), len);
          }
        }

        // ---------------------------------------------------------------- the sea
        const top = Math.max(-H, surfaceY);
        const sea = g.createLinearGradient(0, surfaceY, 0, surfaceY + 140 * U);
        sea.addColorStop(0, '#2e7fa8');
        sea.addColorStop(0.18, '#18557e');
        sea.addColorStop(0.55, '#0a2747');
        sea.addColorStop(1, '#02060f');
        g.fillStyle = sea;
        g.fillRect(-W, top, W * 3, H * 2);

        if (surfaceY > -5 * U && surfaceY < H + 5 * U) {
          if (under < 0.5) {
            // from above: the sun's reflection as a column of glints, small waves
            for (let i = 0; i < 46; i++) {
              const yy = surfaceY + 1 + Math.pow(rnd(i), 1.6) * 18 * U;
              const w = (2 + rnd(i + 2) * 9) * U * (1 - (yy - surfaceY) / (22 * U));
              const x = W / 2 + (rnd(i + 5) - 0.5) * 14 * U + Math.sin(t * 3 + i) * 1.5 * U;
              g.fillStyle = `rgba(255,214,170,${0.35 * (0.5 + 0.5 * Math.sin(t * 6 + i * 2.3))})`;
              g.fillRect(x - w / 2, yy, w, Math.max(1, dpr));
            }
            for (let i = 0; i < 9; i++) {
              const yy = surfaceY + (2 + i * i * 0.35) * U;
              g.strokeStyle = `rgba(200,230,255,${0.08 - i * 0.006})`;
              g.lineWidth = dpr;
              g.beginPath();
              for (let x = 0; x <= W; x += 12 * dpr) g.lineTo(x, yy + Math.sin(x / (30 * dpr) + t * 2 + i) * 0.5 * U);
              g.stroke();
            }
          } else {
            // from below: the bright, rippling underside of the surface
            g.save();
            g.globalCompositeOperation = 'lighter';
            const band = g.createLinearGradient(0, surfaceY - 2 * U, 0, surfaceY + 10 * U);
            band.addColorStop(0, 'rgba(180,230,255,0)');
            band.addColorStop(0.3, 'rgba(180,230,255,0.35)');
            band.addColorStop(1, 'rgba(180,230,255,0)');
            g.fillStyle = band;
            g.fillRect(0, surfaceY - 2 * U, W, 12 * U);
            g.strokeStyle = 'rgba(220,245,255,0.55)';
            g.lineWidth = 1.5 * dpr;
            g.beginPath();
            for (let x = 0; x <= W; x += 10 * dpr) g.lineTo(x, surfaceY + Math.sin(x / (22 * dpr) + t * 3) * 0.9 * U + Math.sin(x / (9 * dpr) - t * 5) * 0.3 * U);
            g.stroke();
            g.restore();
          }
        }

        // ---------------------------------------------------------------- under water: god rays and specks
        if (under > 0) {
          g.save();
          g.globalCompositeOperation = 'lighter';
          for (let i = 0; i < 9; i++) {
            const x0 = W * (0.05 + i * 0.11) + Math.sin(t * 0.6 + i * 1.7) * 3 * U;
            const spread = (4 + rnd(i) * 6) * U;
            const len = (70 + rnd(i + 4) * 50) * U;
            const ray = g.createLinearGradient(0, surfaceY, 0, surfaceY + len);
            const a = (0.07 + 0.05 * Math.sin(t * 1.3 + i * 2.1)) * under * (1 - depth * 0.5);
            ray.addColorStop(0, `rgba(170,225,255,${a})`);
            ray.addColorStop(1, 'rgba(170,225,255,0)');
            g.fillStyle = ray;
            g.beginPath();
            g.moveTo(x0 - spread * 0.3, surfaceY);
            g.lineTo(x0 + spread * 0.3, surfaceY);
            g.lineTo(x0 + spread * 2 + 8 * U, surfaceY + len);
            g.lineTo(x0 - spread * 2 + 8 * U, surfaceY + len);
            g.closePath();
            g.fill();
          }
          g.restore();
          for (let i = 0; i < 60; i++) {
            const x = (rnd(i) * W + Math.sin(t * 0.5 + i) * 2 * U);
            const wy = SURFACE + rnd(i + 11) * 160 * U;
            const y = wy - camY * (0.7 + rnd(i + 2) * 0.5);
            const yy = ((y - surfaceY) % (160 * U) + 160 * U) % (160 * U) + surfaceY;
            g.fillStyle = `rgba(200,235,255,${0.22 * under * rnd(i + 6)})`;
            g.fillRect(x, yy, 1.5 * dpr, 1.5 * dpr);
          }
        }

        // ---------------------------------------------------------------- the character (with a motion trail)
        const size = Math.min(W, H) * 0.42 * (1 - 0.18 * depth);
        const fx = W / 2 + Math.sin(t * 0.9) * 2 * U * under;
        const fy = figY - camY;
        const spin = t < IMPACT ? t * 95 : IMPACT * 95 + (t - IMPACT) * 28;
        Skin3D.draw(figure, px, slim, 'fall', t, { view: [-14 + 22 * under, 24 + spin, Math.sin(t * 2.4) * 7] });
        tg.clearRect(0, 0, 360, 460);
        tg.globalCompositeOperation = 'source-over';
        tg.drawImage(figure, 0, 0);
        if (under > 0) {
          tg.globalCompositeOperation = 'source-atop';
          tg.fillStyle = `rgba(14,70,120,${0.25 * under + 0.45 * depth})`;
          tg.fillRect(0, 0, 360, 460);
        }
        trail.unshift({ y: fy, a: 1 });
        if (trail.length > 6) trail.pop();
        const blur = clamp01(speed / vImpact) * (1 - under);
        for (let i = trail.length - 1; i >= 0; i--) {
          const k = i === 0 ? 1 : blur * (0.22 - i * 0.03);
          if (k <= 0) continue;
          g.globalAlpha = clamp01(p / 0.06) * k * (1 - depth * 0.55);
          g.drawImage(tinted, fx - size * 0.39, trail[i].y - size / 2 - i * speed * 0.004 * dpr, size * 0.78, size);
        }
        g.globalAlpha = clamp01(p / 0.06);

        // bubbles from the figure: rise, wobble, catch the light
        if (t > IMPACT) {
          for (let i = 0; i < 26; i++) {
            const born = IMPACT + rnd(i) * 1.8;
            if (t < born) continue;
            const age = t - born;
            const r = (0.35 + rnd(i + 1) * 0.9) * U;
            const x = fx + (rnd(i + 2) - 0.5) * size * 0.4 + Math.sin(age * 5 + i) * 1.2 * U;
            const y = fy - size * 0.25 - age * (14 + rnd(i + 3) * 18) * U;
            if (y < surfaceY + r) continue;
            g.strokeStyle = `rgba(220,245,255,${0.55 * (1 - depth * 0.4)})`;
            g.lineWidth = Math.max(1, 0.18 * U);
            g.beginPath();
            g.arc(x, y, r, 0, Math.PI * 2);
            g.stroke();
            g.fillStyle = 'rgba(255,255,255,0.7)';
            g.fillRect(x - r * 0.45, y - r * 0.5, Math.max(1, r * 0.35), Math.max(1, r * 0.35));
          }
        }

        // ---------------------------------------------------------------- the splash
        const since = t - IMPACT;
        if (since > -0.02 && since < 1.4) {
          const sy = SURFACE - camY;
          // foam burst
          const foam = g.createRadialGradient(W / 2, sy, 0, W / 2, sy, (8 + since * 30) * U);
          foam.addColorStop(0, `rgba(255,255,255,${0.7 * clamp01(1 - since / 0.6)})`);
          foam.addColorStop(1, 'rgba(255,255,255,0)');
          g.fillStyle = foam;
          g.fillRect(0, sy - 40 * U, W, 80 * U);
          // shock rings on the surface (in perspective)
          for (let i = 0; i < 4; i++) {
            const k = clamp01((since - i * 0.09) / 1.2);
            if (k <= 0 || k >= 1) continue;
            g.strokeStyle = `rgba(235,248,255,${0.6 * (1 - k)})`;
            g.lineWidth = (2.2 - i * 0.4) * dpr;
            g.beginPath();
            g.ellipse(W / 2, sy, (4 + ease(k) * 46) * U, (0.8 + ease(k) * 7) * U, 0, 0, Math.PI * 2);
            g.stroke();
          }
          // the crown: droplets thrown up and out, drawn as short streaks along their motion
          for (let i = 0; i < 120; i++) {
            const ang = Math.PI + 0.2 + rnd(i) * (Math.PI - 0.4);
            const sp = (50 + rnd(i + 1) * 85) * U;
            const vx = Math.cos(ang) * sp * (0.6 + rnd(i + 2) * 0.6);
            const vy = Math.sin(ang) * sp;
            const gr = 120 * U;
            const x = W / 2 + vx * since;
            const y = sy + vy * since + 0.5 * gr * since * since;
            if (y > sy + 1 || since < 0) continue;
            const dxv = vx, dyv = vy + gr * since;
            const l = Math.hypot(dxv, dyv) || 1;
            const len = (1.6 + rnd(i + 3) * 3.4) * U;
            g.strokeStyle = `rgba(240,250,255,${0.85 * clamp01(1 - since / 1.4)})`;
            g.lineWidth = (0.25 + rnd(i + 4) * 0.35) * U;
            g.lineCap = 'round';
            g.beginPath();
            g.moveTo(x, y);
            g.lineTo(x - (dxv / l) * len, y - (dyv / l) * len);
            g.stroke();
          }
          // a short flash as it breaks the surface
          g.fillStyle = `rgba(255,255,255,${0.35 * clamp01(1 - since / 0.25)})`;
          g.fillRect(-W, -H, W * 3, H * 3);
        }

        // ---------------------------------------------------------------- depth, vignette and the end
        g.setTransform(1, 0, 0, 1, 0, 0);
        const vig = g.createRadialGradient(W / 2, H / 2, Math.min(W, H) * 0.25, W / 2, H / 2, Math.max(W, H) * 0.75);
        vig.addColorStop(0, 'rgba(0,0,0,0)');
        vig.addColorStop(1, `rgba(0,4,12,${0.45 + 0.4 * depth})`);
        g.fillStyle = vig;
        g.fillRect(0, 0, W, H);
        const dark = clamp01((p - 0.74) / 0.16);
        g.fillStyle = `rgba(1,4,10,${dark})`;
        g.fillRect(0, 0, W, H);
        layer.style.opacity = String(1 - clamp01((p - 0.9) / 0.1));

        if (p < 1) requestAnimationFrame(frame);
        else {
          layer.remove();
          running = null;
          resolve();
        }
      }
      requestAnimationFrame(frame);
    });
    return running;
  }

  return { play };
})();
