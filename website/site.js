'use strict';

/* The Onix website: the sunken scene behind the start page's header (the same as the launcher's start screen,
   js/fall.js), the menu on phones, download links to the newest release for your system, the module search and an
   image viewer. Every page works without this script; it only adds to it. */

const REPO = 'virtualchickenfr/onix-client';

// ---------------------------------------------------------------- the header scene (start page)
(() => {
  const canvas = document.getElementById('scene');
  if (!canvas || typeof Fall === 'undefined' || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  // an Onix outfit drawn here (not a Mojang skin): dark hoodie with teal trim, grey trousers, white shoes
  const px = new Uint8ClampedArray(64 * 64 * 4);
  const put = (x0, y0, w, h, c) => {
    for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) px.set([...c, 255], (y * 64 + x) * 4);
  };
  put(0, 0, 32, 16, [214, 168, 128]);
  put(0, 0, 32, 8, [44, 32, 26]); put(0, 8, 32, 2, [44, 32, 26]);
  put(8, 8, 8, 8, [214, 168, 128]); put(8, 8, 8, 2, [44, 32, 26]);
  put(9, 11, 2, 1, [250, 250, 250]); put(13, 11, 2, 1, [250, 250, 250]);
  put(10, 11, 1, 1, [40, 110, 130]); put(13, 11, 1, 1, [40, 110, 130]);
  put(10, 14, 4, 1, [160, 100, 80]);
  put(16, 16, 24, 16, [26, 34, 40]); put(20, 20, 8, 1, [95, 208, 218]); put(23, 21, 2, 6, [95, 208, 218]);
  put(40, 16, 16, 16, [26, 34, 40]); put(40, 28, 16, 1, [95, 208, 218]); put(44, 29, 4, 3, [214, 168, 128]);
  put(32, 48, 16, 16, [26, 34, 40]); put(32, 60, 16, 1, [95, 208, 218]); put(36, 61, 4, 3, [214, 168, 128]);
  put(0, 16, 16, 16, [60, 66, 74]); put(0, 28, 16, 4, [235, 238, 240]);
  put(16, 48, 16, 16, [60, 66, 74]); put(16, 60, 16, 4, [235, 238, 240]);
  // the skin of the player named in .github/workflows/website.yml (img/skin.png); the drawn outfit if it cannot load
  let skin = null;
  const started = performance.now();
  fetch('img/skin.json').then((r) => (r.ok ? r.json() : null)).catch(() => null).then((info) => {
    const img = new Image();
    img.onload = () => {
      try {
        skin = { px: Skin3D.pixels(img), slim: !!(info && info.slim) };
      } catch {
        skin = null;
      }
    };
    img.src = 'img/skin.png';
  });
  Fall.boot(canvas, () => skin || (performance.now() - started > 1500 ? { px, slim: false } : null), { loop: 9, y: 0.27, scale: 0.62, waitSkin: 2, light: 2.2, tint: 0.45 });
})();

// ---------------------------------------------------------------- navigation
const nav = document.querySelector('.nav');
const onScroll = () => nav.classList.toggle('scrolled', window.scrollY > 40 || nav.classList.contains('open'));
window.addEventListener('scroll', onScroll, { passive: true });
onScroll();
const menuButton = document.querySelector('.menu-btn');
if (menuButton) {
  menuButton.addEventListener('click', () => {
    const open = nav.classList.toggle('open');
    menuButton.setAttribute('aria-expanded', String(open));
    onScroll();
  });
}

// ---------------------------------------------------------------- downloads
function detectOs() {
  const ua = navigator.userAgent;
  if (/Android/i.test(ua)) return 'android';
  if (/iPhone|iPad|iPod/i.test(ua)) return 'ios';
  if (/Mac/i.test(ua)) return 'mac';
  if (/Win/i.test(ua)) return 'windows';
  if (/Linux|X11/i.test(ua)) return 'linux';
  return null;
}

const FILES = {
  windows: (a) => /Setup-.*\.exe$/i.test(a.name),
  mac: (a) => /\.dmg$/i.test(a.name) && !/arm64/i.test(a.name),
  macArm: (a) => /arm64.*\.dmg$/i.test(a.name),
  linux: (a) => /\.AppImage$/i.test(a.name),
  android: (a) => /^Onix-Android\.apk$/i.test(a.name),
};
const OS_NAMES = { windows: 'Windows', mac: 'macOS', linux: 'Linux', android: 'Android' };

async function setUpDownloads() {
  const os = detectOs();
  const label = document.getElementById('main-download-label');
  if (label && OS_NAMES[os]) label.textContent = `Für ${OS_NAMES[os]} herunterladen`;
  document.querySelectorAll('.dl, .steps article').forEach((d) => d.classList.toggle('current', d.dataset.os === os));
  const version = document.getElementById('version');
  if (!version && !document.querySelector('.dl, [data-jar]')) return;
  let releases;
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=10`);
    if (!res.ok) throw new Error(res.status);
    releases = (await res.json()).filter((r) => !r.draft && !r.prerelease);
  } catch {
    return; // the links keep pointing at the release page
  }
  if (!releases.length) return;
  const latest = releases[0];
  const latestVersion = latest.tag_name.replace(/^v/, '');
  if (version) version.textContent = `Version ${latestVersion} · ${new Date(latest.published_at).toLocaleDateString('de-DE')}`;
  // a file from the newest release that has it (a build for one system can arrive later than the others)
  const find = (test) => {
    for (const r of releases) {
      const asset = r.assets.find(test);
      if (asset) return { url: asset.browser_download_url, name: asset.name, version: r.tag_name.replace(/^v/, '') };
    }
    return null;
  };
  const links = { windows: find(FILES.windows), linux: find(FILES.linux), android: find(FILES.android) };
  // Apple chip Macs get the arm64 build, the rest the Intel one (the page cannot always tell: the arm64 build is the default)
  links.mac = find(FILES.macArm) || find(FILES.mac);
  document.querySelectorAll('.dl').forEach((d) => {
    const link = links[d.dataset.os];
    if (!link) return;
    d.href = link.url;
    if (link.version !== latestVersion) d.querySelector('.dl-file').textContent += ` · Version ${link.version}`;
  });
  const main = document.getElementById('main-download');
  if (main && links[os]) main.href = links[os].url;
  // the mod on its own, per Minecraft version
  document.querySelectorAll('[data-jar]').forEach((row) => {
    const mc = row.dataset.jar;
    const jar = mc === '1.21.1'
      ? find((a) => /^onix-client-[\d.]+\.jar$/.test(a.name))
      : find((a) => a.name.endsWith(`+${mc}.jar`));
    if (!jar) return;
    row.href = jar.url;
    row.querySelector('.jar-name').textContent = jar.name;
  });
}
setUpDownloads();

// ---------------------------------------------------------------- module search (Funktionen)
(() => {
  const input = document.getElementById('mod-search');
  if (!input) return;
  const chips = [...document.querySelectorAll('.chip')];
  const groups = [...document.querySelectorAll('.mod-group')];
  const empty = document.getElementById('no-results');
  let category = 'all';
  const apply = () => {
    const words = input.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
    let shown = 0;
    for (const g of groups) {
      let inGroup = 0;
      const catOk = category === 'all' || g.dataset.cat === category;
      for (const li of g.querySelectorAll('li')) {
        const text = li.dataset.search || (li.dataset.search = (li.dataset.en + ' ' + li.textContent).toLowerCase());
        const ok = catOk && words.every((w) => text.includes(w));
        li.hidden = !ok;
        if (ok) inGroup++;
      }
      g.hidden = inGroup === 0;
      shown += inGroup;
    }
    empty.hidden = shown > 0;
  };
  input.addEventListener('input', apply);
  chips.forEach((chip) => chip.addEventListener('click', () => {
    category = chip.dataset.cat;
    chips.forEach((c) => c.classList.toggle('active', c === chip));
    apply();
  }));
})();

// ---------------------------------------------------------------- image viewer
document.querySelectorAll('.gallery figure').forEach((fig) => {
  fig.tabIndex = 0;
  const open = () => {
    const img = fig.querySelector('img');
    const viewer = document.createElement('div');
    viewer.className = 'viewer';
    const big = document.createElement('img');
    big.src = img.src;
    big.alt = img.alt;
    viewer.append(big);
    const close = () => {
      viewer.remove();
      document.removeEventListener('keydown', esc);
      fig.focus();
    };
    const esc = (e) => { if (e.key === 'Escape') close(); };
    viewer.addEventListener('click', close);
    document.addEventListener('keydown', esc);
    document.body.append(viewer);
  };
  fig.addEventListener('click', open);
  fig.addEventListener('keydown', (e) => { if (e.key === 'Enter') open(); });
});
