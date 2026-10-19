/* Flat pixel renders of Minecraft skins (front / back, both layers) and capes, on 16×32 canvases scaled with CSS. */
(function () {
	"use strict";
	const L = window.L;

	/** Texture rectangles [x, y, w, h] of one view. a = arm width (4 classic, 3 slim). */
	function rects(a, back) {
		const f = !back;
		return {
			head: [f ? 8 : 24, 8, 8, 8], hat: [f ? 40 : 56, 8, 8, 8],
			body: [f ? 20 : 32, 20, 8, 12], jacket: [f ? 20 : 32, 36, 8, 12],
			rArm: [f ? 44 : 48 + a, 20, a, 12], rSleeve: [f ? 44 : 48 + a, 36, a, 12],
			lArm: [f ? 36 : 40 + a, 52, a, 12], lSleeve: [f ? 52 : 56 + a, 52, a, 12],
			rLeg: [f ? 4 : 12, 20, 4, 12], rPants: [f ? 4 : 12, 36, 4, 12],
			lLeg: [f ? 20 : 28, 52, 4, 12], lPants: [f ? 4 : 12, 52, 4, 12]
		};
	}

	const cache = new Map();
	/** Loads an image (data URL or https) once. */
	L.image = (src) => {
		if (!src) return Promise.resolve(null);
		if (!cache.has(src)) {
			cache.set(src, new Promise((resolve) => {
				const img = new Image();
				img.onload = () => resolve(img);
				img.onerror = () => { cache.delete(src); resolve(null); };
				img.src = src;
			}));
		}
		return cache.get(src);
	};

	/**
	 * Draws a skin onto a canvas (16×32 texture pixels).
	 * opts: slim, back (view from behind), cape (Image, only visible from behind)
	 */
	L.drawSkin = (canvas, img, opts = {}) => {
		canvas.width = 16;
		canvas.height = 32;
		const g = canvas.getContext("2d");
		g.imageSmoothingEnabled = false;
		g.clearRect(0, 0, 16, 32);
		if (!img) return;
		const k = img.width / 64;
		const legacy = img.height * 2 === img.width; // old 64×32 skins: no second layer, left limbs mirror the right ones
		const a = opts.slim ? 3 : 4;
		const r = rects(a, !!opts.back);
		const put = (rect, dx, dy, mirror) => {
			const [x, y, w, h] = rect;
			if (!mirror) { g.drawImage(img, x * k, y * k, w * k, h * k, dx, dy, w, h); return; }
			g.save();
			g.translate(dx + w, dy);
			g.scale(-1, 1);
			g.drawImage(img, x * k, y * k, w * k, h * k, 0, 0, w, h);
			g.restore();
		};
		// seen from behind, the character's left side is on the viewer's left
		const leftX = 4 - a, rightX = 12;
		const armR = opts.back ? rightX : leftX, armL = opts.back ? leftX : rightX;
		const legR = opts.back ? 8 : 4, legL = opts.back ? 4 : 8;

		put(r.head, 4, 0);
		put(r.body, 4, 8);
		put(r.rArm, armR, 8);
		put(r.rLeg, legR, 20);
		if (legacy) {
			// mirrored right limbs; from behind the mirrored copy of the right arm's back face
			put(r.rArm, armL, 8, true);
			put(r.rLeg, legL, 20, true);
		} else {
			put(r.lArm, armL, 8);
			put(r.lLeg, legL, 20);
			put(r.jacket, 4, 8);
			put(r.rSleeve, armR, 8);
			put(r.lSleeve, armL, 8);
			put(r.rPants, legR, 20);
			put(r.lPants, legL, 20);
		}
		put(r.hat, 4, 0);
		if (opts.back && opts.cape) {
			const c = opts.cape.width / 64;
			g.drawImage(opts.cape, 1 * c, 1 * c, 10 * c, 16 * c, 3, 8, 10, 16);
		}
	};

	/** Draws the outside of a cape (10×16 texture pixels). */
	L.drawCape = (canvas, img) => {
		canvas.width = 10;
		canvas.height = 16;
		const g = canvas.getContext("2d");
		g.imageSmoothingEnabled = false;
		g.clearRect(0, 0, 10, 16);
		if (!img) return;
		const c = img.width / 64;
		g.drawImage(img, 1 * c, 1 * c, 10 * c, 16 * c, 0, 0, 10, 16);
	};
})();
