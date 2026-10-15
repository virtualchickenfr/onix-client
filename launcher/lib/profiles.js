const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

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
  create(name, copyFrom) {
    const base = copyFrom ? this.list().find((p) => p.id === copyFrom) : null;
    const used = new Set(this.list().map((p) => p.color));
    const color = COLORS.find((c) => !used.has(c)) || COLORS[this.list().length % COLORS.length];
    const profile = this.save({ ...(base || {}), id: undefined, name, color });
    if (base) {
      const from = path.join(this.dir(base), 'config', 'onix');
      const to = path.join(this.dir(profile), 'config', 'onix');
      if (fs.existsSync(from)) fs.cpSync(from, to, { recursive: true });
    }
    this.store.set('activeProfile', profile.id);
    return profile;
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
