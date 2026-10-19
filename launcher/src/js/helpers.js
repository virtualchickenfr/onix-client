'use strict';

/* DOM helpers, toasts, the loading gem and small utilities. */

function el(tag, attrs = {}, children = []) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v == null || v === false) continue;
    if (k === 'class') n.className = v;
    else if (k === 'text') n.textContent = v;
    else if (k === 'style') n.style.cssText = v;
    else if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
    else n.setAttribute(k, v === true ? '' : v);
  }
  for (const c of [].concat(children)) if (c != null && c !== false) n.append(c);
  return n;
}

function icon(id) {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  const use = document.createElementNS(ns, 'use');
  use.setAttribute('href', `#i-${id}`);
  svg.append(use);
  return svg;
}

/** The animated Onix gem used as loading indicator everywhere. */
function gem(extraClass = '') {
  const node = document.getElementById('gem-template').content.firstElementChild.cloneNode(true);
  if (extraClass) node.classList.add(...extraClass.split(' '));
  return node;
}

function loadingBlock(text) {
  return el('div', { class: 'loading-block' }, [gem(), el('span', { text })]);
}

let toastTimer;
function toast(text, error = false) {
  const t = $('#toast');
  t.textContent = text;
  t.classList.toggle('error', error);
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 3600);
}

const gb = (mb) => `${(mb / 1024).toFixed(mb % 1024 ? 1 : 0)} GB`;
/** Minecraft version of a profile (profiles from before multi-version support are 1.21.1). */
const mcOfProfile = (p) => (p && p.mcVersion) || '1.21.1';
const activeProfile = () => profileState.profiles.find((p) => p.id === profileState.active) || profileState.profiles[0];
const LOADER_NAMES = { fabric: 'Fabric', quilt: 'Quilt', neoforge: 'NeoForge', forge: 'Forge', vanilla: 'Vanilla' };
const loaderName = (p) => LOADER_NAMES[(p && p.loader) || 'fabric'];
const profileMeta = (p) => [mcOfProfile(p), (p.loader || 'fabric') !== 'fabric' ? loaderName(p) : null, gb(p.ramMb), (p.loader || 'fabric') === 'fabric' && p.performanceMods ? 'Performance pack' : null, p.quickJoin || null].filter(Boolean).join(' · ');
const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

function mix(a, b, t) {
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const ch = (s) => Math.round(((pa >> s) & 255) + (((pb >> s) & 255) - ((pa >> s) & 255)) * t);
  return `#${((1 << 24) | (ch(16) << 16) | (ch(8) << 8) | ch(0)).toString(16).slice(1)}`;
}

function luminance(hex) {
  const n = parseInt(hex.slice(1), 16);
  return (0.299 * ((n >> 16) & 255) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255)) / 255;
}

function openModal(id) { $(id).classList.add('open'); }
function closeModal(id) { $(id).classList.remove('open'); }
$$('.modal-backdrop').forEach((m) => {
  m.addEventListener('click', (e) => { if (e.target === m || e.target.closest('[data-close]')) m.classList.remove('open'); });
});
