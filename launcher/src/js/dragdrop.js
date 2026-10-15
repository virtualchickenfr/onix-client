'use strict';

/* Drop .jar files anywhere on the launcher to add them to the active profile. */

const dropZone = el('div', { class: 'drop-zone' }, [
  el('div', { class: 'drop-card' }, [
    icon('upload'),
    el('b', { text: 'Drop to add mods' }),
    el('span', { class: 'drop-target' }),
  ]),
]);
document.body.append(dropZone);

let dragDepth = 0;
const hasFiles = (e) => e.dataTransfer && Array.from(e.dataTransfer.types || []).includes('Files');

window.addEventListener('dragenter', (e) => {
  if (!hasFiles(e)) return;
  e.preventDefault();
  dragDepth++;
  const p = activeProfile();
  dropZone.querySelector('.drop-target').textContent = `Fabric mods for ${info.mcVersion || '1.21.1'} go into "${p ? p.name : 'your profile'}"`;
  dropZone.classList.add('open');
});
window.addEventListener('dragleave', (e) => {
  if (!hasFiles(e)) return;
  dragDepth = Math.max(0, dragDepth - 1);
  if (!dragDepth) dropZone.classList.remove('open');
});
// without this the window would navigate to the dropped file
window.addEventListener('dragover', (e) => {
  e.preventDefault();
  if (e.dataTransfer) e.dataTransfer.dropEffect = hasFiles(e) ? 'copy' : 'none';
});
window.addEventListener('drop', async (e) => {
  e.preventDefault();
  dragDepth = 0;
  dropZone.classList.remove('open');
  const files = Array.from((e.dataTransfer && e.dataTransfer.files) || []);
  if (!files.length) return;
  const paths = files.map((f) => api.pathForFile(f)).filter(Boolean);
  const res = await api.addMods(paths, profileState.active);
  if (!res.ok) {
    toast(res.error, true);
    return;
  }
  const { added, skipped, profile } = res.value;
  if (added.length) {
    toast(`${plural(added.length, 'mod', 'mods')} added to "${profile}"${skipped.length ? ` · ${skipped.length} skipped` : ''}`);
    if (currentView === 'mods') renderMods(); else showView('mods');
  } else {
    toast(skipped[0] || 'Nothing to add', true);
  }
  skipped.forEach((line) => console.warn('Skipped mod:', line));
});
