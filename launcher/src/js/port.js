'use strict';

/*
 * Profile → Other Version: the whole profile (worlds, Onix modules and HUD, key binds, settings, servers, mod configs,
 * packs) as a copy for another Minecraft version, every mod swapped for its version there (lib/port.js).
 */

const port = { id: null, seq: 0, plan: null };

function portVersions() {
  const list = (info.versions || []).filter((v) => /^\d+\.\d+(\.\d+)?$/.test(v.id));
  return list.length ? list : [{ id: '1.21.11', onix: true }, { id: '1.21.1', onix: true }];
}

async function openPort(p) {
  if (!p) return;
  port.id = p.id;
  port.plan = null;
  $('#port-from').textContent = p.name;
  const from = mcOfProfile(p);
  const select = $('#port-mc');
  const opts = portVersions().filter((v) => v.id !== from).map((v) => el('option', { value: v.id, text: `${v.id}${v.onix ? ' · with Onix' : ''}` }));
  select.replaceChildren(...opts);
  // newest Onix version that is not the profile's own
  const best = portVersions().find((v) => v.onix && v.id !== from);
  if (best) select.value = best.id;
  $('#port-status').textContent = '';
  openModal('#port-modal');
  loadPortPlan();
}

$('#port-mc').addEventListener('change', loadPortPlan);

async function loadPortPlan() {
  const seq = ++port.seq;
  const p = profileState.profiles.find((x) => x.id === port.id);
  const mc = $('#port-mc').value;
  $('#port-name').value = `${p.name.slice(0, 16)} ${mc}`.slice(0, 24);
  $('#port-run').disabled = true;
  $('#port-run').textContent = `Create Copy for ${mc}`;
  $('#port-plan').replaceChildren(loadingBlock('Checking every mod on Modrinth…'));
  const res = await api.portPlan(port.id, mc);
  if (seq !== port.seq) return;
  if (!res.ok) {
    $('#port-plan').replaceChildren(emptyBlock('Could not check the mods', res.error));
    return;
  }
  port.plan = res.value;
  const c = res.value.counts;
  const older = mc.localeCompare(mcOfProfile(p), undefined, { numeric: true }) < 0;
  const tiles = el('div', { class: 'port-tiles' }, [
    portTile(c.same + c.update, 'mods come along', `${c.update} updated · ${c.same} unchanged`, 'ok'),
    portTile(c.missing, 'not out yet', 'kept, switched off', c.missing ? 'warn' : 'ok'),
    portTile(c.worlds, c.worlds === 1 ? 'world' : 'worlds', 'copied', 'ok'),
    portTile(c.resourcepacks + c.shaderpacks, 'packs', 'resource and shader packs', 'ok'),
  ]);
  const rows = res.value.mods.map((m) => el('div', { class: `port-row ${m.status}` }, [
    el('b', { text: m.name }),
    el('span', { text: m.status === 'update' ? `→ ${m.to}` : m.status === 'same' ? 'works as it is' : res.value.offline ? 'Modrinth not reachable' : `not out for ${mc} yet – switched off` }),
  ]));
  const notes = [
    el('p', { class: 'port-note', text: 'Onix, Fabric API and the performance pack are set up for the new version on its own.' }),
    older ? el('p', { class: 'port-note warn', text: `${mc} is older than ${mcOfProfile(p)}: worlds that were opened in a newer version may not load. They stay safe in your original profile.` }) : null,
  ];
  $('#port-plan').replaceChildren(tiles, rows.length ? el('div', { class: 'port-rows' }, rows) : el('p', { class: 'port-note', text: 'No mods of your own in this profile.' }), ...notes.filter(Boolean));
  $('#port-run').disabled = false;
}

function portTile(n, label, sub, kind) {
  return el('div', { class: `port-tile ${kind}` }, [el('b', { text: String(n) }), el('span', { text: label }), el('small', { text: sub })]);
}

$('#port-run').addEventListener('click', async () => {
  const mc = $('#port-mc').value;
  const btn = $('#port-run');
  btn.disabled = true;
  btn.replaceChildren(gem(), 'Copying…');
  $('#port-status').textContent = 'Copying the profile and updating the mods…';
  const res = await api.portProfile(port.id, mc, $('#port-name').value);
  btn.textContent = `Create Copy for ${mc}`;
  btn.disabled = false;
  if (!res.ok) {
    $('#port-status').textContent = '';
    return toast(res.error, true);
  }
  const r = res.value;
  closeModal('#port-modal');
  profileState = r.state;
  refreshProfiles();
  const parts = [`${r.updated.length} updated`, `${r.kept.length} unchanged`];
  if (r.disabled.length) parts.push(`${r.disabled.length} switched off`);
  if (r.failed.length) parts.push(`${r.failed.length} failed: ${r.failed.map((f) => f.name).join(', ')}`);
  toast(`Ready for ${mc} · ${parts.join(' · ')}`, r.failed.length > 0);
  openProfile(r.id);
});

$('#pv-port').addEventListener('click', () => openPort(pvProfile()));
