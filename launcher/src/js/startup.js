'use strict';

/*
 * Start-up sequence in the style of eDEX-UI: a fast terminal boot log streams while the launcher really loads
 * (every real step is a line), then ONIX glitches in and the interface draws itself panel by panel.
 * A click or key press skips ahead; with reduced motion it is a plain fade.
 */

const bootLog = (() => {
  const box = $('#boot-log');
  const queue = [];
  let timer = null;
  let instant = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const put = ([text, cls]) => {
    if (!box) return;
    box.append(el('div', { class: `bl ${cls}`, text }));
    while (box.childElementCount > 60) box.firstChild.remove();
  };
  const pump = () => {
    if (timer) return;
    timer = setInterval(() => {
      const next = queue.shift();
      if (!next) {
        clearInterval(timer);
        timer = null;
        return;
      }
      put(next);
    }, 13);
  };
  return {
    line(text, cls = '') {
      if (instant) put([text, cls]);
      else {
        queue.push([text, cls]);
        pump();
      }
    },
    flush() {
      instant = true;
      while (queue.length) put(queue.shift());
    },
    drained() {
      return new Promise((resolve) => {
        const check = () => (queue.length === 0 && !timer ? resolve() : setTimeout(check, 16));
        check();
      });
    },
  };
})();

(async () => {
  const boot = $('#boot');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  let skip = false;
  const onSkip = () => { skip = true; bootLog.flush(); };
  boot.addEventListener('click', onSkip);
  window.addEventListener('keydown', onSkip, { once: true });
  const t0 = performance.now();
  const stamp = () => `[${((performance.now() - t0) / 1000).toFixed(6).padStart(11)}]`;
  const bootStatus = (text) => {
    $('#boot-status').textContent = text;
    bootLog.line(text, 'ok');
  };
  // boot header (shown while the first real step runs)
  for (const l of [
    `${stamp()} onix: boot sequence started`,
    `${stamp()} ui: renderer ${navigator.hardwareConcurrency || '?'} cores · ${screen.width}x${screen.height} @${window.devicePixelRatio || 1}x`,
    `${stamp()} ui: fonts geist, geist mono … loaded`,
    `${stamp()} net: ${navigator.onLine ? 'link up' : 'offline'}`,
  ]) bootLog.line(l, 'dim');
  try {
    bootStatus('Starting');
    info = await api.info();
    $('#eyebrow').textContent = `Minecraft ${info.mcVersion}`;
    bootLog.line(`onix launcher ${info.version} · minecraft ${info.mcVersion} · fabric loader ${info.loader}`, 'ok');
    bootLog.line(`${stamp()} mem: ${Math.round((info.totalMemMb || 0) / 1024)} GB system memory`, 'dim');
    bootStatus('Checking your account');
    await loadAccounts();
    bootLog.line(account ? `auth: signed in as ${account.name}` : 'auth: no account signed in', account ? 'ok' : 'warn');
    bootStatus('Loading profiles');
    profileState = await api.getProfiles();
    bootLog.line(`profiles: ${(profileState.profiles || []).length} mounted`, 'ok');
    renderPicker();
    renderEyebrow();
    renderRecent();
    await initSettings();
    await updateBeforeStart(bootStatus);
    bootStatus('Ready');
    bootLog.line(`${stamp()} onix: all systems ready`, 'dim');
    // get sign-in, mods and game files ready in the background so Play starts Java right away
    setTimeout(() => { if (state === 'idle' && api.warmup) api.warmup().catch(() => {}); }, 1200);
    // started from a desktop shortcut: play that profile right away
    api.pendingShortcut().then((req) => { if (req) setTimeout(() => runShortcut(req), 400); }).catch(() => {});
  } finally {
    if (!skip && !reduced) {
      // a short moment of boot log even when everything was instant, so the sequence reads as one
      for (const l of ['mounting /mods … ok', 'link: onix network · cosmetics · friends', 'gpu: webgl ' + (document.createElement('canvas').getContext('webgl') ? 'ready' : 'off'), 'starting interface']) {
        bootLog.line(`${stamp()} ${l}`, 'dim');
      }
      await bootLog.drained();
      const left = 650 - (performance.now() - t0);
      if (left > 0 && !skip) await new Promise((r) => setTimeout(r, left));
    }
    boot.classList.add('title');
    setTimeout(() => {
      boot.classList.add('done');
      document.body.classList.add('edex-enter');
      setTimeout(() => document.body.classList.remove('edex-enter'), 1500);
      boot.addEventListener('transitionend', () => boot.remove(), { once: true });
    }, skip || reduced ? 250 : 650);
  }
})();

/*
 * Launch terminal: while Minecraft starts, the real launch log streams in a small eDEX style terminal and
 * disappears once the game window is up.
 */
const edexTerm = (() => {
  const box = $('#edex-term');
  const body = $('#edex-term-body');
  const clock = $('#edex-term-clock');
  let started = 0;
  let tick = null;
  let lastLabel = '';
  let closing = false;
  const add = (text, dim) => {
    if (box.hidden) return;
    const t = ((performance.now() - started) / 1000).toFixed(1).padStart(5);
    body.append(el('div', {}, [el('span', { class: 't', text: `${t}  ` }), document.createTextNode(text)]));
    while (body.childElementCount > 9) body.firstChild.remove();
    if (dim) body.lastChild.classList.add('t');
  };
  const closeTerm = () => edexTerm.close();
  return {
    open() {
      body.replaceChildren();
      started = performance.now();
      lastLabel = '';
      closing = false;
      box.classList.remove('out');
      box.hidden = false;
      clearInterval(tick);
      tick = setInterval(() => {
        const s = (performance.now() - started) / 1000;
        clock.textContent = `${String(Math.floor(s / 60)).padStart(2, '0')}:${(s % 60).toFixed(1).padStart(4, '0')}`;
        // the start failed (the launcher shows why) or the window never reported in
        if ((state === 'idle' && s > 1) || s > 90) closeTerm();
      }, 100);
      add('> launch sequence initiated');
    },
    label(text) {
      if (closing) return;
      if (text && text !== lastLabel) {
        lastLabel = text;
        add(`> ${text.toLowerCase()}`);
      }
    },
    log(line) {
      const s = String(line || '').trim();
      // only the interesting lines: launcher steps and Minecraft's own start messages
      if (!s || s.length > 200 || /^\[MCLC\]: (Downloaded|Attempting|Using)/.test(s)) return;
      if (/Starting profile|ready after|Checking all game files|only looking for missing|Installed|Loading Minecraft|Loading \d+ mods|Onix Client .* ready|Setting user|Backend library|Reloading ResourceManager|Sound engine started|Created: .*blocks/.test(s)) {
        add(s.replace(/^\[[^\]]*\] \[[^\]]*\] (\([^)]*\) )?/, '').slice(0, 90), false);
      }
      // Minecraft opened its window: the terminal has done its job
      if (/Backend library|Reloading ResourceManager/.test(s)) this.close('game window open · have fun');
    },
    close(text) {
      if (box.hidden || box.classList.contains('out') || closing) return;
      closing = true;
      if (text) add(`> ${text}`);
      clearInterval(tick);
      setTimeout(() => {
        box.classList.add('out');
        setTimeout(() => { box.hidden = true; closing = false; }, 360);
      }, 900);
    },
  };
})();

api.onProgress(({ label }) => { if (state === 'preparing') edexTerm.label(label); });
api.onLog((line) => { if (state === 'preparing' || state === 'running') edexTerm.log(line); });
api.onState((s) => {
  if (s === 'running') edexTerm.label('java started · loading minecraft');
  else if (s === 'closed' || s === 'crashed') edexTerm.close(s === 'crashed' ? 'game stopped with an error' : null);
});
$('#btn-play').addEventListener('click', () => { if (account) edexTerm.open(); });

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
