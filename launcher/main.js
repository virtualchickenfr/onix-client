const { app, BrowserWindow, ipcMain, shell, dialog, nativeImage } = require('electron');
const os = require('os');
const path = require('path');
const fs = require('fs');
const { Store } = require('./lib/store');
const { Profiles } = require('./lib/profiles');
const auth = require('./lib/auth');
const game = require('./lib/game');
const cosmetics = require('./lib/cosmetics');
const onixnet = require('./lib/onixnet');
const { ensureJava } = require('./lib/java');
const importer = require('./lib/importer');
const versions = require('./lib/versions');
const installer = require('./lib/install');
const modrinth = require('./lib/modrinth');
const packs = require('./lib/packs');
const updater = require('./lib/updater');
const content = require('./lib/content');
const skins = require('./lib/skins');
const shortcuts = require('./lib/shortcuts');
const packFolders = require('./lib/packfolders');
const crashDoctor = require('./lib/crashdoctor');

const GAME_DIR = path.join(app.getPath('appData'), '.onix');
const TOTAL_MB = Math.floor(os.totalmem() / 1024 / 1024);

let win;
let store;
let profiles;
let running = false;
/** Launcher profile the running game was started from (its folder must not change underneath it). */
let runningProfileId = null;
let playStartedAt = 0;
let gameProc = null;
/** A shortcut start (--launch=…) waiting for the page to be ready. */
let pendingShortcut = shortcuts.launchArgs(process.argv);
const PACK_FILE = /\.(mrpack|dawnpack|zip)$/i;
const packArg = (argv) => (argv || []).slice(1).find((a) => /\.(mrpack|dawnpack)$/i.test(String(a)) && fs.existsSync(String(a))) || null;
let pendingImport = packArg(process.argv);
// macOS hands opened files over as an event
app.on('open-file', (e, file) => {
  e.preventDefault();
  if (win) win.webContents.send('import-file', file);
  else pendingImport = file;
});
const importSources = new Map();

const DEFAULT_SETTINGS = {
  javaPath: '',
  closeOnLaunch: false,
  fastStart: true,
  theme: 'onyx',
  background: 'carbon',
  onixServer: '',
  autoUpdate: true,
};

/** Writes design choices into config/onix/client.json of every profile, so game and launcher match. */
function syncClientSettings(values) {
  for (const profile of profiles.list()) {
    const file = path.join(profiles.dir(profile), 'config', 'onix', 'client.json');
    let json = {};
    try {
      json = JSON.parse(fs.readFileSync(file, 'utf8'));
    } catch {
      json = {};
    }
    json.settings = { ...(json.settings || {}), ...values };
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, JSON.stringify(json, null, 2));
  }
}

// ------------------------------------------------------------------ in-game sky (Custom Sky module)
// Stored in the active Onix profile of every launcher profile: config/onix/profiles/<name>.json

function onixProfileFile(profile) {
  const base = path.join(profiles.dir(profile), 'config', 'onix');
  let active = 'Default';
  try {
    // same rule as the mod (ProfileManager.sanitize): the name becomes a file name
    const raw = String(JSON.parse(fs.readFileSync(path.join(base, 'client.json'), 'utf8')).activeProfile || '');
    active = raw.replace(/[^\p{L}\p{N} _-]/gu, '').trim().slice(0, 24) || 'Default';
  } catch {
    // no client.json yet: the mod starts with "Default"
  }
  return { file: path.join(base, 'profiles', `${active}.json`), active, base };
}

function readSky() {
  const { file } = onixProfileFile(profiles.active());
  let mod = {};
  try {
    mod = (JSON.parse(fs.readFileSync(file, 'utf8')).modules || {}).customsky || {};
  } catch {
    mod = {};
  }
  const st = mod.settings || {};
  return { enabled: !!mod.enabled, daySky: st.daySky || 'Clear Day', nightSky: st.nightSky || 'Starry Night' };
}

async function writeSky(value) {
  const touched = [];
  for (const profile of profiles.list()) {
    const { file, active, base } = onixProfileFile(profile);
    let json = {};
    try {
      json = JSON.parse(fs.readFileSync(file, 'utf8'));
    } catch {
      json = {};
    }
    json.version = json.version || 2;
    json.modules = json.modules || {};
    const mod = json.modules.customsky || {};
    mod.enabled = !!value.enabled;
    mod.settings = { ...(mod.settings || {}), daySky: String(value.daySky), nightSky: String(value.nightSky) };
    json.modules.customsky = mod;
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, JSON.stringify(json, null, 2));
    touched.push({ gameDir: profiles.dir(profile), active, base });
  }
  // a game that is running right now loads the change immediately (see LauncherInbox in the mod)
  let live = 0;
  try {
    const games = await installer.running(GAME_DIR);
    for (const t of touched) {
      if (!games.some((g) => g.onix && g.gameDir && path.resolve(g.gameDir) === path.resolve(t.gameDir))) continue;
      fs.writeFileSync(path.join(t.base, 'inbox.json'),
        JSON.stringify({ action: 'load-profile', profile: t.active, from: 'Onix Launcher', at: Date.now() }, null, 2));
      live++;
    }
  } catch {
    // detection is best effort
  }
  return { ...readSky(), live };
}

// ------------------------------------------------------------------ custom themes
// Stored in the launcher and as config/onix/themes/<id>.json in every profile (the format the mod uses).

const THEME_ID = /^custom-[0-9a-f]{6}$/;
const HEX = /^#[0-9a-fA-F]{6}$/;

function cleanTheme(t) {
  if (!t || !THEME_ID.test(t.id)) return null;
  const color = (v, fallback) => (HEX.test(v) ? v.toUpperCase() : fallback);
  return {
    id: t.id,
    name: String(t.name || 'My Theme').trim().slice(0, 24) || 'My Theme',
    accent: color(t.accent, '#8B7CFF'),
    panel: color(t.panel, '#0E0C17'),
    sky: color(t.sky, '#7C6CF6'),
    particle: color(t.particle, '#FFFFFF'),
    ambient: ['Sparks', 'Petals', 'Snow', 'Embers'].includes(t.ambient) ? t.ambient : 'Sparks',
  };
}

/** Themes saved in the launcher plus themes created in game (in any profile). */
function listThemes() {
  const byId = new Map();
  for (const t of store.get('customThemes') || []) {
    const c = cleanTheme(t);
    if (c) byId.set(c.id, c);
  }
  for (const profile of profiles.list()) {
    const dir = path.join(profiles.dir(profile), 'config', 'onix', 'themes');
    let files = [];
    try {
      files = fs.readdirSync(dir).filter((f) => f.endsWith('.json'));
    } catch {
      continue;
    }
    for (const f of files) {
      try {
        const c = cleanTheme(JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')));
        if (c && !byId.has(c.id)) byId.set(c.id, c);
      } catch {
        // ignore broken files
      }
    }
  }
  return [...byId.values()].sort((a, b) => a.id.localeCompare(b.id));
}

function writeThemeEverywhere(theme, remove = false) {
  for (const profile of profiles.list()) {
    const file = path.join(profiles.dir(profile), 'config', 'onix', 'themes', `${theme.id}.json`);
    if (remove) {
      fs.rmSync(file, { force: true });
    } else {
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, JSON.stringify(theme, null, 2));
    }
  }
}

function createWindow() {
  win = new BrowserWindow({
    width: 1120,
    height: 680,
    minWidth: 940,
    minHeight: 600,
    frame: false,
    backgroundColor: '#0e0b09',
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  // the launcher is a single local page: never navigate away from it or open new windows
  win.webContents.on('will-navigate', (e) => e.preventDefault());
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.loadFile(path.join(__dirname, 'src', 'index.html'));
}

function send(channel, payload) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
}

function settings() {
  return { ...DEFAULT_SETTINGS, ...(store.get('settings') || {}) };
}

function profileState() {
  return { profiles: profiles.list(), active: profiles.active().id };
}

function safe(fn) {
  return async (...args) => {
    try {
      return { ok: true, value: await fn(...args) };
    } catch (err) {
      return { ok: false, error: friendlyError(err) };
    }
  };
}

// one launcher: a second start (for example from a desktop shortcut) hands its arguments to the open window
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', (_e, argv) => {
    const req = shortcuts.launchArgs(argv);
    if (win) {
      if (win.isMinimized()) win.restore();
      win.show();
      win.focus();
    }
    if (req && win) win.webContents.send('shortcut', req);
    const pack = packArg(argv);
    if (pack && win) win.webContents.send('import-file', pack);
  });
}

app.whenReady().then(() => {
  fs.mkdirSync(GAME_DIR, { recursive: true });
  store = new Store(path.join(app.getPath('userData'), 'onix-launcher.json'), {});
  // all Minecraft versions Fabric supports (cached, refreshed in the background)
  versions.init(path.join(app.getPath('userData'), 'cache', 'versions.json'));
  profiles = new Profiles(store, GAME_DIR, { ramMb: Math.min(4096, Math.floor(TOTAL_MB / 2)) });
  importer.setCacheDir(path.join(app.getPath('userData'), 'cache'));

  versions.refresh(require('./lib/download').fetchJson).catch(() => {});
  ipcMain.handle('loaders:list', () => require('./lib/loaders').LOADERS);
  ipcMain.handle('loaders:versions', safe((_e, loader, game) => require('./lib/loaders').versions(String(loader), String(game))));
  ipcMain.handle('versions:list', async () => {
    await versions.refresh(require('./lib/download').fetchJson).catch(() => {});
    return versions.all();
  });
  ipcMain.handle('app:info', () => ({
    version: app.getVersion(),
    mcVersion: game.MC_VERSION,
    loader: game.LOADER_VERSION,
    versions: versions.all(),
    totalMemMb: TOTAL_MB,
    gameDir: GAME_DIR,
  }));

  // Account
  ipcMain.handle('auth:get', () => auth.current(store));
  ipcMain.handle('auth:list', () => auth.list(store));
  ipcMain.handle('auth:login', safe(async () => {
    const account = await auth.login(store);
    return account;
  }));
  ipcMain.handle('auth:switch', safe((_e, uuid) => {
    if (running) throw new Error('Close Minecraft before switching accounts.');
    const account = auth.switchTo(store, uuid);
    auth.prefetch(store); // get the session ready before Play is pressed
    return account;
  }));
  ipcMain.handle('auth:remove', safe((_e, uuid) => auth.remove(store, uuid)));
  ipcMain.handle('auth:logout', () => auth.logout(store));
  // renew the session in the background so the first Play does not wait for Microsoft
  auth.prefetch(store);

  // Global settings
  ipcMain.handle('settings:get', () => settings());
  ipcMain.handle('settings:set', (_e, value) => {
    store.set('settings', { ...settings(), ...value });
    const sync = {};
    if (value && typeof value.theme === 'string') sync.theme = value.theme;
    if (value && typeof value.background === 'string') sync.titleBackground = value.background;
    if (value && typeof value.onixServer === 'string') sync.onixServer = value.onixServer.trim();
    if (Object.keys(sync).length) {
      try {
        syncClientSettings(sync);
      } catch (err) {
        console.warn('Could not sync design settings:', err);
      }
    }
    return settings();
  });

  // In-game sky
  ipcMain.handle('sky:get', () => readSky());
  ipcMain.handle('sky:set', safe((_e, value) => writeSky(value || {})));

  // Custom themes
  ipcMain.handle('themes:list', () => listThemes());
  ipcMain.handle('themes:save', safe((_e, raw) => {
    const theme = cleanTheme(raw);
    if (!theme) throw new Error('Invalid theme');
    const list = (store.get('customThemes') || []).filter((t) => t.id !== theme.id);
    store.set('customThemes', [...list, theme]);
    writeThemeEverywhere(theme);
    return listThemes();
  }));
  ipcMain.handle('themes:delete', safe((_e, id) => {
    if (typeof id !== 'string' || !THEME_ID.test(id)) throw new Error('Invalid theme');
    store.set('customThemes', (store.get('customThemes') || []).filter((t) => t.id !== id));
    writeThemeEverywhere({ id }, true);
    if (settings().theme === id) {
      store.set('settings', { ...settings(), theme: 'onyx' });
      syncClientSettings({ theme: 'onyx' });
    }
    return listThemes();
  }));

  // Profiles
  ipcMain.handle('profiles:get', () => profileState());
  ipcMain.handle('profiles:select', (_e, id) => {
    profiles.select(id);
    return profileState();
  });
  ipcMain.handle('profiles:save', safe((_e, profile) => {
    const before = profiles.list().find((p) => p.id === (profile && profile.id));
    if (before && running && before.id === runningProfileId && ((profile.mcVersion && profile.mcVersion !== before.mcVersion) || (profile.loader && profile.loader !== (before.loader || 'fabric')))) {
      throw new Error('Close Minecraft before changing the version or loader of this profile.');
    }
    profiles.save(profile);
    return profileState();
  }));
  ipcMain.handle('profiles:create', safe((_e, name, copyFrom, mcVersion) => {
    const created = profiles.create(name, copyFrom, versions.isSupported(mcVersion) ? { mcVersion } : {});
    prepareProfile(created);
    return profileState();
  }));
  ipcMain.handle('profiles:delete', safe((_e, id) => {
    if (running && id === runningProfileId) throw new Error('Close Minecraft before deleting the profile it is running from.');
    profiles.remove(id);
    return profileState();
  }));

  const profileById = (id) => profiles.list().find((p) => p.id === id) || profiles.active();
  const modsDirOf = (id) => path.join(profiles.dir(profileById(id)), 'mods');
  const mcOf = (id) => versions.ofProfile(profileById(id)).mc;
  const loaderOf = (id) => profileById(id).loader || 'fabric';
  const progress = (p) => send('mods:progress', p);
  /** Mods of the profile Minecraft is running from must not change underneath it. */
  const notWhileRunning = (profileId) => {
    if (running && profileById(profileId).id === runningProfileId) throw new Error('Close Minecraft before changing the mods of this profile.');
  };
  const PROJECT_ID = /^[\w-]{1,64}$/;

  // Modrinth: browse and install mods, keep them up to date
  ipcMain.handle('modrinth:search', safe((_e, opts) => {
    const o = opts || {};
    // mods: for the version of the profile they go into; modpacks: any version Onix plays
    const mc = o.type === 'modpack' ? versions.LIST : [versions.isSupported(o.mc) ? o.mc : mcOf(o.profileId)];
    return modrinth.search({ ...o, mc, loader: o.type === 'modpack' ? 'fabric' : loaderOf(o.profileId) });
  }));
  ipcMain.handle('modrinth:installed', safe((_e, profileId) => Object.keys(modrinth.cleanManifest(modsDirOf(profileId)))));
  ipcMain.handle('modrinth:install', safe(async (_e, profileId, projectId) => {
    notWhileRunning(profileId);
    if (typeof projectId !== 'string' || !PROJECT_ID.test(projectId)) throw new Error('Unknown project');
    return modrinth.install(projectId, modsDirOf(profileId), progress, mcOf(profileId), loaderOf(profileId));
  }));
  // Resource packs and shaders from Modrinth (shaders bring Iris along)
  const ASSET_KINDS = { resourcepack: 'resourcepacks', shader: 'shaderpacks' };
  const assetDir = (profileId, kind) => {
    const folder = ASSET_KINDS[kind];
    if (!folder) throw new Error('Unknown kind');
    return path.join(profiles.dir(profileById(profileId)), folder);
  };
  ipcMain.handle('modrinth:installedAssets', safe((_e, profileId, kind) => Object.keys(modrinth.cleanManifest(assetDir(profileId, kind)))));
  ipcMain.handle('modrinth:installAsset', safe(async (_e, profileId, kind, projectId) => {
    notWhileRunning(profileId);
    if (typeof projectId !== 'string' || !PROJECT_ID.test(projectId)) throw new Error('Unknown project');
    return modrinth.installAsset(projectId, kind, assetDir(profileId, kind), modsDirOf(profileId), progress, mcOf(profileId), loaderOf(profileId));
  }));

  // What else is in a profile: packs, worlds, screenshots, logs
  const dirOf = (profileId) => profiles.dir(profileById(profileId));
  const PACK_KINDS = Object.keys(content.KINDS);
  const packKind = (kind) => {
    if (!PACK_KINDS.includes(kind)) throw new Error('Unknown kind');
    return kind;
  };
  ipcMain.handle('content:packs', safe((_e, profileId, kind) => content.listPacks(dirOf(profileId), packKind(kind))));
  ipcMain.handle('content:togglePack', safe((_e, profileId, kind, file) => content.togglePack(dirOf(profileId), packKind(kind), file)));
  ipcMain.handle('content:removePack', safe(async (_e, profileId, kind, file) => {
    await shell.trashItem(content.packPath(dirOf(profileId), packKind(kind), file));
    return true;
  }));
  ipcMain.handle('content:addPacks', safe((_e, profileId, kind, paths) => content.addPacks(dirOf(profileId), packKind(kind), Array.isArray(paths) ? paths : [])));
  ipcMain.handle('content:pickPacks', safe(async (_e, profileId, kind) => {
    const res = await dialog.showOpenDialog(win, {
      title: kind === 'shaderpacks' ? 'Add shader packs' : 'Add resource packs',
      properties: ['openFile', 'multiSelections'],
      filters: [{ name: 'Packs', extensions: ['zip'] }],
    });
    if (res.canceled) return [];
    return content.addPacks(dirOf(profileId), packKind(kind), res.filePaths);
  }));
  ipcMain.handle('content:worlds', safe((_e, profileId) => content.listWorlds(dirOf(profileId))));
  ipcMain.handle('content:openWorld', safe((_e, profileId, folder) => shell.openPath(content.worldPath(dirOf(profileId), folder))));
  ipcMain.handle('content:removeWorld', safe(async (_e, profileId, folder) => {
    if (running && profileById(profileId).id === runningProfileId) throw new Error('Close Minecraft before removing a world.');
    await shell.trashItem(content.worldPath(dirOf(profileId), folder));
    return true;
  }));
  ipcMain.handle('content:screenshots', safe((_e, profileId, limit) => {
    const dir = dirOf(profileId);
    return content.listScreenshots(dir).slice(0, Math.min(200, Number(limit) || 60)).map((s) => {
      let thumb = null;
      try {
        const img = nativeImage.createFromPath(content.screenshotPath(dir, s.file));
        if (!img.isEmpty()) thumb = img.resize({ width: 360, quality: 'good' }).toDataURL();
      } catch {
        thumb = null;
      }
      return { ...s, thumb };
    });
  }));
  ipcMain.handle('content:openScreenshot', safe((_e, profileId, file) => shell.openPath(content.screenshotPath(dirOf(profileId), file))));
  ipcMain.handle('content:showScreenshot', safe((_e, profileId, file) => shell.showItemInFolder(content.screenshotPath(dirOf(profileId), file))));
  ipcMain.handle('content:removeScreenshot', safe(async (_e, profileId, file) => {
    await shell.trashItem(content.screenshotPath(dirOf(profileId), file));
    return true;
  }));
  ipcMain.handle('content:logs', safe((_e, profileId) => content.listLogs(dirOf(profileId))));
  ipcMain.handle('content:readLog', safe((_e, profileId, id) => content.readLog(dirOf(profileId), id)));
  ipcMain.handle('content:sizes', safe((_e, profileId) => {
    const dir = dirOf(profileId);
    return { total: content.sizeOf(dir), saves: content.sizeOf(path.join(dir, 'saves')), mods: content.sizeOf(path.join(dir, 'mods')) };
  }));
  ipcMain.handle('profiles:duplicate', safe((_e, id) => {
    if (running && id === runningProfileId) throw new Error('Close Minecraft before copying the profile it is running from.');
    const copy = profiles.duplicate(id);
    return { state: profileState(), id: copy.id };
  }));
  ipcMain.handle('java:pick', safe(async () => {
    const res = await dialog.showOpenDialog(win, {
      title: 'Choose a Java executable',
      properties: ['openFile'],
      filters: process.platform === 'win32' ? [{ name: 'Java', extensions: ['exe'] }] : [],
    });
    return res.canceled ? null : res.filePaths[0];
  }));

  ipcMain.handle('modrinth:updates', safe((_e, profileId) => modrinth.checkUpdates(modsDirOf(profileId), mcOf(profileId), loaderOf(profileId))));
  ipcMain.handle('modrinth:update', safe(async (_e, profileId, projectIds) => {
    notWhileRunning(profileId);
    // ask Modrinth again instead of trusting what the page sends
    const wanted = new Set(Array.isArray(projectIds) ? projectIds : []);
    const updates = (await modrinth.checkUpdates(modsDirOf(profileId), mcOf(profileId), loaderOf(profileId))).filter((u) => !wanted.size || wanted.has(u.projectId));
    return modrinth.applyUpdates(modsDirOf(profileId), updates, progress, mcOf(profileId), loaderOf(profileId));
  }));

  // Profiles as modpacks: export, import, copy into other launchers
  const uniqueProfileName = (raw) => {
    const existing = new Set(profiles.list().map((p) => p.name.toLowerCase()));
    const base = String(raw || '').replace(/[^\p{L}\p{N} _-]/gu, '').trim().slice(0, 20) || 'Imported';
    let name = base;
    for (let n = 2; existing.has(name.toLowerCase()); n++) name = `${base.slice(0, 17)} ${n}`;
    return name;
  };
  /** Imports a pack file into a new profile; the profile is removed again if anything fails. */
  const importIntoNewProfile = async (file, fallbackName) => {
    const fallbackMc = mcOf();
    const info = await packs.inspect(file, fallbackMc); // fails early for files that are not modpacks
    const before = profiles.active().id;
    const profile = profiles.create(uniqueProfileName(info.name || fallbackName), null, { mcVersion: info.mc });
    try {
      const result = await packs.importPack(file, profiles.dir(profile), progress, fallbackMc);
      await game.adoptManagedMods(profiles.dir(profile));
      return { ...result, profile: profile.name, state: profileState() };
    } catch (err) {
      profiles.remove(profile.id);
      profiles.select(before);
      throw err;
    }
  };
  ipcMain.handle('profile:parts', () => Object.fromEntries(Object.entries(packs.PARTS).map(([k, v]) => [k, { label: v.label, default: v.default }])));
  ipcMain.handle('profile:export', safe(async (_e, profileId, opts) => {
    const profile = profileById(profileId);
    const o = opts || {};
    const format = o.format === 'curseforge' ? 'curseforge' : 'mrpack';
    const ext = format === 'mrpack' ? 'mrpack' : 'zip';
    const res = await dialog.showSaveDialog(win, {
      title: 'Export profile',
      defaultPath: path.join(app.getPath('downloads'), `${profile.name.replace(/[^\w -]/g, '').trim() || 'Onix'}.${ext}`),
      filters: [format === 'mrpack' ? { name: 'Modrinth modpack', extensions: ['mrpack'] } : { name: 'CurseForge modpack', extensions: ['zip'] }],
    });
    if (res.canceled || !res.filePath) return null;
    const parts = {};
    for (const key of Object.keys(packs.PARTS)) if (o.parts && key in o.parts) parts[key] = !!o.parts[key];
    const result = await packs.exportProfile(profiles.dir(profile), res.filePath, {
      format,
      name: String(o.name || profile.name),
      version: String(o.version || '1.0.0'),
      author: (auth.current(store) || {}).name || '',
      includeOnix: o.includeOnix !== false,
      parts,
      mc: mcOf(profileId),
    }, progress);
    shell.showItemInFolder(res.filePath);
    return result;
  }));
  ipcMain.handle('profile:import', safe(async () => {
    const res = await dialog.showOpenDialog(win, {
      title: 'Import a modpack',
      properties: ['openFile'],
      filters: [{ name: 'Modpacks (.mrpack, .dawnpack, CurseForge .zip)', extensions: ['mrpack', 'dawnpack', 'zip', 'json'] }],
    });
    if (res.canceled || !res.filePaths[0]) return null;
    return importIntoNewProfile(res.filePaths[0], path.basename(res.filePaths[0], path.extname(res.filePaths[0])));
  }));
  // a pack dropped on the window or opened with the launcher (double-click on a .dawnpack / .mrpack)
  ipcMain.handle('profile:importFile', safe(async (_e, file) => {
    const p = String(file || '');
    if (!PACK_FILE.test(p) || !fs.existsSync(p) || !fs.statSync(p).isFile()) throw new Error('Only .mrpack, .dawnpack and modpack .zip files can be imported.');
    return importIntoNewProfile(p, path.basename(p, path.extname(p)));
  }));
  ipcMain.handle('profile:pendingImport', () => {
    const p = pendingImport;
    pendingImport = null;
    return p;
  });
  ipcMain.handle('modrinth:installPack', safe(async (_e, projectId) => {
    if (typeof projectId !== 'string' || !PROJECT_ID.test(projectId)) throw new Error('Unknown project');
    // the newest version of the pack that Onix plays, preferring the active profile's version
    const order = [mcOf(), ...versions.LIST.slice().reverse()].filter((v, i, a) => a.indexOf(v) === i);
    let pack = null;
    let lastErr = null;
    for (const mc of order) {
      try {
        pack = await packs.downloadModpack(projectId, progress, mc);
        break;
      } catch (err) {
        lastErr = err;
      }
    }
    if (!pack) throw lastErr || new Error('This modpack has no version Onix can play.');
    try {
      return await importIntoNewProfile(pack.file, pack.title);
    } finally {
      fs.rmSync(pack.file, { force: true });
    }
  }));
  let copyTargets = [];
  ipcMain.handle('profile:targets', safe(() => {
    copyTargets = installer.targets();
    return copyTargets;
  }));
  ipcMain.handle('profile:copyTo', safe(async (_e, profileId, targetId, opts) => {
    const target = copyTargets.find((t) => t.id === targetId);
    if (!target) throw new Error('This installation is no longer available. Please scan again.');
    return installer.copyProfileInto(target, profiles.dir(profileById(profileId)), { settings: !!(opts && opts.settings) }, progress);
  }));

  // Cosmetics (apply to the active profile)
  ipcMain.handle('cosmetics:get', () => cosmetics.get(profiles.dir()));
  ipcMain.handle('cosmetics:set', safe(async (_e, slot, id) => cosmetics.set(profiles.dir(), slot, id)));
  ipcMain.handle('cosmetics:setAll', safe(async (_e, outfit) => cosmetics.setAll(profiles.dir(), outfit)));
  ipcMain.handle('cosmetics:setTagText', safe(async (_e, text) => cosmetics.setTagText(profiles.dir(), text)));

  // Coins, shop and gifts (Onix server, signed in with the active account)
  const onixServer = () => settings().onixServer || onixnet.DEFAULT_SERVER;
  const credentials = () => auth.refresh(store, true);
  ipcMain.handle('wallet:get', safe(() => onixnet.wallet(onixServer(), credentials)));
  ipcMain.handle('wallet:buy', safe((_e, item) => onixnet.buy(onixServer(), credentials, item)));
  ipcMain.handle('wallet:gift', safe((_e, item, to, message) => onixnet.gift(onixServer(), credentials, item, to, message)));
  ipcMain.handle('wallet:seen', safe((_e, ids) => onixnet.seen(onixServer(), credentials, ids)));

  // Mods
  ipcMain.handle('mods:list', (_e, profileId) => {
    const dir = profiles.dir(profileById(profileId));
    const fromModrinth = modrinth.cleanManifest(path.join(dir, 'mods'));
    const byFile = new Map(Object.entries(fromModrinth).map(([id, m]) => [m.file, { id, ...m }]));
    return game.listMods(dir).map((mod) => {
      const m = byFile.get(mod.file.replace(/\.disabled$/, ''));
      return m ? { ...mod, modrinth: { id: m.id, title: m.title, version: m.version, icon: m.icon || null } } : mod;
    });
  });
  ipcMain.handle('mods:remove', safe((_e, profileId, file) => {
    notWhileRunning(profileId);
    const dir = modsDirOf(profileId);
    const name = path.basename(String(file || ''));
    if (!/\.jar(\.disabled)?$/i.test(name)) throw new Error('Not a mod file');
    const mod = game.listMods(path.dirname(dir)).find((m) => m.file === name);
    if (!mod) throw new Error('This mod is already gone.');
    if (mod.core) throw new Error(`${mod.name} is needed by Onix and cannot be removed.`);
    fs.rmSync(path.join(dir, name), { force: true });
    modrinth.cleanManifest(dir);
    return true;
  }));
  // .jar files dropped onto the launcher: Fabric mods for this Minecraft version go into the active profile
  ipcMain.handle('mods:add', safe(async (_e, paths, profileId) => {
    notWhileRunning(profileId);
    const modsDir = modsDirOf(profileId);
    fs.mkdirSync(modsDir, { recursive: true });
    const added = [];
    const skipped = [];
    for (const raw of (Array.isArray(paths) ? paths : []).slice(0, 50)) {
      const file = String(raw || '');
      const name = path.basename(file);
      if (!/\.jar$/i.test(name) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
        skipped.push(`${name || 'File'}: not a .jar file`);
        continue;
      }
      const info = await importer.readFabricModJson(file);
      if (!info) {
        skipped.push(`${name}: not a Fabric mod`);
        continue;
      }
      const mc = info.depends && info.depends.minecraft;
      if (!importer.matchesVersion(mc, mcOf(profileId))) {
        skipped.push(`${info.name || name}: made for Minecraft ${[].concat(mc).join(' / ')}`);
        continue;
      }
      fs.copyFileSync(file, path.join(modsDir, name));
      added.push(info.name || name.replace(/\.jar$/i, ''));
    }
    return { added, skipped, profile: profileById(profileId).name };
  }));
  ipcMain.handle('mods:toggle', safe((_e, file, profileId) => {
    notWhileRunning(profileId);
    const dir = profiles.dir(profileById(profileId));
    game.toggleMod(dir, file);
    return game.listMods(dir);
  }));

  ipcMain.handle('folder:open', (_e, which, profileId) => {
    const base = profileId ? profiles.dir(profileById(profileId)) : profiles.dir();
    const dir = {
      mods: path.join(base, 'mods'),
      screenshots: path.join(base, 'screenshots'),
      resourcepacks: path.join(base, 'resourcepacks'),
      shaderpacks: path.join(base, 'shaderpacks'),
      saves: path.join(base, 'saves'),
      logs: path.join(base, 'logs'),
      profile: base,
    }[which] || GAME_DIR;
    fs.mkdirSync(dir, { recursive: true });
    return shell.openPath(dir);
  });

  // Play
  /**
   * Downloads everything a profile needs in the background (after an import or when a profile is created),
   * so its first start is quick. A start waits for a download that is still running.
   */
  let preparing = null;
  const prepareProfile = (profile) => {
    if (!profile || running) return;
    const before = preparing || Promise.resolve();
    const job = before.then(async () => {
      const report = (label, progress) => send('prepare:progress', { profile: profile.name, label, progress });
      const log = (line) => send('game:log', line);
      try {
        log(`Getting profile "${profile.name}" ready in the background`);
        report('Downloading Java', 0);
        const s = settings();
        const javaPath = s.javaPath && s.javaPath.trim() ? s.javaPath.trim() : await ensureJava(GAME_DIR, report, versions.javaMajor(versions.ofProfile(profile).mc));
        await game.prepare({ root: GAME_DIR, gameDir: profiles.dir(profile), javaPath, settings: profile, report, log });
        send('prepare:progress', { profile: profile.name, done: true });
        log(`Profile "${profile.name}" is ready`);
      } catch (err) {
        log(`Could not prepare "${profile.name}": ${err && err.message ? err.message : err}`);
        send('prepare:progress', { profile: profile.name, done: true, error: friendlyError(err) });
      }
    });
    const settled = job.finally(() => {
      if (preparing === settled) preparing = null;
    });
    preparing = settled;
  };
  ipcMain.handle('profile:prepare', (_e, id) => {
    prepareProfile(profiles.list().find((p) => p.id === id) || profiles.active());
    return true;
  });

  ipcMain.handle('game:launch', async (_e, profileId, quick) => {
    if (running) return { ok: false, error: 'Minecraft is already running.' };
    if (profileId && profiles.list().some((p) => p.id === profileId)) profiles.select(profileId);
    running = true;
    if (preparing) {
      send('game:progress', { label: 'Finishing downloads', progress: 0 });
      await preparing.catch(() => {});
    }
    const s = settings();
    const profile = profiles.active();
    runningProfileId = profile.id;
    const report = (label, progress) => send('game:progress', { label, progress });
    const output = [];
    const log = (line) => {
      send('game:log', line);
      output.push(String(line));
      if (output.length > 6000) output.splice(0, 1000);
    };
    try {
      log(`Starting profile "${profile.name}"`);
      const started = Date.now();
      report('Preparing', 0);
      // sign-in, Java and the game files do not depend on each other – check them at the same time
      const [authorization, javaPath] = await Promise.all([
        auth.refresh(store, s.fastStart !== false),
        profile.javaPath ? profile.javaPath : s.javaPath && s.javaPath.trim() ? s.javaPath.trim() : ensureJava(GAME_DIR, report, versions.javaMajor(versions.ofProfile(profile).mc)),
      ]);
      log(`Sign-in and Java ready after ${((Date.now() - started) / 1000).toFixed(1)} s`);
      packFolders.write(profiles.dir(profile), profiles.list().map((p) => profiles.dir(p)), log);
      gameProc = await game.launch({
        fastStart: s.fastStart !== false,
        root: GAME_DIR,
        gameDir: profiles.dir(profile),
        auth: authorization,
        javaPath,
        settings: profile,
        world: quick && typeof quick.world === 'string' ? path.basename(quick.world) : null,
        server: quick && typeof quick.server === 'string' && /^[A-Za-z0-9.\-_]+(:\d{1,5})?$/.test(quick.server) ? quick.server : null,
        report,
        log,
        onStart: () => {
          playStartedAt = Date.now();
          send('game:state', 'running');
          if (s.closeOnLaunch && win) win.hide();
        },
        onClose: (code) => {
          running = false;
          gameProc = null;
          if (playStartedAt) {
            profiles.recordPlay(profile.id, playStartedAt);
            playStartedAt = 0;
            send('profiles:changed', profileState());
          }
          send('game:state', code === 0 ? 'closed' : 'crashed');
          if (win) win.show();
          if (code !== 0) {
            // name the mod that broke the start, so it can be turned off with one click
            crashDoctor.attachFiles(crashDoctor.analyze(output), path.join(profiles.dir(profile), 'mods'))
              .then((d) => { if (d) send('game:crash', { ...d, profileId: profile.id }); })
              .catch(() => {});
          }
        },
      });
      return { ok: true };
    } catch (err) {
      running = false;
      log(String(err && err.stack ? err.stack : err));
      return { ok: false, error: friendlyError(err) };
    }
  });

  // Import from other clients
  const summarize = (src) => ({
    id: src.id,
    client: src.client,
    name: src.name,
    label: src.label,
    options: src.options,
    servers: src.servers,
    worlds: src.worlds,
    resourcepacks: src.resourcepacks,
    shaderpacks: src.shaderpacks,
    screenshots: src.screenshots,
    mods: src.modsCompatible.map((m) => m.name),
    dawnpacks: src.dawnpacks || 0,
    modsSkipped: src.modsIncompatible.map((m) => `${m.name || m.file}: ${m.reason}`),
    modConfigs: src.modConfigs != null ? src.modConfigs : !!src.gameDir,
    mcVersion: src.mcVersion || null,
    targetVersion: src.targetVersion || versions.DEFAULT,
    newerWorlds: !!(src.mcVersion && importer.isNewerThanGame(src.mcVersion, src.targetVersion || versions.DEFAULT)),
    modules: src.modules,
    lastUsed: src.lastUsed || 0,
    modsTotal: (src.modsAll || []).length + (src.modsOther || []).length,
    exact: !!src.exact,
    profileName: src.profileName || null,
    memoryMb: src.exact ? src.exact.memoryMb : null,
  });
  ipcMain.handle('import:detect', safe(async () => {
    const found = await importer.detect();
    found.forEach((s) => importSources.set(s.id, s));
    return found.map(summarize);
  }));
  ipcMain.handle('import:pick', safe(async () => {
    const res = await dialog.showOpenDialog(win, { title: 'Choose a folder with Minecraft data', properties: ['openDirectory'] });
    if (res.canceled || !res.filePaths[0]) return [];
    const found = await importer.analyzeFolder(res.filePaths[0]);
    found.forEach((s) => importSources.set(s.id, s));
    if (!found.length) throw new Error('No Minecraft data found in this folder.');
    return found.map(summarize);
  }));
  ipcMain.handle('import:plan', safe(async (_e, sourceId, target) => {
    const source = importSources.get(sourceId);
    if (!source) throw new Error('This source is no longer available. Please scan again.');
    const mc = target && target.profileId ? mcOf(target.profileId) : versions.isSupported(target && target.mc) ? target.mc : source.targetVersion || versions.DEFAULT;
    const p = await importer.plan(source, mc, target && target.profileId ? loaderOf(target.profileId) : 'fabric');
    return { mc: p.mc, ready: p.ready.map((m) => m.name), update: p.update.map((u) => ({ name: u.name, to: u.to })), missing: p.missing, offline: p.offline };
  }));
  // folders and files dropped on the Import page
  ipcMain.handle('import:drop', safe(async (_e, paths) => {
    const found = [];
    for (const p of (paths || []).map(String)) {
      if (!fs.existsSync(p) || !fs.statSync(p).isDirectory()) continue;
      for (const src of await importer.analyzeFolder(p)) {
        importSources.set(src.id, src);
        found.push(summarize(src));
      }
    }
    return found;
  }));
  ipcMain.handle('import:run', safe(async (_e, sourceId, target, what) => {
    const source = importSources.get(sourceId);
    if (!source) throw new Error('This source is no longer available. Please scan again.');
    if (running) throw new Error('Close Minecraft first.');
    let profile;
    // Dawn 1:1: a new profile with the same Minecraft version, memory and every file as it was
    if (what && what.exact && source.exact) {
      const mcExact = versions.isSupported(source.mcVersion) ? source.mcVersion : source.targetVersion || versions.DEFAULT;
      const created = profiles.create((target && target.newName) || source.profileName || source.name, null, { mcVersion: mcExact });
      const total = require('os').totalmem() / 1048576;
      // Dawn's own setting, but at most 8 GB (more makes Minecraft stutter) and never more than the PC can spare
      const ram = source.exact.memoryMb ? Math.max(1024, Math.min(source.exact.memoryMb, 8192, Math.floor(total - 2048))) : created.ramMb;
      profiles.save({ ...created, ramMb: ram, performanceMods: false, loader: 'fabric' });
      const dir = profiles.dir(created);
      try {
        const done = await importer.exactCopy(source, dir, (p) => send('import:progress', p));
        prepareProfile(profiles.list().find((p) => p.id === created.id));
        return { done, profile: created.name, state: profileState() };
      } catch (err) {
        profiles.remove(created.id);
        throw err;
      }
    }
    const chosenMc = target && versions.isSupported(target.mc) ? target.mc : source.targetVersion || versions.DEFAULT;
    if (target && target.newName) profile = profiles.create(target.newName, null, { mcVersion: chosenMc });
    else profile = profiles.select(target && target.profileId ? target.profileId : profiles.active().id);
    const mc = versions.ofProfile(profile).mc;
    if (what && what.mods && what.updateMods !== false) await importer.plan(source, mc, loaderOf(profile.id)).catch(() => null);
    const done = await importer.runImport(source, profiles.dir(profile), what || {}, (p) => send('import:progress', p), mc);
    // download Minecraft, Fabric, Java and the mods now, so the first start of the imported profile is quick
    prepareProfile(profile);
    return { done, profile: profile.name, state: profileState() };
  }));

  // Install: running games, other launchers, mod download
  let installTargets = [];
  const profileDirById = (id) => {
    const p = profiles.list().find((x) => x.id === id) || profiles.active();
    return profiles.dir(p);
  };
  ipcMain.handle('install:bundled', (_e, mc) => installer.bundledInfo(versions.hasOnix(mc) ? mc : versions.DEFAULT));
  ipcMain.handle('install:running', safe(() => installer.running(GAME_DIR)));
  ipcMain.handle('install:targets', safe(() => {
    installTargets = installer.targets();
    return installTargets;
  }));
  ipcMain.handle('install:run', safe(async (_e, targetId, options) => {
    const target = installTargets.find((t) => t.id === targetId);
    if (!target) throw new Error('This installation is no longer available. Please scan again.');
    const opts = { fabricApi: !!(options && options.fabricApi), profilesFrom: options && options.profileId ? profileDirById(options.profileId) : null };
    return installer.install(target, opts, (p) => send('install:progress', p));
  }));
  ipcMain.handle('install:runningGame', safe(async (_e, gameDir, options) => {
    if (!gameDir) throw new Error('The folder of this game could not be found.');
    const opts = { fabricApi: !!(options && options.fabricApi), profilesFrom: options && options.profileId ? profileDirById(options.profileId) : null };
    const game0 = (await installer.running(GAME_DIR).catch(() => [])).find((g) => g.gameDir && path.resolve(g.gameDir) === path.resolve(gameDir));
    return installer.install({ gameDir, minecraft: game0 && game0.minecraft }, opts, (p) => send('install:progress', p));
  }));
  ipcMain.handle('install:push', safe((_e, gameDir, profileId) => installer.pushProfile(gameDir, profileDirById(profileId))));
  ipcMain.handle('install:export', safe(async (_e, withFabricApi, mc) => {
    const res = await dialog.showOpenDialog(win, { title: 'Choose where to save the Onix mod', properties: ['openDirectory', 'createDirectory'] });
    if (res.canceled || !res.filePaths[0]) return null;
    const folder = res.filePaths[0];
    const files = await installer.exportMod(folder, !!withFabricApi, (p) => send('install:progress', p), versions.hasOnix(mc) ? mc : versions.DEFAULT);
    shell.showItemInFolder(path.join(folder, files[0]));
    return { folder, files };
  }));

  ipcMain.on('win:minimize', () => win && win.minimize());
  ipcMain.on('win:close', () => win && win.close());


  // Stop a running game (asks it to close, forces it after a few seconds)
  ipcMain.handle('game:stop', safe(() => {
    if (!gameProc) return false;
    const proc = gameProc;
    try { proc.kill(); } catch { /* already gone */ }
    setTimeout(() => { try { if (proc.exitCode == null) proc.kill('SIGKILL'); } catch { /* gone */ } }, 5000);
    return true;
  }));

  // Skins and capes (Mojang profile API) with a local skin library
  skins.init(path.join(app.getPath('userData'), 'skins'), async () => (await auth.refresh(store, true)).access_token);
  ipcMain.handle('skins:profile', safe(() => skins.profile()));
  ipcMain.handle('skins:apply', safe((_e, sha, variant) => skins.apply(String(sha), variant)));
  ipcMain.handle('skins:variant', safe((_e, variant) => skins.setVariant(variant)));
  ipcMain.handle('skins:reset', safe(() => skins.reset()));
  ipcMain.handle('skins:cape', safe((_e, capeId) => skins.setCape(capeId ? String(capeId) : null)));
  ipcMain.handle('skins:library', safe(() => skins.library()));
  ipcMain.handle('skins:keep', safe(() => skins.keepCurrent()));
  ipcMain.handle('skins:remove', safe((_e, sha) => skins.removeFromLibrary(String(sha))));
  ipcMain.handle('skins:rename', safe((_e, sha, name) => skins.rename(String(sha), name)));
  ipcMain.handle('skins:import', safe(async () => {
    const res = await dialog.showOpenDialog(win, { title: 'Add skins', properties: ['openFile', 'multiSelections'], filters: [{ name: 'Skins', extensions: ['png'] }] });
    return res.canceled ? { added: [], errors: [] } : skins.importFiles(res.filePaths);
  }));
  ipcMain.handle('skins:drop', safe((_e, paths) => skins.importFiles((Array.isArray(paths) ? paths : []).filter((p) => /\.png$/i.test(String(p))))));

  // Desktop shortcuts that start a profile (optionally straight into a server or world)
  ipcMain.handle('shortcuts:create', safe((_e, profileId, spec) => shortcuts.create(profileById(profileId), spec || {}, app.getPath('userData'))));
  ipcMain.handle('shortcuts:pending', () => {
    const req = pendingShortcut;
    pendingShortcut = null;
    return req && profiles.list().some((p) => p.id === req.id) ? req : null;
  });

  // Launcher updates: checked on every start (lib/updater.js)
  ipcMain.handle('update:get', () => updater.view());
  ipcMain.handle('update:check', () => updater.check());
  ipcMain.handle('update:install', safe(() => updater.install(running)));
  ipcMain.handle('update:download', safe(() => updater.openDownload()));

  createWindow();
  updater.init((v) => send('update:status', v), () => settings().autoUpdate !== false, () => running);
  win.webContents.once('did-finish-load', () => {
    if (settings().autoUpdate !== false) updater.check();
  });
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

function friendlyError(err) {
  const msg = err && err.message ? err.message : String(err);
  if (/error\.gui\.closed/i.test(msg)) return 'Sign-in cancelled.';
  if (/ENOTFOUND|fetch failed|ECONNRESET|ETIMEDOUT/i.test(msg)) return 'No internet connection.';
  return msg;
}
