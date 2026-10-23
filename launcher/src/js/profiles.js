'use strict';

/* Profiles page and the profile editor. */

let profileSort = 'played';
try { profileSort = localStorage.getItem('onix.profileSort') || 'played'; } catch { /* storage may be blocked */ }

function sortedProfiles() {
  const q = ($('#pf-search').value || '').trim().toLowerCase();
  const list = profileState.profiles.filter((p) => !q || p.name.toLowerCase().includes(q) || mcOfProfile(p).includes(q));
  const by = {
    played: (a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0) || a.name.localeCompare(b.name),
    name: (a, b) => a.name.localeCompare(b.name),
    version: (a, b) => mcOfProfile(b).localeCompare(mcOfProfile(a), undefined, { numeric: true }) || a.name.localeCompare(b.name),
    time: (a, b) => (b.playTimeMs || 0) - (a.playTimeMs || 0),
    created: (a, b) => (b.createdAt || 0) - (a.createdAt || 0),
  }[profileSort] || (() => 0);
  return list.sort(by);
}

function renderProfiles() {
  const grid = $('#profile-grid');
  $('#pf-sort').value = profileSort;
  const list = sortedProfiles();
  if (!list.length) {
    grid.replaceChildren(el('div', { class: 'empty' }, [el('b', { text: 'No profile matches' }), el('span', { text: 'Try another name or version.' })]));
    return;
  }
  grid.replaceChildren(...list.map((p) => {
    const active = p.id === profileState.active;
    const open = () => openProfile(p.id);
    const card = el('div', { class: `pcard${active ? ' active' : ''}`, style: `--pc:${p.color}`, tabindex: '0', role: 'button', 'aria-label': `Open ${p.name}` }, [
      el('div', { class: 'pcard-top' }, [
        el('div', { class: 'pcard-icon', text: p.name.charAt(0).toUpperCase() }),
        el('div', { class: 'pcard-head' }, [
          el('div', { class: 'pcard-name', text: p.name }),
          el('div', { class: 'pcard-sub', text: `${loaderName(p)} · Minecraft ${mcOfProfile(p)}` }),
        ]),
        active ? el('span', { class: 'badge', text: 'Active' }) : null,
      ]),
      el('div', { class: 'pcard-stats' }, [
        el('span', { title: 'Play time' }, [icon('clock'), fmtPlayTime(p.playTimeMs)]),
        el('span', { title: 'Last played' }, [icon('play'), fmtAgo(p.lastPlayed)]),
      ]),
      el('div', { class: 'pcard-actions' }, [
        el('button', {
          class: 'btn sm primary',
          onclick: async (e) => {
            e.stopPropagation();
            if (!account) return login();
            if (!active) {
              profileState = await api.selectProfile(p.id);
              refreshProfiles();
            }
            showView('home');
            $('#btn-play').click();
          },
        }, [icon('play'), 'Play']),
        active ? null : el('button', {
          class: 'btn sm',
          text: 'Select',
          onclick: async (e) => {
            e.stopPropagation();
            profileState = await api.selectProfile(p.id);
            refreshProfiles();
            toast(`${p.name} is now active`);
          },
        }),
        el('div', { class: 'spacer' }),
        el('button', { class: 'icon-btn', title: 'Edit profile', 'aria-label': 'Edit profile', onclick: (e) => { e.stopPropagation(); openProfileEditor(p); } }, [icon('pencil')]),
        el('button', { class: 'icon-btn', title: 'Open', 'aria-label': 'Open', onclick: (e) => { e.stopPropagation(); open(); } }, [icon('arrow')]),
      ]),
    ]);
    card.addEventListener('click', open);
    card.addEventListener('keydown', (e) => { if (e.key === 'Enter' && e.target === card) open(); });
    return card;
  }));
}

$('#pf-search').addEventListener('input', renderProfiles);
$('#pf-sort').addEventListener('change', () => {
  profileSort = $('#pf-sort').value;
  try { localStorage.setItem('onix.profileSort', profileSort); } catch { /* ignore */ }
  renderProfiles();
});

let editing = null;
let editColor = PROFILE_COLORS[0];
let editVersion = '1.21.1';
let editLoader = 'fabric';
let loaderList = [
  { id: 'fabric', name: 'Fabric', note: 'Onix + mods' },
  { id: 'quilt', name: 'Quilt', note: 'Fabric mods too' },
  { id: 'neoforge', name: 'NeoForge', note: 'NeoForge mods' },
  { id: 'forge', name: 'Forge', note: 'Forge mods' },
  { id: 'vanilla', name: 'Vanilla', note: 'No mods' },
];

function renderLoaders() {
  $('#pf-loader').replaceChildren(...loaderList.map((l) => el('button', {
    class: `seg-btn${l.id === editLoader ? ' on' : ''}`,
    type: 'button',
    onclick: () => { editLoader = l.id; renderLoaders(); renderVersions(); },
  }, [el('b', { text: l.name }), el('span', { text: l.note })])));
}

function renderVersions() {
  const list = (info && info.versions) || [{ id: '1.21.1', note: '', onix: true }];
  const onix = list.filter((v) => v.onix);
  const others = list.filter((v) => !v.onix);
  const buttons = onix.map((v) => el('button', {
    class: `seg-btn${v.id === editVersion ? ' on' : ''}`,
    type: 'button',
    title: v.note || '',
    onclick: () => { editVersion = v.id; renderVersions(); },
  }, [el('b', { text: v.id }), v.note ? el('span', { text: v.note }) : null].filter(Boolean)));
  const otherOn = others.some((v) => v.id === editVersion);
  const select = el('select', { class: `input sm seg-select${otherOn ? ' on' : ''}`, title: 'Every Minecraft version Fabric supports' }, [
    el('option', { value: '', text: others.length ? 'Other versions…' : 'Loading versions…', selected: !otherOn }),
    ...others.map((v) => el('option', { value: v.id, text: v.id, selected: v.id === editVersion })),
  ]);
  select.addEventListener('change', () => {
    if (select.value) editVersion = select.value;
    renderVersions();
  });
  const note = editLoader !== 'fabric'
    ? el('span', { class: 'seg-note', text: `${editLoader === 'vanilla' ? 'Plain Minecraft' : `${loaderList.find((l) => l.id === editLoader).name} with your own mods`} – the Onix mod needs Fabric.` })
    : otherOn
      ? el('span', { class: 'seg-note', text: 'Fabric API and the performance pack – the Onix mod is not available for this version yet.' })
      : null;
  $('#pf-version').replaceChildren(...buttons, select, ...(note ? [note] : []));
}

function openProfileEditor(profile) {
  editing = profile;
  $('#pf-title').textContent = profile ? 'Edit Profile' : 'New Profile';
  $('#pf-name').value = profile ? profile.name : '';
  editColor = profile ? profile.color : PROFILE_COLORS[profileState.profiles.length % PROFILE_COLORS.length];
  renderSwatches();
  const ram = $('#pf-ram');
  ram.max = String(Math.min(8192, Math.max(2048, Math.floor(info.totalMemMb / 512) * 512 - 1024)));
  ram.value = String(profile ? profile.ramMb : Math.min(4096, Number(ram.max)));
  showRam();
  $('#pf-server').value = profile ? profile.quickJoin : '';
  $('#pf-perf').checked = profile ? profile.performanceMods : true;
  editVersion = profile ? mcOfProfile(profile) : mcOfProfile(activeProfile());
  editLoader = (profile && profile.loader) || 'fabric';
  renderLoaders();
  // the full version list is fetched from Fabric in the background
  api.versions().then((list) => {
    if (Array.isArray(list) && list.length) {
      info.versions = list;
      renderVersions();
    }
  }).catch(() => {});
  renderVersions();
  $('#pf-copy-row').hidden = !!profile;
  $('#pf-delete').hidden = !profile || profileState.profiles.length <= 1;
  openModal('#profile-modal');
  setTimeout(() => $('#pf-name').focus(), 60);
}

function renderSwatches() {
  $('#pf-colors').replaceChildren(...PROFILE_COLORS.map((c) => el('button', {
    class: `swatch${c === editColor ? ' selected' : ''}`,
    style: `background:${c}`,
    'aria-label': c,
    onclick: () => { editColor = c; renderSwatches(); },
  })));
}

const showRam = () => { $('#pf-ram-out').textContent = gb(Number($('#pf-ram').value)); };
$('#pf-ram').addEventListener('input', showRam);
$('#btn-new-profile').addEventListener('click', () => openProfileEditor(null));

$('#pf-save').addEventListener('click', async () => {
  const name = $('#pf-name').value.trim();
  if (!name) return toast('Please enter a name.', true);
  const values = { name, color: editColor, ramMb: Number($('#pf-ram').value), quickJoin: $('#pf-server').value.trim(), performanceMods: $('#pf-perf').checked, mcVersion: editVersion, loader: editLoader };
  if (editing) {
    if (mcOfProfile(editing) !== editVersion && !confirm(`Switch "${editing.name}" to Minecraft ${editVersion}? Worlds stay, but mods made only for ${mcOfProfile(editing)} will not load.`)) return;
    const res = await api.saveProfile({ ...editing, ...values });
    if (res && res.ok === false) return toast(res.error, true);
    profileState = res && res.ok ? res.value : res;
  } else {
    const res = await api.createProfile(name, $('#pf-copy').checked ? profileState.active : null, editVersion);
    if (!res.ok) return toast(res.error, true);
    profileState = res.value;
    const saved = await api.saveProfile({ ...activeProfile(), ...values });
    profileState = saved && saved.ok ? saved.value : saved;
    toast(`${name} created`);
  }
  closeModal('#profile-modal');
  refreshProfiles();
  renderProfiles();
});

$('#pf-delete').addEventListener('click', async () => {
  if (!editing || !confirm(`Delete profile "${editing.name}" with all of its worlds and mods?`)) return;
  const res = await api.deleteProfile(editing.id);
  if (!res.ok) return toast(res.error, true);
  profileState = res.value;
  closeModal('#profile-modal');
  refreshProfiles();
  renderProfiles();
});
