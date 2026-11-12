'use strict';

/*
 * The start screen – "sunken": you are under water. Far above, the surface shimmers and light falls in through it in
 * slow, wavering rays; specks drift in the light. Your Minecraft character falls backwards through it in slow motion,
 * on its back, arms and legs stretched up towards the light, a trail of air bubbles rising from it, and sinks away
 * into the dark while the launcher loads. Then the launcher fades in.
 */
const Fall = (() => {
  const clamp01 = (x) => Math.min(1, Math.max(0, x));
  const smooth = (x) => { x = clamp01(x); return x * x * (3 - 2 * x); };
  const rnd = (seed) => { const x = Math.sin(seed * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };

  function plainSkin() {
    const px = new Uint8ClampedArray(64 * 64 * 4);
    for (let i = 0; i < 64 * 64; i++) {
      const y = Math.floor(i / 64);
      const shade = y >= 16 && y < 48 ? 190 : 214;
      px.set([shade, shade, shade + 10, 255], i * 4);
    }
    return px;
  }

  /** The water: deep teal getting darker below, the shimmering surface and the light rays falling through it. */
  function drawWater(g, W, H, t) {
    const U = H / 100;
    const bg = g.createLinearGradient(0, 0, 0, H);
    bg.addColorStop(0, '#16505a');
    bg.addColorStop(0.3, '#0c3039');
    bg.addColorStop(0.7, '#061a20');
    bg.addColorStop(1, '#02080a');
    g.fillStyle = bg;
    g.fillRect(0, 0, W, H);

    g.save();
    g.globalCompositeOperation = 'lighter';
    // the surface far above: a bright band that ripples
    const glow = g.createRadialGradient(W / 2, -18 * U, 0, W / 2, -18 * U, 70 * U);
    glow.addColorStop(0, 'rgba(150,230,235,0.45)');
    glow.addColorStop(1, 'rgba(150,230,235,0)');
    g.fillStyle = glow;
    g.fillRect(0, 0, W, H);
    g.filter = `blur(${Math.max(1, U * 0.35).toFixed(1)}px)`;
    for (let i = 0; i < 26; i++) {
      // caustic streaks near the top, drifting and flickering like light through waves
      const x = ((rnd(i) * 1.4 - 0.2) * W + Math.sin(t * 0.35 + i * 1.7) * 6 * U);
      const y = (1.5 + rnd(i + 40) * 9) * U + Math.sin(t * 0.8 + i) * 0.6 * U;
      const len = (4 + rnd(i + 9) * 10) * U;
      const a = 0.03 + 0.04 * (0.5 + 0.5 * Math.sin(t * 1.6 + i * 2.3));
      g.strokeStyle = `rgba(200,250,255,${a})`;
      g.lineWidth = (0.6 + rnd(i + 3) * 0.8) * U;
      g.lineCap = 'round';
      g.beginPath();
      g.moveTo(x - len / 2, y);
      g.quadraticCurveTo(x, y + Math.sin(t + i) * 1.2 * U, x + len / 2, y);
      g.stroke();
    }
    g.filter = 'none';
    // god rays: soft wedges from the surface, swaying slowly and breathing
    for (let i = 0; i < 7; i++) {
      const base = W / 2 + (i - 3) * 11 * U + Math.sin(t * 0.22 + i * 1.3) * 4 * U;
      const lean = Math.sin(t * 0.17 + i) * 6 * U;
      const top = (1.2 + (i % 3) * 0.6) * U;
      const bottom = (9 + (i % 4) * 4) * U;
      const reach = H * (0.75 + 0.15 * rnd(i + 20));
      const a = 0.05 + 0.035 * Math.sin(t * 0.9 + i * 1.9);
      const ray = g.createLinearGradient(0, 0, 0, reach);
      ray.addColorStop(0, `rgba(170,240,245,${a})`);
      ray.addColorStop(0.6, `rgba(150,225,235,${a * 0.45})`);
      ray.addColorStop(1, 'rgba(150,225,235,0)');
      g.fillStyle = ray;
      g.beginPath();
      g.moveTo(base - top, 0);
      g.lineTo(base + top, 0);
      g.lineTo(base + lean + bottom, reach);
      g.lineTo(base + lean - bottom, reach);
      g.closePath();
      g.fill();
    }
    g.restore();

    // specks drifting in the water, three depths, brighter in the light
    for (let i = 0; i < 110; i++) {
      const z = 0.35 + (i % 3) * 0.3;
      const x = ((rnd(i) * W + Math.sin(t * 0.3 + i) * 4 * U * z) % W + W) % W;
      const y = ((rnd(i + 7) * H - t * (1.2 + 2.5 * z) * U) % H + H) % H;
      const light = clamp01(1 - Math.abs(x - W / 2) / (50 * U)) * clamp01(1 - y / H * 0.8);
      g.fillStyle = `rgba(205,248,252,${(0.12 + 0.55 * light) * z * (0.4 + 0.6 * rnd(i + 3))})`;
      const s = (0.12 + 0.22 * z) * U * 0.6;
      g.beginPath();
      g.arc(x, y, s, 0, Math.PI * 2);
      g.fill();
    }
  }

  /** Air bubbles rising from (x, y): born over time, wobbling, growing a little and fading as they rise. */
  function drawBubbles(g, bubbles, U, dt, alpha) {
    for (let i = bubbles.length - 1; i >= 0; i--) {
      const b = bubbles[i];
      b.age += dt;
      b.y -= (b.speed + b.age * 0.6) * U * dt;
      const x = b.x + Math.sin(b.age * b.wob + b.seed) * 0.8 * U;
      const r = b.r * U * (1 + b.age * 0.12);
      const a = clamp01(1 - b.age / b.life) * clamp01(b.age * 4) * alpha;
      if (a <= 0.01 || b.y < -10) {
        bubbles.splice(i, 1);
        continue;
      }
      // a thin bright ring with a highlight: reads as a bubble, not a dot
      g.strokeStyle = `rgba(210,250,255,${0.55 * a})`;
      g.lineWidth = Math.max(1, r * 0.22);
      g.beginPath();
      g.arc(x, b.y, r, 0, Math.PI * 2);
      g.stroke();
      g.fillStyle = `rgba(190,245,255,${0.10 * a})`;
      g.fill();
      g.fillStyle = `rgba(255,255,255,${0.75 * a})`;
      g.beginPath();
      g.arc(x - r * 0.35, b.y - r * 0.35, r * 0.25, 0, Math.PI * 2);
      g.fill();
    }
  }

  /** The bounding box of the visible pixels of the figure canvas (to keep the figure centred however it turns). */
  function bounds(canvas) {
    const { width: w, height: h } = canvas;
    const data = canvas.getContext('2d').getImageData(0, 0, w, h).data;
    let x0 = w, y0 = h, x1 = 0, y1 = 0;
    for (let y = 0; y < h; y += 2) {
      for (let x = 0; x < w; x += 2) {
        if (data[(y * w + x) * 4 + 3] > 20) {
          if (x < x0) x0 = x;
          if (x > x1) x1 = x;
          if (y < y0) y0 = y;
          if (y > y1) y1 = y;
        }
      }
    }
    return x1 > x0 ? { cx: (x0 + x1) / 2, cy: (y0 + y1) / 2 } : { cx: w / 2, cy: h / 2 };
  }

  /**
   * Runs the scene in `canvas` until `finish()` is called. `skin()` returns {px, slim} once your skin is known (or
   * null); the figure waits for it a moment so it never swaps mid-fall. Returns { finish(): Promise }.
   * opts.loop (seconds): the figure falls in again after that long, endlessly (the website's header);
   * opts.y (0..1, default 0.46) and opts.scale (default 1): where the figure floats and how big it is.
   */
  function boot(canvas, skin, opts = {}) {
    const g = canvas.getContext('2d');
    const FW = 600, FH = 600;
    const figure = document.createElement('canvas');
    figure.width = FW;
    figure.height = FH;
    const lit = document.createElement('canvas');
    lit.width = FW;
    lit.height = FH;
    const lg = lit.getContext('2d');
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const start = performance.now();
    const bubbles = [];
    let who = null;          // the skin the figure falls with
    let fallStart = 0;       // seconds after start when the figure appears
    let ending = 0;          // time finish() was called
    let done = null;
    let raf = 0;
    let last = start;
    let centre = null;
    let frameNo = 0;
    let nextBubble = 0;

    function frame(now) {
      const t = (now - start) / 1000;
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      const W = Math.round(canvas.clientWidth * dpr);
      const H = Math.round(canvas.clientHeight * dpr);
      if (W > 0 && (canvas.width !== W || canvas.height !== H)) {
        canvas.width = W;
        canvas.height = H;
      }
      const U = H / 100;
      g.setTransform(1, 0, 0, 1, 0, 0);
      g.globalCompositeOperation = 'source-over';
      g.globalAlpha = 1;
      g.filter = 'none';
      drawWater(g, W, H, t);

      if (!who) {
        const s = skin && skin();
        if (s && s.px) who = { px: s.px, slim: !!s.slim };
        else if (t > 0.9) who = { px: plainSkin(), slim: false };
        if (who) fallStart = t;
      }
      if (who) {
        if (opts.loop && t - fallStart > opts.loop) {
          fallStart = t;
          nextBubble = 0;
        }
        const f = t - fallStart;
        // drops in from above, slows down as the water takes it, then sinks slowly and gets smaller and darker
        const enter = 1 - Math.pow(1 - clamp01(f / 1.6), 3);
        const sink = smooth((f - 1.2) / 4.5);
        const size = Math.min(W, H) * (1.1 - 0.35 * sink) * (opts.scale || 1);
        const cx = W / 2 + Math.sin(f * 0.45) * 1.5 * U;
        const cy = -size * 0.4 + (H * (opts.y || 0.46) + size * 0.4) * enter + sink * 8 * U + Math.sin(f * 0.9) * 0.6 * U;
        const roll = (80 + Math.sin(f * 0.5) * 5) * Math.PI / 180;      // on its back, limbs up

        Skin3D.draw(figure, who.px, who.slim, 'sunken', f, { view: [6 + Math.sin(f * 0.5) * 3, 70 + Math.sin(f * 0.3) * 10, 0] });
        if (!centre || frameNo++ % 12 === 0) centre = bounds(figure);
        // light from the surface: a bright top edge, a cool shadow below, the colour of the water over everything
        lg.globalCompositeOperation = 'source-over';
        lg.clearRect(0, 0, FW, FH);
        lg.drawImage(figure, 0, 0);
        lg.globalCompositeOperation = 'source-atop';
        lg.fillStyle = `rgba(10,60,72,${0.12 + 0.45 * sink})`;
        lg.fillRect(0, 0, FW, FH);
        lg.save();
        lg.translate(centre.cx, centre.cy);
        lg.rotate(-roll);                        // "up" on screen, in the figure's own frame
        const sun = lg.createLinearGradient(0, -FH * 0.4, 0, FH * 0.4);
        sun.addColorStop(0, `rgba(190,245,250,${0.38 * (1 - sink)})`);
        sun.addColorStop(0.5, 'rgba(190,245,250,0)');
        sun.addColorStop(1, `rgba(2,16,20,${0.35 + 0.2 * sink})`);
        lg.fillStyle = sun;
        lg.fillRect(-FW, -FH, FW * 2, FH * 2);
        lg.restore();

        const scale = size / FH;
        g.save();
        g.translate(cx, cy);
        g.rotate(roll);
        g.globalAlpha = 1 - 0.5 * sink;
        if (sink > 0.3) g.filter = `blur(${((sink - 0.3) * 3 * dpr).toFixed(1)}px)`;
        g.drawImage(lit, -centre.cx * scale, -centre.cy * scale, FW * scale, FH * scale);
        g.restore();
        g.filter = 'none';

        // bubbles rise from the face and the hands now and then
        if (f > 0.6 && f > nextBubble) {
          const n = 1 + Math.floor(rnd(f * 13) * 2);
          for (let k = 0; k < n; k++) {
            bubbles.push({
              x: cx + (rnd(f * 7 + k) - 0.3) * size * 0.3, y: cy - size * 0.18 + (rnd(f * 3 + k) - 0.5) * size * 0.08,
              r: 0.35 + rnd(f * 5 + k) * 0.8, speed: 4 + rnd(f * 11 + k) * 4, wob: 3 + rnd(k + f) * 3,
              seed: rnd(f + k * 9) * 6, life: 2.2 + rnd(f * 17 + k) * 1.6, age: 0,
            });
          }
          nextBubble = f + 0.3 + rnd(f * 19) * 0.6;
        }
      }
      drawBubbles(g, bubbles, U, dt, 1);

      // vignette, and the dark that swallows everything at the end
      const vig = g.createRadialGradient(W / 2, H * 0.42, Math.min(W, H) * 0.25, W / 2, H * 0.5, Math.max(W, H) * 0.75);
      vig.addColorStop(0, 'rgba(0,0,0,0)');
      vig.addColorStop(1, 'rgba(0,5,7,0.7)');
      g.fillStyle = vig;
      g.fillRect(0, 0, W, H);
      if (ending) {
        const e = clamp01((now - ending) / 500);
        g.fillStyle = `rgba(1,5,6,${smooth(e)})`;
        g.fillRect(0, 0, W, H);
        if (e >= 1) {
          done();
          return;
        }
      }
      raf = requestAnimationFrame(frame);
    }
    raf = requestAnimationFrame(frame);

    return {
      /** Lets the figure sink away (at least ~3.5 s of the scene), fades to dark, then resolves. */
      finish(fast) {
        return new Promise((resolve) => {
          const go = () => {
            ending = performance.now();
            done = () => { cancelAnimationFrame(raf); resolve(); };
          };
          const left = fast ? 0 : Math.max(0, (fallStart + 3.8) * 1000 - (performance.now() - start));
          setTimeout(go, who || fast ? left : 3800);
        });
      },
    };
  }

  return { boot };
})();
