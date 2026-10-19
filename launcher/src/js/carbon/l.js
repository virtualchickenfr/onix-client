'use strict';

/* Small helper namespace shared with the pieces taken from the Carbon Launcher (pixel icons, voxel island, skin
   renders). Onix's own pages keep using el()/icon() from helpers.js. */
(function () {
  const L = (window.L = window.L || {});
  L.$ = (s, r) => (r || document).querySelector(s);
  L.$$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  L.esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  L.typing = (e) => !!(e.target && e.target.matches && e.target.matches('input, textarea, select'));

  const cache = {};
  /** A 9×9 pixel icon as an SVG string. */
  L.icon = (name) => {
    if (cache[name]) return cache[name];
    const rows = window.ICONS && window.ICONS[name];
    if (!rows) return '';
    let out = '<svg class="picon" viewBox="0 0 9 9" shape-rendering="crispEdges" aria-hidden="true">';
    for (let y = 0; y < 9; y++) {
      for (let x = 0; x < 9; x++) {
        const c = rows[y][x];
        if (c === '#') out += `<rect x="${x}" y="${y}" width="1" height="1"/>`;
        else if (c === '+') out += `<rect class="half" x="${x}" y="${y}" width="1" height="1"/>`;
      }
    }
    return (cache[name] = `${out}</svg>`);
  };
  /** The same icon as a DOM node. */
  L.picon = (name) => {
    const t = document.createElement('template');
    t.innerHTML = L.icon(name);
    return t.content.firstChild || document.createElement('span');
  };
})();
