'use strict';

/* Start-up sequence: loads everything, then hides the boot animation. */

(async () => {
  const bootStarted = performance.now();
  const bootStatus = (text) => { $('#boot-status').textContent = text; };
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
    await initSettings();
    bootStatus('Ready');
  } finally {
    // keep the animation on screen long enough to read as intentional, not as a flicker
    const wait = Math.max(0, 900 - (performance.now() - bootStarted));
    setTimeout(() => {
      const boot = $('#boot');
      boot.classList.add('done');
      boot.addEventListener('transitionend', () => boot.remove(), { once: true });
    }, wait);
  }
})();
