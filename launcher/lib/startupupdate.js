'use strict';

/**
 * Updates when the computer starts: the launcher is registered to start with the system (setting "Update when the
 * PC starts", on by default) with --background-update. Started that way it opens no window: it looks for a new
 * launcher version, downloads and installs it silently, fetches newer Onix mod jars (lib/modupdate.js) and quits.
 * Without anything new it quits right away. A launcher that is already open (or opened meanwhile) is never disturbed.
 *
 * Windows: a login item with the argument (registry Run key, set by Electron).
 * macOS: a login item started hidden; the launcher sees wasOpenedAtLogin. Unsigned macOS builds cannot replace
 *   themselves, so there only the mod jars are updated.
 * Linux: an autostart entry (~/.config/autostart/onix-client-update.desktop) for the AppImage.
 */

const fs = require('fs');
const os = require('os');
const path = require('path');
const { app } = require('electron');

const ARG = '--background-update';
const LINUX_ENTRY = path.join(os.homedir(), '.config', 'autostart', 'onix-client-update.desktop');
const MAX_RUN_MS = 15 * 60 * 1000;   // never stay in the background longer than this

/** This start is the silent one at system start. */
function isBackgroundStart(argv = process.argv) {
  if (argv.includes(ARG)) return true;
  if (process.platform === 'darwin') {
    try {
      return app.getLoginItemSettings().wasOpenedAtLogin === true;
    } catch {
      return false;
    }
  }
  return false;
}

/** Turns the start with the system on or off (only for the installed launcher). */
function register(enabled) {
  if (!app.isPackaged) return false;
  try {
    if (process.platform === 'linux') {
      const appImage = process.env.APPIMAGE;
      if (!appImage) return false;           // only the AppImage can update itself
      if (enabled) {
        fs.mkdirSync(path.dirname(LINUX_ENTRY), { recursive: true });
        fs.writeFileSync(LINUX_ENTRY, [
          '[Desktop Entry]',
          'Type=Application',
          'Name=Onix Client update',
          'Comment=Updates Onix Client in the background when you log in',
          `Exec="${appImage.replace(/"/g, '\\"')}" ${ARG}`,
          'Terminal=false',
          'NoDisplay=true',
          'X-GNOME-Autostart-enabled=true',
          '',
        ].join('\n'));
      } else {
        fs.rmSync(LINUX_ENTRY, { force: true });
      }
      return true;
    }
    app.setLoginItemSettings({
      openAtLogin: !!enabled,
      openAsHidden: true,                     // macOS
      path: process.execPath,                 // Windows
      args: enabled ? [ARG] : [],
    });
    return true;
  } catch (err) {
    console.warn('Could not change the start with the system:', err);
    return false;
  }
}

/**
 * The silent run: updater is lib/updater.js, modCheck checks the mod jars. Quits when done (or after MAX_RUN_MS).
 * Installing replaces the launcher without starting it again.
 */
async function run({ updater, modCheck, log = () => {} }) {
  const quit = () => app.quit();
  setTimeout(quit, MAX_RUN_MS).unref();
  try {
    if (modCheck) await modCheck().catch((err) => log(`mod check: ${err && err.message}`));
  } catch {
    // the launcher update below still runs
  }
  const status = await updater.waitForUpdate(MAX_RUN_MS - 60 * 1000);
  log(`launcher update: ${status}`);
  if (status === 'ready') {
    updater.installQuietly();                 // installs silently and does not start the launcher
    return;
  }
  quit();
}

module.exports = { ARG, isBackgroundStart, register, run };
