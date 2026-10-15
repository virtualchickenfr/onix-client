'use strict';

/* Install page: running games, other launchers, mod download. */

let runningTimer = null;
let installBusy = false;

function installOptions() {
  return { profileId: $('#ins-profile').value || null, fabricApi: $('#ins-fapi').checked };
}

function renderInstallProfiles() {
  const sel = $('#ins-profile');
  const keep = sel.options.length ? sel.value : null;
  sel.replaceChildren(
    ...profileState.profiles.map((p) => el('option', { value: p.id, text: p.name, selected: keep !== null ? p.id === keep : p.id === profileState.active })),
    el('option', { value: '', text: "Don't copy profiles", selected: keep === '' }),
  );
}

/** Runs an install action on a button: spinner while busy, result as toast. */
async function busyButton(btn, label, action) {
  if (installBusy) return;
  installBusy = true;
  const old = [...btn.childNodes];
  btn.disabled = true;
  btn.replaceChildren(el('span', { class: 'ring' }), el('span', { text: label }));
  try {
    return await action();
  } finally {
    installBusy = false;
    btn.disabled = false;
    btn.replaceChildren(...old);
  }
}

async function openInstall() {
  renderInstallProfiles();
  const bundled = await api.installInfo();
  $('#dl-name').textContent = bundled ? bundled.file : 'Onix Client';
  $('#dl-meta').textContent = bundled
    ? `${(bundled.size / 1024 / 1024).toFixed(1)} MB · Fabric Loader 0.15+ · Minecraft ${bundled.minecraft}`
    : 'Not built yet – run "gradlew build" in the mod folder';
  $('#btn-dl').disabled = !bundled;
  refreshRunning(true);
  scanTargets();
  stopRunningPoll();
  runningTimer = setInterval(() => refreshRunning(false), 4000);
}

function stopRunningPoll() {
  if (runningTimer) clearInterval(runningTimer);
  runningTimer = null;
}

async function refreshRunning(showLoader) {
  const box = $('#running-list');
  if (showLoader) box.replaceChildren(loadingBlock('Looking for running Minecraft…'));
  const res = await api.runningGames();
  if (installBusy) return;
  const games = res.ok ? res.value : [];
  if (!games.length) {
    box.replaceChildren(el('div', { class: 'empty' }, [el('b', { text: 'No Minecraft running' }), el('span', { text: 'Start Minecraft from any launcher – it shows up here within a few seconds.' })]));
    return;
  }
  box.replaceChildren(...games.map((g) => {
    let state;
    let action = null;
    let sub = g.gameDir || 'Folder unknown';
    if (g.onix) {
      state = el('span', { class: 'state live', text: 'Onix active' });
      action = el('button', { class: 'btn sm primary' }, [icon('send'), 'Send Profile']);
      action.addEventListener('click', () => busyButton(action, 'Sending…', async () => {
        const r = await api.pushProfile(g.gameDir, installOptions().profileId || profileState.active);
        if (!r.ok) return toast(r.error, true);
        toast(`"${r.value.profile}" is loading in Minecraft now`);
      }));
    } else if (g.compatible && g.gameDir) {
      state = el('span', { class: 'state', text: 'Without Onix' });
      action = el('button', { class: 'btn sm' }, [icon('plug'), 'Install for Next Start']);
      action.addEventListener('click', () => busyButton(action, 'Installing…', async () => {
        const r = await api.installIntoRunning(g.gameDir, installOptions());
        if (!r.ok) return toast(r.error, true);
        toast('Installed – restart Minecraft to play with Onix');
        refreshRunning(false);
      }));
    } else {
      state = el('span', { class: 'state warn', text: g.fabric ? `Minecraft ${g.minecraft}` : 'No Fabric' });
      sub = `Onix needs Fabric ${'1.21.1'} – this game runs ${g.fabric ? 'Fabric ' : ''}${g.minecraft}`;
    }
    const title = `Minecraft ${g.minecraft}${g.fabric ? ' · Fabric' : ''}${g.user ? ' · ' + g.user : ''}${g.ownLauncher ? ' · Onix Launcher' : ''}`;
    return el('div', { class: 'trow' }, [
      el('div', { class: 'ticon' }, [g.onix ? gem('static') : icon('play')]),
      el('div', { class: 'tmain' }, [el('b', { text: title }), el('span', { class: 'mono', text: sub })]),
      state,
      action,
    ]);
  }));
}

async function scanTargets() {
  const box = $('#target-list');
  box.replaceChildren(loadingBlock('Looking for the Minecraft Launcher, Prism, MultiMC, Modrinth App, CurseForge…'));
  const res = await api.installTargets();
  const targets = res.ok ? res.value : [];
  if (!res.ok) toast(res.error, true);
  if (!targets.length) {
    box.replaceChildren(el('div', { class: 'empty' }, [el('b', { text: 'No other launchers found' }), el('span', { text: 'Download the mod below and put it into the mods folder yourself.' })]));
    return;
  }
  const ordered = [...targets].sort((a, b) => (b.official ? 1 : 0) - (a.official ? 1 : 0) || (b.compatible ? 1 : 0) - (a.compatible ? 1 : 0));
  box.replaceChildren(...ordered.map((t) => {
    const usable = t.official || t.compatible || t.minecraft == null;
    const state = t.onix ? el('span', { class: 'state ok', text: 'Installed' })
      : t.compatible || t.official ? el('span', { class: 'state', text: 'Ready' })
      : el('span', { class: 'state warn', text: t.minecraft ? `Minecraft ${t.minecraft}` : 'Unknown' });
    const btn = el('button', { class: `btn sm${t.onix ? '' : ' primary'}` }, [icon(t.onix ? 'refresh' : 'plug'), t.onix ? 'Update' : 'Install']);
    btn.disabled = !usable;
    btn.addEventListener('click', () => busyButton(btn, 'Installing…', async () => {
      const r = await api.installInto(t.id, installOptions());
      if (!r.ok) return toast(r.error, true);
      toast(t.official ? 'Done – pick "Onix Client" in the Minecraft Launcher' : `Installed into ${t.label}`);
      scanTargets();
    }));
    return el('div', { class: 'trow' }, [
      el('div', { class: 'ticon av', style: `--av:${CLIENT_COLORS[t.client] || '#3a3a44'}`, text: t.name.charAt(0) }),
      el('div', { class: 'tmain' }, [el('b', { text: `${t.name} · ${t.label}` }), el('span', { text: t.note || t.gameDir })]),
      state,
      btn,
    ]);
  }));
}

$('#btn-install-scan').addEventListener('click', () => { refreshRunning(true); scanTargets(); });
$('#btn-dl').addEventListener('click', () => busyButton($('#btn-dl'), 'Saving…', async () => {
  const r = await api.exportMod($('#dl-fapi').checked);
  if (!r.ok) return toast(r.error, true);
  if (r.value) toast(`Saved ${r.value.files.length === 1 ? 'the mod' : 'Onix and Fabric API'} to ${r.value.folder}`);
}));
api.onInstallProgress(({ label }) => {
  const busy = document.querySelector('#view-install .btn:disabled .ring + span');
  if (busy && label && label !== 'Done') busy.textContent = label.length > 22 ? `${label.slice(0, 21)}…` : label;
});
