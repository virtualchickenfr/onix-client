'use strict';

/*
 * The Play transition: your Minecraft character falls down through the screen, splashes into a dark, still surface
 * and sinks away into the deep; then the launcher comes back with the game starting. Clean on purpose: no text, a
 * few streaks, soft light, rings on the surface. About 2.4 seconds; skipped for reduced motion.
 */
const Fall = (() => {
  const DURATION = 2400;
  let running = null;

  const ease = (x) => 1 - Math.pow(1 - Math.min(1, Math.max(0, x)), 3);
  const easeIn = (x) => Math.pow(Math.min(1, Math.max(0, x)), 2);
  const clamp01 = (x) => Math.min(1, Math.max(0, x));

  /** A plain light figure for players without a skin picture. */
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
    const figure = document.createElement('canvas');
    figure.width = 360;
    figure.height = 460;
    const g = canvas.getContext('2d');
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const streaks = Array.from({ length: 34 }, (_, i) => ({ x: Math.random(), len: 40 + Math.random() * 120, speed: 0.6 + Math.random() * 1.2, off: Math.random(), a: 0.04 + (i % 4) * 0.025 }));
    const start = performance.now();

    running = new Promise((resolve) => {
      function frame(now) {
        const t = (now - start) / 1000;
        const p = (now - start) / DURATION;
        const W = Math.round(innerWidth * dpr);
        const H = Math.round(innerHeight * dpr);
        if (canvas.width !== W || canvas.height !== H) {
          canvas.width = W;
          canvas.height = H;
        }
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.clearRect(0, 0, W, H);

        // the void: fades in, turns deep blue once the figure has gone under
        const sink = clamp01((p - 0.5) / 0.35);
        const bg = g.createLinearGradient(0, 0, 0, H);
        bg.addColorStop(0, `rgba(${10 - 6 * sink},${10 + 6 * sink},${14 + 22 * sink},1)`);
        bg.addColorStop(1, `rgba(2,${3 + 4 * sink},${6 + 14 * sink},1)`);
        g.globalAlpha = clamp01(p / 0.12) * (1 - clamp01((p - 0.88) / 0.12));
        g.fillStyle = bg;
        g.fillRect(0, 0, W, H);
        const light = g.createRadialGradient(W / 2, -H * 0.1, 0, W / 2, -H * 0.1, H * 0.9);
        light.addColorStop(0, `rgba(180,205,255,${0.16 * (1 - sink)})`);
        light.addColorStop(1, 'rgba(180,205,255,0)');
        g.fillStyle = light;
        g.fillRect(0, 0, W, H);

        // speed streaks rushing upwards while falling
        const rush = 1 - clamp01((p - 0.42) / 0.2);
        for (const s of streaks) {
          const y = H - (((t * s.speed * 1.4 + s.off) % 1.2) * (H + s.len * dpr));
          g.fillStyle = `rgba(255,255,255,${s.a * rush})`;
          g.fillRect(s.x * W, y, Math.max(1, dpr), s.len * dpr);
        }

        // the surface: a soft line where the figure lands, with rings spreading from the splash
        const surfaceY = H * 0.64;
        const splash = clamp01((p - 0.44) / 0.5);
        if (p > 0.38) {
          const line = g.createLinearGradient(0, 0, W, 0);
          const la = 0.18 * clamp01((p - 0.38) / 0.08) * (1 - sink * 0.6);
          line.addColorStop(0, 'rgba(190,215,255,0)');
          line.addColorStop(0.5, `rgba(190,215,255,${la})`);
          line.addColorStop(1, 'rgba(190,215,255,0)');
          g.fillStyle = line;
          g.fillRect(0, surfaceY, W, Math.max(1, dpr));
          for (let i = 0; i < 3; i++) {
            const k = clamp01(splash - i * 0.12);
            if (k <= 0) continue;
            g.strokeStyle = `rgba(200,222,255,${0.4 * (1 - k)})`;
            g.lineWidth = 1.5 * dpr;
            g.beginPath();
            g.ellipse(W / 2, surfaceY, 30 * dpr + k * W * 0.32, 6 * dpr + k * 34 * dpr, 0, 0, Math.PI * 2);
            g.stroke();
          }
        }

        // the figure: falls in from above (accelerating), passes the surface and sinks, smaller and softer
        const fall = easeIn(clamp01(p / 0.46));
        const under = ease(clamp01((p - 0.46) / 0.42));
        const size = Math.min(W, H) * 0.5 * (1 - 0.45 * under);
        const cy = -size + (surfaceY - size * 0.1 + size) * fall + under * H * 0.18;
        Skin3D.draw(figure, px, slim, 'fall', t, { view: [-18 + 30 * under, 30 + t * 140, Math.sin(t * 3) * 8] });
        g.save();
        g.globalAlpha = 1 - under * 0.95;
        if (under > 0) {
          g.filter = `blur(${(under * 6 * dpr).toFixed(1)}px)`;
          // under the surface the figure turns blue
          g.globalCompositeOperation = 'source-over';
        }
        g.drawImage(figure, W / 2 - size * 0.39, cy - size / 2, size * 0.78, size);
        g.restore();
        if (under > 0) {
          g.fillStyle = `rgba(8,22,48,${0.35 * under})`;
          g.fillRect(0, surfaceY, W, H - surfaceY);
        }

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
