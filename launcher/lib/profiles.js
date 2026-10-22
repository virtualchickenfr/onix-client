const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const versions = require('./versions');
const LOADER_IDS = ['fabric', 'quilt', 'neoforge', 'forge', 'vanilla'];

const clampInt = (v, min, max) => {
  const n = Math.round(Number(v) || 0);
  return n <= 0 ? 0 : Math.max(min, Math.min(max, n));
};

const COLORS = ['#ececf1', '#8b7cff', '#4c8dff', '#5eead4', '#ff8fc4', '#ffb547', '#ff4d5e'];

/** Launcher profiles: each one has its own game folder (mods, worlds, settings). */
class Profiles {
  constructor(store, gameDir, defaults) {
    this.store = store;
    this.gameDir = gameDir;
    this.defaults = defaults;
    if (!Array.isArray(store.get('profiles')) || store.get('profiles').length === 0) {
      const old = store.get('settings') || {};
      store.set('profiles', [
        this.normalize({
          id: 'standard',
          name: 'Default',
          color: COLORS[0],
          ramMb: old.ramMb,
          quickJoin: old.quickJoin,
          performanceMods: old.performanceMods,
        }),
      ]);
      store.set('activeProfile', 'standard');
    }
  }

  normalize(p) {
    return {
      id: p.id || crypto.randomBytes(4).toString('hex'),
      name: String(p.name || 'New Profile').slice(0, 24),
      color: COLORS.includes(p.color) ? p.color : COLORS[0],
      ramMb: Number(p.ramMb) || this.defaults.ramMb,
      quickJoin: typeof p.quickJoin === 'string' ? p.quickJoin.trim() : '',
      performanceMods: p.performanceMods !== false,
      mcVersion: versions.isSupported(p.mcVersion) ? p.mcVersion : versions.DEFAULT,
      // Fabric (with Onix) by default; Quilt, NeoForge, Forge and Vanilla run without the Onix mod
      loader: LOADER_IDS.includes(p.loader) ? p.loader : 'fabric',
      loaderVersion: typeof p.loaderVersion === 'string' ? p.loaderVersion.slice(0, 40) : '',
      // extra Java arguments, a Java to use instead of the bundled one, and the game window
      jvmArgs: typeof p.jvmArgs === 'string' ? p.jvmArgs.slice(0, 2000) : '',
      javaPath: typeof p.javaPath === 'string' ? p.javaPath.trim().slice(0, 500) : '',
      width: clampInt(p.width, 0, 7680),
      height: clampInt(p.height, 0, 4320),
      fullscreen: p.fullscreen === true,
      // statistics, kept by the launcher
      playTimeMs: Math.max(0, Number(p.playTimeMs) || 0),
      lastPlayed: Number(p.lastPlayed) || 0,
      createdAt: Number(p.createdAt) || Date.now(),
    };
  }

  list() {
    return this.store.get('profiles');
  }

  active() {
    const id = this.store.get('activeProfile');
    return this.list().find((p) => p.id === id) || this.list()[0];
  }

  dir(profile = this.active()) {
    const d = path.join(this.gameDir, 'profiles', profile.id);
    fs.mkdirSync(d, { recursive: true });
    return d;
  }

  select(id) {
    if (this.list().some((p) => p.id === id)) this.store.set('activeProfile', id);
    return this.active();
  }

  save(profile) {
    const clean = this.normalize(profile);
    const list = this.list().slice();
    const i = list.findIndex((p) => p.id === clean.id);
    if (i >= 0) list[i] = clean;
    else list.push(clean);
    this.store.set('profiles', list);
    return clean;
  }

  /** Creates a profile, optionally copying settings and cosmetics from another one. */
  create(name, copyFrom, extra = {}) {
    const base = copyFrom ? this.list().find((p) => p.id === copyFrom) : null;
    const used = new Set(this.list().map((p) => p.color));
    const color = COLORS.find((c) => !used.has(c)) || COLORS[this.list().length % COLORS.length];
    const profile = this.save({ ...(base || {}), ...extra, id: undefined, name, color });
    if (base) {
      const from = path.join(this.dir(base), 'config', 'onix');
      const to = path.join(this.dir(profile), 'config', 'onix');
      if (fs.existsSync(from)) fs.cpSync(from, to, { recursive: true });
    }
    this.store.set('activeProfile', profile.id);
    return profile;
  }

  /** A full copy of a profile (worlds, mods, settings) under a new name; statistics start over. */
  duplicate(id) {
    const base = this.list().find((p) => p.id === id);
    if (!base) throw new Error('Profile not found');
    const used = new Set(this.list().map((p) => p.name));
    let name = `${base.name} (copy)`.slice(0, 24);
    for (let i = 2; used.has(name); i++) name = `${base.name.slice(0, 18)} (${i})`;
    const copy = this.save({ ...base, id: undefined, name, playTimeMs: 0, lastPlayed: 0, createdAt: Date.now() });
    const from = this.dir(base);
    const to = this.dir(copy);
    fs.cpSync(from, to, { recursive: true, filter: (src) => !/[\\/](logs|crash-reports)$/.test(src) });
    return copy;
  }

  /** Adds a finished session to the play time. */
  recordPlay(id, startedAt, endedAt = Date.now()) {
    const p = this.list().find((x) => x.id === id);
    if (!p) return;
    this.save({ ...p, playTimeMs: (p.playTimeMs || 0) + Math.max(0, endedAt - startedAt), lastPlayed: endedAt });
  }

  remove(id) {
    const list = this.list();
    if (list.length <= 1) throw new Error('The last profile cannot be deleted.');
    this.store.set('profiles', list.filter((p) => p.id !== id));
    if (this.store.get('activeProfile') === id) this.store.set('activeProfile', this.list()[0].id);
    const dir = path.join(this.gameDir, 'profiles', id);
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

module.exports = { Profiles, COLORS };
