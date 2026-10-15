'use strict';

/* Cosmetics page: a turning mannequin with the whole outfit, and a browser over the catalog
   (tabs per slot, search, tier and colour filters, try-on by hovering). Drawing is done by CosRender. */

const COS_SLOTS = [
  ['cape', 'Capes'], ['head', 'Head'], ['face', 'Face'], ['neck', 'Neck'], ['back', 'Back & Wings'], ['aura', 'Auras'], ['pet', 'Pets'],
];
const COS_SLOT_NAMES = { cape: 'Cape', head: 'Head', face: 'Face', neck: 'Neck', back: 'Back', aura: 'Aura', pet: 'Pet' };
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
  CosRender.draw($('#shop-canvas'), item, performance.now() / 50);
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
  $('#cos-sub').textContent = `${CosRender.catalog.items.length.toLocaleString('en')} cosmetics · only visible to you · applies to profile "${p ? p.name : ''}".`;
  renderCosmetics();
  startStage();
  renderCosmetica();
  loadWallet();
}

// ---------------------------------------------------------------- Cosmetica

/* Cosmetica is a separate, free cosmetics mod (Apache-2.0, on Modrinth). Its cosmetics stay on its own
   servers and are picked in its own menu; the launcher only adds or removes the mod in the active profile. */
const COSMETICA_ID = 's9hF9QGp';
let cosmeticaBusy = false;

async function renderCosmetica() {
  const box = $('#cos-cosmetica');
  const p = activeProfile();
  if (!p) return;
  const mods = await api.listMods(p.id);
  const mod = (Array.isArray(mods) ? mods : []).find((m) => (m.modrinth && m.modrinth.id === COSMETICA_ID) || /^cosmetica/i.test(m.file));
  const title = el('b', {}, ['Cosmetica', el('span', { class: 'tag', text: mod ? 'In this profile' : 'Free mod' })]);
  const text = mod
    ? 'Choose your Cosmetica capes and hats in its own menu in game. Its cape is shown instead of the Onix cape (switch in Onix → Cosmetics).'
    : 'Another free cosmetics mod with community-made capes and hats – players who also use Cosmetica see yours. Adds it to this profile.';
  const button = mod
    ? el('button', { class: 'btn sm', disabled: cosmeticaBusy, onclick: () => setCosmetica(p.id, mod) }, [cosmeticaBusy ? 'Removing…' : 'Remove'])
    : el('button', { class: 'btn sm', disabled: cosmeticaBusy, onclick: () => setCosmetica(p.id, null) }, [icon('download'), cosmeticaBusy ? 'Adding…' : 'Add to Profile']);
  box.replaceChildren(title, el('span', { text }), button);
}

async function setCosmetica(profileId, installed) {
  if (cosmeticaBusy) return;
  cosmeticaBusy = true;
  renderCosmetica();
  const res = installed ? await api.removeMod(profileId, installed.file) : await api.installModrinth(profileId, COSMETICA_ID);
  cosmeticaBusy = false;
  if (!res.ok) toast(res.error, true);
  else toast(installed ? 'Cosmetica removed from this profile' : 'Cosmetica added – pick your cosmetics in its menu in game');
  renderCosmetica();
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
  const canvas = el('canvas', cape ? { class: 'cape', width: 116, height: 144 } : { width: 144, height: 144 });
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
  if (item) CosRender.draw(tile.querySelector('canvas'), item, t);
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
    if (cos.hoverTile && cos.hoverTile.isConnected) paintTile(cos.hoverTile, ticks);
    cos.frame = requestAnimationFrame(loop);
  };
  cos.frame = requestAnimationFrame(loop);
}
