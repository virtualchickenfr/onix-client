'use strict';

/* Mods of a profile (installed list + Modrinth browser), Modrinth modpacks, and exporting /
   importing profiles as Modrinth or CurseForge packs or straight into another launcher. */

const mm = {
  profileId: null,
  mode: 'mods', // 'mods' or 'modpacks'
  tab: 'installed',
  sort: 'relevance',
  offset: 0,
  total: 0,
  hits: [],
  installed: new Set(),
  busy: new Set(),
  updates: [],
  confirmRemove: null,
  seq: 0,
};

const fmtCount = (n) => (n >= 1e6 ? `${(n / 1e6).toFixed(1)}M` : n >= 1e3 ? `${Math.round(n / 1e3)}k` : String(n || 0));
const profileOf = (id) => profileState.profiles.find((p) => p.id === id) || activeProfile();

function modIcon(src, name) {
  const box = el('div', { class: 'ticon', text: (name || '?').charAt(0).toUpperCase() });
  if (src) {
    const img = el('img', { src, alt: '', loading: 'lazy' });
    img.onerror = () => img.remove();
    box.append(img);
  }
  return box;
}

// ---------------------------------------------------------------- progress (shared by every action here)

api.onModsProgress((p) => {
  const label = p && p.label ? p.label : '';
  $('#mm-status').textContent = label;
  $('#mm-bar').style.width = `${Math.round(Math.max(0, Math.min(1, (p && p.progress) || 0)) * 100)}%`;
  $('#ex-status').textContent = label;
});

function setBusyStatus(text) {
  $('#mm-status').textContent = text || '';
  if (!text) $('#mm-bar').style.width = '0%';
}

// ---------------------------------------------------------------- dialog

function openMods(profileId, tab = 'installed') {
  mm.mode = 'mods';
  mm.profileId = profileId;
  mm.updates = [];
  mm.confirmRemove = null;
  const p = profileOf(profileId);
  $('#mm-title').textContent = `Mods · ${p.name}`;
  $('#mm-sub').textContent = `Fabric mods for Minecraft ${mcOfProfile(p)}`;
  $('#mm-dot').style.background = p.color;
  $('#mm-tabs').hidden = false;
  $('#mm-query').placeholder = `Search Fabric ${mcOfProfile(p)} mods…`;
  setBusyStatus('');
  openModal('#mods-modal');
  showTab(tab);
}

function openModpacks() {
  mm.mode = 'modpacks';
  mm.profileId = null;
  $('#mm-title').textContent = 'Modpacks from Modrinth';
  $('#mm-sub').textContent = `Fabric ${((info && info.versions) || []).map((v) => v.id).join(' and ') || '1.21.1'} packs – each one becomes a new profile with Onix`;
  $('#mm-dot').style.background = 'var(--accent)';
  $('#mm-tabs').hidden = true;
  $('#mm-query').placeholder = 'Search modpacks…';
  setBusyStatus('');
  openModal('#mods-modal');
  showTab('browse');
}

/** Resource packs or shaders from Modrinth for one profile ('resourcepack' or 'shader'). */
function openAssets(profileId, kind) {
  mm.mode = kind;
  mm.profileId = profileId;
  const p = profileOf(profileId);
  const shader = kind === 'shader';
  $('#mm-title').textContent = `${shader ? 'Shaders' : 'Resource Packs'} · ${p.name}`;
  $('#mm-sub').textContent = shader
    ? `Shader packs for Minecraft ${mcOfProfile(p)} – Iris is added to the profile with the first one`
    : `Resource packs for Minecraft ${mcOfProfile(p)} – switch them on in Options › Resource Packs`;
  $('#mm-dot').style.background = p.color;
  $('#mm-tabs').hidden = true;
  $('#mm-query').placeholder = shader ? 'Search shaders…' : 'Search resource packs…';
  $('#mm-query').value = '';
  setBusyStatus('');
  openModal('#mods-modal');
  showTab('browse');
}

const isAssetMode = () => mm.mode === 'resourcepack' || mm.mode === 'shader';

function showTab(tab) {
  mm.tab = tab;
  $$('#mm-tabs button').forEach((b) => b.classList.toggle('active', b.dataset.tab === tab));
  $('#mm-tools-installed').hidden = tab !== 'installed';
  $('#mm-tools-browse').hidden = tab !== 'browse';
  if (tab === 'installed') renderInstalled();
  else {
    search(true);
    setTimeout(() => $('#mm-query').focus(), 50);
  }
}

$$('#mm-tabs button').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tab)));

/** Keeps the Mods page in sync when the dialog changed the active profile's mods. */
function modsChanged() {
  if (currentView === 'mods' && mm.profileId === profileState.active) renderMods();
  if (typeof profileViewChanged === 'function') profileViewChanged(mm.profileId);
}

// ---------------------------------------------------------------- installed

async function renderInstalled() {
  const body = $('#mm-body');
  if (!body.querySelector('.trow')) body.replaceChildren(loadingBlock('Reading mods…'));
  const mods = await api.listMods(mm.profileId);
  $('#mm-count').textContent = mods.length ? String(mods.length) : '';
  if (!mods.length) {
    body.replaceChildren(el('div', { class: 'empty' }, [
      el('b', { text: 'No mods yet' }),
      el('span', { text: 'Onix, Fabric API and the performance pack are added on the first launch. Find more under Browse Modrinth.' }),
      el('button', { class: 'btn sm primary', text: 'Browse Modrinth', onclick: () => showTab('browse') }),
    ]));
    return;
  }
  const updates = new Map(mm.updates.map((u) => [u.file.replace(/\.disabled$/, ''), u]));
  body.replaceChildren(el('div', { class: 'table mm-table' }, mods.map((mod) => {
    const m = mod.modrinth;
    const update = updates.get(mod.file.replace(/\.disabled$/, ''));
    const toggle = el('input', { type: 'checkbox' });
    toggle.checked = mod.enabled;
    toggle.disabled = mod.core;
    toggle.addEventListener('change', async () => {
      const res = await api.toggleMod(mod.file, mm.profileId);
      if (!res.ok) toast(res.error, true);
      renderInstalled();
      modsChanged();
    });
    const confirming = mm.confirmRemove === mod.file;
    const remove = mod.core ? null : el('button', {
      class: `icon-btn mm-remove${confirming ? ' confirm' : ''}`,
      title: confirming ? 'Click again to delete the file' : 'Remove',
      onclick: async () => {
        if (!confirming) {
          mm.confirmRemove = mod.file;
          renderInstalled();
          setTimeout(() => { if (mm.confirmRemove === mod.file) { mm.confirmRemove = null; renderInstalled(); } }, 3000);
          return;
        }
        mm.confirmRemove = null;
        const res = await api.removeMod(mm.profileId, mod.file);
        if (!res.ok) toast(res.error, true);
        else toast(`${m ? m.title : mod.name} removed`);
        renderInstalled();
        modsChanged();
      },
    }, confirming ? [el('span', { text: 'Remove?' })] : [icon('x')]);
    const sub = mod.core ? 'Needed by Onix' : mod.managed ? 'Kept up to date by the launcher' : m ? `Modrinth · ${m.version || ''}` : 'Your file';
    return el('div', { class: `trow${mod.enabled ? '' : ' off'}` }, [
      modIcon(m && m.icon, m ? m.title : mod.name),
      el('div', { class: 'tmain' }, [el('b', { text: m ? m.title : mod.name }), el('span', { text: sub })]),
      update ? el('button', { class: 'btn sm update', onclick: () => runUpdates([update.projectId]) }, [icon('download'), `Update to ${update.version}`]) : null,
      mod.core ? el('span', { class: 'tag', text: 'Required' }) : null,
      remove,
      el('label', { class: 'switch' }, [toggle, el('i')]),
    ]);
  })));
  renderUpdateButton();
}

function renderUpdateButton() {
  const box = $('#mm-tools-installed');
  const n = mm.updates.length;
  box.replaceChildren(n
    ? el('button', { class: 'btn sm primary', onclick: () => runUpdates(null) }, [icon('download'), `Update all (${n})`])
    : el('button', { class: 'btn sm', onclick: checkUpdates }, [icon('refresh'), 'Check for updates']));
}

async function checkUpdates() {
  setBusyStatus('Asking Modrinth for updates…');
  const res = await api.modUpdates(mm.profileId);
  if (!res.ok) {
    setBusyStatus('');
    toast(res.error, true);
    return;
  }
  mm.updates = res.value;
  setBusyStatus(res.value.length ? `${plural(res.value.length, 'update', 'updates')} available` : 'Everything is up to date');
  renderInstalled();
}

async function runUpdates(projectIds) {
  setBusyStatus('Updating…');
  const res = await api.updateMods(mm.profileId, projectIds);
  if (!res.ok) {
    toast(res.error, true);
    setBusyStatus('');
    return;
  }
  toast(res.value.length ? `Updated ${res.value.join(', ')}` : 'Nothing to update');
  mm.updates = mm.updates.filter((u) => projectIds && !projectIds.includes(u.projectId));
  setBusyStatus('');
  renderInstalled();
  modsChanged();
}

// ---------------------------------------------------------------- browse Modrinth

let searchTimer;
$('#mm-query').addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => search(true), 280);
});
$('#mm-query').addEventListener('keydown', (e) => { if (e.key === 'Enter') { clearTimeout(searchTimer); search(true); } });
$('#mm-sort').addEventListener('change', () => { mm.sort = $('#mm-sort').value; search(true); });

async function search(reset) {
  const seq = ++mm.seq;
  const body = $('#mm-body');
  if (reset) {
    mm.offset = 0;
    mm.hits = [];
    body.replaceChildren(el('div', { class: 'mm-grid' }, [0, 1, 2, 3, 4, 5].map(() => el('div', { class: 'skeleton mm-skel' }))));
    if (mm.mode === 'mods') {
      const inst = await api.installedModrinth(mm.profileId);
      mm.installed = new Set(inst.ok ? inst.value : []);
    } else if (isAssetMode()) {
      const inst = await api.installedAssets(mm.profileId, mm.mode);
      mm.installed = new Set(inst.ok ? inst.value : []);
    }
  }
  const type = mm.mode === 'modpacks' ? 'modpack' : isAssetMode() ? mm.mode : 'mod';
  const res = await api.searchModrinth({ query: $('#mm-query').value.trim(), type, offset: mm.offset, sort: mm.sort, profileId: mm.profileId });
  if (seq !== mm.seq) return; // a newer search started meanwhile
  if (!res.ok) {
    body.replaceChildren(el('div', { class: 'empty' }, [
      el('b', { text: 'Modrinth could not be reached' }),
      el('span', { text: res.error }),
      el('button', { class: 'btn sm', text: 'Try again', onclick: () => search(true) }),
    ]));
    return;
  }
  mm.hits.push(...res.value.hits);
  mm.total = res.value.total;
  mm.offset = mm.hits.length;
  renderHits();
}

function renderHits() {
  const body = $('#mm-body');
  if (!mm.hits.length) {
    body.replaceChildren(el('div', { class: 'empty' }, [el('b', { text: 'Nothing found' }), el('span', { text: isAssetMode() ? 'Try another word – only packs for this Minecraft version are shown.' : 'Try another word – only Fabric projects for this Minecraft version are shown.' })]));
    return;
  }
  const keep = body.scrollTop;
  const cards = mm.hits.map((h) => el('div', { class: 'mm-card' }, [
    modIcon(h.icon, h.title),
    el('div', { class: 'mm-info' }, [
      el('div', { class: 'mm-title' }, [el('b', { text: h.title }), el('span', { text: `by ${h.author}` })]),
      el('p', { text: h.description }),
      el('div', { class: 'mm-meta' }, [
        el('span', {}, [icon('download'), fmtCount(h.downloads)]),
        ...h.categories.map((c) => el('span', { class: 'tag', text: c })),
      ]),
    ]),
    hitAction(h),
  ]));
  const more = mm.hits.length < mm.total
    ? el('button', { class: 'btn sm mm-more', text: `Load more (${mm.total - mm.hits.length} left)`, onclick: () => search(false) })
    : null;
  body.replaceChildren(el('div', { class: 'mm-grid' }, cards), more);
  body.scrollTop = keep;
}

function hitAction(h) {
  if (mm.mode === 'modpacks') {
    if (mm.busy.has(h.id)) return el('button', { class: 'btn sm', disabled: true }, [el('span', { class: 'ring' }), 'Installing…']);
    return el('button', { class: 'btn sm primary', onclick: () => installPack(h) }, [icon('plus'), 'New profile']);
  }
  if (isAssetMode()) {
    if (mm.busy.has(h.id)) return el('button', { class: 'btn sm', disabled: true }, [el('span', { class: 'ring' }), 'Installing…']);
    if (mm.installed.has(h.id)) return el('button', { class: 'btn sm', disabled: true }, [icon('check'), 'Installed']);
    return el('button', { class: 'btn sm primary', onclick: () => installAsset(h) }, [icon('download'), 'Install']);
  }
  if (h.managed) return el('span', { class: 'tag', text: 'Included' });
  if (mm.busy.has(h.id)) return el('button', { class: 'btn sm', disabled: true }, [el('span', { class: 'ring' }), 'Installing…']);
  if (mm.installed.has(h.id)) return el('button', { class: 'btn sm', disabled: true }, [icon('check'), 'Installed']);
  return el('button', { class: 'btn sm primary', onclick: () => installMod(h) }, [icon('download'), 'Install']);
}

async function installMod(h) {
  mm.busy.add(h.id);
  renderHits();
  const res = await api.installModrinth(mm.profileId, h.id);
  mm.busy.delete(h.id);
  setBusyStatus('');
  if (!res.ok) {
    toast(res.error, true);
  } else {
    const { installed, skipped, warnings } = res.value;
    const deps = installed.filter((i) => i.dependency).length;
    if (installed.length) toast(`${h.title} installed${deps ? ` + ${plural(deps, 'dependency', 'dependencies')}` : ''}`);
    else if (skipped.length) toast(skipped[0]);
    if (warnings.length) setTimeout(() => toast(warnings[0], true), 2600);
    const inst = await api.installedModrinth(mm.profileId);
    if (inst.ok) mm.installed = new Set(inst.value);
    modsChanged();
  }
  renderHits();
}

async function installAsset(h) {
  const kind = mm.mode;
  mm.busy.add(h.id);
  renderHits();
  const res = await api.installAsset(mm.profileId, kind, h.id);
  mm.busy.delete(h.id);
  setBusyStatus('');
  if (!res.ok) {
    toast(res.error, true);
  } else {
    const { installed, skipped, warnings } = res.value;
    const iris = installed.some((i) => i.dependency);
    if (installed.length) toast(`${h.title} installed${iris ? ' (with Iris)' : ''}`);
    else if (skipped.length) toast(skipped[0]);
    if (warnings.length) setTimeout(() => toast(warnings[0], true), 2600);
    mm.installed.add(h.id);
    if (typeof profileViewChanged === 'function') profileViewChanged(mm.profileId);
  }
  renderHits();
}

async function installPack(h) {
  mm.busy.add(h.id);
  renderHits();
  const res = await api.installModpack(h.id);
  mm.busy.delete(h.id);
  setBusyStatus('');
  renderHits();
  if (!res.ok) {
    toast(res.error, true);
    return;
  }
  afterImport(res.value);
  closeModal('#mods-modal');
}

/** Shared by "Import Pack" and Modrinth modpacks. */
function afterImport(result) {
  profileState = result.state;
  refreshProfiles();
  if (currentView !== 'profiles') showView('profiles');
  const missing = result.missing || [];
  toast(`Profile "${result.profile}" created · ${plural(result.downloaded, 'mod', 'mods')} downloaded`);
  if (missing.length) {
    setTimeout(() => toast(`${plural(missing.length, 'file', 'files')} could not be added: ${missing[0]}`, true), 2800);
    missing.forEach((m) => console.warn('Not imported:', m));
  }
}

$('#btn-modpacks').addEventListener('click', openModpacks);
$('#btn-mods-browse').addEventListener('click', () => openMods(profileState.active, 'browse'));
$('#btn-import-pack').addEventListener('click', async () => {
  const btn = $('#btn-import-pack');
  btn.disabled = true;
  const res = await api.importProfile();
  btn.disabled = false;
  if (!res.ok) toast(res.error, true);
  else if (res.value) afterImport(res.value);
});

// ---------------------------------------------------------------- export

const ex = { profileId: null, format: 'mrpack', targets: [], target: null, parts: null };

async function openExport(profile) {
  ex.profileId = profile.id;
  $('#ex-title').textContent = `Export "${profile.name}"`;
  $('#ex-name').value = profile.name;
  $('#ex-result').hidden = true;
  $('#ex-status').textContent = '';
  if (!ex.parts) ex.parts = await api.exportParts();
  $('#ex-parts').replaceChildren(...Object.entries(ex.parts).map(([key, part]) => el('label', { class: 'check' }, [
    el('input', { type: 'checkbox', 'data-part': key, checked: part.default }),
    el('span', { class: 't' }, [el('b', { text: part.label })]),
  ])));
  setExportFormat(ex.format);
  openModal('#export-modal');
}

function setExportFormat(format) {
  ex.format = format;
  $$('#ex-formats .ex-format').forEach((b) => b.classList.toggle('selected', b.dataset.format === format));
  $('#ex-file').hidden = format === 'launcher';
  $('#ex-launcher').hidden = format !== 'launcher';
  $('#ex-run').textContent = format === 'launcher' ? 'Copy' : 'Export';
  $('#ex-result').hidden = true;
  if (format === 'launcher') loadTargets();
}
$$('#ex-formats .ex-format').forEach((b) => b.addEventListener('click', () => setExportFormat(b.dataset.format)));

async function loadTargets() {
  const box = $('#ex-targets');
  box.replaceChildren(loadingBlock('Looking for other launchers…'));
  const res = await api.copyTargets();
  if (!res.ok) {
    box.replaceChildren(el('div', { class: 'empty', text: res.error }));
    return;
  }
  ex.targets = res.value;
  const usable = ex.targets.filter((t) => t.official || t.compatible);
  if (!ex.target || !usable.some((t) => t.id === ex.target)) ex.target = usable.length ? usable[0].id : null;
  if (!ex.targets.length) {
    box.replaceChildren(el('div', { class: 'empty' }, [el('b', { text: 'No other launchers found' }), el('span', { text: 'Use a Modrinth or CurseForge pack instead – every launcher can import those.' })]));
    return;
  }
  box.replaceChildren(...ex.targets.map((t) => {
    const ok = t.official || t.compatible;
    return el('label', { class: `check ex-target${ok ? '' : ' disabled'}` }, [
      el('input', { type: 'radio', name: 'ex-target', value: t.id, disabled: !ok, checked: t.id === ex.target, onchange: () => { ex.target = t.id; } }),
      el('span', { class: 't' }, [el('b', { text: `${t.name} · ${t.label}` }), el('span', { text: t.note || (t.onix ? 'Onix already installed – will be updated' : `Fabric ${t.minecraft || ''}`) })]),
    ]);
  }));
}

$('#ex-run').addEventListener('click', async () => {
  const btn = $('#ex-run');
  btn.disabled = true;
  const result = $('#ex-result');
  try {
    if (ex.format === 'launcher') {
      if (!ex.target) {
        toast('Pick an instance first', true);
        return;
      }
      const res = await api.copyProfileTo(ex.profileId, ex.target, { settings: $('#ex-settings').checked });
      if (!res.ok) return toast(res.error, true);
      result.replaceChildren(el('b', { text: 'Copied' }), el('ul', {}, res.value.map((d) => el('li', { text: d }))));
    } else {
      const parts = {};
      $$('#ex-parts input').forEach((i) => { parts[i.dataset.part] = i.checked; });
      const res = await api.exportProfile(ex.profileId, {
        format: ex.format,
        name: $('#ex-name').value.trim(),
        version: $('#ex-version').value.trim(),
        includeOnix: $('#ex-onix').checked,
        parts,
      });
      if (!res.ok) return toast(res.error, true);
      if (!res.value) return; // save dialog cancelled
      const v = res.value;
      const lines = [];
      if (v.format === 'mrpack') lines.push(`${plural(v.linked, 'mod', 'mods')} linked from Modrinth, ${v.packed} packed inside`);
      else lines.push(`${plural(v.packed, 'mod', 'mods')} packed inside`);
      lines.push(...v.notes);
      result.replaceChildren(el('b', { text: 'Saved' }), el('span', { class: 'mono', text: v.file }), el('ul', {}, lines.map((d) => el('li', { text: d }))));
    }
    result.hidden = false;
  } finally {
    btn.disabled = false;
    $('#ex-status').textContent = '';
  }
});
