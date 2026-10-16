'use strict';

/* Mods page. */

async function renderMods() {
  const p = activeProfile();
  $('#mods-sub').textContent = `Profile "${p ? p.name : ''}" · Fabric mods for ${mcOfProfile(p)}. Drop .jar files anywhere on the launcher to add them.`;
  const list = $('#mod-list');
  if (!list.children.length || list.querySelector('.empty')) {
    list.replaceChildren(...[0, 1, 2, 3].map(() => el('div', { class: 'skeleton row' })));
  }
  const mods = await api.listMods(profileState.active);
  if (!mods.length) {
    list.replaceChildren(el('div', { class: 'empty' }, [el('b', { text: 'No mods yet' }), el('span', { text: 'Onix, Fabric API and the performance pack are installed on the first launch. Add more with “Add from Modrinth”.' })]));
    return;
  }
  list.replaceChildren(...mods.map((mod) => {
    const input = el('input', { type: 'checkbox' });
    input.checked = mod.enabled;
    input.disabled = mod.core;
    input.addEventListener('change', async () => {
      const res = await api.toggleMod(mod.file, profileState.active);
      if (!res.ok) toast(res.error, true);
      renderMods();
    });
    return el('div', { class: `trow${mod.enabled ? '' : ' off'}` }, [
      modIcon(mod.modrinth && mod.modrinth.icon, mod.modrinth ? mod.modrinth.title : mod.name),
      el('div', { class: 'tmain' }, [el('b', { text: mod.modrinth ? mod.modrinth.title : mod.name }), el('span', { text: mod.core ? 'Managed automatically' : mod.managed ? 'Kept up to date by the launcher' : mod.modrinth ? `Modrinth · ${mod.modrinth.version || ''}` : 'Your mod' })]),
      mod.core ? el('span', { class: 'tag', text: 'Required' }) : null,
      el('label', { class: 'switch' }, [input, el('i')]),
    ]);
  }));
}
$('#btn-mods-folder').addEventListener('click', () => api.openFolder('mods'));
