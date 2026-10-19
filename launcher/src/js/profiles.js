'use strict';

/* Profiles page and the profile editor. */

function renderProfiles() {
  const grid = $('#profile-grid');
  grid.replaceChildren(...profileState.profiles.map((p) => {
    const active = p.id === profileState.active;
    return el('div', { class: `pcard${active ? ' active' : ''}`, style: `--pc:${p.color}` }, [
      el('div', { class: 'pcard-top' }, [
        el('div', { class: 'pcard-icon', text: p.name.charAt(0).toUpperCase() }),
        el('div', { class: 'pcard-name', text: p.name }),
        active ? el('span', { class: 'badge', text: 'Active' }) : null,
        el('button', { class: 'icon-btn pcard-edit', title: 'Edit profile', 'aria-label': 'Edit profile', onclick: () => openProfileEditor(p) }, [icon('pencil')]),
      ]),
      el('div', { class: 'meta' }, profileMeta(p).split(' · ').map((m) => el('span', { text: m }))),
      el('div', { class: 'pcard-actions' }, [
        active ? null : el('button', {
          class: 'btn sm primary',
          text: 'Select',
          onclick: async () => {
            profileState = await api.selectProfile(p.id);
            refreshProfiles();
            toast(`${p.name} is now active`);
          },
        }),
        el('button', { class: 'btn sm', onclick: () => openMods(p.id) }, [icon('puzzle'), 'Mods']),
        el('button', { class: 'btn sm', onclick: () => openExport(p) }, [icon('send'), 'Export']),
      ]),
    ]);
  }));
}

let editing = null;
let editColor = PROFILE_COLORS[0];
let editVersion = '1.21.1';

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
  const note = otherOn
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
  ram.max = String(Math.max(2048, Math.floor(info.totalMemMb / 512) * 512 - 1024));
  ram.value = String(profile ? profile.ramMb : Math.min(4096, Number(ram.max)));
  showRam();
  $('#pf-server').value = profile ? profile.quickJoin : '';
  $('#pf-perf').checked = profile ? profile.performanceMods : true;
  editVersion = profile ? mcOfProfile(profile) : mcOfProfile(activeProfile());
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
  const values = { name, color: editColor, ramMb: Number($('#pf-ram').value), quickJoin: $('#pf-server').value.trim(), performanceMods: $('#pf-perf').checked, mcVersion: editVersion };
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
