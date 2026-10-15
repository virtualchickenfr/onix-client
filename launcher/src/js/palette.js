'use strict';

/* Command palette (Ctrl+K): jump to any page, play, switch profile, account, theme,
   background or sky, flip settings – all from the keyboard. Also the global shortcuts. */

const PAGE_ICONS = { home: 'home', profiles: 'layers', mods: 'puzzle', cosmetics: 'shirt', import: 'import', install: 'plug', settings: 'settings', console: 'terminal' };
const PAGE_ORDER = ['home', 'profiles', 'mods', 'cosmetics', 'import', 'install', 'settings', 'console'];
const isMac = navigator.platform.toUpperCase().includes('MAC');
const MOD = isMac ? '⌘' : 'Ctrl';

function paletteCommands() {
  const cmds = [];
  const add = (group, title, run, extra = {}) => cmds.push({ group, title, run, ...extra });

  add('Actions', state === 'running' ? 'Minecraft is running' : 'Play', () => { if (!playBtn.disabled) playBtn.click(); },
    { icon: 'play', keys: `${MOD} Enter`, sub: activeProfile() ? `Profile "${activeProfile().name}"` : '' });
  PAGE_ORDER.forEach((page, i) => add('Pages', TITLES[page], () => showView(page), { icon: PAGE_ICONS[page], keys: `${MOD} ${i + 1}` }));

  for (const p of profileState.profiles || []) {
    const active = p.id === profileState.active;
    add('Profiles', `Profile: ${p.name}`, async () => {
      profileState = await api.selectProfile(p.id);
      refreshProfiles();
      toast(`Profile "${p.name}" selected`);
    }, { icon: 'layers', sub: profileMeta(p), current: active });
  }
  for (const a of accounts) {
    add('Accounts', `Play as ${a.name}`, () => switchAccount(a), { icon: 'check', current: a.active, sub: a.active ? 'Active' : ago(a.lastUsed) });
  }
  add('Accounts', 'Add another account', () => login(), { icon: 'plus' });

  for (const t of allThemes()) {
    add('Design', `Theme: ${t.name}`, () => { selectTheme(t.id); renderThemes(); }, { swatch: t.accent, current: t.id === currentThemeId });
  }
  for (const [id, name] of BACKGROUNDS) {
    add('Design', `Background: ${name}`, async () => { applyBackground(id); await api.setSettings({ background: id }); },
      { image: `assets/backgrounds/${id}.jpg`, current: id === currentBackground });
  }
  for (const [id, name] of SKIES) {
    add('In-game sky', `Day sky: ${name}`, () => saveSky({ daySky: name, enabled: true }), { image: `assets/skies/${id}.jpg`, current: sky.enabled && sky.daySky === name });
    add('In-game sky', `Night sky: ${name}`, () => saveSky({ nightSky: name, enabled: true }), { image: `assets/skies/${id}.jpg`, current: sky.enabled && sky.nightSky === name });
  }
  add('In-game sky', sky.enabled ? 'Turn custom sky off' : 'Turn custom sky on', () => saveSky({ enabled: !sky.enabled }), { icon: 'refresh' });

  const toggle = (title, id) => add('Settings', title, () => { const box = $(id); box.checked = !box.checked; box.dispatchEvent(new Event('change')); },
    { icon: 'settings', sub: $(id) && $(id).checked ? 'On – run to turn off' : 'Off – run to turn on' });
  toggle('Fast start', '#set-fast');
  toggle('Hide while playing', '#set-hide');

  add('Folders', 'Open mods folder', () => api.openFolder('mods'), { icon: 'folder' });
  add('Folders', 'Open screenshots', () => api.openFolder('screenshots'), { icon: 'folder' });
  add('Folders', 'Open profile folder', () => api.openFolder('profile'), { icon: 'folder' });
  return cmds;
}

/** Subsequence match with a bonus for word starts and runs; returns -1 when the query does not match. */
function fuzzy(text, query) {
  if (!query) return 0;
  const t = text.toLowerCase();
  const q = query.toLowerCase();
  let score = 0;
  let ti = 0;
  let run = 0;
  for (const ch of q) {
    const found = t.indexOf(ch, ti);
    if (found < 0) return -1;
    run = found === ti ? run + 1 : 0;
    score += 1 + run * 2 + (found === 0 || /[\s:]/.test(t[found - 1]) ? 4 : 0) - Math.min(3, found - ti) * 0.3;
    ti = found + 1;
  }
  return score + (t.startsWith(q) ? 8 : 0);
}

const palette = (() => {
  const input = el('input', { class: 'palette-input', placeholder: 'Type a command or search…', spellcheck: 'false' });
  const list = el('div', { class: 'palette-list', role: 'listbox' });
  const foot = el('div', { class: 'palette-foot' }, [
    el('span', {}, [el('kbd', { text: '↑' }), el('kbd', { text: '↓' }), ' move']),
    el('span', {}, [el('kbd', { text: 'Enter' }), ' run']),
    el('span', {}, [el('kbd', { text: 'Esc' }), ' close']),
    el('span', { class: 'grow' }),
    el('span', {}, [el('kbd', { text: `${MOD} K` }), ' anywhere']),
  ]);
  const box = el('div', { class: 'palette' }, [el('div', { class: 'palette-head' }, [icon('search'), input]), list, foot]);
  const backdrop = el('div', { class: 'palette-backdrop' }, [box]);
  document.body.append(backdrop);

  let items = [];
  let index = 0;

  function render() {
    const q = input.value.trim();
    const scored = paletteCommands()
      .map((c, order) => ({ c, order, s: fuzzy(`${c.group}: ${c.title}`, q) }))
      .filter((x) => x.s >= 0);
    // without a query keep the natural order (and hide the long sky list); with one, best match first
    items = q ? scored.sort((a, b) => b.s - a.s || a.order - b.order).slice(0, 40).map((x) => x.c)
      : scored.filter((x) => x.c.group !== 'In-game sky' && x.c.group !== 'Design').map((x) => x.c)
        .concat(scored.filter((x) => x.c.group === 'Design' && x.c.title.startsWith('Theme')).map((x) => x.c));
    index = Math.min(index, Math.max(0, items.length - 1));
    let lastGroup = null;
    const rows = [];
    items.forEach((c, i) => {
      if (!q && c.group !== lastGroup) rows.push(el('div', { class: 'palette-group', text: c.group }));
      lastGroup = c.group;
      const lead = c.image ? el('img', { class: 'palette-thumb', src: c.image, alt: '' })
        : c.swatch ? el('span', { class: 'palette-swatch', style: `background:${c.swatch}` })
        : c.icon ? icon(c.icon) : el('span');
      rows.push(el('div', {
        class: `palette-item${i === index ? ' active' : ''}${c.current ? ' current' : ''}`,
        role: 'option',
        onmousemove: () => { if (index !== i) { index = i; highlight(); } },
        onclick: () => run(i),
      }, [
        lead,
        el('span', { class: 't' }, [el('b', { text: c.title }), c.sub ? el('span', { text: c.sub }) : null]),
        c.current ? el('em', { text: 'Current' }) : null,
        c.keys ? el('kbd', { text: c.keys }) : null,
      ]));
    });
    if (!items.length) rows.push(el('div', { class: 'palette-empty', text: `Nothing matches "${q}"` }));
    list.replaceChildren(...rows);
    highlight();
  }

  function highlight() {
    const nodes = $$('.palette-item', list);
    nodes.forEach((n, i) => n.classList.toggle('active', i === index));
    if (nodes[index]) nodes[index].scrollIntoView({ block: 'nearest' });
  }

  function run(i) {
    const c = items[i];
    if (!c) return;
    close();
    Promise.resolve(c.run()).catch((err) => toast(String(err && err.message ? err.message : err), true));
  }

  function open() {
    input.value = '';
    index = 0;
    render();
    backdrop.classList.add('open');
    requestAnimationFrame(() => input.focus());
  }

  function close() {
    backdrop.classList.remove('open');
  }

  input.addEventListener('input', () => { index = 0; render(); });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown') { index = Math.min(items.length - 1, index + 1); highlight(); e.preventDefault(); }
    else if (e.key === 'ArrowUp') { index = Math.max(0, index - 1); highlight(); e.preventDefault(); }
    else if (e.key === 'Enter') { run(index); e.preventDefault(); }
    else if (e.key === 'Escape') { close(); e.stopPropagation(); }
  });
  backdrop.addEventListener('mousedown', (e) => { if (e.target === backdrop) close(); });
  return { open, close, isOpen: () => backdrop.classList.contains('open') };
})();

$('#btn-palette').addEventListener('click', () => palette.open());
$('#btn-palette kbd').textContent = `${MOD} K`;

// ---------- global shortcuts
document.addEventListener('keydown', (e) => {
  const mod = isMac ? e.metaKey : e.ctrlKey;
  if (!mod) return;
  const k = e.key.toLowerCase();
  if (k === 'k' || k === 'p') {
    e.preventDefault();
    if (palette.isOpen()) palette.close(); else palette.open();
  } else if (k === 'enter') {
    e.preventDefault();
    if (!playBtn.disabled) playBtn.click();
  } else if (/^[1-8]$/.test(k)) {
    e.preventDefault();
    palette.close();
    showView(PAGE_ORDER[Number(k) - 1]);
  } else if (k === ',') {
    e.preventDefault();
    showView('settings');
  }
});
