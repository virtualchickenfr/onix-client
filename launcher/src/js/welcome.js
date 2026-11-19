'use strict';

/*
 * Mod bundles and the welcome steps.
 *
 * Bundles (lib/bundles.js) are hand-picked sets of Modrinth mods that go into a profile with one click: on the first
 * start in the welcome steps, and any time later in Profile → Bundles.
 *
 * The welcome steps show once (settings.welcomed): 1. hello, 2. sign in, 3. pick a look, 4. pick bundles. Everything
 * can be skipped; the choices are installed into the active profile at the end.
 */

/** A bundle card: colour strip, name, text, mod icons and a switch (or a button when onInstall is given). */
function bundleCard(b, { selected = false, onToggle, onInstall } = {}) {
  const icons = el('div', { class: 'bundle-mods' }, b.mods.map((m) => el('span', { class: `bundle-mod${m.installed ? ' have' : ''}`, title: `${m.title}${m.installed ? ' · installed' : ''}` },
    [m.icon ? el('img', { src: m.icon, alt: '', loading: 'lazy' }) : el('i', { text: (m.title || '?').slice(0, 1).toUpperCase() })])));
  const names = el('p', { class: 'bundle-names', text: b.mods.map((m) => m.title).join(' · ') });
  let action;
  if (onInstall) {
    action = b.installed
      ? el('span', { class: 'tag', text: 'Installed' })
      : el('button', { class: 'btn sm primary', onclick: (e) => onInstall(b, e.currentTarget) }, [icon('plus'), 'Add']);
  } else {
    const box = el('input', { type: 'checkbox' });
    box.checked = selected;
    box.addEventListener('change', () => onToggle(b, box.checked));
    action = el('label', { class: 'switch' }, [box, el('i')]);
  }
  return el('article', { class: `bundle${selected ? ' on' : ''}`, style: `--bundle:${b.color}` }, [
    el('div', { class: 'bundle-top' }, [el('div', {}, [el('b', { text: b.name }), el('span', { text: b.about })]), action]),
    icons,
    names,
  ]);
}

function bundleResultText(res) {
  const parts = [];
  if (res.installed.length) parts.push(`${plural(res.installed.length, 'mod', 'mods')} added`);
  if (res.missing.length) parts.push(`not yet for this version: ${res.missing.join(', ')}`);
  if (res.failed.length) parts.push(`failed: ${res.failed.map((f) => f.title).join(', ')}`);
  return parts.join(' · ') || 'Nothing new to add';
}

// ---------------------------------------------------------------- Profile → Bundles

async function renderPvBundles(seq) {
  const id = pv.id;
  tools(el('button', { class: 'btn sm', onclick: () => openMods(id, 'browse') }, [icon('search'), 'Browse Modrinth']));
  const res = await api.bundles(id);
  if (stale(seq)) return;
  const body = $('#pv-body');
  if (!res.ok) {
    body.replaceChildren(emptyBlock('Bundles could not load', res.error));
    return;
  }
  const install = async (b, button) => {
    button.disabled = true;
    button.replaceChildren(gem(), 'Adding…');
    const r = await api.installBundles(id, [b.id]);
    if (!r.ok) toast(r.error, true);
    else toast(`${b.name}: ${bundleResultText(r.value)}`, r.value.failed.length > 0);
    if (!stale(seq)) renderTab();
    refreshCounts();
  };
  body.replaceChildren(
    el('p', { class: 'bundle-intro', text: 'Hand-picked mod sets for this profile. One click adds every mod with what it needs; a mod that is not out for this Minecraft version yet is skipped.' }),
    el('div', { class: 'bundles' }, res.value.map((b) => bundleCard(b, { onInstall: install }))),
  );
}

// ---------------------------------------------------------------- welcome steps

async function maybeWelcome() {
  const s = await api.getSettings();
  if (s.welcomed) return;
  const picked = new Set(['qol']);
  let step = 0;
  let bundles = null;
  const back = el('div', { class: 'welcome', role: 'dialog', 'aria-modal': 'true', 'aria-label': 'Welcome to Onix' });
  const card = el('div', { class: 'welcome-card' });
  back.append(card);
  document.body.append(back);
  requestAnimationFrame(() => back.classList.add('open'));

  const finish = async (install) => {
    await api.setSettings({ welcomed: true });
    back.classList.remove('open');
    setTimeout(() => back.remove(), 300);
    const p = activeProfile();
    if (install && p && picked.size && typeof api.installBundles === 'function' && bundles && bundles.length) {
      toast(`Adding ${plural(picked.size, 'bundle', 'bundles')} to ${p.name}…`);
      const r = await api.installBundles(p.id, [...picked]);
      if (!r.ok) toast(r.error, true);
      else toast(bundleResultText(r.value), r.value.failed.length > 0);
    }
  };
  const dots = () => el('div', { class: 'welcome-dots' }, [0, 1, 2, 3].map((i) => el('i', { class: i === step ? 'on' : '' })));
  const nav = (nextLabel, onNext, { skip = true } = {}) => el('div', { class: 'welcome-nav' }, [
    step > 0 ? el('button', { class: 'btn', text: 'Back', onclick: () => { step--; render(); } }) : el('button', { class: 'btn', text: 'Skip all', onclick: () => finish(false) }),
    dots(),
    el('div', { class: 'btn-row' }, [
      skip && step > 0 ? el('button', { class: 'btn', text: 'Skip', onclick: () => { step++; render(); } }) : null,
      el('button', { class: 'btn primary', text: nextLabel, onclick: onNext }),
    ]),
  ]);

  const steps = [
    () => [
      el('img', { class: 'welcome-logo', src: 'assets/logo.png', alt: '' }),
      el('h2', { text: 'Welcome to Onix' }),
      el('div', { class: 'welcome-lang', 'data-no-i18n': true }, [['en', 'English'], ['de', 'Deutsch']].map(([id, label]) => el('button', {
        class: I18N.lang === id ? 'on' : '',
        text: label,
        onclick: async () => {
          if (I18N.lang === id) return;
          await api.setSettings({ language: id });
          api.reload();
        },
      }))),
      el('p', { text: 'Let’s get you set up in a few clicks: sign in, choose a look and pick the mods you want. You can change all of it later.' }),
      nav('Let’s go', () => { step++; render(); }),
    ],
    () => [
      el('h2', { text: 'Sign in' }),
      el('p', { text: account ? `Signed in as ${account.name}.` : 'Sign in with the Microsoft account that owns Minecraft. You can also do this later.' }),
      account
        ? el('div', { class: 'welcome-account' }, [icon('check'), el('b', { text: account.name })])
        : el('button', { class: 'btn primary', onclick: async (e) => { e.currentTarget.disabled = true; await login(); render(); } }, [icon('arrow'), 'Sign in with Microsoft']),
      nav('Next', () => { step++; render(); }),
    ],
    () => [
      el('h2', { text: 'Choose a look' }),
      el('p', { text: 'The design of your start page and of the main menu in game. You can change it any time in the settings.' }),
      // the choice shows at once; saving runs in the background (a slow save never holds the steps up)
      el('div', { class: 'designs compact' }, DESIGNS.map((d) => designCard(d, (x) => { selectDesign(x.id).catch(() => {}); render(); }))),
      nav('Next', () => { step++; render(); }),
    ],
    () => {
      const list = el('div', { class: 'bundles compact' });
      const fill = () => list.replaceChildren(...bundles.map((b) => bundleCard(b, {
        selected: picked.has(b.id),
        onToggle: (x, on) => { if (on) picked.add(x.id); else picked.delete(x.id); fill(); },
      })));
      if (bundles) fill();
      else {
        list.append(loadingBlock('Loading bundles…'));
        const p = activeProfile();
        Promise.resolve(typeof api.bundles === 'function' ? api.bundles(p && p.id) : { ok: false }).catch(() => ({ ok: false })).then((r) => {
          bundles = r && r.ok ? r.value : [];
          if (step === 3) (bundles.length ? fill() : list.replaceChildren(el('p', { text: 'Bundles need an internet connection. You find them later in every profile.' })));
        });
      }
      return [
        el('h2', { text: 'Pick your mods' }),
        el('p', { text: 'Onix and the performance pack are always included. Add more with one click:' }),
        list,
        nav('Finish', () => finish(true), { skip: false }),
      ];
    },
  ];
  const render = () => {
    try {
      card.replaceChildren(...steps[step]());
    } catch (e) {
      // a step that cannot be shown is skipped, so the welcome never gets stuck
      console.error('welcome step', step, e);
      if (step < steps.length - 1) {
        step++;
        render();
      } else finish(false);
    }
  };
  render();
}
