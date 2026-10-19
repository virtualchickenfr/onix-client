'use strict';

/* Discover: Modrinth for every profile – mods, resource packs, shaders and modpacks (from the Carbon Launcher). */

const dc = { type: 'mod', sort: 'relevance', offset: 0, total: 0, hits: [], installed: new Set(), busy: new Set(), seq: 0, target: null, opened: false };

const DC_EMPTY = {
  mod: 'Only Fabric mods for the version of the chosen profile are shown.',
  resourcepack: 'Only resource packs for the version of the chosen profile are shown.',
  shader: 'Only Iris shaders for the version of the chosen profile are shown.',
  modpack: 'Modpacks become a new profile with Onix.',
};

function renderTargets() {
  const sel = $('#dc-target');
  if (!dc.target || !profileState.profiles.some((p) => p.id === dc.target)) dc.target = profileState.active;
  sel.replaceChildren(...profileState.profiles.map((p) => el('option', { value: p.id, text: `${p.name} · ${mcOfProfile(p)}`, selected: p.id === dc.target })));
  sel.disabled = dc.type === 'modpack';
}

function openDiscover() {
  renderTargets();
  if (!dc.opened) {
    dc.opened = true;
    dcSearch(true);
  }
}

$('#dc-target').addEventListener('change', () => {
  dc.target = $('#dc-target').value;
  dcSearch(true);
});
$$('#dc-tabs button').forEach((b) => b.addEventListener('click', () => {
  dc.type = b.dataset.type;
  $$('#dc-tabs button').forEach((x) => x.classList.toggle('active', x === b));
  $('#dc-query').placeholder = { mod: 'Search mods…', resourcepack: 'Search resource packs…', shader: 'Search shaders…', modpack: 'Search modpacks…' }[dc.type];
  renderTargets();
  dcSearch(true);
}));
let dcTimer;
$('#dc-query').addEventListener('input', () => {
  clearTimeout(dcTimer);
  dcTimer = setTimeout(() => dcSearch(true), 280);
});
$('#dc-sort').addEventListener('change', () => {
  dc.sort = $('#dc-sort').value;
  dcSearch(true);
});

async function dcInstalled() {
  if (dc.type === 'modpack') return new Set();
  const res = dc.type === 'mod' ? await api.installedModrinth(dc.target) : await api.installedAssets(dc.target, dc.type);
  return new Set(res && res.ok ? res.value : []);
}

async function dcSearch(reset) {
  const seq = ++dc.seq;
  const body = $('#dc-body');
  if (reset) {
    dc.offset = 0;
    dc.hits = [];
    body.replaceChildren(el('div', { class: 'mm-grid' }, [0, 1, 2, 3, 4, 5].map(() => el('div', { class: 'skeleton mm-skel' }))));
    dc.installed = await dcInstalled();
  }
  const res = await api.searchModrinth({ query: $('#dc-query').value.trim(), type: dc.type, offset: dc.offset, sort: dc.sort, profileId: dc.target });
  if (seq !== dc.seq) return;
  if (!res.ok) {
    body.replaceChildren(el('div', { class: 'empty' }, [
      el('b', { text: 'Modrinth could not be reached' }),
      el('span', { text: res.error }),
      el('button', { class: 'btn sm', text: 'Try again', onclick: () => dcSearch(true) }),
    ]));
    return;
  }
  dc.hits.push(...res.value.hits);
  dc.total = res.value.total;
  dc.offset = dc.hits.length;
  dcRender();
}

function dcAction(h) {
  if (dc.busy.has(h.id)) return el('button', { class: 'btn sm', disabled: true }, [el('span', { class: 'ring' }), 'Installing…']);
  if (dc.type === 'modpack') return el('button', { class: 'btn sm primary', onclick: () => dcInstall(h) }, [icon('plus'), 'New profile']);
  if (h.managed && dc.type === 'mod') return el('span', { class: 'tag', text: 'Included' });
  if (dc.installed.has(h.id)) return el('button', { class: 'btn sm', disabled: true }, [icon('check'), 'Installed']);
  return el('button', { class: 'btn sm primary', onclick: () => dcInstall(h) }, [icon('download'), 'Install']);
}

function dcRender() {
  const body = $('#dc-body');
  if (!dc.hits.length) {
    body.replaceChildren(el('div', { class: 'empty' }, [el('b', { text: 'Nothing found' }), el('span', { text: DC_EMPTY[dc.type] })]));
    return;
  }
  const keep = body.scrollTop;
  const cards = dc.hits.map((h, i) => el('div', { class: 'mm-card dc-card', style: `--i:${i % 20}` }, [
    modIcon(h.icon, h.title),
    el('div', { class: 'mm-info' }, [
      el('div', { class: 'mm-title' }, [el('b', { text: h.title }), el('span', { text: `by ${h.author}` })]),
      el('p', { text: h.description }),
      el('div', { class: 'mm-meta' }, [
        el('span', {}, [icon('download'), fmtCount(h.downloads)]),
        ...h.categories.map((c) => el('span', { class: 'tag', text: c })),
      ]),
    ]),
    dcAction(h),
  ]));
  const more = dc.hits.length < dc.total
    ? el('button', { class: 'btn sm mm-more', text: `Load more (${dc.total - dc.hits.length} left)`, onclick: () => dcSearch(false) })
    : null;
  body.replaceChildren(el('div', { class: 'mm-grid stagger' }, cards), more);
  body.scrollTop = keep;
}

async function dcInstall(h) {
  dc.busy.add(h.id);
  dcRender();
  let res;
  if (dc.type === 'modpack') res = await api.installModpack(h.id);
  else if (dc.type === 'mod') res = await api.installModrinth(dc.target, h.id);
  else res = await api.installAsset(dc.target, dc.type, h.id);
  dc.busy.delete(h.id);
  if (!res.ok) {
    toast(res.error, true);
    dcRender();
    return;
  }
  if (dc.type === 'modpack') {
    afterImport(res.value);
    return;
  }
  const { installed, skipped, warnings } = res.value;
  const extra = installed.filter((i) => i.dependency).length;
  if (installed.length) toast(`${h.title} installed${extra ? ` + ${plural(extra, 'dependency', 'dependencies')}` : ''}`);
  else if (skipped.length) toast(skipped[0]);
  if (warnings.length) setTimeout(() => toast(warnings[0], true), 2600);
  dc.installed.add(h.id);
  if (typeof profileViewChanged === 'function') profileViewChanged(dc.target);
  dcRender();
}
