/* The floating voxel island from the website, as a reusable scene for the home page.
   Columns drop in, the island bobs, columns near the cursor lift, debris and motes drift. */
(function () {
	"use strict";
	const L = window.L;
	const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;

	L.World = function (canvas, { textRight = () => 0, bottomSpace = 120 } = {}) {
		let ctx = canvas.getContext("2d"); // swapped for the cache canvas while the settled island is rendered
		let W = 0, H = 0, dpr = 1, a = 20, ox = 0, oy = 0;
		let start = performance.now(), raf = 0, running = false;
		const mouse = { x: -1e4, y: -1e4, on: false };

		let seed = 1407;
		const rnd = () => {
			seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
			let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
			t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
			return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
		};
		const hash = (x, y) => { const h = Math.sin(x * 127.1 + y * 311.7) * 43758.5453; return h - Math.floor(h); };
		const noise = (x, y) => {
			const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
			const u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
			const a0 = hash(xi, yi), b0 = hash(xi + 1, yi), c0 = hash(xi, yi + 1), d0 = hash(xi + 1, yi + 1);
			return a0 + (b0 - a0) * u + (c0 - a0) * v + (a0 - b0 - c0 + d0) * u * v;
		};

		const R = 9;
		const blocks = [], cols = {}, colList = [], occ = {}, tops = {};
		const key = (i, j, k) => i + "," + j + "," + k;
		const col = (i, j) => {
			const id = i + "," + j;
			if (!cols[id]) {
				cols[id] = { i, j, delay: 0.1 + Math.sqrt(i * i + j * j) * 0.06 + rnd() * 0.25, lift: 0, off: 0, alpha: 0 };
				colList.push(cols[id]);
			}
			return cols[id];
		};
		const put = (i, j, k, type, c) => {
			const kk = key(i, j, k);
			if (occ[kk]) return;
			const b = { i, j, k, type, col: c || col(i, j), shade: (hash(i * 3.1 + k, j * 7.7 - k) - 0.5) * 14 };
			occ[kk] = b;
			blocks.push(b);
		};
		for (let i = -R; i <= R; i++) for (let j = -R; j <= R; j++) {
			const n = noise(i * 0.28 + 3, j * 0.28 + 9);
			const d = Math.sqrt(i * i + j * j) / R + (n - 0.5) * 0.35;
			if (d >= 1) continue;
			const top = Math.round(1 + n * 2.4 - d * 1.6);
			const depth = Math.round(Math.pow(1 - d, 0.75) * 8 + noise(i * 0.5, j * 0.5) * 2);
			tops[i + "," + j] = top;
			for (let k = top - depth; k <= top; k++) put(i, j, k, k === top ? "grass" : k >= top - 2 ? "dirt" : hash(i + k * 5, j - k) > 0.93 ? "ore" : "stone");
		}
		[[-3, -2], [4, -4], [2, 3], [-5, 4]].forEach(([pi, pj]) => {
			const t = tops[pi + "," + pj];
			if (t === undefined) return;
			const c = col(pi, pj);
			const h = 3 + (hash(pi, pj) > 0.5 ? 1 : 0);
			for (let k = 1; k <= h; k++) put(pi, pj, t + k, "log", c);
			for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) for (let dk = 0; dk <= 1; dk++) {
				if (dk === 1 && dx !== 0 && dz !== 0) continue;
				put(pi + dx, pj + dz, t + h + dk, "leaves", c);
			}
			put(pi, pj, t + h + 2, "leaves", c);
		});
		blocks.sort((p, q) => p.i + p.j - (q.i + q.j) || p.k - q.k || p.i - q.i);
		blocks.forEach((b) => { b.up = occ[key(b.i, b.j, b.k + 1)]; b.ni = occ[key(b.i + 1, b.j, b.k)]; b.nj = occ[key(b.i, b.j + 1, b.k)]; });
		let introEnd = 0, spanU = 0;
		colList.forEach((c) => { introEnd = Math.max(introEnd, c.delay + 1); spanU = Math.max(spanU, Math.abs(c.i - c.j) + 1); });

		const debris = [];
		for (let q = 0; q < 7; q++) {
			const ang = (q / 7) * Math.PI * 2 + rnd();
			debris.push({ i: Math.cos(ang) * (R * 0.6 + rnd() * 3), j: Math.sin(ang) * (R * 0.6 + rnd() * 3), k: -8 - rnd() * 6, s: 0.35 + rnd() * 0.35, ph: rnd() * 6.28, type: rnd() > 0.5 ? "stone" : "dirt" });
		}
		const motes = [];
		for (let m = 0; m < 46; m++) motes.push({ x: rnd(), y: rnd(), s: rnd() > 0.7 ? 3 : 2, sp: 0.006 + rnd() * 0.014, ph: rnd() * 6.28 });

		const PAL = {
			dark: { grass: [218, 150, 112], dirt: [128, 96, 72], stone: [86, 64, 48], ore: [150, 70, 52], log: [92, 58, 40], leaves: [238, 190, 150], mote: "255,255,255" },
			light: { grass: [204, 150, 118], dirt: [148, 112, 90], stone: [110, 86, 70], ore: [44, 96, 80], log: [64, 44, 34], leaves: [222, 170, 136], mote: "20,20,24" }
		};
		let pal = PAL.dark;
		const g = (v) => { v = Math.max(0, Math.min(255, Math.round(v))); return `rgb(${v},${v},${v})`; };
		const quad = (x1, y1, x2, y2, x3, y3, x4, y4) => { ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.lineTo(x3, y3); ctx.lineTo(x4, y4); ctx.closePath(); ctx.fill(); };
		function cube(cx, cy, s, b, sideI, sideJ, top) {
			const p = pal[b.type], sh = b.shade || 0, h = s / 2;
			if (top) {
				ctx.fillStyle = g(p[0] + sh);
				quad(cx, cy - h, cx + s, cy, cx, cy + h, cx - s, cy);
				if (b.type === "grass") { ctx.fillStyle = g(p[0] + sh + 14); quad(cx - s * 0.5, cy - h * 0.5, cx, cy - h, cx + s * 0.5, cy - h * 0.5, cx, cy); }
			}
			if (sideJ) { ctx.fillStyle = g(p[1] + sh); quad(cx - s, cy, cx, cy + h, cx, cy + h + s, cx - s, cy + s); }
			if (sideI) { ctx.fillStyle = g(p[2] + sh); quad(cx + s, cy, cx, cy + h, cx, cy + h + s, cx + s, cy + s); }
			if (b.type === "grass") {
				const gh = s * 0.22;
				if (sideJ) { ctx.fillStyle = g(p[1] + 40 + sh); quad(cx - s, cy, cx, cy + h, cx, cy + h + gh, cx - s, cy + gh); }
				if (sideI) { ctx.fillStyle = g(p[2] + 40 + sh); quad(cx + s, cy, cx, cy + h, cx, cy + h + gh, cx + s, cy + gh); }
			}
		}

		function resize() {
			dpr = Math.min(2, window.devicePixelRatio || 1);
			W = canvas.clientWidth; H = canvas.clientHeight;
			canvas.width = Math.round(W * dpr); canvas.height = Math.round(H * dpr);
			const room = W - textRight() - 80;
			a = Math.max(10, Math.min(28, room / (2 * spanU), (H - bottomSpace) / 30));
			ox = Math.max(textRight() + 40 + spanU * a, W - 60 - spanU * a);
			oy = (H - bottomSpace) * 0.46;
		}

		const cache = { canvas: document.createElement("canvas"), key: "", valid: false };

		function drawIsland(g, baseY) {
			for (const b of blocks) {
				const cb = b.col;
				if (cb.alpha <= 0) continue;
				const cx = ox + (b.i - b.j) * a;
				const cy = baseY + ((b.i + b.j) * a) / 2 - b.k * a + cb.off;
				if (cy < -2 * a || cy > H + 2 * a) continue;
				const top = !b.up || b.up.col !== cb;
				const sideI = !b.ni || Math.abs(b.ni.col.off - cb.off) > 0.5 || b.ni.col.alpha < 1;
				const sideJ = !b.nj || Math.abs(b.nj.col.off - cb.off) > 0.5 || b.nj.col.alpha < 1;
				if (!top && !sideI && !sideJ) continue;
				g.globalAlpha = cb.alpha;
				cube(cx, cy, a, b, sideI, sideJ, top);
			}
			g.globalAlpha = 1;
		}

		function frame(now) {
			raf = 0;
			let t = (now - start) / 1000;
			if (reduced) t = introEnd + 1;
			pal = PAL[document.documentElement.dataset.theme === "light" ? "light" : "dark"];
			ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
			ctx.clearRect(0, 0, W, H);
			const bob = reduced ? 0 : Math.sin(t * 0.9) * a * 0.22;
			const baseY = oy + bob;
			let mi = 1e4, mj = 1e4;
			if (mouse.on && t > introEnd) {
				const u = (mouse.x - ox) / a, v = (mouse.y - baseY) / (a / 2) + 4;
				mi = (v + u) / 2; mj = (v - u) / 2;
			}
			let moving = t <= introEnd;
			for (const cl of colList) {
				const dist = Math.hypot(cl.i - mi, cl.j - mj);
				const target = dist < 3 ? Math.pow(1 - dist / 3, 2) * 0.75 : 0;
				cl.lift += (target - cl.lift) * 0.12;
				if (Math.abs(cl.lift) < 0.001 && target === 0) cl.lift = 0;
				else moving = true;
				const p = Math.max(0, Math.min(1, (t - cl.delay) / 0.95));
				const e = 1 - Math.pow(1 - p, 3);
				cl.alpha = Math.min(1, p * 2.5);
				cl.off = -(1 - e) * H * 0.75 - cl.lift * a;
			}
			for (const db of debris) {
				const dy = Math.sin(t * 0.8 + db.ph) * a * 0.5;
				ctx.globalAlpha = Math.min(1, Math.max(0, (t - 0.8) / 1.2));
				cube(ox + (db.i - db.j) * a, baseY + ((db.i + db.j) * a) / 2 - db.k * a + dy, a * db.s, { type: db.type, shade: 0 }, true, true, true);
			}
			if (moving) {
				cache.valid = false;
				drawIsland(ctx, baseY);
			} else {
				// settled: the island is one bitmap that only bobs (thousands of polygons → one image copy per frame)
				const key = `${W}x${H}@${dpr}|${pal === PAL.light ? "l" : "d"}|${a}|${ox}|${oy}`;
				if (!cache.valid || cache.key !== key) {
					cache.canvas.width = canvas.width;
					cache.canvas.height = canvas.height;
					const c2 = cache.canvas.getContext("2d");
					c2.setTransform(dpr, 0, 0, dpr, 0, 0);
					const keep = ctx;
					ctx = c2;
					drawIsland(c2, oy);
					ctx = keep;
					cache.key = key;
					cache.valid = true;
				}
				ctx.globalAlpha = 1;
				ctx.setTransform(1, 0, 0, 1, 0, 0);
				if (cache.canvas.width && cache.canvas.height) ctx.drawImage(cache.canvas, 0, bob * dpr);
				ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
			}
			if (!reduced) {
				ctx.fillStyle = `rgb(${pal.mote})`;
				for (const mo of motes) {
					const yy = (((mo.y - t * mo.sp) % 1) + 1) % 1;
					const xx = mo.x + Math.sin(t * 0.6 + mo.ph) * 0.01;
					const al = Math.sin(yy * Math.PI) * (0.25 + 0.25 * Math.sin(t * 2 + mo.ph));
					ctx.globalAlpha = Math.max(0, al) * Math.min(1, t / 2);
					ctx.fillRect(Math.round(xx * W), Math.round(yy * H), mo.s, mo.s);
				}
			}
			ctx.globalAlpha = 1;
			if (running && !reduced) raf = requestAnimationFrame(frame);
		}

		const host = canvas.parentElement;
		host.addEventListener("pointermove", (e) => {
			const r = canvas.getBoundingClientRect();
			mouse.x = e.clientX - r.left; mouse.y = e.clientY - r.top; mouse.on = e.pointerType === "mouse";
		});
		host.addEventListener("pointerleave", () => (mouse.on = false));
		new ResizeObserver(() => { resize(); if (!raf) raf = requestAnimationFrame(frame); }).observe(canvas);

		return {
			start(replay) {
				if (replay) start = performance.now();
				running = true;
				resize();
				if (!raf) raf = requestAnimationFrame(frame);
			},
			stop() { running = false; },
			repaint() { if (!raf) raf = requestAnimationFrame(frame); }
		};
	};
})();
