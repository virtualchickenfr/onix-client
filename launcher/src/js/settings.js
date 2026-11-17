'use strict';

/* Launcher settings. */

async function initSettings() {
  const s = await api.getSettings();
  applyTheme();
  await selectDesign(s.design || 'water', false);
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
  const language = $('#set-language');
  if (language) {
    language.value = ['en', 'de'].includes(s.language) ? s.language : 'auto';
    language.addEventListener('change', async () => {
      await api.setSettings({ language: language.value });
      api.reload();   // every text on the page in the new language
    });
  }
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
