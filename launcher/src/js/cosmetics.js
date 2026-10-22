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
  $('#cos-sub').textContent = `Only visible to you · profile "${p ? p.name : ''}"`;
  renderCosmetics();
  startStage();
  loadWallet();
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
