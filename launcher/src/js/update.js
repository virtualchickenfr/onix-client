'use strict';

/* Launcher updates: the main process checks on every start (lib/updater.js); this shows the state
   as a pill in the top bar and a row in Settings. */

let updateState = null;

function renderUpdate(v) {
  updateState = v;
  const pill = $('#update-pill');
  const text = $('#update-text');
  pill.className = 'update-pill no-drag';
  pill.disabled = false;
  pill.hidden = true;
  const version = v.version ? ` ${v.version}` : '';
  switch (v.status) {
    case 'downloading':
      pill.hidden = false;
      pill.disabled = true;
      pill.replaceChildren(icon('download'), `Downloading update${version}`, el('span', { class: 'bar' }, [el('i', { style: `width:${v.percent || 0}%` })]));
      text.textContent = `Downloading version${version} (${v.percent || 0} %) in the background. It installs by itself when you close the launcher.`;
      break;
    case 'ready':
      pill.hidden = false;
      pill.classList.add('ready');
      pill.replaceChildren(icon('check'), `Update${version} ready`);
      pill.title = 'Nothing to do: it installs by itself when you close the launcher. Click to install it right now.';
      text.textContent = `Version${version} is downloaded and installs by itself when you close the launcher. The Onix mod is already up to date for your next game.`;
      break;
    case 'installing':
      pill.hidden = false;
      pill.disabled = true;
      pill.classList.add('ready');
      pill.replaceChildren(icon('refresh'), `Installing update${version}…`);
      text.textContent = `Installing version${version} – the launcher restarts in a moment.`;
      break;
    case 'available':
      pill.hidden = false;
      pill.replaceChildren(icon('download'), `Update${version} available`);
      pill.title = 'Open the download page';
      text.textContent = `Version${version} is available. Download it and install it over this one.`;
      break;
    case 'checking':
      text.textContent = 'Looking for a new version…';
      break;
    case 'latest':
      text.textContent = `You have the newest version (v${v.current}). The launcher checks again every time it starts.`;
      break;
    case 'error':
      text.textContent = `Could not check for updates: ${v.error}`;
      break;
    case 'disabled':
      text.textContent = `v${v.current} · ${v.error}`;
      break;
    default:
      text.textContent = `v${v.current} · The switch turns the automatic check on every start on or off.`;
  }
}

async function runUpdateAction() {
  if (!updateState) return;
  if (updateState.status === 'ready') {
    const r = await api.installUpdate();
    if (!r.ok) toast(r.error, true);
  } else if (updateState.status === 'available') {
    const r = await api.openUpdateDownload();
    if (!r.ok) toast(r.error, true);
  }
}

(async () => {
  api.onUpdate((v) => {
    const was = updateState && updateState.status;
    renderUpdate(v);
    if (v.status === 'ready' && was !== 'ready') toast(`Update ${v.version} downloaded – it installs by itself when you close the launcher`);
  });
  if (api.onModUpdated) api.onModUpdated((m) => toast(`Onix mod ${m.version} downloaded – your next game uses it, no restart needed`));
  $('#update-pill').addEventListener('click', runUpdateAction);
  $('#update-check').addEventListener('click', async () => {
    const btn = $('#update-check');
    btn.disabled = true;
    const v = await api.checkUpdate();
    btn.disabled = false;
    renderUpdate(v);
    if (v.status === 'latest') toast('You have the newest version');
    else if (v.status === 'error') toast(`Could not check for updates: ${v.error}`, true);
  });
  renderUpdate(await api.updateStatus());
})();
