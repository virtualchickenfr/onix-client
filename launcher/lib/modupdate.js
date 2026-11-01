'use strict';

/**
 * Onix mod updates without a launcher restart: the launcher looks at the newest GitHub release in the background
 * (on start, every 30 minutes and right before a game starts) and downloads the Onix mod jars of that release
 * (onix-client-<version>+<mc>.jar, the 1.21.1 one is onix-client-<version>.jar) into <userData>/onix-mods/<mc>/.
 * game.findBundledMod then uses whichever is newer – the jar inside the launcher or the downloaded one – so the next
 * game start already has the new mod. The launcher itself still updates through lib/updater.js (silently when it closes).
 * Only releases of the same series (same first two numbers as the launcher, e.g. 1.1.x) are taken, so an old
 * launcher never gets a mod that needs a newer launcher.
 */

const fs = require('fs');
const path = require('path');
const { download, fetchJson } = require('./download');

const CHECK_MS = 30 * 60 * 1000;
const JAR = /^onix-client-(\d+(?:\.\d+)*)(?:\+([\d.]+))?\.jar$/i;

let dir = null;
let repo = null;
let series = null;
let defaultMc = '1.21.1';
let notify = () => {};
let running = null;
let lastCheck = 0;

function parse(name) {
  const m = JAR.exec(name);
  return m ? { version: m[1], mc: m[2] || defaultMc } : null;
}

function compare(a, b) {
  const x = String(a).split('.').map(Number);
  const y = String(b).split('.').map(Number);
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    const d = (x[i] || 0) - (y[i] || 0);
    if (d) return d;
  }
  return 0;
}

const seriesOf = (v) => String(v).split('.').slice(0, 2).join('.');

/**
 * userDataDir: where downloaded jars go; repoSlug: "owner/name" on GitHub; launcherVersion: e.g. "1.1.4";
 * defaultMinecraft: the version whose jar has no "+mc" in its name; send: gets a message when a new mod arrived.
 */
function init(userDataDir, repoSlug, launcherVersion, defaultMinecraft, send) {
  dir = path.join(userDataDir, 'onix-mods');
  repo = repoSlug;
  series = seriesOf(launcherVersion);
  if (defaultMinecraft) defaultMc = defaultMinecraft;
  if (send) notify = send;
  if (!repo) return;
  setTimeout(() => check().catch(() => {}), 5000).unref();
  setInterval(() => check().catch(() => {}), CHECK_MS).unref();
}

/** The downloaded jar for a Minecraft version, or null. */
function downloaded(mc) {
  if (!dir) return null;
  const d = path.join(dir, mc);
  if (!fs.existsSync(d)) return null;
  let best = null;
  for (const f of fs.readdirSync(d)) {
    const p = parse(f);
    if (!p || p.mc !== mc || seriesOf(p.version) !== series) continue;
    if (!best || compare(p.version, best.version) > 0) best = { version: p.version, file: path.join(d, f) };
  }
  return best;
}

/** Picks the newer of the launcher's own jar and the downloaded one. */
function newest(mc, bundledFile) {
  const own = bundledFile ? parse(path.basename(bundledFile)) : null;
  const got = downloaded(mc);
  if (!got) return bundledFile;
  if (!own || compare(got.version, own.version) > 0) return got.file;
  return bundledFile;
}

async function run() {
  const release = await fetchJson(`https://api.github.com/repos/${repo}/releases/latest`);
  const version = String(release.tag_name || '').replace(/^v/, '');
  if (!version || seriesOf(version) !== series) return null;
  const got = [];
  for (const asset of release.assets || []) {
    const p = parse(asset.name);
    if (!p || p.version !== version) continue;
    const target = path.join(dir, p.mc, asset.name);
    if (fs.existsSync(target) && fs.statSync(target).size === asset.size) continue;
    await download(asset.browser_download_url, target);
    got.push(p.mc);
    // drop older downloads of this Minecraft version
    for (const f of fs.readdirSync(path.dirname(target))) {
      if (f !== asset.name) fs.rmSync(path.join(path.dirname(target), f), { force: true });
    }
  }
  if (got.length) notify({ version, minecraft: got });
  return version;
}

/** Looks for a newer mod (at most once a minute; a running check is shared). */
function check(force = false) {
  if (!dir || !repo) return Promise.resolve(null);
  if (running) return running;
  if (!force && Date.now() - lastCheck < 60 * 1000) return Promise.resolve(null);
  lastCheck = Date.now();
  running = run().finally(() => {
    running = null;
  });
  return running;
}

/** Right before a game start: waits a few seconds at most for a newer mod. */
async function beforeLaunch(timeoutMs = 4000) {
  await Promise.race([check().catch(() => null), new Promise((r) => setTimeout(r, timeoutMs))]);
}

module.exports = { init, check, newest, beforeLaunch, downloaded };
