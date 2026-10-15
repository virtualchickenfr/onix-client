'use strict';

/* Console page (launcher and game output). */

const logEl = $('#log');
let logQueued = false;
api.onLog((line) => {
  logLines.push(line);
  if (logLines.length > 3000) logLines.splice(0, logLines.length - 3000);
  if (state === 'preparing') $('#launch-detail').textContent = line.slice(0, 160);
  if (logQueued) return;
  logQueued = true;
  requestAnimationFrame(() => {
    logQueued = false;
    const atBottom = logEl.scrollTop + logEl.clientHeight >= logEl.scrollHeight - 30;
    logEl.textContent = logLines.join('\n');
    if (atBottom) logEl.scrollTop = logEl.scrollHeight;
  });
});
$('#btn-copy-log').addEventListener('click', async () => { await navigator.clipboard.writeText(logLines.join('\n')); toast('Console copied'); });
$('#btn-clear-log').addEventListener('click', () => { logLines.length = 0; logEl.textContent = ''; });
