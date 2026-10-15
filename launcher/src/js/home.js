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

function refreshProfiles() {
  renderPicker();
  if (currentView === 'profiles') renderProfiles();
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
  const res = await api.launch();
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
    if (s === 'crashed') toast('Minecraft exited with an error. See the console for details.', true);
  }
  updatePlay();
});
