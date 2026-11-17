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

  /*
   * The meadow (tools/meadow.py) comes as two pictures from the same random numbers – in bloom and bare (the hearts
   * only) – and a list of the clearly visible flowers: where they are, how big, which kind, how their heads are turned.
   * On Play a gust runs through the meadow from the front: where it passes, every flower lets go of its own petals
   * (each petal starts where it sat on the flower, turned the way it grew, in the flower's colour), they lift, flutter
   * and are carried up, and the bare meadow shows underneath.
   */
  const PETAL_FRAMES = 8;
  let flowerList = null;
  fetch('assets/designs/meadow_flowers.json').then((r) => r.json()).then((list) => { flowerList = list; }).catch(() => { flowerList = []; });

  // the gust: from just below the bottom centre, through the whole meadow in about a second
  const GUST = { x: 0.5, y: 1.08, speed: 1.05, soft: 0.14, delay: 0.05 };
  const gustAt = (nx, ny, W, H) => Math.hypot((nx - GUST.x) * W / H, ny - GUST.y) / 1.25;   // 0 … ~1.1

  function meadow(canvas) {
    const bg = image('meadow.jpg');
    const bare = image('meadow_bare.jpg');
    const sheet = image('petals.png');
    const petals = [];
    const layer = document.createElement('canvas');
    let bursting = -1;
    let built = false;
    let ambientNext = 1.5;
    let view = { s: 1, ox: 0, oy: 0 };
    const rnd = Math.random;

    /** A petal of flower f (index i of n), leaving at `start` seconds. */
    function petal(f, i, start, W, H) {
      const [fx, fy, fsize, kind, flat, rot, n, plen, pwid] = f;
      const iw = bg.naturalWidth || 1920;
      const ih = bg.naturalHeight || 1080;
      const sizePx = fsize * iw * view.s;
      const L = sizePx * plen;
      const a = rot + (2 * Math.PI * i) / n;
      // where the petal sits: half a petal out from the heart, the head seen from a little above
      const x = view.ox + fx * iw * view.s + Math.cos(a) * L * 0.5;
      const y = view.oy + fy * ih * view.s + Math.sin(a) * L * 0.5 * flat;
      const out = Math.cos(a);
      petals.push({
        x, y, L, start, kind,
        ang: Math.atan2(Math.sin(a) * flat, Math.cos(a)),
        squash: Math.max(0.35, flat),
        vx: out * L * (1.5 + rnd()) + (rnd() - 0.5) * L * 2,
        vy: -L * (1.4 + rnd() * 1.6) - H * 0.04,
        lift: -(0.38 + rnd() * 0.42) * H,                    // the gust carries it up harder and harder
        drift: (rnd() - 0.5) * W * 0.08 + W * 0.02,           // and to the side, every petal its own way
        spin: (rnd() - 0.5) * 7,
        flip: rnd() * Math.PI * 2,
        flipSpeed: 2.5 + rnd() * 4,
        wob: rnd() * Math.PI * 2,
        blur: fsize * iw > 40 ? 1 : 0,
        life: 0,
      });
    }

    /** Every visible flower's petals, timed by when the gust reaches it; tiny far petals for the flowers too small to list. */
    function build(W, H, t) {
      built = true;
      if (!flowerList || !ready(bg)) return;
      const iw = bg.naturalWidth;
      // every visible flower loses petals: the close ones all of them, the farther ones fewer – shared out so that every
      // flower the gust passes sends some up, never more than the budget
      const budget = reduced ? 700 : 3200;
      const visible = [];
      let wanted = 0;
      for (const f of flowerList) {
        const sx = view.ox + f[0] * iw * view.s;
        const sy = view.oy + f[1] * bg.naturalHeight * view.s;
        if (sx < -40 || sx > W + 40 || sy < -40 || sy > H + 40) continue;
        const sizePx = f[2] * iw * view.s;
        const want = sizePx > 26 ? f[6] : sizePx > 16 ? Math.min(f[6], 6) : 3;
        visible.push([f, sx, sy, want]);
        wanted += want;
      }
      const share = Math.min(1, budget / Math.max(1, wanted));
      for (const [f, sx, sy, want] of visible) {
        const count = Math.max(1, Math.round(want * share));
        const when = t + GUST.delay + gustAt(sx / W, sy / H, W, H) / GUST.speed + rnd() * 0.12;
        for (let i = 0; i < count; i++) {
          const k = count >= f[6] ? i : Math.floor((i + rnd()) * f[6] / count);
          petal(f, k, when + i * 0.02 + rnd() * 0.06, W, H);
        }
      }
      // the many small flowers too far away to list: their petals rise as a fine haze where the gust passes
      for (let i = 0; i < (reduced ? 150 : 1100); i++) {
        const nx = rnd();
        const ny = 0.36 + rnd() * 0.5;
        const depth = Math.max(0, (ny - 0.34) / 0.66);
        const fake = [((nx * W - view.ox) / view.s) / iw, ((ny * H - view.oy) / view.s) / bg.naturalHeight, (3 + 14 * depth * depth) / (iw * view.s),
          Math.floor(rnd() * 5), 0.7, rnd() * 6.28, 8, 1, 0.35];
        petal(fake, Math.floor(rnd() * 8), t + GUST.delay + gustAt(nx, ny, W, H) / GUST.speed + rnd() * 0.2, W, H);
      }
    }

    const scene = loop(canvas, (g, W, H, t, dt) => {
      g.fillStyle = '#5c6064';
      g.fillRect(0, 0, W, H);
      const b = bursting >= 0 ? t - bursting : -1;
      // the camera drifts slowly; it holds still once the gust comes, so the petals leave from the right places
      if (b < 0) {
        const zoom = 1.06 + Math.sin(t * 0.05) * 0.015;
        const s = ready(bg) ? Math.max(W / bg.naturalWidth, H / bg.naturalHeight) * zoom : 1;
        view = {
          s,
          ox: (W - (bg.naturalWidth || W) * s) / 2 - mouse.ex * W * 0.01 + Math.sin(t * 0.04) * W * 0.01,
          oy: (H - (bg.naturalHeight || H) * s) / 2 - mouse.ey * H * 0.008,
        };
      }
      if (ready(bg)) g.drawImage(bg, view.ox, view.oy, bg.naturalWidth * view.s, bg.naturalHeight * view.s);
      if (b >= 0 && !built) build(W, H, bursting);
      // the bare meadow shows where the gust has passed (a soft-edged circle growing from the front)
      if (b >= 0 && ready(bare)) {
        if (layer.width !== W || layer.height !== H) { layer.width = W; layer.height = H; }
        const lg = layer.getContext('2d');
        lg.globalCompositeOperation = 'source-over';
        lg.clearRect(0, 0, W, H);
        lg.drawImage(bare, view.ox, view.oy, bare.naturalWidth * view.s, bare.naturalHeight * view.s);
        const reach = Math.max(0, (b - GUST.delay - 0.15) * GUST.speed) * 1.25 * H;    // a little behind the petals
        const soft = GUST.soft * 1.25 * H;
        const cx = GUST.x * W;
        const cy = GUST.y * H;
        const mask = lg.createRadialGradient(cx, cy, Math.max(0, reach - soft), cx, cy, reach + soft);
        mask.addColorStop(0, 'rgba(0,0,0,1)');
        mask.addColorStop(1, 'rgba(0,0,0,0)');
        lg.globalCompositeOperation = 'destination-in';
        lg.fillStyle = mask;
        lg.fillRect(0, 0, W, H);
        g.drawImage(layer, 0, 0);
      }
      // soft light moving over the meadow
      g.globalCompositeOperation = 'soft-light';
      const lx = W * (0.5 + Math.sin(t * 0.07) * 0.3);
      const light = g.createRadialGradient(lx, H * 0.6, 0, lx, H * 0.6, W * 0.5);
      light.addColorStop(0, 'rgba(255,255,255,0.28)');
      light.addColorStop(1, 'rgba(255,255,255,0)');
      g.fillStyle = light;
      g.fillRect(0, 0, W, H);
      g.globalCompositeOperation = 'source-over';
      // now and then a single petal lets go of a flower in the breeze
      if (!reduced && b < 0 && t > ambientNext && flowerList && flowerList.length && ready(bg)) {
        const f = flowerList[Math.floor(rnd() * Math.min(600, flowerList.length))];
        petal(f, Math.floor(rnd() * f[6]), t, W, H);
        const p = petals[petals.length - 1];
        p.lift *= 0.25;
        ambientNext = t + 0.6 + rnd() * 1.4;
      }
      if (!ready(sheet)) return;
      const fs = sheet.naturalHeight;
      for (let i = petals.length - 1; i >= 0; i--) {
        const p = petals[i];
        if (t < p.start) continue;
        const age = t - p.start;
        p.life = age;
        // leaving: a moment of loosening, then the gust takes it
        const go = Math.min(1, age / 0.35);
        p.vy += p.lift * dt * go;
        p.vx += (p.drift - p.vx * 0.6) * dt;
        p.x += (p.vx + Math.sin(age * 2.2 + p.wob) * p.L * 2.2 * go) * dt;
        p.y += p.vy * dt;
        p.ang += p.spin * dt * go;
        p.flip += p.flipSpeed * dt * go;
        if (p.y < -p.L * 2 || age > 7) { petals.splice(i, 1); continue; }
        const sx = p.L / fs * 1.05;
        const sy = sx * (p.squash + (1 - p.squash) * go) * Math.max(0.12, Math.abs(Math.cos(p.flip * go)));
        const c = Math.cos(p.ang);
        const sn = Math.sin(p.ang);
        g.setTransform(c * sx, sn * sx, -sn * sy, c * sy, p.x, p.y);
        g.globalAlpha = Math.min(1, age * 6) * (age > 5 ? Math.max(0, 1 - (age - 5) / 2) : 1);
        g.drawImage(sheet, (p.kind % PETAL_FRAMES) * fs, 0, fs, fs, -fs / 2, -fs / 2, fs, fs);
      }
      g.setTransform(1, 0, 0, 1, 0, 0);
      g.globalAlpha = 1;
      g.filter = 'none';
    });
    return {
      stop: scene.stop,
      burst() { if (bursting < 0) bursting = (performance.now() - startedAt) / 1000; },
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
