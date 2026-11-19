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
        face = 90 - (1 - smooth(l / 0.25)) * 65;       // turns away from you and runs off to the left, facing where it goes
      } else {
        x = W + fw / 2 + (target - W - fw / 2) * easeOut(arrive);
        speed = 1 - smooth((arrive - 0.75) / 0.3);
        face = 90 - smooth((arrive - 0.85) / 0.35) * 65; // runs in facing left (where it goes), then turns to you
      }
      if (lastX !== null) cycle += dt * (5 + 7 * speed) * (speed > 0.02 ? 1 : 0);
      lastX = x;
      const swing = Math.sin(cycle) * speed;
      const bob = Math.abs(Math.cos(cycle)) * speed * fh * 0.04;
      const idle = t * 1.2;
      const add = {
        rl: [swing * 62, 0, 0], ll: [-swing * 62, 0, 0],
        ra: [-swing * 58, 0, speed ? 4 : Math.sin(idle) * 2], la: [swing * 58, 0, speed ? 4 : Math.sin(idle) * 2],
        head: [speed * 6, 0, 0],
      };
      const skin = skinNow();
      // leaning into the run (the top of the body a little ahead, to the left)
      Skin3D.draw(fig, skin.px, skin.slim, 'idle', t, { add, view: [-6, face, -9 * speed] });
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
   * On Play a gust runs through the meadow from the front. Where it passes, every flower lets go of its own petals
   * (each starts where it sat on the flower, turned the way it grew) and its own patch of meadow goes bare, so the
   * bare meadow opens up flower by flower rather than as a ring. The petals ride the air: an upward gust with slow
   * swirls in it, so they rise in drifting ribbons instead of a straight fan; near petals move fast, are big and out
   * of focus, far ones are small, slow and lost in the haze; turning, they catch the red moonlight on one side and
   * go dark on the other. A few light ones stay behind and float down again, and petals keep drifting down afterwards.
   */
  const PETAL_FRAMES = 8;
  let flowerList = null;
  // the Android app loads the page from file://, where fetch does not work: it gets the list as a script (MEADOW_FLOWERS)
  if (Array.isArray(window.MEADOW_FLOWERS)) flowerList = window.MEADOW_FLOWERS;
  else fetch('assets/designs/meadow_flowers.json').then((r) => r.json()).then((list) => { flowerList = list; }).catch(() => { flowerList = []; });

  // the gust: from just below the bottom centre, through the whole meadow in about a second
  const GUST = { x: 0.5, y: 1.08, speed: 1.05, soft: 0.16, delay: 0.05 };
  const gustAt = (nx, ny, W, H) => Math.hypot((nx - GUST.x) * W / H, ny - GUST.y) / 1.25;   // 0 … ~1.1
  const HORIZON = 0.36;                 // where the meadow meets the sky, as a fraction of the height
  const MOON = { x: 0.70, y: 0.19 };    // tools/meadow.py, as fractions of the picture

  /**
   * The air (in px/s): the curl of a few slow moving waves – swirls that never pile petals up or thin them out.
   * x, y and the result are in pixels; H sets the scale.
   */
  function swirl(x, y, t, H, out) {
    const u = x / H;
    const v = y / H;
    const a = 2.3, b = 3.1;
    const s1 = Math.sin(a * u + 0.8 * t), c1 = Math.cos(a * u + 0.8 * t);
    const s2 = Math.sin(b * v - 0.55 * t), c2 = Math.cos(b * v - 0.55 * t);
    const c3 = Math.cos(1.7 * (u - 0.6 * v) - 0.7 * t) * 0.6;
    const c4 = Math.cos(4.3 * u + 3.7 * v + 1.1 * t) * 0.18;
    out.x = (-b * s1 * s2 - 0.6 * 1.7 * c3 + 3.7 * c4) * H * 0.065;
    out.y = -(a * c1 * c2 + 1.7 * c3 + 4.3 * c4) * H * 0.065;
  }

  function meadow(canvas) {
    const bg = image('meadow.jpg');
    const bare = image('meadow_bare.jpg');
    const sheet = image('petals.png');
    let petals = [];
    const layer = document.createElement('canvas');   // the bare meadow, cut to where it shows
    const bareMask = document.createElement('canvas'); // where it shows: soft patches, growing, never shrinking
    const releases = [];                               // patches in the order they go bare: [time, x, y, radius]
    let nextRelease = 0;
    const opening = [];                                // patches going bare right now
    let bareFrame = 0;
    let bareFinish = -1;                               // when every patch is open: the whole bare picture fades in
    let bareDone = false;
    let bursting = -1;
    let built = false;
    let ambientNext = 1.5;
    let fallNext = 0;
    let view = { s: 1, ox: 0, oy: 0 };
    let looks = null;
    const rnd = Math.random;
    const air = { x: 0, y: 0 };

    // a soft round spot: one flower's patch of bare meadow
    const spot = document.createElement('canvas');
    spot.width = spot.height = 64;
    {
      const x = spot.getContext('2d');
      const gr = x.createRadialGradient(32, 32, 0, 32, 32, 32);
      gr.addColorStop(0, 'rgba(0,0,0,1)');
      gr.addColorStop(0.5, 'rgba(0,0,0,0.8)');
      gr.addColorStop(1, 'rgba(0,0,0,0)');
      x.fillStyle = gr;
      x.fillRect(0, 0, 64, 64);
    }

    /** The petal sheet out of focus (near petals), lit by the moon, and in shadow – made once. */
    function makeLooks() {
      const w = sheet.naturalWidth;
      const h = sheet.naturalHeight;
      const make = (paint) => {
        const c = document.createElement('canvas');
        c.width = w;
        c.height = h;
        paint(c.getContext('2d'));
        return c;
      };
      looks = {
        soft: make((x) => { x.filter = `blur(${Math.max(1.5, h * 0.05)}px)`; x.drawImage(sheet, 0, 0); }),
        lit: make((x) => {
          x.drawImage(sheet, 0, 0);
          x.globalCompositeOperation = 'source-atop';
          x.fillStyle = 'rgb(255,96,80)';
          x.globalAlpha = 0.5;
          x.fillRect(0, 0, w, h);
          x.globalCompositeOperation = 'lighter';
          x.globalAlpha = 0.3;
          x.drawImage(sheet, 0, 0);
        }),
        dark: make((x) => {
          x.drawImage(sheet, 0, 0);
          x.globalCompositeOperation = 'source-atop';
          x.fillStyle = 'rgb(22,8,12)';
          x.globalAlpha = 0.85;
          x.fillRect(0, 0, w, h);
        }),
      };
    }

    /** A petal of flower f (index i of n), leaving at `start` seconds. */
    function petal(f, i, start, W, H) {
      const [fx, fy, fsize, kind, flat, rot, n, plen] = f;
      const iw = bg.naturalWidth || 1920;
      const ih = bg.naturalHeight || 1080;
      const sizePx = fsize * iw * view.s;
      const L = sizePx * plen;
      const a = rot + (2 * Math.PI * i) / n;
      // where the petal sits: half a petal out from the heart, the head seen from a little above
      const x = view.ox + fx * iw * view.s + Math.cos(a) * L * 0.5;
      const y = view.oy + fy * ih * view.s + Math.sin(a) * L * 0.5 * flat;
      const d = clamp01((y / H - HORIZON) / (1 - HORIZON));
      const out = Math.cos(a);
      const p = {
        x, y, L, start, kind, d,
        m: 0.3 + 0.7 * d,                                  // far petals move slower on screen
        ang: Math.atan2(Math.sin(a) * flat, Math.cos(a)),
        squash: Math.max(0.35, flat),
        vx: out * L * (1.5 + rnd() * 1.5),                 // a little pop away from the heart
        vy: -L * (1 + rnd() * 1.5),
        light: rnd() < 0.12 ? 0.18 + rnd() * 0.22 : 0.75 + rnd() * 0.55,   // a few stay low and float down again
        side: (rnd() - 0.5) * 2,
        drag: 1.8 + rnd() * 1.8,
        spin: (rnd() - 0.5) * 6,
        flip: 0,
        flipSpeed: (2 + rnd() * 4.5) * (rnd() < 0.5 ? -1 : 1),
        max: 9 + rnd() * 5,
        fall: false,
      };
      petals.push(p);
      return p;
    }

    /** A petal coming down from above after the gust, as if from the flowers further back. */
    function fallingPetal(W, H, t) {
      const d = rnd();
      const L = (5 + 11 * d * d) * (H / 1080) * 1.6;
      petals.push({
        x: rnd() * W * 1.1 - W * 0.05, y: -L * 3, L, start: t, kind: Math.floor(rnd() * PETAL_FRAMES), d, m: 0.3 + 0.7 * d,
        ang: rnd() * Math.PI * 2, squash: 0.6, vx: 0, vy: H * 0.05, light: 0, side: 0, drag: 1.2 + rnd(),
        spin: (rnd() - 0.5) * 3, flip: rnd() * 6, flipSpeed: 1.5 + rnd() * 2.5, max: 30, fall: true,
      });
    }

    /** Every visible flower's petals, timed by when the gust reaches it; tiny far petals for the flowers too small to list. */
    function build(W, H, t) {
      built = true;
      if (!flowerList || !ready(bg)) return;
      const iw = bg.naturalWidth;
      // every visible flower loses petals: the close ones all of them, the farther ones fewer – shared out so that every
      // flower the gust passes sends some up, never more than the budget
      const budget = reduced ? 600 : 2600;
      const visible = [];
      let wanted = 0;
      for (const f of flowerList) {
        const sx = view.ox + f[0] * iw * view.s;
        const sy = view.oy + f[1] * bg.naturalHeight * view.s;
        if (sx < -40 || sx > W + 40 || sy < -40 || sy > H + 40) continue;
        const sizePx = f[2] * iw * view.s;
        const want = sizePx > 26 ? f[6] : sizePx > 16 ? Math.min(f[6], 6) : 3;
        visible.push([f, sx, sy, want, sizePx]);
        wanted += want;
      }
      const share = Math.min(1, budget / Math.max(1, wanted));
      for (const [f, sx, sy, want, sizePx] of visible) {
        const count = Math.max(1, Math.round(want * share));
        // the gust's front is ragged: some flowers give way a little early, some hold on
        const when = t + GUST.delay + gustAt(sx / W, sy / H, W, H) / GUST.speed + (rnd() - 0.3) * 0.22;
        for (let i = 0; i < count; i++) {
          const k = count >= f[6] ? i : Math.floor((i + rnd()) * f[6] / count);
          petal(f, k, when + i * 0.025 + rnd() * 0.08, W, H);
        }
        if (sizePx > 9) releases.push([when + 0.3, sx, sy, sizePx * 1.6]);
      }
      // the many small flowers too far away to list: their petals rise as a fine haze where the gust passes
      for (let i = 0; i < (reduced ? 120 : 800); i++) {
        const nx = rnd();
        const ny = HORIZON + rnd() * 0.5;
        const depth = Math.max(0, (ny - HORIZON + 0.02) / 0.66);
        const fake = [((nx * W - view.ox) / view.s) / iw, ((ny * H - view.oy) / view.s) / bg.naturalHeight, (3 + 14 * depth * depth) / (iw * view.s),
          Math.floor(rnd() * 5), 0.7, rnd() * 6.28, 8, 1, 0.35];
        petal(fake, Math.floor(rnd() * 8), t + GUST.delay + gustAt(nx, ny, W, H) / GUST.speed + rnd() * 0.25, W, H);
      }
      // the grass between the flowers goes bare a little behind the gust, in large soft patches
      const cell = H * 0.11;
      for (let y = HORIZON * H - cell; y < H + cell; y += cell * 0.8) {
        for (let x = -cell * 0.5; x < W + cell; x += cell) {
          const jx = x + (rnd() - 0.5) * cell * 0.5;
          const jy = y + (rnd() - 0.5) * cell * 0.4;
          releases.push([t + GUST.delay + gustAt(jx / W, jy / H, W, H) / GUST.speed + 0.5 + rnd() * 0.25, jx, jy, cell * 1.15]);
        }
      }
      releases.sort((a, b) => a[0] - b[0]);
      // far petals first, so the near ones are drawn over them
      petals.sort((a, b) => a.d - b.d);
      for (const c of [layer, bareMask]) { c.width = W; c.height = H; }
    }

    /** The bare meadow where the gust has been: every flower's own patch first, the grass around it a little later. */
    function drawBare(g, W, H, t, dt) {
      if (!ready(bare)) return;
      const full = () => g.drawImage(bare, view.ox, view.oy, bare.naturalWidth * view.s, bare.naturalHeight * view.s);
      if (bareDone) { full(); return; }
      for (const c of [layer, bareMask]) if (c.width !== W || c.height !== H) { c.width = W; c.height = H; }
      while (nextRelease < releases.length && releases[nextRelease][0] <= t) opening.push(releases[nextRelease++]);
      const mg = bareMask.getContext('2d');
      mg.globalAlpha = Math.min(1, dt * 2.2);
      for (let i = opening.length - 1; i >= 0; i--) {
        const [at, x, y, r] = opening[i];
        // each patch opens over most of a second (drawn a little stronger every frame)
        mg.drawImage(spot, x - r, y - r * 0.7, r * 2, r * 1.4);
        if (t > at + 1) opening.splice(i, 1);
      }
      mg.globalAlpha = 1;
      // the patches open slowly: putting the picture together every other frame is plenty
      if (bareFrame++ % 2 === 0) {
        const lg = layer.getContext('2d');
        lg.globalCompositeOperation = 'source-over';
        lg.clearRect(0, 0, W, H);
        lg.drawImage(bare, view.ox, view.oy, bare.naturalWidth * view.s, bare.naturalHeight * view.s);
        lg.globalCompositeOperation = 'destination-in';
        lg.drawImage(bareMask, 0, 0);
        lg.globalCompositeOperation = 'source-over';
      }
      g.drawImage(layer, 0, 0);
      // every patch open: the whole bare picture fades in over what is left, then it is just the picture
      if (bareFinish < 0 && built && nextRelease >= releases.length && !opening.length) bareFinish = t;
      if (bareFinish >= 0) {
        const f = smooth((t - bareFinish) / 0.8);
        g.globalAlpha = f;
        full();
        g.globalAlpha = 1;
        if (f >= 1) bareDone = true;
      }
    }

    const scene = loop(canvas, (g, W, H, t, dt) => {
      g.setTransform(1, 0, 0, 1, 0, 0);
      g.fillStyle = '#060506';
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
      if (b >= 0 && !built) build(W, H, bursting);
      // with the gust the camera leans in a little and looks up after the petals
      const lean = b >= 0 ? smooth(b / 3.5) : 0;
      const k = 1 + 0.035 * lean;
      const camX = W * 0.5 * (1 - k);
      const camY = H * 0.8 * (1 - k) + H * 0.025 * lean;
      g.setTransform(k, 0, 0, k, camX, camY);
      if (ready(bg)) g.drawImage(bg, view.ox, view.oy, bg.naturalWidth * view.s, bg.naturalHeight * view.s);
      if (b >= 0) drawBare(g, W, H, t, dt);
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
        const p = petal(f, Math.floor(rnd() * f[6]), t, W, H);
        p.light = 0.18;
        ambientNext = t + 0.6 + rnd() * 1.4;
      }
      // after the gust, petals keep drifting down from the flowers further back
      if (!reduced && b > 2.2 && t > fallNext) {
        fallingPetal(W, H, t);
        fallNext = t + 0.18 + rnd() * 0.5;
      }
      if (!ready(sheet)) return;
      if (!looks) makeLooks();
      const fs = sheet.naturalHeight;
      const moonX = view.ox + MOON.x * (bg.naturalWidth || W) * view.s;
      const moonY = view.oy + MOON.y * (bg.naturalHeight || H) * view.s;
      let keep = 0;
      for (let i = 0; i < petals.length; i++) {
        const p = petals[i];
        if (t < p.start) { petals[keep++] = p; continue; }
        const age = t - p.start;
        // leaving: a moment of loosening, then the gust takes it; later the gust dies down to a breeze
        const go = smooth(age / 0.45);
        const gust = p.fall ? 0 : go * (age < 2.2 ? 1 : Math.max(0.15, 1 - (age - 2.2) / 2.6));
        swirl(p.x, p.y, t, H, air);
        const s = 0.4 + gust;
        const tx = air.x * s + p.side * W * 0.03 * gust + W * 0.012;
        const ty = air.y * s - H * 0.6 * gust * p.light + H * 0.06 * (1 - gust);
        const pull = Math.min(1, dt * p.drag * (0.2 + 0.8 * go));
        p.vx += (tx - p.vx) * pull;
        p.vy += (ty - p.vy) * pull;
        p.x += p.vx * p.m * dt;
        p.y += p.vy * p.m * dt;
        p.ang += p.spin * dt * go;
        p.flip += p.flipSpeed * dt * go;
        if (p.y < -p.L * 4 || p.y > H + p.L * 4 || p.x < -W * 0.3 || p.x > W * 1.3 || age > p.max) continue;
        petals[keep++] = p;

        const near = p.L > H * 0.026;
        const src = near ? looks.soft : sheet;
        const face = Math.cos(p.flip);
        const sx = (p.L / fs) * (near ? 1.15 : 1.05);
        const sy = sx * (p.squash + (1 - p.squash) * go) * Math.max(0.1, Math.abs(face));
        const c = Math.cos(p.ang);
        const sn = Math.sin(p.ang);
        const fade = Math.min(1, age * 6) * Math.min(1, (p.max - age) / 1.5) * (0.75 + 0.25 * p.d) * (near ? 0.85 : 1);
        const u = (p.kind % PETAL_FRAMES) * fs;
        const dark = Math.min(0.85, (1 - Math.abs(face)) * 0.45 + (face < 0 ? 0.3 : 0) + (1 - p.d) * 0.15);
        const q = Math.max(0, 1 - Math.hypot(p.x - moonX, p.y - moonY) / (H * 1.1));
        const lit = Math.max(0, face) * (0.12 + 0.88 * q * q) * 0.85;
        if (p.L < 5) {
          // far and tiny: one draw, shaded by how bright it is
          g.setTransform(k, 0, 0, k, camX, camY);
          g.transform(c * sx, sn * sx, -sn * sy, c * sy, p.x, p.y);
          g.globalAlpha = fade * (1 - dark * 0.7);
          g.drawImage(lit > 0.35 ? looks.lit : sheet, u, 0, fs, fs, -fs / 2, -fs / 2, fs, fs);
          continue;
        }
        // fast near petals smear a little
        const speed = Math.hypot(p.vx, p.vy) * p.m;
        if (speed > H * 0.5 && p.L > 9) {
          g.setTransform(k, 0, 0, k, camX, camY);
          g.transform(c * sx, sn * sx, -sn * sy, c * sy, p.x - p.vx * p.m * 0.028, p.y - p.vy * p.m * 0.028);
          g.globalAlpha = fade * 0.22;
          g.drawImage(src, u, 0, fs, fs, -fs / 2, -fs / 2, fs, fs);
        }
        g.setTransform(k, 0, 0, k, camX, camY);
        g.transform(c * sx, sn * sx, -sn * sy, c * sy, p.x, p.y);
        g.globalAlpha = fade;
        g.drawImage(src, u, 0, fs, fs, -fs / 2, -fs / 2, fs, fs);
        // turned away or edge-on it goes dark, far away it sinks into the night; the side facing the moon catches its
        // red light, the more the closer the petal comes to it – one of the two, whichever is stronger
        const shade = lit > dark ? looks.lit : looks.dark;
        const amount = Math.max(lit, dark);
        if (amount > 0.06) {
          g.globalAlpha = fade * amount;
          g.drawImage(shade, u, 0, fs, fs, -fs / 2, -fs / 2, fs, fs);
        }
      }
      petals.length = keep;
      g.setTransform(1, 0, 0, 1, 0, 0);
      g.globalAlpha = 1;
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
