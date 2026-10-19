'use strict';

/* One profile on its own page: mods, resource packs, shaders, worlds, screenshots, logs and settings. */

const pv = { id: null, tab: 'mods', confirm: null, log: null, seq: 0 };

const pvProfile = () => profileState.profiles.find((p) => p.id === pv.id);

function fmtPlayTime(ms) {
  const min = Math.floor((ms || 0) / 60000);
  if (min < 1) return ms > 0 ? '< 1 min' : 'Not played yet';
  const h = Math.floor(min / 60);
  return h ? `${h} h ${min % 60} min` : `${min} min`;
}

function fmtAgo(t) {
  if (!t) return 'Never';
  const s = (Date.now() - t) / 1000;
  if (s < 60) return 'Just now';
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  if (s < 86400 * 7) return `${Math.floor(s / 86400)} d ago`;
  return new Date(t).toLocaleDateString();
}

function fmtSize(bytes) {
  if (!bytes) return '0 MB';
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 / 1024).toFixed(bytes < 10 * 1024 * 1024 ? 1 : 0)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
}

const versionInfo = (mc) => ((info && info.versions) || []).find((v) => v.id === mc);

// ---------------------------------------------------------------- open / header

function openProfile(id, tab) {
  pv.id = id;
  pv.confirm = null;
  pv.log = null;
  if (tab) pv.tab = tab;
  showView('profile');
}

function renderProfileView() {
  const p = pvProfile();
  if (!p) return showView('profiles');
  const mc = mcOfProfile(p);
  const v = versionInfo(mc);
  const iconBox = $('#pv-icon');
  iconBox.textContent = p.name.charAt(0).toUpperCase();
  iconBox.style.setProperty('--pc', p.color);
  $('#pv-name').textContent = p.name;
  const active = p.id === profileState.active;
  $('#pv-meta').textContent = [`Minecraft ${mc}`, v && v.onix ? v.note || 'Onix' : 'Fabric without Onix', active ? 'Active profile' : null].filter(Boolean).join(' · ');
  pvSyncPlay();
  renderStats(p);
  $$('#pv-tabs button').forEach((b) => b.classList.toggle('active', b.dataset.tab === pv.tab));
  renderTab();
  refreshCounts();
}

function pvSyncPlay() {
  const play = $('#pv-play');
  const busy = state !== 'idle';
  play.disabled = busy;
  play.replaceChildren(busy ? el('span', { class: state === 'running' ? 'run-dot' : 'ring' }) : icon('play'),
    document.createTextNode(state === 'running' ? 'Running' : state === 'preparing' ? 'Starting…' : 'Play'));
}

api.onProfilesChanged((value) => {
  if (!value || !Array.isArray(value.profiles)) return;
  profileState = value;
  refreshProfiles();
});

function stat(label, value, id) {
  return el('div', { class: 'pv-stat' }, [el('span', { text: label }), el('b', { text: value, id })]);
}

function renderStats(p) {
  $('#pv-stats').replaceChildren(
    stat('Play time', fmtPlayTime(p.playTimeMs)),
    stat('Last played', fmtAgo(p.lastPlayed)),
    stat('Memory', gb(p.ramMb)),
    stat('On disk', '…', 'pv-size'),
  );
  const id = p.id;
  api.profileSizes(id).then((res) => {
    if (pv.id !== id || !res.ok) return;
    const n = $('#pv-size');
    if (n) n.textContent = fmtSize(res.value.total);
  });
}

async function refreshCounts() {
  const id = pv.id;
  const [mods, rp, sp, worlds, shots] = await Promise.all([
    api.listMods(id), api.packs(id, 'resourcepacks'), api.packs(id, 'shaderpacks'), api.worlds(id), api.screenshots(id, 1),
  ]);
  if (pv.id !== id) return;
  const set = (k, n) => { $(`#pv-n-${k}`).textContent = n ? String(n) : ''; };
  set('mods', Array.isArray(mods) ? mods.length : 0);
  set('resourcepacks', rp.ok ? rp.value.length : 0);
  set('shaderpacks', sp.ok ? sp.value.length : 0);
  set('worlds', worlds.ok ? worlds.value.length : 0);
  set('screenshots', shots.ok && shots.value.length ? '•' : '');
}

/** Called when mods or packs of a profile changed elsewhere (Modrinth dialog). */
function profileViewChanged(id) {
  if (currentView === 'profile' && pv.id === id) {
    renderTab();
    refreshCounts();
  }
}

$$('#pv-tabs button').forEach((b) => b.addEventListener('click', () => {
  pv.tab = b.dataset.tab;
  pv.confirm = null;
  $$('#pv-tabs button').forEach((x) => x.classList.toggle('active', x === b));
  renderTab();
}));
$('#pv-back').addEventListener('click', () => showView('profiles'));
$('#pv-folder').addEventListener('click', () => api.openFolder('profile', pv.id));
$('#pv-edit').addEventListener('click', () => openProfileEditor(pvProfile()));
$('#pv-export').addEventListener('click', () => openExport(pvProfile()));
$('#pv-duplicate').addEventListener('click', async () => {
  const btn = $('#pv-duplicate');
  btn.disabled = true;
  const res = await api.duplicateProfile(pv.id);
  btn.disabled = false;
  if (!res.ok) return toast(res.error, true);
  profileState = res.value.state;
  refreshProfiles();
  const copy = profileState.profiles.find((x) => x.id === res.value.id);
  toast(`${copy ? copy.name : 'Copy'} created`);
  openProfile(res.value.id);
});
$('#pv-play').addEventListener('click', async () => {
  if (!account) return login();
  if (pv.id !== profileState.active) {
    profileState = await api.selectProfile(pv.id);
    refreshProfiles();
  }
  showView('home');
  $('#btn-play').click();
});

// ---------------------------------------------------------------- tabs

function tools(...nodes) {
  $('#pv-tools').replaceChildren(...nodes.filter(Boolean));
}

function emptyBlock(title, text, action) {
  return el('div', { class: 'empty' }, [el('b', { text: title }), el('span', { text }), action || null]);
}

function renderTab() {
  const seq = ++pv.seq;
  const body = $('#pv-body');
  body.className = `pv-body pv-${pv.tab}`;
  const run = {
    mods: renderPvMods,
    resourcepacks: () => renderPvPacks('resourcepacks'),
    shaderpacks: () => renderPvPacks('shaderpacks'),
    worlds: renderPvWorlds,
    screenshots: renderPvScreenshots,
    logs: renderPvLogs,
    settings: renderPvSettings,
  }[pv.tab] || renderPvMods;
  if (!body.children.length) body.replaceChildren(loadingBlock('Reading…'));
  run(seq);
}

const stale = (seq) => seq !== pv.seq || currentView !== 'profile';

/** A remove button that asks for a second click. */
function confirmButton(key, title, onConfirm) {
  const confirming = pv.confirm === key;
  return el('button', {
    class: `icon-btn mm-remove${confirming ? ' confirm' : ''}`,
    title: confirming ? 'Click again to move it to the trash' : title,
    onclick: async () => {
      if (!confirming) {
        pv.confirm = key;
        renderTab();
        setTimeout(() => { if (pv.confirm === key) { pv.confirm = null; renderTab(); } }, 3000);
        return;
      }
      pv.confirm = null;
      await onConfirm();
    },
  }, confirming ? [el('span', { text: 'Remove?' })] : [icon('trash')]);
}

// ----- mods

async function renderPvMods(seq) {
  const id = pv.id;
  tools(
    el('button', { class: 'btn sm', onclick: () => api.openFolder('mods', id) }, [icon('folder'), 'Folder']),
    el('button', { class: 'btn sm', onclick: () => openMods(id, 'installed') }, [icon('refresh'), 'Updates']),
    el('button', { class: 'btn sm primary', onclick: () => openMods(id, 'browse') }, [icon('search'), 'Browse Modrinth']),
  );
  const mods = await api.listMods(id);
  if (stale(seq)) return;
  const body = $('#pv-body');
  if (!mods.length) {
    body.replaceChildren(emptyBlock('No mods yet', 'Onix, Fabric API and the performance pack are added on the first start.',
      el('button', { class: 'btn sm primary', text: 'Browse Modrinth', onclick: () => openMods(id, 'browse') })));
    return;
  }
  body.replaceChildren(el('div', { class: 'table' }, mods.map((mod) => {
    const m = mod.modrinth;
    const toggle = el('input', { type: 'checkbox' });
    toggle.checked = mod.enabled;
    toggle.disabled = mod.core;
    toggle.addEventListener('change', async () => {
      const res = await api.toggleMod(mod.file, id);
      if (res && res.ok === false) toast(res.error, true);
      renderTab();
    });
    const sub = mod.core ? 'Needed by Onix' : mod.managed ? 'Kept up to date by the launcher' : m ? `Modrinth · ${m.version || ''}` : 'Your file';
    return el('div', { class: `trow${mod.enabled ? '' : ' off'}` }, [
      modIcon(m && m.icon, m ? m.title : mod.name),
      el('div', { class: 'tmain' }, [el('b', { text: m ? m.title : mod.name }), el('span', { text: sub })]),
      mod.core ? el('span', { class: 'tag', text: 'Required' }) : confirmButton(`mod:${mod.file}`, 'Remove', async () => {
        const res = await api.removeMod(id, mod.file);
        if (!res.ok) toast(res.error, true);
        else toast(`${m ? m.title : mod.name} removed`);
        renderTab();
        refreshCounts();
      }),
      el('label', { class: 'switch' }, [toggle, el('i')]),
    ]);
  })));
}

// ----- resource packs and shaders

async function renderPvPacks(kind, seq = pv.seq) {
  const id = pv.id;
  const shader = kind === 'shaderpacks';
  tools(
    el('button', { class: 'btn sm', onclick: () => api.openFolder(kind, id) }, [icon('folder'), 'Folder']),
    el('button', {
      class: 'btn sm',
      onclick: async () => {
        const res = await api.pickPacks(id, kind);
        if (!res.ok) return toast(res.error, true);
        if (res.value.length) toast(`${plural(res.value.length, 'pack', 'packs')} added`);
        renderTab();
        refreshCounts();
      },
    }, [icon('plus'), 'Add file']),
    el('button', { class: 'btn sm primary', onclick: () => openAssets(id, shader ? 'shader' : 'resourcepack') }, [icon('search'), 'Browse Modrinth']),
  );
  const res = await api.packs(id, kind);
  if (stale(seq)) return;
  const body = $('#pv-body');
  if (!res.ok) return body.replaceChildren(emptyBlock('Could not read the folder', res.error));
  const list = res.value;
  if (!list.length) {
    body.replaceChildren(emptyBlock(
      shader ? 'No shaders yet' : 'No resource packs yet',
      shader ? 'Install one from Modrinth – Iris comes with it. Pick the shader in Options › Video › Shader Packs.' : 'Install one from Modrinth or drop a .zip here. Switch packs on in Options › Resource Packs.',
      el('button', { class: 'btn sm primary', text: 'Browse Modrinth', onclick: () => openAssets(id, shader ? 'shader' : 'resourcepack') }),
    ));
    return;
  }
  body.replaceChildren(el('div', { class: 'table' }, list.map((pack) => {
    const m = pack.modrinth;
    const toggle = el('input', { type: 'checkbox' });
    toggle.checked = pack.enabled;
    toggle.title = pack.enabled ? 'Shown in the game' : 'Hidden from the game';
    toggle.disabled = pack.folder;
    toggle.addEventListener('change', async () => {
      const r = await api.togglePack(id, kind, pack.file);
      if (!r.ok) toast(r.error, true);
      renderTab();
    });
    const sub = [m ? `Modrinth · ${m.version || ''}` : pack.folder ? 'Folder' : 'Your file', pack.size ? fmtSize(pack.size) : null].filter(Boolean).join(' · ');
    return el('div', { class: `trow${pack.enabled ? '' : ' off'}` }, [
      modIcon(m && m.icon, m ? m.title : pack.name),
      el('div', { class: 'tmain' }, [el('b', { text: m ? m.title : pack.name }), el('span', { text: sub })]),
      confirmButton(`${kind}:${pack.file}`, 'Remove', async () => {
        const r = await api.removePack(id, kind, pack.file);
        if (!r.ok) toast(r.error, true);
        else toast(`${m ? m.title : pack.name} moved to the trash`);
        renderTab();
        refreshCounts();
      }),
      el('label', { class: 'switch' }, [toggle, el('i')]),
    ]);
  })));
}

// ----- worlds

async function renderPvWorlds(seq) {
  const id = pv.id;
  tools(el('button', { class: 'btn sm', onclick: () => api.openFolder('saves', id) }, [icon('folder'), 'Saves folder']));
  const res = await api.worlds(id);
  if (stale(seq)) return;
  const body = $('#pv-body');
  if (!res.ok) return body.replaceChildren(emptyBlock('Could not read the worlds', res.error));
  if (!res.value.length) return body.replaceChildren(emptyBlock('No worlds yet', 'Worlds you create in this profile show up here.'));
  body.replaceChildren(el('div', { class: 'pv-worlds' }, res.value.map((w) => {
    const pic = el('div', { class: 'pv-world-icon' }, [w.icon ? el('img', { src: w.icon, alt: '' }) : icon('globe')]);
    return el('div', { class: 'pv-world' }, [
      pic,
      el('div', { class: 'tmain' }, [
        el('b', { text: w.name }),
        el('span', { text: [w.mode, `Played ${fmtAgo(w.lastPlayed).toLowerCase()}`].filter(Boolean).join(' · ') }),
      ]),
      el('div', { class: 'pv-world-actions' }, [
        el('button', { class: 'btn sm primary', title: 'Start the game straight into this world', onclick: () => playWorld(w) }, [icon('play'), 'Play']),
        el('button', { class: 'icon-btn', title: 'Open folder', onclick: () => api.openWorld(id, w.folder) }, [icon('folder')]),
        confirmButton(`world:${w.folder}`, 'Remove world', async () => {
          const r = await api.removeWorld(id, w.folder);
          if (!r.ok) toast(r.error, true);
          else toast(`${w.name} moved to the trash`);
          renderTab();
          refreshCounts();
        }),
      ]),
    ]);
  })));
}

async function playWorld(w) {
  if (!account) return login();
  if (pv.id !== profileState.active) {
    profileState = await api.selectProfile(pv.id);
    refreshProfiles();
  }
  pendingQuickPlay = { world: w.folder };
  showView('home');
  $('#btn-play').click();
}

// ----- screenshots

async function renderPvScreenshots(seq) {
  const id = pv.id;
  tools(el('button', { class: 'btn sm', onclick: () => api.openFolder('screenshots', id) }, [icon('folder'), 'Folder']));
  const body = $('#pv-body');
  body.replaceChildren(el('div', { class: 'pv-shots' }, [0, 1, 2, 3, 4, 5].map(() => el('div', { class: 'skeleton pv-shot' }))));
  const res = await api.screenshots(id, 120);
  if (stale(seq)) return;
  if (!res.ok) return body.replaceChildren(emptyBlock('Could not read the screenshots', res.error));
  if (!res.value.length) return body.replaceChildren(emptyBlock('No screenshots yet', 'Press F2 in the game – screenshots of this profile show up here.'));
  body.replaceChildren(el('div', { class: 'pv-shots' }, res.value.map((s) => el('figure', { class: 'pv-shot' }, [
    el('button', { class: 'pv-shot-img', title: 'Open', onclick: () => api.openScreenshot(id, s.file) }, [s.thumb ? el('img', { src: s.thumb, alt: s.file, loading: 'lazy' }) : icon('image')]),
    el('figcaption', {}, [
      el('span', { text: new Date(s.time).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) }),
      el('button', { class: 'icon-btn', title: 'Show in folder', onclick: () => api.showScreenshot(id, s.file) }, [icon('folder')]),
      confirmButton(`shot:${s.file}`, 'Remove', async () => {
        const r = await api.removeScreenshot(id, s.file);
        if (!r.ok) toast(r.error, true);
        renderTab();
      }),
    ]),
  ]))));
}

// ----- logs

async function renderPvLogs(seq) {
  const id = pv.id;
  const res = await api.logs(id);
  if (stale(seq)) return;
  const body = $('#pv-body');
  tools(
    el('button', { class: 'btn sm', onclick: () => api.openFolder('logs', id) }, [icon('folder'), 'Folder']),
    el('button', { class: 'btn sm', onclick: () => renderTab() }, [icon('refresh'), 'Reload']),
  );
  if (!res.ok) return body.replaceChildren(emptyBlock('Could not read the logs', res.error));
  if (!res.value.length) return body.replaceChildren(emptyBlock('No logs yet', 'The game log and crash reports of this profile show up here after the first start.'));
  if (!pv.log || !res.value.some((l) => l.id === pv.log)) pv.log = res.value[0].id;
  const list = el('div', { class: 'pv-log-list' }, res.value.map((l) => el('button', {
    class: `pv-log-item${l.id === pv.log ? ' active' : ''}${l.crash ? ' crash' : ''}`,
    onclick: () => { pv.log = l.id; renderTab(); },
  }, [icon(l.crash ? 'info' : 'file'), el('span', {}, [el('b', { text: l.name }), el('em', { text: fmtAgo(l.time) })])])));
  const pre = el('pre', { class: 'pv-log-text' }, [el('span', { class: 'muted', text: 'Reading…' })]);
  const copy = el('button', { class: 'btn sm pv-log-copy', onclick: () => navigator.clipboard.writeText(pre.textContent).then(() => toast('Log copied')) }, [icon('copy'), 'Copy']);
  body.replaceChildren(list, el('div', { class: 'pv-log-view' }, [copy, pre]));
  const text = await api.readLog(id, pv.log);
  if (stale(seq)) return;
  if (!text.ok) {
    pre.textContent = text.error;
    return;
  }
  // colour warnings and errors; the log stays plain text
  const lines = text.value.split('\n').slice(-4000);
  pre.replaceChildren(...lines.map((line) => el('span', {
    class: /\b(ERROR|FATAL|Exception|Caused by)\b/.test(line) ? 'err' : /\bWARN\b/.test(line) ? 'warn' : null,
    text: `${line}\n`,
  })));
  pre.scrollTop = pre.scrollHeight;
}

// ----- settings

function renderPvSettings() {
  const p = pvProfile();
  tools();
  const field = (label, control, hint) => el('label', { class: 'field' }, [el('span', { text: label }), control, hint ? el('small', { class: 'hint', text: hint }) : null]);
  const ram = el('input', { type: 'range', min: '1024', step: '512' });
  ram.max = String(Math.max(2048, Math.floor((info.totalMemMb || 8192) / 512) * 512 - 1024));
  ram.value = String(p.ramMb);
  const ramOut = el('output', { text: gb(p.ramMb) });
  ram.addEventListener('input', () => { ramOut.textContent = gb(Number(ram.value)); });
  const java = el('input', { class: 'input', placeholder: 'Automatic (Java is downloaded for this version)', spellcheck: 'false', value: p.javaPath || '' });
  const javaPick = el('button', {
    class: 'btn sm',
    type: 'button',
    onclick: async () => {
      const r = await api.pickJava();
      if (r.ok && r.value) java.value = r.value;
    },
  }, [icon('folder'), 'Browse']);
  const args = el('textarea', { class: 'input pv-args', rows: '3', spellcheck: 'false', placeholder: 'e.g. -XX:+UseZGC -Dsodium.checks.issue2561=false' });
  args.value = p.jvmArgs || '';
  const width = el('input', { class: 'input sm pv-num', type: 'number', min: '0', placeholder: 'Auto', value: p.width || '' });
  const height = el('input', { class: 'input sm pv-num', type: 'number', min: '0', placeholder: 'Auto', value: p.height || '' });
  const full = el('input', { type: 'checkbox' });
  full.checked = !!p.fullscreen;
  const server = el('input', { class: 'input', placeholder: 'Server address (optional)', spellcheck: 'false', value: p.quickJoin || '' });
  const perf = el('input', { type: 'checkbox' });
  perf.checked = p.performanceMods;

  const save = el('button', {
    class: 'btn primary',
    onclick: async () => {
      const res = await api.saveProfile({
        ...p,
        ramMb: Number(ram.value),
        javaPath: java.value.trim(),
        jvmArgs: args.value.trim(),
        width: Number(width.value) || 0,
        height: Number(height.value) || 0,
        fullscreen: full.checked,
        quickJoin: server.value.trim(),
        performanceMods: perf.checked,
      });
      if (res && res.ok === false) return toast(res.error, true);
      profileState = res && res.ok ? res.value : res;
      refreshProfiles();
      renderStats(pvProfile());
      toast('Settings saved');
    },
  }, [icon('check'), 'Save']);

  $('#pv-body').replaceChildren(el('div', { class: 'pv-settings' }, [
    el('section', { class: 'pv-card' }, [
      el('h4', { text: 'Game' }),
      el('label', { class: 'field' }, [el('span', {}, ['Memory ', ramOut]), ram]),
      field('Join on launch', server),
      el('div', { class: 'field inline' }, [el('span', { text: 'Performance pack (Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling)' }), el('label', { class: 'switch' }, [perf, el('i')])]),
    ]),
    el('section', { class: 'pv-card' }, [
      el('h4', { text: 'Window' }),
      el('div', { class: 'field' }, [el('span', { text: 'Size' }), el('div', { class: 'pv-size-row' }, [width, el('span', { text: '×' }), height, el('em', { text: 'pixels – empty for the default' })])]),
      el('div', { class: 'field inline' }, [el('span', { text: 'Start in fullscreen' }), el('label', { class: 'switch' }, [full, el('i')])]),
    ]),
    el('section', { class: 'pv-card' }, [
      el('h4', { text: 'Java' }),
      el('div', { class: 'field' }, [el('span', { text: 'Java executable' }), el('div', { class: 'pv-java-row' }, [java, javaPick])]),
      field('Extra JVM arguments', args, 'Memory comes from the slider above; -Xmx and -Xms here are ignored.'),
    ]),
    el('div', { class: 'pv-save' }, [save]),
  ]));
}
