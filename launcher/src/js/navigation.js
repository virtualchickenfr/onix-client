'use strict';

/* Sidebar navigation between pages. */

const TITLES = { home: 'Home', profiles: 'Profiles', mods: 'Mods', cosmetics: 'Cosmetics', import: 'Import', install: 'Install', settings: 'Settings', console: 'Console' };

function showView(name) {
  currentView = name;
  $$('.nav-item').forEach((b) => b.classList.toggle('active', b.dataset.view === name));
  $$('.view').forEach((v) => v.classList.toggle('active', v.id === `view-${name}`));
  $('#crumb').textContent = TITLES[name] || '';
  closePicker();
  if (name === 'profiles') renderProfiles();
  if (name === 'mods') renderMods();
  if (name === 'cosmetics') loadCosmetics();
  if (name === 'import' && !importScanned) scanImports();
  if (name === 'install') openInstall(); else stopRunningPoll();
  if (name === 'console') $('#log').scrollTop = $('#log').scrollHeight;
}

$$('.nav-item').forEach((b) => b.addEventListener('click', () => showView(b.dataset.view)));
$$('[data-goto]').forEach((b) => b.addEventListener('click', () => showView(b.dataset.goto)));
$('#btn-min').addEventListener('click', () => api.minimize());
$('#btn-close').addEventListener('click', () => api.close());

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    $$('.modal-backdrop.open').forEach((m) => m.classList.remove('open'));
    closePicker();
  }
});
