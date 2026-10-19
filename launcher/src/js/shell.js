'use strict';

/* The launcher shell in the Carbon Launcher's style: hotbar navigation (keys 1–9, mouse wheel), the XP bar and task
   line for progress, numbered page headings. */

const hotButtons = [...$$('#hotbar .nav-item')];
hotButtons.forEach((b) => b.prepend(L.picon(b.dataset.icon)));
$('.tb-logo').append(L.picon('hex'));

let hotNameTimer = 0;
let hotFirst = true;
function hotbarSync(slot) {
  const btn = hotButtons.find((b) => b.dataset.view === slot);
  if (!btn) return;
  $('#hot-sel').style.transform = `translate(${btn.offsetLeft - 4}px, ${btn.offsetTop - 4}px)`;
  if (hotFirst) {
    hotFirst = false;
    return;
  }
  const name = $('#hot-name');
  name.textContent = btn.dataset.name;
  name.classList.add('show');
  clearTimeout(hotNameTimer);
  hotNameTimer = setTimeout(() => name.classList.remove('show'), 1400);
}
window.addEventListener('resize', () => {
  const on = hotButtons.find((b) => b.classList.contains('active'));
  if (on) $('#hot-sel').style.transform = `translate(${on.offsetLeft - 4}px, ${on.offsetTop - 4}px)`;
});
requestAnimationFrame(() => hotbarSync('home'));

$('#hotbar').addEventListener('wheel', (e) => {
  e.preventDefault();
  const i = hotButtons.findIndex((b) => b.classList.contains('active'));
  const n = Math.max(0, Math.min(hotButtons.length - 1, i + (e.deltaY > 0 ? 1 : -1)));
  showView(hotButtons[n].dataset.view);
}, { passive: false });

document.addEventListener('keydown', (e) => {
  if (L.typing(e) || e.ctrlKey || e.altKey || e.metaKey) return;
  if ($('.modal-backdrop.open') || ($('#palette') && $('#palette').classList.contains('open'))) return;
  const n = parseInt(e.key, 10);
  if (n >= 1 && n <= hotButtons.length) showView(hotButtons[n - 1].dataset.view);
});

// numbered headings like the Carbon Launcher: "02 — PROFILES" above a big pixel title
hotButtons.forEach((b, i) => {
  const head = $(`#view-${b.dataset.view} .page-head`);
  if (!head) return;
  const block = head.firstElementChild;
  if (!block || block.querySelector('.rule')) return;
  block.prepend(el('div', { class: 'rule' }, [el('b', { text: String(i + 1).padStart(2, '0') }), ` ${b.dataset.name}`]));
});

// ---------------------------------------------------------------- XP bar: every download and start shows here

let xpHide = 0;
/** t: { title, stage, fraction (0..1 or null), state: 'running' | 'done' | 'error' } */
function xpTask(t) {
  const xp = $('#xp');
  const line = $('#taskline');
  clearTimeout(xpHide);
  xp.classList.remove('idle');
  const f = t.fraction == null ? null : Math.max(0, Math.min(1, t.fraction));
  xp.classList.toggle('busy', f == null && t.state === 'running');
  $('#xp-fill').style.width = f == null ? '' : `${(f * 100).toFixed(1)}%`;
  $('#task-lvl').textContent = f == null ? '…' : String(Math.round(f * 100));
  line.classList.add('show');
  line.classList.toggle('error', t.state === 'error');
  const text = $('#task-text');
  const status = t.state === 'error' ? 'failed' : t.state === 'done' ? 'done' : t.stage || '';
  text.replaceChildren(el('b', { text: t.title }), status ? ` · ${status}` : '');
  if (t.state !== 'running') {
    xpHide = setTimeout(() => {
      line.classList.remove('show');
      xp.classList.add('idle');
      setTimeout(() => { if (xp.classList.contains('idle')) $('#xp-fill').style.width = '0'; }, 400);
    }, t.state === 'error' ? 3500 : 1400);
  }
}

api.onProgress(({ label, progress }) => {
  if (state === 'running') return;
  xpTask({ title: 'Starting Minecraft', stage: label, fraction: progress > 0 && progress <= 1 ? progress : null, state: 'running' });
});
api.onState((s) => {
  if (s === 'running') xpTask({ title: 'Minecraft', stage: 'running', fraction: 1, state: 'done' });
  else if (s === 'crashed') xpTask({ title: 'Minecraft', fraction: 1, state: 'error' });
});
api.onPrepare((p) => {
  if (p.done) xpTask({ title: 'Downloading game files', fraction: 1, state: p.error ? 'error' : 'done' });
  else xpTask({ title: 'Downloading game files', stage: p.label, fraction: p.progress > 0 && p.progress <= 1 ? p.progress : null, state: 'running' });
});
api.onModsProgress((p) => {
  if (!p || !p.label) return;
  xpTask({ title: p.label, fraction: p.progress > 0 && p.progress <= 1 ? p.progress : null, state: p.progress >= 1 ? 'done' : 'running' });
});

// ---------------------------------------------------------------- desktop shortcuts: --launch=<profile>

async function runShortcut(req) {
  if (!req || !req.id) return;
  if (!account) return;
  if (state !== 'idle') return toast('Minecraft is already running.', true);
  if (req.id !== profileState.active) {
    profileState = await api.selectProfile(req.id);
    refreshProfiles();
  }
  if (req.world) pendingQuickPlay = { world: req.world };
  else if (req.server) pendingQuickPlay = { server: req.server };
  showView('home');
  $('#btn-play').click();
}
api.onShortcut(runShortcut);
