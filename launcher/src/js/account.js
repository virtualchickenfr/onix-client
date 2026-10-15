'use strict';

/* Microsoft accounts in the sidebar: the active one, plus every account that has signed in
   on this launcher – switching is one click, no new sign-in. */

let accounts = [];

/** Skin head; the first letter of the name shows until (or if) the image loads. */
const avatar = (a, size = 56) => {
  const img = el('img', { src: `https://mc-heads.net/avatar/${a.uuid}/${size}`, alt: '' });
  img.onerror = () => img.remove();
  return el('span', { class: 'acc-avatar', text: (a.name || '?')[0].toUpperCase() }, [img]);
};

function ago(ms) {
  if (!ms) return 'Not used yet';
  const min = Math.round((Date.now() - ms) / 60000);
  if (min < 2) return 'Used just now';
  if (min < 60) return `Used ${min} min ago`;
  const h = Math.round(min / 60);
  if (h < 24) return `Used ${h} h ago`;
  const d = Math.round(h / 24);
  return `Used ${d} ${d === 1 ? 'day' : 'days'} ago`;
}

async function loadAccounts() {
  accounts = await api.accounts();
  account = accounts.find((a) => a.active) || null;
  renderAccount();
}

function renderAccount() {
  const box = $('#account');
  box.replaceChildren();
  if (account) {
    const n = accounts.length;
    const pop = el('div', { class: 'popover account-pop', id: 'account-pop' });
    const card = el('button', { class: 'account-card', title: 'Switch account', onclick: (e) => {
      e.stopPropagation();
      renderAccountPop(pop);
      pop.classList.toggle('open');
      card.classList.toggle('open', pop.classList.contains('open'));
    } }, [
      avatar(account),
      el('div', { class: 'account-text' }, [
        el('div', { class: 'account-name', text: account.name }),
        el('div', { class: 'account-sub', text: n > 1 ? `${n} accounts signed in` : 'Signed in' }),
      ]),
      icon('chevron-down'),
    ]);
    box.append(pop, card);
  } else {
    const ms = el('span', { class: 'ms' }, ['#f25022', '#7fba00', '#00a4ef', '#ffb900'].map((c) => el('i', { style: `background:${c}` })));
    box.append(el('button', { class: 'login', onclick: login }, [ms, 'Sign in with Microsoft']));
  }
  updatePlay();
}

function renderAccountPop(pop) {
  const head = el('div', { class: 'pop-head' }, [
    el('b', { text: 'Accounts' }),
    el('span', { text: plural(accounts.length, 'account has signed in', 'accounts have signed in') }),
  ]);
  const rows = accounts.map((a) => el('div', { class: `pop-item account-row${a.active ? ' active' : ''}`, onclick: () => switchAccount(a) }, [
    avatar(a, 40),
    el('span', { class: 't' }, [el('b', { text: a.name }), el('span', { text: a.active ? 'Active' : ago(a.lastUsed) })]),
    a.active ? icon('check') : null,
    el('button', { class: 'remove', title: `Remove ${a.name} from this launcher`, onclick: (e) => { e.stopPropagation(); removeAccount(a); } }, [icon('x')]),
  ]));
  pop.replaceChildren(
    head,
    ...rows,
    el('div', { class: 'pop-sep' }),
    el('button', { class: 'pop-item', onclick: () => { closeAccountPop(); login(); } }, [icon('plus'), el('span', { class: 't', text: 'Add another account' })]),
  );
}

function closeAccountPop() {
  const pop = $('#account-pop');
  if (pop) pop.classList.remove('open');
  const card = $('.account-card');
  if (card) card.classList.remove('open');
}
document.addEventListener('click', (e) => {
  const pop = $('#account-pop');
  if (pop && !pop.contains(e.target)) closeAccountPop();
});

async function switchAccount(a) {
  closeAccountPop();
  if (a.active) return;
  const res = await api.switchAccount(a.uuid);
  if (!res.ok) {
    toast(res.error, true);
    return;
  }
  toast(`Playing as ${res.value.name}`);
  await loadAccounts();
}

async function removeAccount(a) {
  const res = await api.removeAccount(a.uuid);
  if (!res.ok) {
    toast(res.error, true);
    return;
  }
  toast(`${a.name} removed from this launcher`);
  await loadAccounts();
  const pop = $('#account-pop');
  if (pop && accounts.length) {
    renderAccountPop(pop);
    pop.classList.add('open');
  }
}

async function login() {
  const res = await api.login();
  if (res.ok) toast(`Welcome, ${res.value.name}`);
  else toast(res.error, true);
  await loadAccounts();
}

