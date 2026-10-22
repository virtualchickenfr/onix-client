'use strict';

/* Import from other clients and launchers: scan, filter, drop folders or modpacks, choose the Minecraft version,
   see which mods come along (and which are updated to that version through Modrinth), then import. */

let importScanned = false;
let importSources = [];
let importing = null;
let planSeq = 0;

function sourceChips(s) {
  const chips = [];
  if (s.options) chips.push('Settings & keys');
  if (s.servers) chips.push('Servers');
  if (s.worlds) chips.push(plural(s.worlds, 'world', 'worlds'));
  if (s.resourcepacks) chips.push(plural(s.resourcepacks, 'resource pack', 'resource packs'));
  if (s.shaderpacks) chips.push(plural(s.shaderpacks, 'shader', 'shaders'));
  if (s.screenshots) chips.push(plural(s.screenshots, 'screenshot', 'screenshots'));
  const mods = Math.max(s.mods.length, s.modsTotal || 0);
  if (mods) chips.push(plural(mods, 'mod', 'mods'));
  if (s.dawnpacks) chips.push(plural(s.dawnpacks, '.dawnpack', '.dawnpacks'));
  const modules = Object.keys(s.modules).length;
  if (modules) chips.push(plural(modules, 'client module', 'client modules'));
  return chips;
}

function playedAgo(ms) {
  if (!ms) return '';
  const min = Math.round((Date.now() - ms) / 60000);
  if (min < 60) return 'played just now';
  const h = Math.round(min / 60);
  if (h < 24) return `played ${h} h ago`;
  const d = Math.round(h / 24);
  if (d < 60) return `played ${d} ${d === 1 ? 'day' : 'days'} ago`;
  return `played ${Math.round(d / 30)} months ago`;
}

function matchesFilter(s, q) {
  if (!q) return true;
  const hay = [s.name, s.label, s.client, s.mcVersion || '', ...(s.mods || [])].join(' ').toLowerCase();
  return q.toLowerCase().split(/\s+/).every((w) => hay.includes(w));
}

function renderSources() {
  const grid = $('#source-grid');
  const q = $('#im-search').value.trim();
  const shown = importSources.filter((s) => matchesFilter(s, q));
  $('#im-count').textContent = importSources.length ? `${shown.length} of ${plural(importSources.length, 'source', 'sources')}` : '';
  if (!importSources.length) {
    grid.replaceChildren(el('div', { class: 'empty', style: 'grid-column: 1 / -1' }, [
      el('b', { text: 'No other clients found' }),
      el('span', { text: 'Choose a folder, or drop your .minecraft folder, an instance folder or a modpack file here.' }),
    ]));
    return;
  }
  if (!shown.length) {
    grid.replaceChildren(el('div', { class: 'empty', style: 'grid-column: 1 / -1' }, [el('b', { text: 'Nothing matches' }), el('span', { text: 'Try another word.' })]));
    return;
  }
  grid.replaceChildren(...shown.map((s, i) => el('button', { class: 'source', style: `--i:${i % 20}`, onclick: () => openImport(s) }, [
    el('div', { class: 'source-top' }, [
      el('div', { class: 'avatar', style: `--av:${CLIENT_COLORS[s.client] || '#333'}`, text: s.name.charAt(0) }),
      el('div', { class: 'source-name' }, [
        el('b', { text: s.name }),
        el('span', { class: 'mono', text: s.label, title: s.label }),
      ]),
      i === 0 && s.lastUsed ? el('span', { class: 'tag', text: 'Last played' }) : null,
    ]),
    el('div', { class: 'source-meta' }, [
      s.mcVersion ? el('span', { text: `Minecraft ${s.mcVersion}` }) : null,
      s.lastUsed ? el('span', { text: playedAgo(s.lastUsed) }) : null,
    ]),
    el('div', { class: 'chips' }, sourceChips(s).map((c) => el('span', { class: 'chip', text: c }))),
    el('div', { class: 'cta' }, [el('span', { text: 'Import' }), icon('arrow')]),
  ])));
}

async function scanImports() {
  importScanned = true;
  $('#source-grid').replaceChildren(
    el('div', { class: 'loading-block', style: 'grid-column: 1 / -1' }, [gem(), el('span', { text: 'Looking for Lunar, Feather, Dawn, Badlion, LabyMod, NoRisk and launcher instances…' })]),
    el('div', { class: 'skeleton' }), el('div', { class: 'skeleton' }));
  const res = await api.detectImports();
  if (!res.ok) {
    toast(res.error, true);
    importSources = [];
  } else {
    importSources = res.value;
  }
  renderSources();
}

function addSources(list) {
  const ids = new Set(list.map((s) => s.id));
  importSources = [...list, ...importSources.filter((s) => !ids.has(s.id))];
  renderSources();
  if (list.length === 1) openImport(list[0]);
  else if (list.length > 1) toast(`${plural(list.length, 'source', 'sources')} found`);
}

$('#btn-import-scan').addEventListener('click', scanImports);
$('#im-search').addEventListener('input', renderSources);
$('#btn-import-folder').addEventListener('click', async () => {
  const res = await api.pickImportFolder();
  if (!res.ok) return toast(res.error, true);
  if (res.value.length) addSources(res.value);
});
$('#btn-import-file').addEventListener('click', async () => {
  const res = await api.importProfile();
  if (!res.ok) toast(res.error, true);
  else if (res.value) afterImport(res.value);
});

/** Folders dropped while the Import page is open (modpack files are handled by the window-wide drop). */
async function importDropped(paths) {
  $('#im-drop').classList.add('busy');
  const res = await api.dropImport(paths);
  $('#im-drop').classList.remove('busy');
  if (!res.ok) return toast(res.error, true);
  if (!res.value.length) return toast('No Minecraft data found in what you dropped.', true);
  addSources(res.value);
}

// ------------------------------------------------------------------ the import dialog

function versionOptions(selected) {
  const list = (info.versions && info.versions.length ? info.versions : [{ id: selected, onix: true }]);
  const onix = list.filter((v) => v.onix);
  const other = list.filter((v) => !v.onix).slice(0, 40);
  const opt = (v) => el('option', { value: v.id, text: `Minecraft ${v.id}${v.onix ? '' : ' (without Onix)'}`, selected: v.id === selected });
  return [el('optgroup', { label: 'With Onix' }, onix.map(opt)), other.length ? el('optgroup', { label: 'Other versions' }, other.map(opt)) : null].filter(Boolean);
}

function importTarget() {
  const toNew = document.querySelector('input[name=im-target]:checked').value === 'new';
  return toNew ? { newName: $('#im-new-name').value.trim() || importing.name, mc: $('#im-mc').value } : { profileId: $('#im-existing').value };
}

/** Shows which mods load as they are, which get the version for the chosen Minecraft, and which have none. */
async function refreshPlan() {
  const s = importing;
  const box = $('#im-plan');
  if (!s || !(s.modsTotal || s.mods.length)) {
    box.replaceChildren();
    return;
  }
  const seq = ++planSeq;
  box.replaceChildren(el('div', { class: 'im-plan-row' }, [gem(), el('span', { text: 'Checking your mods on Modrinth…' })]));
  const res = await api.planImport(s.id, importTarget());
  if (seq !== planSeq) return;
  if (!res.ok) {
    box.replaceChildren(el('div', { class: 'im-plan-row warn', text: res.error }));
    return;
  }
  const p = res.value;
  const part = (cls, count, label, names) => (count ? el('details', { class: `im-plan-part ${cls}` }, [
    el('summary', {}, [el('b', { text: String(count) }), el('span', { text: label })]),
    el('div', { class: 'im-plan-list' }, names.map((n) => el('span', { text: n }))),
  ]) : null);
  box.replaceChildren(
    el('div', { class: 'im-plan-head', text: `Mods on Minecraft ${p.mc}` }),
    el('div', { class: 'im-plan-parts' }, [
      part('ok', p.ready.length, 'work as they are', p.ready),
      part('up', p.update.length, `updated to ${p.mc} from Modrinth`, p.update.map((u) => `${u.name} → ${u.to}`)),
      part('miss', p.missing.length, p.offline ? 'not checked (offline)' : `have no version for ${p.mc}`, p.missing.map((m) => m.name)),
    ].filter(Boolean)),
  );
  if (!p.ready.length && !p.update.length && !p.missing.length) box.replaceChildren();
}

function openImport(s) {
  importing = s;
  $('#im-avatar').style.setProperty('--av', CLIENT_COLORS[s.client] || '#333');
  $('#im-avatar').textContent = s.name.charAt(0);
  $('#im-title').textContent = `Import from ${s.name}`;
  $('#im-path').textContent = `${s.label}${s.mcVersion ? ` · Minecraft ${s.mcVersion}` : ''}`;
  const modules = Object.entries(s.modules);
  const onModules = modules.filter(([, v]) => v).map(([k]) => MODULE_NAMES[k] || k);
  const modsCount = Math.max(s.mods.length, s.modsTotal || 0);
  const options = [
    ['options', 'Settings & key binds', s.options ? 'Video, sound, controls' : 'Not found', s.options],
    ['servers', 'Server list', s.servers ? 'Every saved server' : 'Not found', s.servers],
    ['worlds', 'Worlds', s.worlds ? plural(s.worlds, 'world', 'worlds') + (s.newerWorlds ? ` · made in ${s.mcVersion}, may not open in older versions` : '') : 'None', s.worlds > 0],
    ['resourcepacks', 'Resource packs', s.resourcepacks ? String(s.resourcepacks) : 'None', s.resourcepacks > 0],
    ['shaderpacks', 'Shader packs', s.shaderpacks ? String(s.shaderpacks) : 'None', s.shaderpacks > 0],
    ['screenshots', 'Screenshots', s.screenshots ? String(s.screenshots) : 'None', s.screenshots > 0],
    ['mods', 'Mods', modsCount ? `${plural(modsCount, 'mod', 'mods')} – see below what fits` : 'None', modsCount > 0],
    ['modConfigs', 'Mod settings', s.modConfigs ? 'Config files of your mods' : 'None', s.modConfigs],
    ['modules', 'Client modules (beta)', modules.length ? (onModules.slice(0, 4).join(', ') + (onModules.length > 4 ? ` +${onModules.length - 4}` : '')) || 'All off' : 'None detected', modules.length > 0],
  ];
  // only what was found is offered; the rest is named in one line
  const missingParts = options.filter((o) => !o[3]).map((o) => o[1]);
  $('#im-what').replaceChildren(...options.filter((o) => o[3]).map(([key, label, sub]) => {
    const input = el('input', { type: 'checkbox', 'data-key': key });
    input.checked = key !== 'screenshots' && !(key === 'worlds' && s.newerWorlds);
    if (key === 'mods') input.addEventListener('change', refreshPlan);
    return el('label', { class: 'check' }, [
      input, el('div', { class: 't' }, [el('b', { text: label }), el('span', { text: sub })]),
    ]);
  }), missingParts.length ? el('div', { class: 'im-notfound', text: `Not found there: ${missingParts.join(', ')}` }) : null);
  $('#im-new-name').value = `${s.profileName || s.name}`.slice(0, 24);
  $('#im-mc').replaceChildren(...versionOptions(s.targetVersion));
  $('#im-existing').replaceChildren(...profileState.profiles.map((p) => el('option', { value: p.id, text: `${p.name} · ${mcOfProfile(p)}`, selected: p.id === profileState.active })));
  document.querySelector('input[name=im-target][value=new]').checked = true;
  $('#im-form').hidden = false;
  $('#im-progress').hidden = true;
  $('#im-finish').hidden = true;
  $('#im-done').replaceChildren();
  openModal('#import-modal');
  refreshPlan();
}

$('#im-new-name').addEventListener('focus', () => { document.querySelector('input[name=im-target][value=new]').checked = true; });
$('#im-mc').addEventListener('change', () => { document.querySelector('input[name=im-target][value=new]').checked = true; refreshPlan(); });
$('#im-existing').addEventListener('focus', () => { document.querySelector('input[name=im-target][value=existing]').checked = true; });
$('#im-existing').addEventListener('change', () => { document.querySelector('input[name=im-target][value=existing]').checked = true; refreshPlan(); });
$$('input[name=im-target]').forEach((r) => r.addEventListener('change', refreshPlan));

$('#im-run').addEventListener('click', async () => {
  if (!importing) return;
  const what = {};
  $$('#im-what input').forEach((i) => { what[i.dataset.key] = i.checked && !i.disabled; });
  if (!Object.values(what).some(Boolean)) return toast('Select at least one thing to import.', true);
  what.updateMods = true;
  const target = importTarget();
  $('#im-form').hidden = true;
  $('#im-progress').hidden = false;
  $('#im-status').textContent = 'Importing…';
  $('#im-gem').replaceChildren(gem());
  $('#im-bar').style.width = '0%';
  const res = await api.runImport(importing.id, target, what);
  if (!res.ok) {
    $('#im-status').textContent = 'Import failed';
    $('#im-gem').replaceChildren(icon('x'));
    $('#im-done').replaceChildren(el('li', { text: res.error }));
    $('#im-finish').hidden = false;
    return;
  }
  profileState = res.value.state;
  refreshProfiles();
  $('#im-status').textContent = `Done. Everything is in "${res.value.profile}"`;
  $('#im-bar').style.width = '100%';
  $('#im-gem').replaceChildren(icon('check'));
  $('#im-done').replaceChildren(...res.value.done.map((d) => el('li', {}, [icon(/^Could not/.test(d) ? 'x' : 'check'), d])));
  $('#im-finish').hidden = false;
});

api.onImportProgress(({ label, progress }) => {
  $('#im-status').textContent = label;
  $('#im-bar').style.width = `${Math.round(progress * 100)}%`;
});

$('#im-play').addEventListener('click', () => { closeModal('#import-modal'); showView('home'); });
