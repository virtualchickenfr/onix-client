'use strict';

/**
 * Launcher updates from GitHub Releases (electron-updater), published by .github/workflows/build.yml when a
 * version tag is pushed. Every start checks for a new version;
 * Windows and the Linux AppImage download it in the background and install it when the launcher
 * closes (or right away with "Restart"). macOS builds without an Apple signature cannot replace
 * themselves, so there the launcher only offers the download page.
 * The Onix mod is inside the launcher, so a launcher update also updates the mod in every profile.
 */

const { app, shell } = require('electron');
const pkg = require('../package.json');

let autoUpdater = null;
try {
  ({ autoUpdater } = require('electron-updater'));
} catch {
  autoUpdater = null;
}

const RECHECK_MS = 6 * 60 * 60 * 1000;
const state = { status: 'idle', version: null, percent: 0, error: null, current: app.getVersion() };
let notify = () => {};
let ready = false;

/** The GitHub page with the releases, from "repository" in package.json (set by the build workflow). */
function releasesUrl() {
  const repo = typeof pkg.repository === 'string' ? pkg.repository : pkg.repository && pkg.repository.url;
  if (!repo) return null;
  const m = String(repo).match(/github\.com[/:]([^/]+)\/([^/.#]+)/i);
  return m ? `https://github.com/${m[1]}/${m[2]}/releases/latest` : null;
}

function view() {
  return { ...state, canInstall: state.status === 'ready', downloadPage: state.status === 'available' ? releasesUrl() : null };
}

function set(patch) {
  Object.assign(state, patch);
  notify(view());
}

let automatic = () => true;
let gameRunning = () => false;
const startedAt = Date.now();
/** An update that finishes downloading this soon after the start is installed right away. */
const INSTALL_ON_START_MS = 3 * 60 * 1000;

/**
 * send: pushes the state to the window; isAutomatic: whether the user lets the launcher check by itself;
 * isGameRunning: whether Minecraft runs (then the update waits).
 */
function init(send, isAutomatic, isGameRunning) {
  notify = send;
  if (isAutomatic) automatic = isAutomatic;
  if (isGameRunning) gameRunning = isGameRunning;
  if (!app.isPackaged) return set({ status: 'disabled', error: 'Updates are only checked in the installed launcher.' });
  if (!autoUpdater) return set({ status: 'disabled', error: 'The update module is missing.' });
  if (process.platform === 'linux' && !process.env.APPIMAGE) return set({ status: 'disabled', error: 'Only the AppImage can update itself.' });
  const selfInstall = process.platform !== 'darwin';
  autoUpdater.autoDownload = selfInstall;
  autoUpdater.autoInstallOnAppQuit = selfInstall;
  autoUpdater.allowPrerelease = false;
  autoUpdater.logger = null;
  autoUpdater.on('checking-for-update', () => set({ status: 'checking', error: null }));
  autoUpdater.on('update-not-available', () => set({ status: 'latest', version: null }));
  autoUpdater.on('update-available', (info) => set({ status: selfInstall ? 'downloading' : 'available', version: info.version, percent: 0 }));
  autoUpdater.on('download-progress', (p) => set({ status: 'downloading', percent: Math.round(p.percent || 0) }));
  autoUpdater.on('update-downloaded', (info) => {
    set({ status: 'ready', version: info.version, percent: 100 });
    // Installing only when the launcher closes left many players on old versions (the launcher often keeps
    // running). Right after the start nothing is lost by restarting, so the new version is installed at once.
    if (automatic() && !gameRunning() && Date.now() - startedAt < INSTALL_ON_START_MS) {
      set({ status: 'installing' });
      setTimeout(() => {
        if (!gameRunning()) autoUpdater.quitAndInstall(true, true);
        else set({ status: 'ready' });
      }, 1500);
    }
  });
  autoUpdater.on('error', (err) => {
    // keep a finished download even if a later check fails
    if (state.status !== 'ready') set({ status: 'error', error: err && err.message ? err.message.split('\n')[0] : String(err) });
  });
  ready = true;
  setInterval(() => {
    if (automatic()) check();
  }, RECHECK_MS).unref();
}

/** Looks for a new version (on every start, then every few hours, or when asked). */
async function check() {
  if (!ready || ['downloading', 'ready', 'installing'].includes(state.status)) return view();
  try {
    await autoUpdater.checkForUpdates();
  } catch (err) {
    set({ status: 'error', error: err && err.message ? err.message.split('\n')[0] : String(err) });
  }
  return view();
}

/** Restarts into the downloaded version. */
function install(gameRunning) {
  if (state.status !== 'ready') throw new Error('No update has been downloaded yet.');
  if (gameRunning) throw new Error('Close Minecraft first – the update installs when the launcher restarts.');
  setImmediate(() => autoUpdater.quitAndInstall(false, true));
  return true;
}

function openDownload() {
  const url = releasesUrl();
  if (!url) throw new Error('No download page is set for this launcher.');
  shell.openExternal(url);
  return true;
}

module.exports = { init, check, install, openDownload, view, releasesUrl };
