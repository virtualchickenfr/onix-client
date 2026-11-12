'use strict';

/*
 * The start screen – "sunken": a dark teal void, soft light falling in from above with dust drifting in it, and your
 * Minecraft character sinking through it in slow motion, always looking up at the light with both arms stretched up
 * towards it, turning slowly as it sinks away into the dark. It runs while the launcher loads; then the launcher fades in.
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

  /** Draws the background (light, dust) for time t. */
  function drawVoid(g, W, H, t) {
    const U = H / 100;
    const bg = g.createLinearGradient(0, 0, 0, H);
    bg.addColorStop(0, '#0f3a44');
    bg.addColorStop(0.45, '#08232b');
    bg.addColorStop(1, '#020709');
    g.fillStyle = bg;
    g.fillRect(0, 0, W, H);

    g.save();
    g.globalCompositeOperation = 'lighter';
    const haze = g.createRadialGradient(W / 2, -10 * U, 0, W / 2, -10 * U, 75 * U);
    haze.addColorStop(0, 'rgba(120,210,220,0.35)');
    haze.addColorStop(1, 'rgba(120,210,220,0)');
    g.fillStyle = haze;
    g.fillRect(0, 0, W, H);
    const sway = Math.sin(t * 0.5) * 2 * U;
    for (let i = 0; i < 5; i++) {
      const x = W / 2 + (i - 2) * 9 * U + sway * (i % 2 ? 1 : -1);
      const cone = g.createLinearGradient(0, 0, 0, H * 0.9);
      cone.addColorStop(0, `rgba(150,225,235,${0.06 + 0.03 * Math.sin(t * 1.1 + i)})`);
      cone.addColorStop(1, 'rgba(150,225,235,0)');
      g.fillStyle = cone;
      g.beginPath();
      g.moveTo(x - 2 * U, 0);
      g.lineTo(x + 2 * U, 0);
      g.lineTo(x + (8 + i * 2) * U, H * 0.9);
      g.lineTo(x - (8 + i * 2) * U, H * 0.9);
      g.closePath();
      g.fill();
    }
    g.restore();

    for (let i = 0; i < 90; i++) {
      const z = 0.4 + (i % 3) * 0.3;
      const x = ((rnd(i) * W + Math.sin(t * 0.4 + i) * 3 * U * z) % W + W) % W;
      const y = ((rnd(i + 7) * H - t * (2 + 3 * z) * U) % H + H) % H;
      const near = clamp01(1 - Math.abs(x - W / 2) / (45 * U));
      g.fillStyle = `rgba(200,245,250,${(0.25 + 0.5 * near) * z * (0.4 + 0.6 * rnd(i + 3))})`;
      const s = (0.18 + 0.25 * z) * U * 0.6;
      g.fillRect(x, y, s, s);
    }
  }

  /**
   * Runs the scene in `canvas` until `finish()` is called. `skin()` returns {px, slim} once your skin is known (or
   * null); the figure waits for it a moment so it never swaps mid-fall. Returns { finish(): Promise }.
   */
  function boot(canvas, skin) {
    const g = canvas.getContext('2d');
    const figure = document.createElement('canvas');
    figure.width = 420;
    figure.height = 520;
    const tinted = document.createElement('canvas');
    tinted.width = 420;
    tinted.height = 520;
    const tg = tinted.getContext('2d');
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const start = performance.now();
    let who = null;          // the skin the figure falls with
    let fallStart = 0;       // seconds after start when the figure appears
    let ending = 0;          // time finish() was called
    let done = null;
    let raf = 0;

    function frame(now) {
      const t = (now - start) / 1000;
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
      drawVoid(g, W, H, t);

      if (!who) {
        const s = skin && skin();
        if (s && s.px) who = { px: s.px, slim: !!s.slim };
        else if (t > 0.9) who = { px: plainSkin(), slim: false };
        if (who) fallStart = t;
      }
      if (who) {
        const f = t - fallStart;
        const enter = smooth(f / 1.2);
        const sink = clamp01((f - 0.8) / 2.6);
        const size = Math.min(W, H) * (1.05 - 0.4 * sink);
        const cx = W / 2 + Math.sin(f * 0.7) * 2.5 * U;
        const cy = -size * 0.15 + (H * 0.42 + size * 0.15) * enter + sink * 6 * U;
        const roll = Math.sin(f * 0.7) * 5 * Math.PI / 180;           // upright, swaying gently in the water
        Skin3D.draw(figure, who.px, who.slim, 'sunken', f, { view: [8 + Math.sin(f * 0.6) * 3, 24 + Math.sin(f * 0.35) * 14, 0] });
        tg.clearRect(0, 0, 420, 520);
        tg.globalCompositeOperation = 'source-over';
        tg.drawImage(figure, 0, 0);
        tg.globalCompositeOperation = 'source-atop';
        tg.fillStyle = `rgba(8,52,62,${0.18 + 0.5 * sink})`;
        tg.fillRect(0, 0, 420, 520);
        const rim = tg.createLinearGradient(0, 0, 0, 520);
        rim.addColorStop(0, 'rgba(160,235,240,0)');
        rim.addColorStop(1, `rgba(170,240,245,${0.32 * (1 - sink)})`);
        tg.fillStyle = rim;
        tg.fillRect(0, 0, 420, 520);
        g.save();
        g.translate(cx, cy);
        g.rotate(roll);
        g.globalAlpha = 1 - 0.7 * sink;
        if (sink > 0.3) g.filter = `blur(${((sink - 0.3) * 5 * dpr).toFixed(1)}px)`;
        g.drawImage(tinted, -size * 0.4, -size * 0.5, size * 0.8, size);
        g.restore();
      }

      const vig = g.createRadialGradient(W / 2, H * 0.45, Math.min(W, H) * 0.2, W / 2, H * 0.5, Math.max(W, H) * 0.7);
      vig.addColorStop(0, 'rgba(0,0,0,0)');
      vig.addColorStop(1, 'rgba(0,4,6,0.75)');
      g.fillStyle = vig;
      g.fillRect(0, 0, W, H);
      // the dark that swallows everything at the end
      if (ending) {
        const e = clamp01((now - ending) / 450);
        g.fillStyle = `rgba(1,4,5,${e})`;
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
      /** Lets the figure sink away (at least ~3 s of the scene), fades to dark, then resolves. */
      finish(fast) {
        return new Promise((resolve) => {
          const go = () => {
            ending = performance.now();
            done = () => { cancelAnimationFrame(raf); resolve(); };
          };
          const left = fast ? 0 : Math.max(0, (fallStart + 3.2) * 1000 - (performance.now() - start));
          setTimeout(go, who || fast ? left : 3400);
        });
      },
    };
  }

  return { boot };
})();
