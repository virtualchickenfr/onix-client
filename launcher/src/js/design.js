'use strict';

/* Themes (built-in and custom), the theme editor and backgrounds. */

function spawnParticles(theme) {
  const ambient = theme.ambient.toLowerCase();
  const box = $('#particles');
  box.className = `particles ${ambient}`;
  box.replaceChildren();
  const falling = ambient === 'petals' || ambient === 'snow';
  const count = ambient === 'sparks' ? 18 : 30;
  for (let i = 0; i < count; i++) {
    const p = el('i');
    p.style.left = `${Math.random() * 100 - (ambient === 'petals' ? 12 : 0)}%`;
    p.style.animationDuration = `${(falling ? 10 : 9) + Math.random() * 9}s`;
    p.style.animationDelay = `${-Math.random() * 18}s`;
    if (ambient === 'snow') p.style.width = p.style.height = `${2 + Math.round(Math.random() * 2)}px`;
    box.append(p);
  }
}

function applyTheme(id) {
  const t = themeById(id);
  currentThemeId = t.id;
  const r = document.documentElement.style;
  r.setProperty('--accent', t.accent);
  r.setProperty('--on-accent', luminance(t.accent) > 0.6 ? '#0b0b0e' : '#ffffff');
  r.setProperty('--accent-soft', `${t.accent}26`);
  r.setProperty('--sky', t.sky);
  r.setProperty('--particle', t.particle);
  r.setProperty('--bg', mix(t.panel, '#000000', 0.4));
  r.setProperty('--s1', t.panel);
  r.setProperty('--s2', mix(t.panel, '#ffffff', 0.025));
  r.setProperty('--s3', mix(mix(t.panel, t.accent, 0.03), '#ffffff', 0.05));
  r.setProperty('--s4', mix(mix(t.panel, t.accent, 0.05), '#ffffff', 0.09));
  spawnParticles(t);
  $$('.theme[data-theme]').forEach((b) => b.classList.toggle('selected', b.dataset.theme === t.id));
  if ($('#home-theme')) $('#home-theme').textContent = `Current: ${t.name}`;
}

async function selectTheme(id) {
  applyTheme(id);
  await api.setSettings({ theme: currentThemeId });
}

function themeTile(t) {
  const tile = el('button', {
    class: `theme${t.id === currentThemeId ? ' selected' : ''}`,
    'data-theme': t.id,
    style: `--ta:${t.accent};--tp:${t.panel}`,
    onclick: () => selectTheme(t.id).then(() => toast(`${t.name} is now active in the launcher and in game`)),
  }, [
    el('div', { class: 'theme-mini' }, [
      el('div', { class: 'side' }, [el('i'), el('i'), el('i')]),
      el('div', { class: 'body' }, [el('i'), el('i'), el('i')]),
    ]),
    el('div', { class: 'theme-name' }, [el('span', { text: t.name }), icon('check')]),
  ]);
  const edit = el('span', { class: 'edit', role: 'button', tabindex: 0, title: t.custom ? 'Edit theme' : 'Customize a copy' }, [icon('pencil')]);
  edit.addEventListener('click', (e) => {
    e.stopPropagation();
    openThemeEditor(t);
  });
  tile.append(edit);
  return tile;
}

function renderThemes() {
  const tiles = allThemes().map((t) => themeTile({ ...t, custom: !THEMES.includes(t) }));
  const add = el('button', { class: 'theme add', onclick: () => openThemeEditor(null) }, [icon('plus'), el('span', { text: 'New Theme' })]);
  $('#themes').replaceChildren(...tiles, add);
}

// ---------- theme editor

let editingTheme = null;

function newThemeId() {
  const hex = Array.from(crypto.getRandomValues(new Uint8Array(3)), (b) => b.toString(16).padStart(2, '0')).join('');
  return `custom-${hex}`;
}

function themePreview() {
  const p = $('#th-preview').style;
  p.setProperty('--tp-accent', $('#th-accent').value);
  p.setProperty('--tp-panel', $('#th-panel').value);
  p.setProperty('--tp-sky', $('#th-sky').value);
}

function openThemeEditor(theme) {
  const custom = theme && !THEMES.includes(themeById(theme.id)) && theme.id.startsWith('custom-');
  const base = theme || themeById(currentThemeId);
  editingTheme = custom ? theme.id : null;
  $('#th-title').textContent = custom ? 'Edit Theme' : 'New Theme';
  $('#th-name').value = custom ? base.name : (theme ? `${base.name} Copy` : 'My Theme');
  $('#th-accent').value = base.accent.slice(0, 7);
  $('#th-panel').value = base.panel.slice(0, 7);
  $('#th-sky').value = base.sky.slice(0, 7);
  $('#th-particle').value = base.particle.slice(0, 7);
  $('#th-ambient').value = base.ambient;
  $('#th-delete').hidden = !custom;
  themePreview();
  openModal('#theme-modal');
  setTimeout(() => $('#th-name').select(), 60);
}

['#th-accent', '#th-panel', '#th-sky'].forEach((id) => $(id).addEventListener('input', themePreview));

$('#th-save').addEventListener('click', async () => {
  const theme = {
    id: editingTheme || newThemeId(),
    name: $('#th-name').value.trim() || 'My Theme',
    accent: $('#th-accent').value,
    panel: $('#th-panel').value,
    sky: $('#th-sky').value,
    particle: $('#th-particle').value,
    ambient: $('#th-ambient').value,
  };
  const res = await api.saveTheme(theme);
  if (!res.ok) return toast(res.error, true);
  customThemes = res.value;
  renderThemes();
  await selectTheme(theme.id);
  closeModal('#theme-modal');
  toast(`${theme.name} saved – also available in game`);
});

$('#th-delete').addEventListener('click', async () => {
  if (!editingTheme) return;
  const name = themeById(editingTheme).name;
  if (!confirm(`Delete the theme "${name}"?`)) return;
  const res = await api.deleteTheme(editingTheme);
  if (!res.ok) return toast(res.error, true);
  customThemes = res.value;
  if (currentThemeId === editingTheme) applyTheme('onyx');
  renderThemes();
  closeModal('#theme-modal');
});

// ---------- backgrounds

function applyBackground(id) {
  currentBackground = BACKGROUNDS.some(([b]) => b === id) ? id : 'carbon';
  document.documentElement.style.setProperty('--hero-image', `url(assets/backgrounds/${currentBackground}.jpg)`);
  $$('.bg-tile').forEach((b) => b.classList.toggle('selected', b.dataset.bg === currentBackground));
}

function renderBackgrounds() {
  $('#backgrounds').replaceChildren(...BACKGROUNDS.map(([id, name]) => el('button', {
    class: `bg-tile${id === currentBackground ? ' selected' : ''}`,
    'data-bg': id,
    onclick: async () => {
      applyBackground(id);
      await api.setSettings({ background: id });
    },
  }, [el('img', { src: `assets/backgrounds/${id}.jpg`, alt: '', loading: 'lazy' }), el('span', { text: name })])));
}


// ---------- in-game sky

let sky = { enabled: false, daySky: 'Clear Day', nightSky: 'Starry Night' };
let skyMode = 'day';

function renderSkies() {
  $('#sky-on').checked = sky.enabled;
  $('#sky-day-name').textContent = sky.daySky;
  $('#sky-night-name').textContent = sky.nightSky === SAME_AS_DAY ? 'same as day' : sky.nightSky;
  $$('#sky-mode button').forEach((b) => b.classList.toggle('active', b.dataset.mode === skyMode));
  $('#skies').classList.toggle('off', !sky.enabled);
  const tile = (id, name) => {
    const isDay = sky.daySky === name;
    const isNight = sky.nightSky === name;
    const selected = skyMode === 'day' ? isDay : isNight;
    const badges = el('div', { class: 'badges' }, [
      el('img', { src: `assets/skies/${id}.jpg`, alt: '', loading: 'lazy' }),
      isDay ? el('em', { text: 'Day' }) : null,
      isNight ? el('em', { text: 'Night' }) : null,
    ]);
    return el('button', { class: `bg-tile${selected ? ' selected' : ''}`, onclick: () => chooseSky(name) }, [badges, el('span', { text: name })]);
  };
  const tiles = SKIES.map(([id, name]) => tile(id, name));
  if (skyMode === 'night') {
    tiles.unshift(el('button', { class: `bg-tile${sky.nightSky === SAME_AS_DAY ? ' selected' : ''}`, onclick: () => chooseSky(SAME_AS_DAY) },
      [el('div', { class: 'same', text: 'Darkened day sky' }), el('span', { text: 'Same as day' })]));
  }
  $('#skies').replaceChildren(...tiles);
}

async function saveSky(patch) {
  const res = await api.setSky({ ...sky, ...patch });
  if (!res.ok) return toast(res.error, true);
  const live = res.value.live;
  sky = { enabled: res.value.enabled, daySky: res.value.daySky, nightSky: res.value.nightSky };
  renderSkies();
  if (live) toast('Applied in your running game');
}

function chooseSky(name) {
  // picking a sky turns the module on – that is almost always what you want
  saveSky(skyMode === 'day' ? { daySky: name, enabled: true } : { nightSky: name, enabled: true });
}

$$('#sky-mode button').forEach((b) => b.addEventListener('click', () => {
  skyMode = b.dataset.mode;
  renderSkies();
}));
$('#sky-on').addEventListener('change', () => saveSky({ enabled: $('#sky-on').checked }));

async function loadSky() {
  sky = await api.getSky();
  renderSkies();
}
