'use strict';

/* Launcher settings. */

async function initSettings() {
  const s = await api.getSettings();
  customThemes = await api.listThemes();
  applyTheme(s.theme || 'onyx');
  renderThemes();
  currentBackground = s.background || 'carbon';
  renderBackgrounds();
  applyBackground(currentBackground);
  await loadSky();
  const clean = $('#set-clean');
  clean.checked = s.launcherStyle !== 'blocks';
  applyLayout(clean.checked);
  clean.addEventListener('change', () => {
    applyLayout(clean.checked);
    api.setSettings({ launcherStyle: clean.checked ? 'clean' : 'blocks' });
  });
  const hide = $('#set-hide');
  hide.checked = s.closeOnLaunch;
  hide.addEventListener('change', () => api.setSettings({ closeOnLaunch: hide.checked }));
  const fast = $('#set-fast');
  fast.checked = s.fastStart !== false;
  fast.addEventListener('change', () => api.setSettings({ fastStart: fast.checked }));
  const auto = $('#set-autoupdate');
  auto.checked = s.autoUpdate !== false;
  auto.addEventListener('change', () => {
    api.setSettings({ autoUpdate: auto.checked });
    toast(auto.checked ? 'The launcher checks for updates when it starts' : 'Automatic update checks are off – use Check now');
  });
  const startupUpdate = $('#set-startup-update');
  if (startupUpdate) {
    startupUpdate.checked = s.updateAtStartup !== false;
    startupUpdate.addEventListener('change', () => {
      api.setSettings({ updateAtStartup: startupUpdate.checked });
      toast(startupUpdate.checked ? 'Onix updates in the background when your PC starts' : 'No more updates when the PC starts');
    });
  }
  const java = $('#set-java');
  java.value = s.javaPath || '';
  java.addEventListener('change', () => api.setSettings({ javaPath: java.value.trim() }));
  const server = $('#set-server');
  server.value = s.onixServer || '';
  server.addEventListener('change', async () => {
    const value = server.value.trim().replace(/\/+$/, '');
    if (value && !/^https?:\/\/[^\s/]+/i.test(value)) {
      toast('The Onix server address must start with http:// or https://', true);
      return;
    }
    server.value = value;
    await api.setSettings({ onixServer: value });
    toast(value ? 'Onix server saved – applies in game too' : 'Onix server cleared');
    if (typeof loadWallet === 'function') loadWallet();
  });
  $('#game-dir').textContent = info.gameDir;
  $$('[data-folder]').forEach((b) => b.addEventListener('click', () => api.openFolder(b.dataset.folder)));
}

/** Clean layout (default) or the blocky hotbar look. */
function applyLayout(clean) {
  document.body.classList.toggle('clean', clean);
  $$('.nav-item').forEach((b) => { if (b.dataset.name) b.title = b.dataset.name; });
  if (typeof renderHomeStage === 'function') renderHomeStage();
  if (typeof homeWorld === 'function') homeWorld(currentView === 'home');
  if (typeof hotbarSync === 'function') hotbarSync(currentView === 'profile' || currentView === 'mods' ? 'profiles' : currentView);
}
