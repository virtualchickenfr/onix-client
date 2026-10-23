'use strict';

/* Cosmetics page: a turning mannequin with the whole outfit, and a browser over the catalog
   (tabs per slot, search, tier and colour filters, try-on by hovering). Drawing is done by CosRender. */

const COS_SLOTS = [
  ['cape', 'Capes'], ['head', 'Head'], ['face', 'Face'], ['neck', 'Neck'], ['back', 'Back & Wings'], ['aura', 'Auras'], ['pet', 'Pets'],
  ['nametag', 'Name Styles'], ['tag', 'Tags'],
];
const COS_SLOT_NAMES = { cape: 'Cape', head: 'Head', face: 'Face', neck: 'Neck', back: 'Back', aura: 'Aura', pet: 'Pet', nametag: 'Name Style', tag: 'Tag' };
/** Slots drawn as coloured text instead of a model. */
const TEXT_SLOTS = new Set(['nametag', 'tag']);
const TIER_COLORS = { common: '#9aa0aa', rare: '#4ea8ff', epic: '#b46cff', legendary: '#ffc23d' };
const COS_PAGE = 96;

const cos = {
  tab: 'all',
  query: '',
  tier: '',
  palette: null,
  list: [],
  shown: 0,
  tryOn: null,
  yaw: -25,
  dragging: null,
  hoverTile: null,
  frame: 0,
  observer: null,
  ready: false,
  own: '',
};

// ---------------------------------------------------------------- name styles and tags (same looks as the mod's NameTags.java)

const hexRgb = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const rgbCss = ([r, g, b]) => `rgb(${Math.round(r)},${Math.round(g)},${Math.round(b)})`;
const mixRgb = (a, b, f) => [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f];

function lookStops(cols, p, cyclic) {
  if (cols.length === 1) return cols[0];
  const segs = cyclic ? cols.length : cols.length - 1;
  const x = Math.max(0, Math.min(0.9999, p)) * segs;
  const k = Math.floor(x);
  return mixRgb(cols[k], cols[(k + 1) % cols.length], x - k);
}

function hsvRgb(h, s, v) {
  const ch = (x) => v * (1 - s + s * Math.max(0, Math.min(1, x))) * 255;
  return [ch(Math.abs(h * 6 - 3) - 1), ch(2 - Math.abs(h * 6 - 2)), ch(2 - Math.abs(h * 6 - 4))];
}

/** Colour of letter i of n at time t (ticks). */
function lookColor(look, i, n, t) {
  const cols = (look.colors || []).map(hexRgb);
  const speed = look.speed || 1;
  const white = [255, 255, 255];
  switch (look.type) {
    case 'gradient': return cols.length ? lookStops(cols, n <= 1 ? 0 : i / (n - 1), false) : white;
    case 'wave': {
      const p = i / Math.max(n, 6) - t * 0.02 * speed;
      return cols.length ? lookStops(cols, p - Math.floor(p), true) : white;
    }
    case 'rainbow': {
      const h = i * 0.07 - t * 0.012 * speed;
      return hsvRgb(h - Math.floor(h), 0.62, 1);
    }
    case 'pulse': return cols.length < 2 ? cols[0] || white : mixRgb(cols[0], cols[1], 0.5 + 0.5 * Math.sin(t * 0.15 * speed));
    case 'shine': {
      const p = ((t * 0.5 * speed) % (n + 10)) - 5;
      return cols.length < 2 ? cols[0] || white : mixRgb(cols[0], cols[1], Math.max(0, 1 - Math.abs(i - p) / 2.5));
    }
    default: return cols[0] || white;
  }
}

/** Letters with colours: [{ ch, color, bold }]. */
function lookRuns(text, look, t) {
  const chars = [...text];
  return chars.map((ch, i) => ({ ch, color: rgbCss(lookColor(look, i, chars.length, t)), bold: !!look.bold }));
}

function tagRuns(item, t) {
  const text = item.custom ? (cosmetics.tagText || 'TAG') : item.tagText;
  if (!text) return [];
  const body = lookRuns(text, item.look, t);
  if (![...text].some((c) => /[\p{L}\p{N}]/u.test(c))) return body;
  const br = { color: '#9a9aa6', bold: false };
  return [{ ch: '[', ...br }, ...body, { ch: ']', ...br }];
}

function ownName() {
  return (typeof account !== 'undefined' && account && account.name) || 'Player';
}

/** The whole name tag: tag, then the name in its style. */
function nameTagRuns(style, tag, t) {
  const runs = tag ? [...tagRuns(tag, t), { ch: ' ', color: '#fff' }] : [];
  return runs.concat(style ? lookRuns(ownName(), style.look, t) : [...ownName()].map((ch) => ({ ch, color: '#ffffff' })));
}

/** Draws text runs centred on a canvas, with a Minecraft-like shadow; shrinks to fit. */
function drawRuns(canvas, runs, size, plate = false) {
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  if (!runs.length) return;
  const family = getComputedStyle(document.body).getPropertyValue('--display') || 'sans-serif';
  const fontOf = (r, px) => `${r.bold ? 800 : 650} ${px}px ${family}`;
  const widthAt = (px) => runs.reduce((w, r) => {
    ctx.font = fontOf(r, px);
    return w + ctx.measureText(r.ch).width;
  }, 0);
  let px = size;
  let w = widthAt(px);
  if (w > canvas.width - 12) {
    px = Math.max(8, px * (canvas.width - 12) / w);
    w = widthAt(px);
  }
  let x = (canvas.width - w) / 2;
  const y = canvas.height / 2;
  ctx.textBaseline = 'middle';
  if (plate) {
    ctx.fillStyle = 'rgba(0,0,0,.35)';
    ctx.fillRect(x - px * 0.35, y - px * 0.72, w + px * 0.7, px * 1.44);
  }
  const shadow = Math.max(1, px / 9);
  for (const r of runs) {
    ctx.font = fontOf(r, px);
    ctx.fillStyle = 'rgba(0,0,0,.55)';
    ctx.fillText(r.ch, x + shadow, y + shadow);
    ctx.fillStyle = r.color;
    ctx.fillText(r.ch, x, y);
    x += ctx.measureText(r.ch).width;
  }
}

/** A name style (on your name) or a tag, for tiles and the shop dialog. */
function drawLookItem(canvas, item, t) {
  const runs = item.slot === 'nametag' ? lookRuns(ownName(), item.look, t) : tagRuns(item, t);
  drawRuns(canvas, runs, canvas.height * 0.6);
}

function drawAnyItem(canvas, item, t) {
  if (TEXT_SLOTS.has(item.slot)) drawLookItem(canvas, item, t);
  else CosRender.draw(canvas, item, t);
}

function renderTagText() {
  const box = $('#cos-tagtext');
  box.hidden = cos.tab !== 'tag';
  $('#cos-swatches').hidden = TEXT_SLOTS.has(cos.tab);
  if (box.hidden) return;
  const input = $('#cos-tagtext-input');
  if (document.activeElement !== input) input.value = cosmetics.tagText || '';
  const ownsCustom = CosRender.catalog.items.some((i) => i.custom && owns(i));
  const info = $('#cos-tagtext-info');
  info.classList.remove('err');
  info.textContent = ownsCustom ? `Your own text for custom tags · max ${OnixNameTags.MAX} characters` : 'Buy a custom tag to show your own text';
}

async function saveTagText() {
  const input = $('#cos-tagtext-input');
  const text = input.value.replace(/\s+/g, ' ').trim();
  const problem = text ? OnixNameTags.problem(text) : null;
  const info = $('#cos-tagtext-info');
  if (problem) {
    info.textContent = problem;
    info.classList.add('err');
    return;
  }
  const r = await api.setTagText(text);
  if (!r.ok) {
    info.textContent = r.error;
    info.classList.add('err');
    return;
  }
  cosmetics = r.value;
  input.blur();
  renderTagText();
  $$('.ctile').forEach((t) => paintTile(t));
  toast(text ? `Tag saved: [${text}]` : 'Tag text removed');
}

// ---------------------------------------------------------------- coins (Onix server)

/** Last wallet from the server: { coins, unlimited, owned: Set, playMinutes, nextCoinMs } or null. */
let wallet = null;
let walletError = '';

function owns(item) {
  return !!item && (item.price === 0 || (wallet && (wallet.unlimited || wallet.owned.has(item.id))));
}

function canAfford(item) {
  return !!wallet && (wallet.unlimited || wallet.coins >= item.price);
}

const coinText = (n) => Number(n).toLocaleString('en');

async function loadWallet() {
  const r = await api.wallet();
  if (r.ok) {
    setWallet(r.value);
  } else {
    wallet = null;
    walletError = r.error;
  }
  renderWallet();
  refreshOwnership();
}

function setWallet(w) {
  wallet = { ...w, owned: new Set(w.owned || []) };
  walletError = '';
  const gifts = w.gifts || [];
  gifts.forEach((g, i) => {
    const item = cosItem(g.item);
    setTimeout(() => toast(`Gift from ${g.from}: ${item ? item.name : 'a cosmetic'}${g.message ? ` – “${g.message}”` : ''}`), i * 3800);
  });
  if (gifts.length) api.giftsSeen(gifts.map((g) => g.id));
}

function renderWallet() {
  const box = $('#cos-wallet');
  box.classList.toggle('off', !wallet);
  $('#cos-coins').textContent = wallet ? (wallet.unlimited ? '∞' : coinText(wallet.coins)) : '–';
  $('#cos-wallet-sub').textContent = wallet
    ? (wallet.unlimited ? 'Unlimited coins' : `${coinText(wallet.playMinutes)} min played`)
    : (/No Onix server/.test(walletError) ? 'No server set' : walletError ? 'Offline' : 'Connecting…');
  box.title = wallet
    ? '1 coin for every minute you play with Onix. Click to refresh.'
    : walletError || 'Connecting to the Onix server…';
}

/** Price tags and "selected" state after the wallet changed. */
function refreshOwnership() {
  $$('.ctile').forEach((t) => {
    const item = cosItem(t.dataset.id);
    if (!item) return;
    const mine = owns(item);
    t.classList.toggle('locked', !mine);
    const tag = t.querySelector('.price');
    if (mine && tag) tag.remove();
    if (!mine && !tag) t.append(priceTag(item));
  });
  if (cos.own) filterCosmetics();
}

function priceTag(item) {
  return el('b', { class: 'price' }, [el('i', { class: 'coin' }), coinText(item.price)]);
}

// ---------------------------------------------------------------- buy / gift dialog

const shop = { item: null, gift: false, busy: false };

function openShop(item, gift = false) {
  shop.item = item;
  shop.gift = gift && item.price > 0;
  shop.busy = false;
  $('#shop-title').textContent = gift ? 'Gift a cosmetic' : owns(item) ? 'Your cosmetic' : 'Buy cosmetic';
  $('#shop-name').textContent = item.name;
  $('#shop-tier').textContent = `${item.tier[0].toUpperCase()}${item.tier.slice(1)} · ${COS_SLOT_NAMES[item.slot]}`;
  $('#shop-price').textContent = item.price === 0 ? 'Free for everyone' : `${coinText(item.price)} coins`;
  $('#shop-message').value = '';
  $('#shop-canvas').classList.toggle('nt', TEXT_SLOTS.has(item.slot));
  drawAnyItem($('#shop-canvas'), item, performance.now() / 50);
  renderShop();
  openModal('#shop-modal');
  if (shop.gift) setTimeout(() => $('#shop-to').focus(), 50);
}

function shopStatus(text, error = false) {
  const st = $('#shop-status');
  st.textContent = text;
  st.classList.toggle('error', error);
}

function renderShop() {
  const item = shop.item;
  if (!item) return;
  const mine = owns(item);
  $('#shop-gift').hidden = !shop.gift;
  $('#shop-mode').hidden = item.price === 0;
  $('#shop-mode').textContent = shop.gift ? 'Buy instead' : 'Gift instead';
  const run = $('#shop-run');
  if (!wallet) {
    shopStatus(walletError || 'Connecting to the Onix server…', !!walletError);
    run.disabled = true;
    run.textContent = shop.gift ? 'Send Gift' : 'Buy';
    return;
  }
  if (shop.gift) {
    run.textContent = shop.busy ? 'Sending…' : `Send Gift · ${coinText(item.price)}`;
    run.disabled = shop.busy || !canAfford(item);
    shopStatus(canAfford(item) ? 'The player needs to have played with Onix on this server.' : `You need ${coinText(item.price - wallet.coins)} more coins – keep playing!`, !canAfford(item));
  } else if (mine) {
    run.textContent = cosmetics[item.slot] === item.id ? 'Take Off' : 'Wear';
    run.disabled = false;
    shopStatus(item.price === 0 ? 'Everyone owns this one.' : 'You own this cosmetic.');
  } else {
    run.textContent = shop.busy ? 'Buying…' : `Buy · ${coinText(item.price)}`;
    run.disabled = shop.busy || !canAfford(item);
    shopStatus(canAfford(item) ? `You have ${wallet.unlimited ? 'unlimited' : coinText(wallet.coins)} coins.` : `You need ${coinText(item.price - wallet.coins)} more coins – 1 coin per minute you play.`, !canAfford(item));
  }
}

async function runShop() {
  const item = shop.item;
  if (!item || shop.busy) return;
  if (!shop.gift && owns(item)) {
    closeModal('#shop-modal');
    choose(item.slot, cosmetics[item.slot] === item.id ? null : item.id);
    return;
  }
  shop.busy = true;
  renderShop();
  const to = $('#shop-to').value.trim();
  const r = shop.gift ? await api.giftCosmetic(item.id, to, $('#shop-message').value.trim()) : await api.buyCosmetic(item.id);
  shop.busy = false;
  if (!r.ok) {
    renderShop();
    shopStatus(r.error, true);
    return;
  }
  setWallet(r.value);
  renderWallet();
  refreshOwnership();
  closeModal('#shop-modal');
  if (shop.gift) {
    toast(`${item.name} sent to ${r.value.to || to}`);
  } else {
    toast(`${item.name} bought – you are wearing it now`);
    choose(item.slot, item.id);
  }
}

function cosItem(id) {
  return id ? CosRender.item(id) : null;
}

async function loadCosmetics() {
  setupCosmetics();
  cosmetics = await api.getCosmetics();
  const p = activeProfile();
  $('#cos-sub').textContent = `Name styles & tags are visible to other Onix players · profile "${p ? p.name : ''}"`;
  renderCosmetics();
  startStage();
  loadWallet();
}

/** One-time wiring of the page controls. */
function setupCosmetics() {
  if (cos.ready) return;
  cos.ready = true;
  $('#cos-query').addEventListener('input', (e) => {
    cos.query = e.target.value;
    filterCosmetics();
  });
  $('#cos-tier').addEventListener('change', (e) => {
    cos.tier = e.target.value;
    filterCosmetics();
  });
  $('#cos-own').addEventListener('change', (e) => {
    cos.own = e.target.value;
    filterCosmetics();
  });
  $('#cos-wallet').addEventListener('click', () => {
    if (/No Onix server/.test(walletError)) showView('settings');
    else loadWallet();
  });
  $('#shop-run').addEventListener('click', runShop);
  $('#shop-mode').addEventListener('click', () => {
    shop.gift = !shop.gift;
    $('#shop-title').textContent = shop.gift ? 'Gift a cosmetic' : 'Buy cosmetic';
    renderShop();
    if (shop.gift) $('#shop-to').focus();
  });
  $('#shop-to').addEventListener('input', renderShop);
  $('#shop-to').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('#shop-message').focus(); });
  $('#shop-message').addEventListener('keydown', (e) => { if (e.key === 'Enter') runShop(); });
  $('#cos-random').addEventListener('click', randomOutfit);
  $('#cos-tagtext-save').addEventListener('click', saveTagText);
  $('#cos-tagtext-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') saveTagText(); });
  $('#cos-clear').addEventListener('click', () => saveOutfit({}, 'All cosmetics removed'));

  // palette swatches
  $('#cos-swatches').replaceChildren(...Object.entries(CosRender.catalog.palettes).map(([id, p]) => el('button', {
    class: 'cos-swatch',
    title: p.name,
    'data-palette': id,
    style: `--a:${p.colors[0]};--b:${p.colors[2]}`,
    onclick: () => {
      cos.palette = cos.palette === id ? null : id;
      $$('.cos-swatch').forEach((s) => s.classList.toggle('on', s.dataset.palette === cos.palette));
      filterCosmetics();
    },
  })));

  // the stage turns by dragging
  const stage = $('#cos-stage');
  stage.addEventListener('pointerdown', (e) => {
    cos.dragging = { x: e.clientX, yaw: cos.yaw };
    stage.setPointerCapture(e.pointerId);
    stage.classList.add('dragging');
  });
  stage.addEventListener('pointermove', (e) => {
    if (cos.dragging) cos.yaw = cos.dragging.yaw + (e.clientX - cos.dragging.x) * 0.8;
  });
  const stop = () => {
    cos.dragging = null;
    stage.classList.remove('dragging');
  };
  stage.addEventListener('pointerup', stop);
  stage.addEventListener('pointercancel', stop);

  // tiles draw themselves when they scroll into view; the sentinel loads the next page
  cos.observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      if (entry.target.id === 'cos-more') {
        appendTiles();
        continue;
      }
      paintTile(entry.target);
      cos.observer.unobserve(entry.target);
    }
  }, { rootMargin: '200px' });
  cos.observer.observe($('#cos-more'));
}

function renderCosmetics() {
  renderTagText();
  renderTabs();
  renderWorn();
  filterCosmetics();
}

function renderTabs() {
  const counts = {};
  for (const item of CosRender.catalog.items) counts[item.slot] = (counts[item.slot] || 0) + 1;
  const tab = (id, label, count) => el('button', {
    class: `cos-tab${cos.tab === id ? ' on' : ''}`,
    onclick: () => {
      cos.tab = id;
      renderTabs();
      filterCosmetics();
      renderTagText();
    },
  }, [cosmetics[id] ? el('span', { class: 'dot' }) : null, label, el('small', { text: count.toLocaleString('en') })]);
  $('#cos-tabs').replaceChildren(tab('all', 'All', CosRender.catalog.items.length), ...COS_SLOTS.map(([id, label]) => tab(id, label, counts[id] || 0)));
}

function renderWorn() {
  const rows = COS_SLOTS.map(([slot]) => [slot, cosItem(cosmetics[slot])]).filter(([, item]) => item);
  $('#cos-worn').replaceChildren(...(rows.length ? rows.map(([slot, item]) => el('div', { class: 'wrow' }, [
    el('i', { text: COS_SLOT_NAMES[slot] }),
    el('b', { text: item.name, title: 'Show in the list', onclick: () => { cos.tab = slot; renderTabs(); filterCosmetics(); } }),
    el('button', { title: 'Take off', onclick: () => choose(slot, null) }, [icon('x')]),
  ])) : [el('div', { class: 'empty-worn', text: 'Nothing equipped yet' })]));
  $('#cos-clear').disabled = !rows.length;
}

function filterCosmetics() {
  const words = cos.query.toLowerCase().split(/\s+/).filter(Boolean);
  cos.list = CosRender.catalog.items.filter((item) => {
    if (cos.tab !== 'all' && item.slot !== cos.tab) return false;
    if (cos.tier && item.tier !== cos.tier) return false;
    if (cos.palette && item.palette !== cos.palette) return false;
    if (cos.own === 'owned' && !owns(item)) return false;
    if (cos.own === 'shop' && owns(item)) return false;
    if (!words.length) return true;
    const hay = `${item.name} ${item.slot} ${item.tier} ${(item.tags || []).join(' ')}`.toLowerCase();
    return words.every((w) => hay.includes(w));
  });
  cos.shown = 0;
  $('#cos-grid').replaceChildren();
  $('#cos-count').textContent = cos.list.length
    ? `${cos.list.length.toLocaleString('en')} ${cos.list.length === 1 ? 'item' : 'items'}`
    : '';
  if (!cos.list.length) {
    $('#cos-grid').append(el('div', { class: 'empty', style: 'grid-column: 1 / -1' }, [el('b', { text: 'Nothing matches' }), el('span', { text: 'Try another word, tier or colour.' })]));
  }
  appendTiles();
}

function appendTiles() {
  if (cos.shown >= cos.list.length) {
    $('#cos-more').textContent = cos.list.length > COS_PAGE ? 'That’s everything' : '';
    return;
  }
  const next = cos.list.slice(cos.shown, cos.shown + COS_PAGE);
  cos.shown += next.length;
  $('#cos-grid').append(...next.map(makeTile));
  $('#cos-more').textContent = cos.shown < cos.list.length ? 'Loading more…' : '';
  // the sentinel may still be visible: observe again so the next page follows
  cos.observer.unobserve($('#cos-more'));
  cos.observer.observe($('#cos-more'));
}

function makeTile(item) {
  const cape = item.slot === 'cape';
  const canvas = el('canvas', TEXT_SLOTS.has(item.slot) ? { class: 'nt', width: 264, height: 72 }
    : cape ? { class: 'cape', width: 116, height: 144 } : { width: 144, height: 144 });
  const check = icon('check');
  check.setAttribute('class', 'tick');
  const tile = el('button', {
    class: `ctile${cosmetics[item.slot] === item.id ? ' selected' : ''}`,
    'data-id': item.id,
    title: `${item.name}\n${item.tier[0].toUpperCase()}${item.tier.slice(1)}${item.palette ? ` · ${CosRender.catalog.palettes[item.palette].name}` : ''}`,
    style: `--tier:${TIER_COLORS[item.tier] || TIER_COLORS.common}`,
    onclick: () => (owns(item) ? choose(item.slot, cosmetics[item.slot] === item.id ? null : item.id) : openShop(item)),
    oncontextmenu: (e) => {
      e.preventDefault();
      openShop(item, item.price > 0);
    },
    onmouseenter: () => {
      cos.tryOn = item;
      cos.hoverTile = tile;
    },
    onmouseleave: () => {
      if (cos.tryOn === item) cos.tryOn = null;
      if (cos.hoverTile === tile) {
        cos.hoverTile = null;
        paintTile(tile);
      }
    },
  }, [canvas, el('span', { text: item.name }), el('i', { class: 'tier' }), check, owns(item) ? null : priceTag(item)]);
  if (!owns(item)) tile.classList.add('locked');
  cos.observer.observe(tile);
  return tile;
}

function paintTile(tile, t = 0) {
  const item = cosItem(tile.dataset.id);
  if (item) drawAnyItem(tile.querySelector('canvas'), item, t);
}

async function choose(slot, id) {
  const r = await api.setCosmetic(slot, id);
  if (!r.ok) {
    toast(r.error, true);
    return;
  }
  cosmetics = r.value;
  afterChange();
}

async function saveOutfit(outfit, message) {
  const r = await api.setCosmetics(outfit);
  if (!r.ok) {
    toast(r.error, true);
    return;
  }
  cosmetics = r.value;
  afterChange();
  if (message) toast(message);
}

function afterChange() {
  $$('.ctile').forEach((t) => {
    const item = cosItem(t.dataset.id);
    t.classList.toggle('selected', !!item && cosmetics[item.slot] === item.id);
  });
  renderTabs();
  renderWorn();
}

/** A random item for every slot except one or two left empty, so outfits do not get too busy. */
function randomOutfit() {
  const pick = (list) => list[Math.floor(Math.random() * list.length)];
  const outfit = {};
  for (const [slot] of COS_SLOTS) {
    const mine = CosRender.catalog.items.filter((i) => i.slot === slot && owns(i));
    const keep = slot === 'cape' || slot === 'head' || Math.random() < 0.45;
    if (keep && mine.length) outfit[slot] = pick(mine).id;
  }
  saveOutfit(outfit, 'New random outfit from your cosmetics');
}

// ---------------------------------------------------------------- stage

function startStage() {
  if (cos.frame) return;
  let last = performance.now();
  const loop = (now) => {
    if (currentView !== 'cosmetics') {
      cos.frame = 0;
      return;
    }
    const dt = Math.min(0.1, (now - last) / 1000);
    last = now;
    const ticks = now / 50;
    // hovering a cape or back item turns the mannequin around; otherwise it turns slowly by itself
    if (!cos.dragging) {
      if (cos.tryOn) {
        const target = cos.tryOn.slot === 'cape' || cos.tryOn.slot === 'back' ? 160 : -25;
        const diff = ((((target - cos.yaw) % 360) + 540) % 360) - 180;
        cos.yaw += diff * Math.min(1, dt * 6);
      } else {
        cos.yaw += dt * 18;
      }
    }
    const outfit = {};
    for (const [slot] of COS_SLOTS) outfit[slot] = cosItem(cosmetics[slot]);
    if (cos.tryOn) outfit[cos.tryOn.slot] = cos.tryOn;
    CosRender.drawOutfit($('#cos-stage'), outfit, ticks, cos.yaw);
    drawRuns($('#cos-nametag'), outfit.nametag || outfit.tag ? nameTagRuns(outfit.nametag, outfit.tag, ticks) : [], 26, true);
    if (cos.hoverTile && cos.hoverTile.isConnected) paintTile(cos.hoverTile, ticks);
    cos.frame = requestAnimationFrame(loop);
  };
  cos.frame = requestAnimationFrame(loop);
}
