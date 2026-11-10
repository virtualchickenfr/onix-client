'use strict';

/*
 * The Onix PC launcher's `api` (launcher/preload.js) for Android: the interface is the same HTML/CSS/JS, these
 * functions answer it with Zalith Launcher underneath (OnixWebFragment.Bridge). Profiles are game versions,
 * Modrinth is used straight from here, files are reached only inside a profile's game folder.
 * Things a phone cannot do (other launchers, desktop shortcuts, Java paths …) answer with a friendly message.
 */
(() => {
  const bridge = window.OnixAndroid;
  const pending = new Map();
  let seq = 0;
  const listeners = {};

  window.__onixDone = (id, ok, value) => {
    const p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    if (ok) p.resolve(value);
    else p.reject(new Error(value || 'Error'));
  };
  window.__onixEmit = (channel, value) => {
    for (const cb of listeners[channel] || []) {
      try {
        cb(value);
      } catch (e) {
        console.error(e);
      }
    }
  };
  const on = (channel) => (cb) => {
    (listeners[channel] = listeners[channel] || []).push(cb);
  };
  const emit = (channel, value) => window.__onixEmit(channel, value);

  /** One call into the app. */
  const call = (method, ...args) => new Promise((resolve, reject) => {
    if (!bridge) return reject(new Error('Not running inside the Onix app'));
    const id = String(++seq);
    pending.set(id, { resolve, reject });
    bridge.call(id, method, JSON.stringify(args));
  });
  /** {ok, value} | {ok: false, error} like the PC launcher's safe() handlers. */
  const safe = (fn) => async (...args) => {
    try {
      return { ok: true, value: await fn(...args) };
    } catch (e) {
      return { ok: false, error: String((e && e.message) || e) };
    }
  };
  const notHere = (what) => safe(async () => {
    throw new Error(`${what} is not available on Android.`);
  });

  // ---------------------------------------------------------------- small stores (app preferences)

  const prefs = {
    async get(key, fallback) {
      try {
        const raw = await call('prefsGet', key);
        return raw == null ? fallback : JSON.parse(raw);
      } catch {
        return fallback;
      }
    },
    set: (key, value) => call('prefsSet', key, JSON.stringify(value)),
  };

  const MC_DEFAULT = '1.21.11';
  const VERSIONS = (window.ONIX_VERSIONS || [{ id: MC_DEFAULT, mc: MC_DEFAULT, loader: '', note: 'Onix + Carbon', onix: true }]);
  const COLORS = ['#ececf1', '#8b7cff', '#3dd6c6', '#ff8a5b', '#f472b6', '#facc15', '#60a5fa', '#34d399'];
  const DEFAULT_SETTINGS = { javaPath: '', closeOnLaunch: false, fastStart: true, theme: 'onyx', background: 'carbon', onixServer: '', autoUpdate: true };

  // ---------------------------------------------------------------- profiles = game versions

  async function profileList() {
    const [versions, meta] = await Promise.all([call('versions'), prefs.get('profileMeta', {})]);
    let active = null;
    const profiles = versions.map((v, i) => {
      const m = meta[v.name] || {};
      if (v.current) active = v.name;
      return {
        id: v.name,
        name: m.name || v.name,
        color: m.color || COLORS[i % COLORS.length],
        ramMb: m.ramMb || 2048,
        quickJoin: m.quickJoin || '',
        performanceMods: m.performanceMods !== false,
        shaders: false,
        mcVersion: v.mc,
        loader: v.loader === 'fabric' ? 'fabric' : v.loader,
        loaderVersion: v.loaderVersion || '',
        jvmArgs: '',
        javaPath: '',
        width: 0,
        height: 0,
        fullscreen: false,
        playTimeMs: m.playTimeMs || 0,
        lastPlayed: m.lastPlayed || 0,
        createdAt: m.createdAt || 0,
      };
    });
    return { profiles, active: active || (profiles[0] && profiles[0].id) || null };
  }

  async function changedProfiles() {
    const state = await profileList();
    emit('profiles:changed', state);
    return state;
  }

  const plain = (name) => String(name || 'Profile').replace(/[^A-Za-z0-9 ._-]/g, '').trim().slice(0, 32) || 'Profile';

  async function freeName(name, taken) {
    let n = plain(name);
    let i = 2;
    while (taken.includes(n)) n = `${plain(name).slice(0, 28)} ${i++}`;
    return n;
  }

  async function activeId(id) {
    if (id) return id;
    const s = await profileList();
    if (!s.active) throw new Error('Create a profile first.');
    return s.active;
  }

  // ---------------------------------------------------------------- files of a profile

  const list = (id, dir) => call('list', id, dir).catch(() => []);

  async function modrinthManifest(id, dir) {
    try {
      return JSON.parse((await call('read', id, `${dir}/.onix-modrinth.json`)) || '{}') || {};
    } catch {
      return {};
    }
  }
  const saveManifest = (id, dir, data) => call('write', id, `${dir}/.onix-modrinth.json`, JSON.stringify(data, null, 2));

  async function listMods(id) {
    id = await activeId(id);
    const [files, manifest] = await Promise.all([list(id, 'mods'), modrinthManifest(id, 'mods')]);
    const byFile = new Map(Object.entries(manifest).filter(([k]) => !k.startsWith('_')).map(([pid, m]) => [m.file || m, { id: pid, ...(typeof m === 'object' ? m : {}) }]));
    return files
      .filter((f) => !f.dir && /\.jar(\.disabled)?$/.test(f.name))
      .map((f) => {
        const enabled = f.name.endsWith('.jar');
        const base = enabled ? f.name : f.name.slice(0, -'.disabled'.length);
        const m = byFile.get(base);
        const mod = { file: f.name, name: base.replace(/\.jar$/, ''), enabled, core: /^(onix-client-|fabric-api-)/.test(base), managed: /^(sodium|lithium|ferritecore|entityculling)-/.test(base), size: f.size };
        if (m && m.title) mod.modrinth = { id: m.id, title: m.title, version: m.version, icon: m.icon || null };
        return mod;
      })
      .sort((a, b) => Number(b.core) - Number(a.core) || a.name.localeCompare(b.name));
  }

  const KIND_DIR = { resourcepacks: 'resourcepacks', shaderpacks: 'shaderpacks', resourcepack: 'resourcepacks', shader: 'shaderpacks' };

  async function listPacks(id, kind) {
    const dir = KIND_DIR[kind];
    if (!dir) throw new Error('Unknown kind');
    const [files, manifest] = await Promise.all([list(id, dir), modrinthManifest(id, dir)]);
    const byFile = new Map(Object.entries(manifest).map(([pid, m]) => [m.file, { id: pid, ...m }]));
    return files
      .filter((e) => !e.name.startsWith('.') && (e.dir || /\.zip(\.disabled)?$/i.test(e.name)))
      .map((e) => {
        const enabled = !e.name.endsWith('.disabled');
        const base = enabled ? e.name : e.name.slice(0, -'.disabled'.length);
        const m = byFile.get(base);
        return { file: e.name, name: base.replace(/\.zip$/i, ''), folder: e.dir, enabled, size: e.size, modrinth: m ? { id: m.id, title: m.title, version: m.version, icon: m.icon || null } : null };
      })
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  async function toggleFile(id, dir, file) {
    const on = !file.endsWith('.disabled');
    const to = on ? `${file}.disabled` : file.slice(0, -'.disabled'.length);
    await call('rename', id, `${dir}/${file}`, `${dir}/${to}`);
    return !on;
  }

  // ---------------------------------------------------------------- Modrinth (straight from the app)

  const MR = 'https://api.modrinth.com/v2';
  const q = (v) => encodeURIComponent(JSON.stringify(v));
  async function mr(route) {
    const res = await fetch(MR + route, { headers: { Accept: 'application/json' } });
    if (res.status === 404) return null;
    if (res.status === 429) throw new Error('Modrinth is rate limiting requests – wait a minute and try again.');
    if (!res.ok) throw new Error(`Modrinth answered with HTTP ${res.status}.`);
    return res.json();
  }

  async function profileInfo(id) {
    const s = await profileList();
    const p = s.profiles.find((x) => x.id === id) || s.profiles.find((x) => x.id === s.active);
    if (!p) throw new Error('Create a profile first.');
    return p;
  }

  async function search(opts = {}) {
    const p = await profileInfo(opts.profileId);
    const kind = ['mod', 'modpack', 'resourcepack', 'shader'].includes(opts.type) ? opts.type : 'mod';
    const facets = [[`project_type:${kind}`], [`versions:${p.mcVersion}`]];
    if (kind === 'mod' || kind === 'modpack') facets.push(['categories:fabric']);
    if (kind === 'shader') facets.push(['categories:iris']);
    const sort = ['relevance', 'downloads', 'follows', 'newest', 'updated'].includes(opts.sort) ? opts.sort : 'relevance';
    const res = await mr(`/search?query=${encodeURIComponent(opts.query || '')}&facets=${q(facets)}&index=${sort}&offset=${Math.max(0, opts.offset | 0)}&limit=${Math.min(50, opts.limit || 20)}`);
    return {
      total: res ? res.total_hits : 0,
      hits: (res ? res.hits : []).map((h) => ({
        id: h.project_id, slug: h.slug, title: h.title, description: h.description, author: h.author, downloads: h.downloads,
        icon: h.icon_url || null, categories: (h.categories || []).filter((c) => !['fabric', 'iris', 'minecraft'].includes(c)).slice(0, 3),
        clientSide: h.client_side, managed: false,
      })),
    };
  }

  async function bestVersion(projectId, mc, loaders) {
    const lq = loaders ? `loaders=${q(loaders)}&` : '';
    const versions = await mr(`/project/${encodeURIComponent(projectId)}/version?${lq}game_versions=${q([mc])}`);
    if (!versions || !versions.length) return null;
    return versions.find((v) => v.version_type === 'release') || versions[0];
  }

  /** Installs a project (and its required dependencies) into a folder of the profile, with the manifest. */
  async function installInto(id, projectId, dir, loaders, result, depth = 0) {
    const p = await profileInfo(id);
    const project = await mr(`/project/${encodeURIComponent(projectId)}`);
    if (!project) throw new Error('This project no longer exists on Modrinth.');
    const version = await bestVersion(projectId, p.mcVersion, loaders);
    if (!version) throw new Error(`${project.title} has no version for Minecraft ${p.mcVersion}.`);
    const file = version.files.find((f) => f.primary) || version.files[0];
    const name = file.filename.split('/').pop();
    const manifest = await modrinthManifest(id, dir);
    const current = manifest[project.id];
    if (current && current.versionId === version.id && await call('exists', id, `${dir}/${current.file}`)) {
      result.skipped.push(`${project.title} ${version.version_number} is already installed`);
    } else {
      await call('download', id, file.url, `${dir}/${name}`, project.title);
      if (current && current.file !== name) await call('delete', id, `${dir}/${current.file}`).catch(() => {});
      manifest[project.id] = { file: name, versionId: version.id, version: version.version_number, title: project.title, icon: project.icon_url || null };
      await saveManifest(id, dir, manifest);
      result.installed.push({ title: project.title, version: version.version_number, dependency: depth > 0 });
    }
    if (dir === 'mods' && depth < 3) {
      for (const dep of version.dependencies || []) {
        if (dep.dependency_type !== 'required' || !dep.project_id) continue;
        try {
          await installInto(id, dep.project_id, 'mods', ['fabric'], result, depth + 1);
        } catch (e) {
          result.warnings.push(String(e.message || e));
        }
      }
    }
    return result;
  }

  // ---------------------------------------------------------------- the api

  const api = {
    info: async () => {
      const i = await call('info').catch(() => ({}));
      return { version: i.versionName || '', mcVersion: MC_DEFAULT, loader: '', versions: VERSIONS, totalMemMb: i.deviceMemMb || 4096, gameDir: '', android: true };
    },
    versions: async () => VERSIONS,
    loaders: async () => [{ id: 'fabric', name: 'Fabric' }],

    // accounts: signing in happens in the app's own Microsoft login
    account: async () => {
      const a = (await call('accounts').catch(() => [])).find((x) => x.active);
      return a ? { name: a.name, uuid: a.uuid, demo: false } : null;
    },
    accounts: async () => (await call('accounts').catch(() => [])).map((a) => ({ name: a.name, uuid: a.uuid, active: a.active, lastUsed: 0, demo: false })),
    login: safe(async () => {
      await call('open', 'accounts');
      return null;
    }),
    logout: async () => null,
    switchAccount: safe((uuid) => call('switchAccount', uuid).then(() => api.account())),
    removeAccount: safe((uuid) => call('removeAccount', uuid)),

    // settings, sky and themes are kept in the app
    getSettings: async () => ({ ...DEFAULT_SETTINGS, ...(await prefs.get('settings', {})) }),
    setSettings: async (value) => {
      const s = { ...DEFAULT_SETTINGS, ...(await prefs.get('settings', {})), ...value };
      await prefs.set('settings', s);
      return s;
    },
    getSky: async () => prefs.get('sky', { daySky: 'Clear Day', nightSky: 'Starry Night' }),
    setSky: safe(async (value) => prefs.set('sky', value)),
    listThemes: async () => prefs.get('themes', []),
    saveTheme: safe(async (theme) => {
      const themes = (await prefs.get('themes', [])).filter((t) => t.id !== theme.id);
      themes.push(theme);
      await prefs.set('themes', themes);
      return themes;
    }),
    deleteTheme: safe(async (id) => {
      const themes = (await prefs.get('themes', [])).filter((t) => t.id !== id);
      await prefs.set('themes', themes);
      return themes;
    }),

    // profiles
    getProfiles: () => profileList(),
    selectProfile: async (id) => {
      await call('selectVersion', id);
      return changedProfiles();
    },
    saveProfile: safe(async (profile) => {
      const meta = await prefs.get('profileMeta', {});
      const old = meta[profile.id] || {};
      meta[profile.id] = { ...old, name: profile.name, color: profile.color, ramMb: profile.ramMb, quickJoin: profile.quickJoin, performanceMods: profile.performanceMods };
      await prefs.set('profileMeta', meta);
      return changedProfiles();
    }),
    createProfile: safe(async (name, copyFrom, mcVersion) => {
      const state = await profileList();
      const id = await freeName(name, state.profiles.map((p) => p.id));
      emit('mods:progress', { label: `Creating ${id}`, progress: 0 });
      await call('createVersion', id, mcVersion || MC_DEFAULT);
      const meta = await prefs.get('profileMeta', {});
      meta[id] = { name: name || id, createdAt: Date.now() };
      await prefs.set('profileMeta', meta);
      await call('selectVersion', id);
      return changedProfiles();
    }),
    deleteProfile: safe(async (id) => {
      const state = await profileList();
      if (state.profiles.length <= 1) throw new Error('Keep at least one profile.');
      await call('deleteVersion', id);
      return changedProfiles();
    }),
    duplicateProfile: safe(async (id) => {
      const p = await profileInfo(id);
      return (await api.createProfile(`${p.name} copy`, id, p.mcVersion)).value;
    }),
    onProfilesChanged: on('profiles:changed'),
    prepareProfile: async () => null,
    onPrepare: on('prepare'),
    pickJava: notHere('Choosing a Java'),

    // packs, worlds, screenshots and logs
    packs: safe((id, kind) => listPacks(id, kind)),
    togglePack: safe((id, kind, file) => toggleFile(id, KIND_DIR[kind], file)),
    removePack: safe((id, kind, file) => call('delete', id, `${KIND_DIR[kind]}/${file}`)),
    addPacks: notHere('Adding files'),
    pickPacks: safe(async () => {
      await call('open', 'files');
      return [];
    }),
    worlds: safe(async (id) => {
      const saves = (await list(id, 'saves')).filter((e) => e.dir);
      return Promise.all(saves.map(async (e) => ({
        folder: e.name, name: e.name, mode: null, lastPlayed: e.mtime,
        icon: await call('dataUrl', id, `saves/${e.name}/icon.png`).catch(() => null),
      }))).then((w) => w.sort((a, b) => b.lastPlayed - a.lastPlayed));
    }),
    openWorld: notHere('Opening a world folder'),
    removeWorld: safe((id, folder) => call('delete', id, `saves/${folder}`)),
    screenshots: safe(async (id, limit) => (await list(id, 'screenshots'))
      .filter((f) => /\.(png|jpe?g)$/i.test(f.name))
      .map((f) => ({ file: f.name, time: f.mtime }))
      .sort((a, b) => b.time - a.time)
      .slice(0, limit || 1000)),
    openScreenshot: notHere('Opening a screenshot'),
    showScreenshot: notHere('Showing a screenshot in a folder'),
    removeScreenshot: safe((id, file) => call('delete', id, `screenshots/${file}`)),
    logs: safe(async (id) => {
      const out = [];
      const logs = await list(id, 'logs');
      const latest = logs.find((f) => f.name === 'latest.log');
      if (latest) out.push({ id: 'logs/latest.log', name: 'Latest game log', time: latest.mtime, crash: false });
      for (const f of (await list(id, 'crash-reports')).filter((x) => x.name.endsWith('.txt'))) {
        out.push({ id: `crash-reports/${f.name}`, name: f.name.replace(/^crash-/, 'Crash ').replace(/-client\.txt$|\.txt$/, ''), time: f.mtime, crash: true });
      }
      return out.sort((a, b) => Number(a.crash) - Number(b.crash) || b.time - a.time);
    }),
    readLog: safe((id, logId) => call('readTail', id, logId)),
    profileSizes: safe(async () => ({})),

    // shaders and resource packs from Modrinth (Iris shaders are too heavy for phones)
    installedAssets: safe(async (id, kind) => Object.keys(await modrinthManifest(id, KIND_DIR[kind]))),
    installAsset: safe((id, kind, projectId) => installInto(id, projectId, KIND_DIR[kind], kind === 'shader' ? ['iris'] : null, { installed: [], skipped: [], warnings: [] })),
    quickShaders: async () => [],
    quickShaderSetup: notHere('Shaders'),

    // cosmetics, wallet and skins need the Onix PC launcher for now
    getCosmetics: async () => ({}),
    setCosmetic: notHere('Cosmetics'),
    setCosmetics: notHere('Cosmetics'),
    wallet: notHere('The wallet'),
    buyCosmetic: notHere('The shop'),
    giftCosmetic: notHere('Gifts'),
    giftsSeen: safe(async () => true),

    // mods
    listMods: (id) => listMods(id).catch(() => []),
    toggleMod: safe(async (file, id) => toggleFile(await activeId(id), 'mods', file)),
    addMods: notHere('Adding mod files'),
    pickModFiles: async () => [],
    removeMod: safe(async (id, file) => call('delete', await activeId(id), `mods/${file}`)),
    searchModrinth: safe((opts) => search(opts)),
    installedModrinth: safe(async (id) => Object.keys(await modrinthManifest(await activeId(id), 'mods')).filter((k) => !k.startsWith('_'))),
    installModrinth: safe(async (id, projectId) => installInto(await activeId(id), projectId, 'mods', ['fabric'], { installed: [], skipped: [], warnings: [] })),
    modUpdates: safe(async () => []),
    updateMods: safe(async () => ({ updated: [], failed: [] })),
    installModpack: notHere('Modpacks'),
    exportParts: async () => [],
    exportProfile: notHere('Exporting'),
    importProfile: notHere('Importing'),
    importFile: notHere('Importing'),
    pendingImport: async () => null,
    onImportFile: on('import:file'),
    copyTargets: safe(async () => []),
    copyProfileTo: notHere('Copying profiles'),
    onModsProgress: on('mods:progress'),
    openFolder: async (which) => {
      if (which === 'settings' || which === 'game') await call('open', 'settings');
      return null;
    },
    pathForFile: () => '',

    // importing from and installing into other launchers is a PC thing
    detectImports: safe(async () => []),
    pickImportFolder: notHere('Importing'),
    runImport: notHere('Importing'),
    planImport: notHere('Importing'),
    dropImport: notHere('Importing'),
    onImportProgress: on('import:progress'),
    installInfo: async () => ({}),
    runningGames: safe(async () => []),
    installTargets: safe(async () => []),
    installInto: notHere('Installing into other launchers'),
    installIntoRunning: notHere('Installing into other launchers'),
    pushProfile: notHere('Installing into other launchers'),
    exportMod: notHere('Exporting the mod'),
    onInstallProgress: on('install:progress'),

    // play: the app downloads the game, signs in and starts it (its own progress bar shows the steps)
    launch: async (profileId) => {
      try {
        const id = await activeId(profileId);
        const meta = await prefs.get('profileMeta', {});
        meta[id] = { ...(meta[id] || {}), lastPlayed: Date.now() };
        await prefs.set('profileMeta', meta);
        await call('launch', id);
        emit('game:progress', { label: 'Starting Minecraft', progress: 0 });
        return { ok: true };
      } catch (e) {
        return { ok: false, error: String(e.message || e) };
      }
    },
    warmup: async () => null,
    stopGame: notHere('Stopping the game from here'),

    // skins: shown from the account; changing them needs the PC launcher for now
    skinProfile: notHere('Skins'),
    applySkin: notHere('Skins'),
    skinVariant: notHere('Skins'),
    resetSkin: notHere('Skins'),
    setCape: notHere('Capes'),
    skinLibrary: safe(async () => []),
    keepSkin: notHere('Skins'),
    removeSkin: notHere('Skins'),
    renameSkin: notHere('Skins'),
    importSkins: notHere('Skins'),
    dropSkins: notHere('Skins'),

    createShortcut: notHere('Desktop shortcuts'),
    pendingShortcut: async () => null,
    onShortcut: on('shortcut'),

    minimize: () => {},
    close: () => {},
    onProgress: on('game:progress'),
    onLog: on('game:log'),
    onState: on('game:state'),
    onCrash: on('game:crash'),

    // app updates come from the Onix releases (the app asks on start)
    updateStatus: async () => ({ status: 'latest' }),
    installUpdateNow: safe(async () => null),
    checkUpdate: async () => {
      await call('checkUpdate').catch(() => {});
      return { status: 'latest' };
    },
    installUpdate: safe(async () => null),
    openUpdateDownload: safe(() => call('openUrl', 'https://github.com/virtualchickenfr/onix-client/releases/latest')),
    onUpdate: on('update'),
    onModUpdated: on('mod:updated'),

    // Android only: open parts of the app that have no page in the launcher interface
    androidOpen: (page) => call('open', page),
  };

  // back from the game (or another page of the app): the launcher is idle again
  on('android:resume')(() => {
    emit('game:state', 'closed');
    changedProfiles().catch(() => {});
  });

  window.onix = api;
  document.documentElement.classList.add('android');
})();
