'use strict';

/* Import from other clients and launchers. */

let importScanned = false;
let importSources = [];
let importing = null;

function sourceChips(s) {
  const chips = [];
  if (s.options) chips.push('Settings & keys');
  if (s.servers) chips.push('Servers');
  if (s.worlds) chips.push(plural(s.worlds, 'world', 'worlds'));
  if (s.resourcepacks) chips.push(plural(s.resourcepacks, 'resource pack', 'resource packs'));
  if (s.shaderpacks) chips.push(plural(s.shaderpacks, 'shader', 'shaders'));
  if (s.screenshots) chips.push(plural(s.screenshots, 'screenshot', 'screenshots'));
  if (s.mods.length) chips.push(plural(s.mods.length, 'mod', 'mods'));
  if (s.dawnpacks) chips.push(plural(s.dawnpacks, '.dawnpack', '.dawnpacks'));
  const modules = Object.keys(s.modules).length;
  if (modules) chips.push(plural(modules, 'client module', 'client modules'));
  return chips;
}

function renderSources() {
  const grid = $('#source-grid');
  if (!importSources.length) {
    grid.replaceChildren(el('div', { class: 'empty', style: 'grid-column: 1 / -1' }, [
      el('b', { text: 'No other clients found' }),
      el('span', { text: 'Choose the folder yourself, for example your .minecraft folder or a Prism instance.' }),
    ]));
    return;
  }
  grid.replaceChildren(...importSources.map((s) => el('button', { class: 'source', onclick: () => openImport(s) }, [
    el('div', { class: 'source-top' }, [
      el('div', { class: 'avatar', style: `--av:${CLIENT_COLORS[s.client] || '#333'}`, text: s.name.charAt(0) }),
      el('div', {}, [el('b', { text: s.client === 'custom' || ['prism', 'multimc', 'modrinth', 'curseforge', 'atlauncher'].includes(s.client) ? `${s.name}` : s.name }), el('span', { class: 'mono', text: s.label })]),
    ]),
    el('div', { class: 'chips' }, sourceChips(s).map((c) => el('span', { class: 'chip', text: c })).concat(
      s.modsSkipped.length ? [el('span', { class: 'chip dim', text: `${s.modsSkipped.length} incompatible` })] : [])),
    el('div', { class: 'cta' }, [el('span', { text: 'Import' }), icon('arrow')]),
  ])));
}

async function scanImports() {
  importScanned = true;
  $('#source-grid').replaceChildren(
    el('div', { class: 'loading-block', style: 'grid-column: 1 / -1' }, [gem(), el('span', { text: 'Looking for Lunar, Feather, Dawn, Badlion, LabyMod and launcher instances…' })]),
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

$('#btn-import-scan').addEventListener('click', scanImports);
$('#btn-import-folder').addEventListener('click', async () => {
  const res = await api.pickImportFolder();
  if (!res.ok) return toast(res.error, true);
  if (!res.value.length) return;
  const ids = new Set(importSources.map((s) => s.id));
  importSources = [...res.value.filter((s) => !ids.has(s.id)), ...importSources];
  renderSources();
  if (res.value.length === 1) openImport(res.value[0]);
});

function openImport(s) {
  importing = s;
  $('#im-avatar').style.setProperty('--av', CLIENT_COLORS[s.client] || '#333');
  $('#im-avatar').textContent = s.name.charAt(0);
  $('#im-title').textContent = `Import from ${s.name}`;
  $('#im-path').textContent = s.label;
  const modules = Object.entries(s.modules);
  const onModules = modules.filter(([, v]) => v).map(([k]) => MODULE_NAMES[k] || k);
  const options = [
    ['options', 'Settings & key binds', s.options ? 'Video, sound, controls' : 'Not found', s.options],
    ['servers', 'Server list', s.servers ? 'Every saved server' : 'Not found', s.servers],
    ['worlds', 'Worlds', s.worlds ? plural(s.worlds, 'world', 'worlds') : 'None', s.worlds > 0],
    ['resourcepacks', 'Resource packs', s.resourcepacks ? String(s.resourcepacks) : 'None', s.resourcepacks > 0],
    ['shaderpacks', 'Shader packs', s.shaderpacks ? String(s.shaderpacks) : 'None', s.shaderpacks > 0],
    ['screenshots', 'Screenshots', s.screenshots ? String(s.screenshots) : 'None', s.screenshots > 0],
    ['mods', 'Mods', s.mods.length ? `${s.mods.length} compatible${s.modsSkipped.length ? `, ${s.modsSkipped.length} skipped` : ''}` : 'None compatible', s.mods.length > 0],
    ['modConfigs', 'Mod settings', s.modConfigs ? 'Config files of your mods' : 'None', s.modConfigs],
    ['modules', 'Client modules (beta)', modules.length ? (onModules.slice(0, 4).join(', ') + (onModules.length > 4 ? ` +${onModules.length - 4}` : '')) || 'All off' : 'None detected', modules.length > 0],
  ];
  $('#im-what').replaceChildren(...options.map(([key, label, sub, available]) => {
    const input = el('input', { type: 'checkbox', 'data-key': key });
    input.checked = available && key !== 'screenshots';
    input.disabled = !available;
    return el('label', { class: `check${available ? '' : ' disabled'}`, title: key === 'mods' && s.modsSkipped.length ? s.modsSkipped.join('\n') : null }, [
      input, el('div', { class: 't' }, [el('b', { text: label }), el('span', { text: sub })]),
    ]);
  }));
  $('#im-new-name').value = `${s.name}`.slice(0, 24);
  $('#im-existing').replaceChildren(...profileState.profiles.map((p) => el('option', { value: p.id, text: p.name, selected: p.id === profileState.active })));
  document.querySelector('input[name=im-target][value=new]').checked = true;
  $('#im-form').hidden = false;
  $('#im-progress').hidden = true;
  $('#im-finish').hidden = true;
  $('#im-done').replaceChildren();
  openModal('#import-modal');
}

$('#im-new-name').addEventListener('focus', () => { document.querySelector('input[name=im-target][value=new]').checked = true; });
$('#im-existing').addEventListener('focus', () => { document.querySelector('input[name=im-target][value=existing]').checked = true; });

$('#im-run').addEventListener('click', async () => {
  if (!importing) return;
  const what = {};
  $$('#im-what input').forEach((i) => { what[i.dataset.key] = i.checked && !i.disabled; });
  if (!Object.values(what).some(Boolean)) return toast('Select at least one thing to import.', true);
  const toNew = document.querySelector('input[name=im-target]:checked').value === 'new';
  const target = toNew ? { newName: $('#im-new-name').value.trim() || importing.name } : { profileId: $('#im-existing').value };
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
  $('#im-done').replaceChildren(...res.value.done.map((d) => el('li', {}, [icon('check'), d])));
  $('#im-finish').hidden = false;
});

api.onImportProgress(({ label, progress }) => {
  $('#im-status').textContent = label;
  $('#im-bar').style.width = `${Math.round(progress * 100)}%`;
});

$('#im-play').addEventListener('click', () => { closeModal('#import-modal'); showView('home'); });
