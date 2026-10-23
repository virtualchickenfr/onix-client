'use strict';

/**
 * Rules for the text of custom tags – the same as the mod (cosmetics/NameTags.java) and the server
 * (server/cloudflare/src/index.js). Also loaded by the launcher page (src/js/cosmetics.js) as window.OnixNameTags.
 */
(function (root) {
  const MAX = 12;
  const ALLOWED = /^[\p{L}\p{N} \-_.!?#&+*'★♥✦☀☾❄♪✿⚔☠♛♦♣♠☺✔⚡☁☂☃♫]+$/u;
  const BLOCKED_PARTS = ['admin', 'owner', 'moderator', 'staff', 'helper', 'developer', 'support', 'mojang', 'microsoft', 'official',
    'nigg', 'fagg', 'hitler', 'nazi', 'retard', 'fuck', 'cunt', 'whore', 'hurensohn', 'wichser', 'fotze', 'schwuchtel', 'missgeburt', 'spast', 'nutte', 'kys'];
  const BLOCKED_WORDS = new Set(['mod', 'dev', 'gm', 'op', 'fag', 'hure', 'sex']);

  /** Null if the text may be used as a custom tag, otherwise why not. */
  function problem(text) {
    if (typeof text !== 'string' || !text.trim()) return 'Type some text for your tag.';
    if ([...text].length > MAX) return `At most ${MAX} characters.`;
    if (!ALLOWED.test(text)) return 'Letters, numbers, spaces and a few symbols only.';
    const lower = text.toLowerCase();
    const flat = lower.replace(/[^\p{L}\p{N}]/gu, '');
    if (BLOCKED_PARTS.some((b) => flat.includes(b))) return 'This tag is not allowed.';
    if (lower.split(/[^\p{L}\p{N}]+/u).some((w) => BLOCKED_WORDS.has(w))) return 'This tag is not allowed.';
    return null;
  }

  const api = { MAX, problem };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.OnixNameTags = api;
}(typeof window !== 'undefined' ? window : globalThis));
