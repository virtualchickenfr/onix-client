'use strict';

/*
 * The scenes of the three designs (js/design.js), on the start page and on the start screen:
 *   water  – js/fall.js: your character sinks through deep water
 *   runner – a dark studio with light from the top right; your character runs in from the right, slows down, turns to
 *            you and waits; on Play it runs off to the left
 *   meadow – thousands of flowers on a grey meadow (tools/meadow.py) drifting slowly; on Play every petal lets go and
 *            flies up
 * Scenes.home(design) runs the start page's scene, Scenes.active(on) pauses it, Scenes.burst() is the Play moment,
 * Scenes.boot(canvas, design) runs the start screen and returns { finish(fast): Promise }.
 */
const Scenes = (() => {
  const clamp01 = (x) => Math.min(1, Math.max(0, x));
  const smooth = (x) => { x = clamp01(x); return x * x * (3 - 2 * x); };
  const easeOut = (x) => 1 - Math.pow(1 - clamp01(x), 3);
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;

  const images = {};
  function image(name) {
    if (!images[name]) {
      const img = new Image();
      img.src = `assets/designs/${name}`;
      images[name] = img;
    }
    return images[name];
  }
  const ready = (img) => img.complete && img.naturalWidth > 0;

  /** Draws an image to cover W×H (like background-size: cover), scaled by zoom and moved by (dx, dy) in pixels. */
  function cover(g, img, W, H, zoom = 1, dx = 0, dy = 0) {
    const s = Math.max(W / img.naturalWidth, H / img.naturalHeight) * zoom;
    const w = img.naturalWidth * s;
    const h = img.naturalHeight * s;
    g.drawImage(img, (W - w) / 2 + dx, (H - h) / 2 + dy, w, h);
  }

  /** The player's skin from the start page (js/home.js), or a plain figure until it is known. */
  function plainSkin() {
    const px = new Uint8ClampedArray(64 * 64 * 4);
    for (let i = 0; i < 64 * 64; i++) {
      const y = Math.floor(i / 64);
      const shade = y >= 16 && y < 48 ? 120 : 200;
      px.set([shade, shade + 4, shade + 14, 255], i * 4);
    }
    return { px, slim: false };
  }
  const PLAIN = plainSkin();
  const skinNow = () => (typeof homeSkin !== 'undefined' && homeSkin.px ? { px: homeSkin.px, slim: homeSkin.slim } : PLAIN);

  // mouse position for a little parallax (-1..1)
  const mouse = { x: 0, y: 0, ex: 0, ey: 0 };
  window.addEventListener('mousemove', (e) => {
    mouse.x = (e.clientX / Math.max(1, innerWidth)) * 2 - 1;
    mouse.y = (e.clientY / Math.max(1, innerHeight)) * 2 - 1;
  }, { passive: true });

  /** A scene loop on a canvas with device pixel sizing; draw(g, W, H, t, dt). */
  function loop(canvas, draw) {
    const g = canvas.getContext('2d');
    const start = performance.now();
    let last = start;
    let raf = 0;
    const frame = (now) => {
      raf = requestAnimationFrame(frame);
      const dpr = Math.min(2, window.devicePixelRatio || 1);
      const W = Math.round(canvas.clientWidth * dpr);
      const H = Math.round(canvas.clientHeight * dpr);
      if (!W || !H) return;
      if (canvas.width !== W || canvas.height !== H) {
        canvas.width = W;
        canvas.height = H;
      }
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      mouse.ex += (mouse.x - mouse.ex) * Math.min(1, dt * 3);
      mouse.ey += (mouse.y - mouse.ey) * Math.min(1, dt * 3);
      g.setTransform(1, 0, 0, 1, 0, 0);
      g.globalAlpha = 1;
      g.globalCompositeOperation = 'source-over';
      g.filter = 'none';
      draw(g, W, H, (now - start) / 1000, dt);
    };
    raf = requestAnimationFrame(frame);
    return { stop() { cancelAnimationFrame(raf); raf = 0; } };
  }

  // ------------------------------------------------------------------ runner

  function runner(canvas) {
    const bg = image('studio.jpg');
    const light = image('studio_light.png');
    const fig = document.createElement('canvas');
    let leaving = -1;          // time Play was pressed
    let cycle = 0;             // running cycle phase
    let lastX = null;
    const scene = loop(canvas, (g, W, H, t, dt) => {
      g.fillStyle = '#0c0d10';
      g.fillRect(0, 0, W, H);
      const breathe = Math.sin(t * 0.08) * 0.012;
      if (ready(bg)) cover(g, bg, W, H, 1.07 + breathe, -mouse.ex * W * 0.012, -mouse.ey * H * 0.012);
      // the figure: about half the height of the window, standing on an invisible floor
      const fh = Math.round(H * 0.6);
      const fw = Math.round(fh * 0.79);
      if (fig.width !== fw || fig.height !== fh) { fig.width = fw; fig.height = fh; }
      const target = W * 0.7;
      const arrive = (t - 0.6) / 1.9;                 // running in
      let x;
      let speed;                                      // 0..1 how fast the legs go
      let face;                                       // degrees: -90 running to the left, ~25 facing you
      if (leaving >= 0) {
        const l = t - leaving;
        x = target - easeIn(l / 1.4) * (target + fw);
        speed = Math.min(1, 0.4 + l * 2);
        face = -90 + (1 - smooth(l / 0.25)) * 115;
      } else {
        x = W + fw / 2 + (target - W - fw / 2) * easeOut(arrive);
        speed = 1 - smooth((arrive - 0.75) / 0.3);
        face = -90 + smooth((arrive - 0.85) / 0.35) * 115;
      }
      if (lastX !== null) cycle += dt * (5 + 7 * speed) * (speed > 0.02 ? 1 : 0);
      lastX = x;
      const swing = Math.sin(cycle) * speed;
      const bob = Math.abs(Math.cos(cycle)) * speed * fh * 0.025;
      const idle = t * 1.2;
      const add = {
        rl: [swing * 55, 0, 0], ll: [-swing * 55, 0, 0],
        ra: [-swing * 50, 0, speed ? 0 : Math.sin(idle) * 2], la: [swing * 50, 0, speed ? 0 : Math.sin(idle) * 2],
        head: [speed * 6, 0, 0],
      };
      const skin = skinNow();
      Skin3D.draw(fig, skin.px, skin.slim, 'idle', t, { add, view: [-6, face, 0] });
      const floor = H * 0.86;
      const fx = x - fw / 2;
      const fy = floor - fh - bob;
      // soft shadow on the floor
      const sh = g.createRadialGradient(x, floor, 0, x, floor, fw * 0.45);
      sh.addColorStop(0, 'rgba(0,0,0,0.55)');
      sh.addColorStop(1, 'rgba(0,0,0,0)');
      g.save();
      g.translate(0, floor);
      g.scale(1, 0.18);
      g.fillStyle = sh;
      g.fillRect(x - fw, -fw, fw * 2, fw * 2);
      g.restore();
      // a faint reflection on the floor
      g.save();
      g.globalAlpha = 0.12;
      g.translate(fx, floor * 2 - fy + bob * 2);
      g.scale(1, -1);
      g.drawImage(fig, 0, -fh * 0.02, fw, fh);
      g.restore();
      g.drawImage(fig, fx, fy, fw, fh);
      // the light from the top right, closer to the camera than the figure
      if (ready(light)) {
        g.globalCompositeOperation = 'screen';
        g.globalAlpha = 0.75 + 0.2 * Math.sin(t * 0.5);
        cover(g, light, W, H, 1.12, -mouse.ex * W * 0.035 + Math.sin(t * 0.21) * W * 0.006, -mouse.ey * H * 0.03);
        g.globalAlpha = 1;
        g.globalCompositeOperation = 'source-over';
      }
    });
    return {
      stop: scene.stop,
      burst() { if (leaving < 0) leaving = (performance.now() - startedAt) / 1000; },
    };
  }
  const easeIn = (x) => clamp01(x) ** 2.2;
  let startedAt = performance.now();

  // ------------------------------------------------------------------ meadow

  const PETAL_FRAMES = 8;

  function meadow(canvas) {
    const bg = image('meadow.jpg');
    const sheet = image('petals.png');
    const petals = [];
    let bursting = -1;
    let ambientNext = 0;
    const spawn = (W, H, near, fromMeadow) => {
      const y = fromMeadow ? H * (0.42 + Math.random() * 0.62) : H * (0.35 + Math.random() * 0.7);
      const depth = clamp01((y / H - 0.34) / 0.66);       // 0 far … 1 near
      petals.push({
        x: Math.random() * W,
        y,
        size: (16 + 46 * depth ** 1.3) * (H / 800) * (0.75 + Math.random() * 0.5) * (near ? 1.25 : 1),
        vx: (Math.random() - 0.5) * W * 0.04,
        vy: -(0.03 + Math.random() * 0.08) * H * (near ? 1 : 0.4),
        acc: -(0.12 + Math.random() * 0.3) * H * (near ? 1 : 0.04),
        rot: Math.random() * Math.PI * 2,
        spin: (Math.random() - 0.5) * 6,
        flip: Math.random() * Math.PI * 2,
        flipSpeed: 3 + Math.random() * 5,
        frame: Math.floor(Math.random() * PETAL_FRAMES),
        wob: Math.random() * Math.PI * 2,
        life: 0,
        blur: depth > 0.85 ? 2 : 0,
      });
    };
    const scene = loop(canvas, (g, W, H, t, dt) => {
      g.fillStyle = '#9a9ea2';
      g.fillRect(0, 0, W, H);
      const b = bursting >= 0 ? t - bursting : -1;
      if (ready(bg)) {
        // slow drift like a camera on a slider; it lifts a little when the petals fly
        const lift = b >= 0 ? easeOut(b / 3) * H * 0.02 : 0;
        cover(g, bg, W, H, 1.06 + Math.sin(t * 0.05) * 0.015, -mouse.ex * W * 0.01 + Math.sin(t * 0.04) * W * 0.01, -mouse.ey * H * 0.008 + lift);
      }
      // soft light moving over the meadow
      g.globalCompositeOperation = 'soft-light';
      const lx = W * (0.5 + Math.sin(t * 0.07) * 0.3);
      const lg = g.createRadialGradient(lx, H * 0.6, 0, lx, H * 0.6, W * 0.5);
      lg.addColorStop(0, 'rgba(255,255,255,0.35)');
      lg.addColorStop(1, 'rgba(255,255,255,0)');
      g.fillStyle = lg;
      g.fillRect(0, 0, W, H);
      g.globalCompositeOperation = 'source-over';
      // a few loose petals all the time, a storm of them on Play
      if (!reduced && t > ambientNext) {
        spawn(W, H, false, false);
        ambientNext = t + 0.5 + Math.random() * 0.9;
      }
      if (b >= 0 && b < 1.6) {
        const n = Math.max(2, Math.round(dt * 520 * (1 - b / 1.6) + dt * 80));
        for (let i = 0; i < n; i++) spawn(W, H, true, true);
      }
      if (b >= 0 && b < 2.5) {
        // the meadow is a little paler once its petals are gone
        g.fillStyle = `rgba(210,212,214,${0.12 * smooth(b / 1.5)})`;
        g.fillRect(0, 0, W, H);
      }
      for (let i = petals.length - 1; i >= 0; i--) {
        const p = petals[i];
        p.life += dt;
        p.vy += p.acc * dt;
        p.x += (p.vx + Math.sin(p.life * 2 + p.wob) * H * 0.04) * dt;
        p.y += p.vy * dt;
        p.rot += p.spin * dt;
        p.flip += p.flipSpeed * dt;
        if (p.y < -60 || p.life > 14) { petals.splice(i, 1); continue; }
        if (!ready(sheet)) continue;
        const fs = sheet.naturalHeight;
        g.save();
        g.translate(p.x, p.y);
        g.rotate(p.rot);
        g.scale(1, Math.max(0.15, Math.abs(Math.cos(p.flip))));
        g.globalAlpha = Math.min(1, p.life * 4) * (p.blur ? 0.85 : 1);
        if (p.blur) g.filter = `blur(${p.blur}px)`;
        g.drawImage(sheet, p.frame * fs, 0, fs, fs, -p.size / 2, -p.size / 2, p.size, p.size);
        g.restore();
      }
    });
    return {
      stop: scene.stop,
      burst() { bursting = (performance.now() - startedAt) / 1000; },
    };
  }

  // ------------------------------------------------------------------ the start page

  let home = null;
  let homeDesign = null;
  let homeOn = true;

  function make(canvas, design) {
    startedAt = performance.now();
    if (design === 'runner') return runner(canvas);
    if (design === 'meadow') return meadow(canvas);
    const fall = Fall.boot(canvas, () => (typeof homeSkin !== 'undefined' && homeSkin.px ? homeSkin : null), { loop: 16, y: 0.42, scale: 0.85, waitSkin: 2 });
    return { stop: fall.stop, burst() {} };
  }

  function startHome() {
    const canvas = $('#design-scene');
    if (!canvas || !homeDesign) return;
    if (home) home.stop();
    home = homeOn ? make(canvas, homeDesign) : null;
  }

  return {
    home(design) {
      if (design === homeDesign && home) return;
      homeDesign = design;
      startHome();
    },
    active(on) {
      if (on === homeOn) return;
      homeOn = on;
      if (!on && home) { home.stop(); home = null; } else if (on) startHome();
    },
    burst() { if (home) home.burst(); },
    /** The start screen: runs the design's scene until finish(); then a short fade to dark. */
    boot(canvas, design) {
      if (design === 'water' || !design) return Fall.boot(canvas, () => (typeof homeSkin !== 'undefined' && homeSkin.px ? homeSkin : null));
      const s = make(canvas, design);
      const begun = performance.now();
      return {
        finish(fast) {
          return new Promise((resolve) => {
            const wait = fast ? 0 : Math.max(0, 2600 - (performance.now() - begun));
            setTimeout(() => {
              canvas.style.transition = 'opacity .5s';
              canvas.style.opacity = '0';
              setTimeout(() => { s.stop(); resolve(); }, 500);
            }, wait);
          });
        },
      };
    },
  };
})();
