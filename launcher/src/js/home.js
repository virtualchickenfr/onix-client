'use strict';

/* Home page: profile picker and the Play button. */

const pickerBtn = $('#picker-btn');
const pickerPop = $('#picker-pop');

function renderPicker() {
  const p = activeProfile();
  if (!p) return;
  $('#picker-dot').style.background = p.color;
  $('#picker-name').textContent = p.name;
  $('#picker-meta').textContent = profileMeta(p);
  pickerPop.replaceChildren(
    ...profileState.profiles.map((prof) => el('button', {
      class: 'pop-item',
      onclick: async () => {
        profileState = await api.selectProfile(prof.id);
        closePicker();
        refreshProfiles();
      },
    }, [
      el('span', { class: 'dot', style: `background:${prof.color}` }),
      el('span', { class: 't' }, [el('b', { text: prof.name }), el('span', { text: profileMeta(prof) })]),
      prof.id === profileState.active ? icon('check') : null,
    ])),
    el('div', { class: 'pop-sep' }),
    el('button', { class: 'pop-item muted', onclick: () => showView('profiles') }, ['Manage profiles…']),
  );
}

function closePicker() {
  pickerPop.classList.remove('open');
  pickerBtn.classList.remove('open');
}

pickerBtn.addEventListener('click', (e) => {
  e.stopPropagation();
  const open = !pickerPop.classList.contains('open');
  pickerPop.classList.toggle('open', open);
  pickerBtn.classList.toggle('open', open);
});
document.addEventListener('click', (e) => { if (!pickerPop.contains(e.target)) closePicker(); });

function renderEyebrow() {
  if (!info) return;
  $('#eyebrow').textContent = `Minecraft ${mcOfProfile(activeProfile())}`;
}

function refreshProfiles() {
  renderPicker();
  renderEyebrow();
  if (typeof renderRecent === 'function') renderRecent();
  if (currentView === 'profiles') renderProfiles();
  if (currentView === 'profile') renderProfileView();
  if (currentView === 'mods') renderMods();
  if (currentView === 'cosmetics') loadCosmetics();
  loadSky().catch(() => {});
}

const playBtn = $('#btn-play');

const launchGem = gem();
launchGem.hidden = true;
const launchLead = el('span', { class: 'launch-lead' });
$('#launch-status').before(launchLead);
launchLead.append(launchGem, $('#launch-status'));

function updatePlay() {
  playBtn.disabled = state !== 'idle';
  playBtn.classList.toggle('preparing', state === 'preparing');
  playBtn.classList.toggle('running', state === 'running');
  const lead = state === 'preparing' ? el('span', { class: 'ring' }) : state === 'running' ? el('span', { class: 'run-dot' }) : icon('play');
  const label = state === 'preparing' ? 'Starting…' : state === 'running' ? 'Running' : account ? 'Play' : 'Sign in';
  playBtn.replaceChildren(lead, el('span', { text: label }));
  launchGem.hidden = state !== 'preparing';
  if (state === 'idle') {
    $('#launch-status').textContent = account ? 'Ready' : 'Sign in to play';
    $('#launch-pct').textContent = '';
  }
  pickerBtn.disabled = state !== 'idle';
  if (typeof pvSyncPlay === 'function') pvSyncPlay();
}

function setProgress(v) {
  const bar = $('#bar-fill').parentElement;
  bar.classList.toggle('indeterminate', v == null);
  $('#bar-fill').style.width = v == null ? '' : `${Math.round(Math.max(0, Math.min(1, v)) * 100)}%`;
  $('#launch-pct').textContent = v == null || v >= 1 ? '' : `${Math.round(v * 100)} %`;
}

playBtn.addEventListener('click', async () => {
  if (!account) return login();
  state = 'preparing';
  updatePlay();
  $('#launch-status').textContent = 'Preparing';
  $('#launch-detail').textContent = '';
  setProgress(null);
  const quick = pendingQuickPlay;
  pendingQuickPlay = null;
  const res = await api.launch(undefined, quick);
  if (!res.ok) {
    state = 'idle';
    setProgress(0);
    $('#launch-detail').textContent = '';
    updatePlay();
    toast(res.error, true);
  }
});

api.onProgress(({ label, progress }) => {
  if (state !== 'preparing') return;
  $('#launch-status').textContent = label;
  setProgress(progress > 0 && progress <= 1 ? progress : null);
});

// background downloads after an import or a new profile (see prepareProfile in main.js)
api.onPrepare((p) => {
  if (state !== 'idle') return;
  if (p.done) {
    $('#launch-status').textContent = p.error ? 'Ready' : 'Ready – files downloaded';
    $('#launch-detail').textContent = p.error ? `Download will be retried at start: ${p.error}` : '';
    setProgress(0);
    return;
  }
  $('#launch-status').textContent = `Getting "${p.profile}" ready · ${p.label}`;
  setProgress(p.progress > 0 && p.progress <= 1 ? p.progress : null);
});

// a failed start names the mods that caused it; they can be turned off right here
let crashToast = null;
api.onCrash((d) => {
  clearTimeout(crashToast);
  $('#crash-title').textContent = d.title;
  $('#crash-text').textContent = d.text || '';
  $('#crash-mods').replaceChildren(...d.mods.map((m) => el('div', { class: 'crash-mod' }, [
    el('div', {}, [el('b', { text: m.name }), el('small', { text: m.file || `${m.id} (not found in the mods folder)` })]),
    m.file ? el('button', {
      class: 'btn sm',
      text: 'Turn off',
      onclick: async (e) => {
        const r = await api.toggleMod(m.file, d.profileId);
        if (r && r.ok === false) return toast(r.error, true);
        e.target.replaceWith(el('span', { class: 'tag', text: 'Off' }));
        toast(`${m.name} is off – turn it on again under Mods`);
      },
    }) : null,
  ])));
  openModal('#crash-modal');
});
$('#crash-console').addEventListener('click', () => { closeModal('#crash-modal'); showView('console'); });
$('#crash-retry').addEventListener('click', () => { closeModal('#crash-modal'); $('#btn-play').click(); });

api.onState((s) => {
  if (s === 'running') {
    state = 'running';
    $('#launch-status').textContent = 'Minecraft is running';
    $('#launch-detail').textContent = 'Right Shift opens the Onix menu';
    setProgress(1);
  } else {
    state = 'idle';
    setProgress(0);
    $('#launch-detail').textContent = '';
    if (s === 'crashed') crashToast = setTimeout(() => toast('Minecraft exited with an error. See the console for details.', true), 1500);
  }
  updatePlay();
});

// ---------------------------------------------------------------- Carbon-style home: voxel island, quick buttons, recent profiles

$('#home-folder').append(L.picon('folder'));
$('#home-manage').append(L.picon('dots'));
$('#home-folder').addEventListener('click', () => api.openFolder('profile', profileState.active));
$('#home-manage').addEventListener('click', () => openProfile(profileState.active));
$('#btn-stop').addEventListener('click', async () => {
  const res = await api.stopGame();
  if (res && res.ok === false) toast(res.error, true);
});

let homeIsland = null;
/** Starts the voxel island on the home page, stops it elsewhere and while Minecraft runs (the game gets the GPU). */
function homeWorld(on) {
  if (!homeIsland) {
    homeIsland = L.World($('#world'), {
      textRight: () => {
        let right = 0;
        $$('#home-inner .logo .ch, #home-inner .play-row > *').forEach((n) => { right = Math.max(right, n.getBoundingClientRect().right); });
        return right - $('#view-home').getBoundingClientRect().left;
      },
      bottomSpace: 150,
    });
  }
  if (on && state !== 'running') homeIsland.start(); else homeIsland.stop();
}
requestAnimationFrame(() => homeWorld(currentView === 'home'));

function renderRecent() {
  const box = $('#recent');
  const list = profileState.profiles.filter((p) => p.lastPlayed).sort((a, b) => b.lastPlayed - a.lastPlayed).slice(0, 3);
  if (!list.length) return box.replaceChildren();
  box.replaceChildren(el('span', { class: 'recent-label', text: 'Recent' }), ...list.map((p) => el('button', {
    class: 'tile',
    style: `--pc:${p.color}`,
    onclick: async () => {
      if (p.id !== profileState.active) {
        profileState = await api.selectProfile(p.id);
        refreshProfiles();
      }
    },
  }, [
    el('span', { class: 'tile-ico', text: p.name.charAt(0).toUpperCase() }),
    el('span', {}, [el('b', { text: p.name }), el('small', { text: `${mcOfProfile(p)} · ${fmtAgo(p.lastPlayed)}` })]),
  ])));
}

api.onState((s) => {
  $('#btn-stop').hidden = s !== 'running';
  homeWorld(currentView === 'home');
});
