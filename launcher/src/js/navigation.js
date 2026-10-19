'use strict';

/* Sidebar navigation between pages. */

const TITLES = { home: 'Home', profiles: 'Profiles', profile: 'Profile', discover: 'Discover', skins: 'Skins', mods: 'Mods', cosmetics: 'Cosmetics', import: 'Import', install: 'Install', settings: 'Settings', console: 'Console' };

function showView(name) {
  currentView = name;
  const slot = name === 'profile' || name === 'mods' ? 'profiles' : name;
  $$('.nav-item').forEach((b) => b.classList.toggle('active', b.dataset.view === slot));
  if (typeof hotbarSync === 'function') hotbarSync(slot);
  $$('.view').forEach((v) => v.classList.toggle('active', v.id === `view-${name}`));
  closePicker();
  if (name === 'profiles') renderProfiles();
  if (name === 'profile') renderProfileView();
  if (name === 'discover' && typeof openDiscover === 'function') openDiscover();
  if (name === 'skins' && typeof openSkins === 'function') openSkins();
  if (name === 'home' && typeof homeWorld === 'function') homeWorld(true); else if (typeof homeWorld === 'function') homeWorld(false);
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
