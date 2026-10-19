'use strict';

/* Skin & cape of the signed-in Microsoft account and a local skin library (from the Carbon Launcher).
   Hover a skin or cape to try it on, click to wear it. */

const sk = { prof: null, lib: [], busy: false, failed: null, preview: null, loaded: false };

async function openSkins() {
  sk.preview = null;
  sk.failed = null;
  renderSkins();
  const lib = await api.skinLibrary();
  sk.lib = lib.ok ? lib.value : [];
  if (!account) {
    renderSkins();
    return;
  }
  const res = await api.skinProfile();
  if (res.ok) sk.prof = res.value;
  else sk.failed = res.error;
  sk.loaded = true;
  renderSkins();
}

async function skRun(promise, okText) {
  sk.busy = true;
  renderSkins();
  const res = await promise;
  sk.busy = false;
  sk.preview = null;
  if (!res.ok) {
    toast(res.error, true);
  } else {
    sk.prof = res.value;
    sk.failed = null;
    const lib = await api.skinLibrary();
    if (lib.ok) sk.lib = lib.value;
    if (okText) toast(okText);
  }
  renderSkins();
}

async function skImport(promise) {
  const res = await promise;
  if (!res.ok) return toast(res.error, true);
  const lib = await api.skinLibrary();
  if (lib.ok) sk.lib = lib.value;
  if (res.value.errors.length) toast(res.value.errors[0], true);
  if (res.value.added.length) toast(res.value.added.length === 1 ? 'Skin added – click it to wear it' : `${res.value.added.length} skins added`);
  renderSkins();
}

$('#sk-upload').addEventListener('click', () => skImport(api.importSkins()));

function skinCanvas(attrs) {
  return el('canvas', attrs);
}

function renderSkins() {
  const body = $('#sk-body');
  if (!account) {
    body.replaceChildren(el('div', { class: 'note sk-note' }, [
      L.picon('key'),
      el('div', {}, [el('b', { text: 'Only with a Microsoft account' }), el('span', { text: 'Skins and capes belong to your Minecraft account. Sign in first.' }),
        el('button', { class: 'btn sm primary', text: 'Sign in with Microsoft', onclick: login })]),
    ]));
    return;
  }
  const prof = sk.prof;
  const slim = prof && prof.skin && prof.skin.variant === 'slim';
  const status = sk.failed ? sk.failed : !sk.loaded && !prof ? 'Loading your profile…' : sk.busy ? 'Sending to Mojang…' : '';

  const stage = el('div', { class: 'sk-stage' }, [
    el('div', { class: `sk-views${sk.busy ? ' busy' : ''}` }, [
      el('figure', {}, [skinCanvas({ id: 'sk-front' }), el('figcaption', { text: 'Front' })]),
      el('figure', {}, [skinCanvas({ id: 'sk-back' }), el('figcaption', { text: 'Back' })]),
      status ? el('div', { class: 'sk-status', text: status }) : null,
    ]),
    el('div', { class: 'sk-who' }, [el('b', { text: (prof && prof.name) || account.name }), el('small', { text: sk.preview ? 'Preview – click to wear it' : 'Your current skin' })]),
    el('span', { class: 'sk-label', text: 'Model' }),
    el('div', { class: 'sk-seg' }, ['classic', 'slim'].map((v) => el('button', {
      class: `btn sm${(v === 'slim') === slim ? ' primary' : ''}`,
      disabled: !(prof && prof.skin) || sk.busy,
      onclick: () => { if ((v === 'slim') !== slim) skRun(api.skinVariant(v), v === 'slim' ? 'Model: Slim (3 px arms)' : 'Model: Classic (4 px arms)'); },
    }, [el('span', { text: v === 'slim' ? 'Slim' : 'Classic' }), el('small', { text: v === 'slim' ? '3 px arms' : '4 px arms' })]))),
    el('div', { class: 'sk-acts' }, [
      el('button', {
        class: 'btn sm',
        disabled: !(prof && prof.skin),
        title: 'Save your current skin to the library',
        onclick: async () => {
          const r = await api.keepSkin();
          if (!r.ok) return toast(r.error, true);
          const lib = await api.skinLibrary();
          if (lib.ok) sk.lib = lib.value;
          toast('Your current skin is in the library');
          renderSkins();
        },
      }, [L.picon('star'), 'Save']),
      el('button', {
        class: 'btn sm',
        disabled: !(prof && prof.skin) || sk.busy,
        title: 'Mojang gives you a default character again; your skin stays in the library',
        onclick: () => { if (confirm('Use a default skin? Your current skin is saved to your library first.')) skRun(api.resetSkin(), 'Skin reset'); },
      }, [L.picon('refresh'), 'Reset']),
    ]),
  ]);

  const grid = el('div', { class: 'sk-grid dropzone', id: 'sk-grid' }, sk.lib.length ? sk.lib.map((s) => {
    const on = prof && prof.skin && prof.skin.sha1 === s.sha1;
    const card = el('div', { class: `sk-card${on ? ' on' : ''}`, tabindex: '0', title: s.name }, [
      el('div', { class: 'sk-thumb' }, [el('canvas', { 'data-skin': s.sha1 })]),
      el('b', { text: s.name }),
      el('small', { text: `${s.variant === 'slim' ? 'Slim' : 'Classic'}${on ? ' · wearing' : ''}` }),
      el('div', { class: 'sk-card-acts' }, [
        el('button', {
          class: 'icon-btn',
          title: 'Rename',
          onclick: async (e) => {
            e.stopPropagation();
            const name = prompt('Name of the skin', s.name);
            if (name == null) return;
            const r = await api.renameSkin(s.sha1, name);
            if (r.ok) sk.lib = r.value;
            renderSkins();
          },
        }, [L.picon('pencil')]),
        el('button', {
          class: 'icon-btn',
          title: 'Remove from the library',
          onclick: async (e) => {
            e.stopPropagation();
            if (!confirm(`Remove "${s.name}" from your library? Your Minecraft skin stays as it is.`)) return;
            const r = await api.removeSkin(s.sha1);
            if (r.ok) sk.lib = r.value;
            renderSkins();
          },
        }, [L.picon('trash')]),
      ]),
    ]);
    card.addEventListener('mouseenter', () => { if (!sk.busy) { sk.preview = { skin: s.data, slim: s.variant === 'slim' }; paintSkins(); } });
    card.addEventListener('mouseleave', () => { sk.preview = null; paintSkins(); });
    card.addEventListener('click', () => { if (!sk.busy && !on) skRun(api.applySkin(s.sha1, s.variant), `Now wearing "${s.name}"`); });
    return card;
  }) : [el('div', { class: 'empty' }, [L.picon('person'), el('b', { text: 'No skins yet' }), el('span', { text: 'Add a PNG (64×64) or drop it here. Your current skin is saved here automatically when you change it.' })])]);
  grid.addEventListener('dragover', (e) => { e.preventDefault(); grid.classList.add('drag'); });
  grid.addEventListener('dragleave', (e) => { if (!grid.contains(e.relatedTarget)) grid.classList.remove('drag'); });
  grid.addEventListener('drop', (e) => {
    e.preventDefault();
    grid.classList.remove('drag');
    const files = [...e.dataTransfer.files].filter((f) => /\.png$/i.test(f.name)).map((f) => api.pathForFile(f));
    if (files.length) skImport(api.dropSkins(files));
    else toast('Skins are PNG images of 64×64 pixels.', true);
  });

  let capes = null;
  if (prof) {
    const none = !prof.capes.some((c) => c.active);
    const capeCard = (c) => {
      const on = c ? c.active : none;
      const card = el('div', { class: `sk-cape${on ? ' on' : ''}`, tabindex: '0' }, [
        el('div', { class: 'sk-cape-thumb' }, [c ? el('canvas', { 'data-cape': c.id }) : L.picon('close')]),
        el('b', { text: c ? c.name : 'No cape' }),
      ]);
      card.addEventListener('mouseenter', () => { if (!sk.busy) { sk.preview = { cape: c ? c.data : null }; paintSkins(); } });
      card.addEventListener('mouseleave', () => { sk.preview = null; paintSkins(); });
      card.addEventListener('click', () => { if (!sk.busy && !on) skRun(api.setCape(c ? c.id : null), c ? `Cape: ${c.name}` : 'Cape hidden'); });
      return card;
    };
    capes = prof.capes.length
      ? el('div', { class: 'sk-capes' }, [capeCard(null), ...prof.capes.map(capeCard)])
      : el('div', { class: 'note' }, [L.picon('flag'), el('span', { text: 'This account has no capes yet. Capes from Minecraft events show up here automatically.' })]);
  }

  body.replaceChildren(el('div', { class: 'sk-layout' }, [
    stage,
    el('div', { class: 'sk-side' }, [
      el('h3', { class: 'sk-h' }, ['Your skins ', el('em', { text: String(sk.lib.length) })]),
      grid,
      el('h3', { class: 'sk-h' }, ['Capes ', el('em', { text: prof ? String(prof.capes.length) : '' })]),
      capes,
      el('p', { class: 'hint', text: 'Hover a skin or cape to try it on. Other players see the change after they join again.' }),
    ]),
  ]));
  paintSkins();
}

async function paintSkins() {
  const prof = sk.prof;
  const front = $('#sk-front');
  const back = $('#sk-back');
  if (front && back) {
    const activeCape = prof && prof.capes.find((c) => c.active);
    const pv = sk.preview;
    const skinSrc = (pv && pv.skin) || (prof && prof.skin && prof.skin.data);
    const slim = pv && pv.skin ? pv.slim : prof && prof.skin && prof.skin.variant === 'slim';
    const capeSrc = pv && 'cape' in pv ? pv.cape : activeCape && activeCape.data;
    const [img, cape] = await Promise.all([L.image(skinSrc), L.image(capeSrc)]);
    L.drawSkin(front, img, { slim });
    L.drawSkin(back, img, { slim, back: true, cape });
  }
  for (const c of $$('#sk-body [data-skin]')) {
    const s = sk.lib.find((x) => x.sha1 === c.dataset.skin);
    if (s) L.drawSkin(c, await L.image(s.data), { slim: s.variant === 'slim' });
  }
  for (const c of $$('#sk-body [data-cape]')) {
    const cape = prof && prof.capes.find((x) => x.id === c.dataset.cape);
    L.drawCape(c, await L.image(cape && cape.data));
  }
}
