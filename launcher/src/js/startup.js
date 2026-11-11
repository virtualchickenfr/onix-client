'use strict';

/*
 * Start screen: the "sunken" scene (js/fall.js) runs while the launcher really loads, with the ONIX wordmark and one
 * line of status; then it fades to dark and the launcher fades in. A click or key press skips ahead; with reduced
 * motion it is a plain fade.
 */
(async () => {
  const boot = $('#boot');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  let skip = false;
  const onSkip = () => { skip = true; };
  boot.addEventListener('click', onSkip);
  window.addEventListener('keydown', onSkip, { once: true });
  const bootStatus = (text) => { $('#boot-status').textContent = text; };
  const scene = !reduced && typeof Fall !== 'undefined'
    ? Fall.boot($('#boot-scene'), () => (typeof homeSkin !== 'undefined' && homeSkin.px ? homeSkin : null))
    : null;
  try {
    bootStatus('Starting');
    info = await api.info();
    $('#eyebrow').textContent = `Minecraft ${info.mcVersion}`;
    bootStatus('Checking your account');
    await loadAccounts();
    bootStatus('Loading profiles');
    profileState = await api.getProfiles();
    renderPicker();
    renderEyebrow();
    renderRecent();
    await initSettings();
    await updateBeforeStart(bootStatus);
    bootStatus('Ready');
    // get sign-in, mods and game files ready in the background so Play starts Java right away
    setTimeout(() => { if (state === 'idle' && api.warmup) api.warmup().catch(() => {}); }, 1200);
    // started from a desktop shortcut: play that profile right away
    api.pendingShortcut().then((req) => { if (req) setTimeout(() => runShortcut(req), 400); }).catch(() => {});
  } finally {
    if (scene) {
      // skipping still waits for a click, not for the whole scene
      await Promise.race([scene.finish(false), new Promise((r) => { const c = () => (skip ? r() : setTimeout(c, 50)); c(); })]);
      if (skip) await scene.finish(true);
    }
    boot.classList.add('done');
    document.body.classList.add('boot-enter');
    setTimeout(() => document.body.classList.remove('boot-enter'), 900);
    boot.addEventListener('transitionend', () => boot.remove(), { once: true });
  }
})();

/**
 * Every start: if a new launcher version exists, it is downloaded and installed before the launcher opens, so nobody
 * plays on an old version. No update, no internet or updates switched off: the launcher opens normally.
 */
async function updateBeforeStart(bootStatus) {
  let v;
  try {
    v = await api.updateStatus();
  } catch {
    return;
  }
  if (!v || v.status === 'disabled') return;
  bootStatus('Looking for updates');
  return new Promise((resolve) => {
    let done = false;
    const finish = () => { if (!done) { done = true; resolve(); } };
    const handle = (s) => {
      if (done || !s) return;
      if (s.status === 'downloading') {
        bootStatus(`Updating to ${s.version || 'the new version'} · ${s.percent || 0} %`);
        const bar = $('#boot .boot-bar i');
        if (bar) { bar.style.animation = 'none'; bar.style.width = `${s.percent || 0}%`; }
      } else if (s.status === 'ready' || s.status === 'installing') {
        bootStatus(`Installing ${s.version || 'the update'} – the launcher restarts`);
        if (s.status === 'ready') api.installUpdateNow().then((r) => { if (r && r.ok === false) finish(); });
      } else if (s.status === 'latest' || s.status === 'error' || s.status === 'available' || s.status === 'disabled') {
        finish();
      }
    };
    api.onUpdate(handle);
    handle(v);
    // no answer from the update server within a few seconds: open anyway (an update found later still installs on close)
    setTimeout(() => { if (!done) { api.updateStatus().then((s) => { if (!s || s.status !== 'downloading') finish(); }).catch(finish); } }, 8000);
    if (v.status === 'idle' || v.status === 'latest' || v.status === 'error') api.checkUpdate().then(handle).catch(finish);
  });
}
