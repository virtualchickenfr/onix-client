'use strict';

/* The Onix website: the sunken scene behind the header (the same as the launcher's start screen, js/fall.js),
   download links to the newest release for your system, an image viewer and gentle reveals while scrolling. */

const REPO = 'virtualchickenfr/onix-client';

// ---------------------------------------------------------------- the header scene
(() => {
  const canvas = document.getElementById('scene');
  if (!canvas || typeof Fall === 'undefined' || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  // an Onix outfit drawn here (not a Mojang skin): dark hoodie with teal trim, grey trousers, white shoes
  const px = new Uint8ClampedArray(64 * 64 * 4);
  const put = (x0, y0, w, h, c) => {
    for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) px.set([...c, 255], (y * 64 + x) * 4);
  };
  put(0, 0, 32, 16, [214, 168, 128]);                    // head
  put(0, 0, 32, 8, [44, 32, 26]); put(0, 8, 32, 2, [44, 32, 26]); // hair
  put(8, 8, 8, 8, [214, 168, 128]); put(8, 8, 8, 2, [44, 32, 26]); // face
  put(9, 11, 2, 1, [250, 250, 250]); put(13, 11, 2, 1, [250, 250, 250]);
  put(10, 11, 1, 1, [40, 110, 130]); put(13, 11, 1, 1, [40, 110, 130]);
  put(10, 14, 4, 1, [160, 100, 80]);
  put(16, 16, 24, 16, [26, 34, 40]); put(20, 20, 8, 1, [95, 208, 218]); put(23, 21, 2, 6, [95, 208, 218]); // hoodie + trim
  put(40, 16, 16, 16, [26, 34, 40]); put(40, 28, 16, 1, [95, 208, 218]); put(44, 29, 4, 3, [214, 168, 128]); // right arm
  put(32, 48, 16, 16, [26, 34, 40]); put(32, 60, 16, 1, [95, 208, 218]); put(36, 61, 4, 3, [214, 168, 128]); // left arm
  put(0, 16, 16, 16, [60, 66, 74]); put(0, 28, 16, 4, [235, 238, 240]);   // right leg + shoe
  put(16, 48, 16, 16, [60, 66, 74]); put(16, 60, 16, 4, [235, 238, 240]); // left leg + shoe
  Fall.boot(canvas, () => ({ px, slim: false }), { loop: 9, y: 0.27, scale: 0.62 });
})();

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
  const main = document.getElementById('main-download');
  if (OS_NAMES[os]) label.textContent = `Für ${OS_NAMES[os]} herunterladen`;
  document.querySelectorAll('.dl').forEach((d) => d.classList.toggle('current', d.dataset.os === os));
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
  document.getElementById('version').textContent = `Version ${latest.tag_name.replace(/^v/, '')} · ${new Date(latest.published_at).toLocaleDateString('de-DE')}`;
  // a file from the newest release that has it (a build for one system can arrive later than the others)
  const find = (test) => {
    for (const r of releases) {
      const asset = r.assets.find(test);
      if (asset) return { url: asset.browser_download_url, version: r.tag_name.replace(/^v/, '') };
    }
    return null;
  };
  const links = {};
  for (const key of ['windows', 'linux', 'android']) links[key] = find(FILES[key]);
  // Apple chip Macs get the arm64 build, the rest the Intel one (the page cannot always tell: the arm64 build is the default)
  links.mac = find(FILES.macArm) || find(FILES.mac);
  document.querySelectorAll('.dl').forEach((d) => {
    const link = links[d.dataset.os];
    if (!link) return;
    d.href = link.url;
    if (link.version !== latest.tag_name.replace(/^v/, '')) {
      d.querySelector('.dl-file').textContent += ` · Version ${link.version}`;
    }
  });
  if (links[os]) main.href = links[os].url;
  else main.href = '#download';
}
setUpDownloads();

// ---------------------------------------------------------------- navigation, viewer, reveals
const nav = document.querySelector('.nav');
const onScroll = () => nav.classList.toggle('scrolled', window.scrollY > 40);
window.addEventListener('scroll', onScroll, { passive: true });
onScroll();

document.querySelectorAll('.gallery figure').forEach((fig) => {
  fig.addEventListener('click', () => {
    const img = fig.querySelector('img');
    const viewer = document.createElement('div');
    viewer.className = 'viewer';
    const big = document.createElement('img');
    big.src = img.src;
    big.alt = img.alt;
    viewer.append(big);
    viewer.addEventListener('click', () => viewer.remove());
    document.addEventListener('keydown', function esc(e) {
      if (e.key === 'Escape') {
        viewer.remove();
        document.removeEventListener('keydown', esc);
      }
    });
    document.body.append(viewer);
  });
});

if ('IntersectionObserver' in window) {
  const io = new IntersectionObserver((entries) => {
    for (const e of entries) {
      if (e.isIntersecting) {
        e.target.classList.add('shown');
        io.unobserve(e.target);
      }
    }
  }, { rootMargin: '0px 0px -8% 0px' });
  document.querySelectorAll('.stats, .features article, .gallery figure, .dl, .faq details, .section h2, .sub').forEach((el, i) => {
    el.classList.add('reveal');
    el.style.transitionDelay = `${(i % 3) * 60}ms`;
    io.observe(el);
  });
}
