/**
 * Import from other launchers and clients.
 *
 * Reliable, because every Java client uses the same Minecraft formats:
 *   video and game settings including key binds (options.txt), the server list (servers.dat),
 *   worlds, resource packs, shader packs, screenshots and compatible Fabric mods with their configs.
 *
 * Best effort: which client modules (Keystrokes, FPS, Zoom, …) were enabled in Lunar,
 * Feather/Dawn, Badlion or LabyMod. Their files are not publicly documented, so we search
 * their JSON files for known module names and only carry over on/off.
 */
const fs = require('fs');
const path = require('path');
const os = require('os');

const MC_VERSION = '1.21.1';
const SKIP_MOD_IDS = new Set(['onix', 'fabric-api', 'fabricloader', 'minecraft', 'java', 'sodium']);

// ------------------------------------------------------------------ locations

function dirs() {
  const home = os.homedir();
  const plat = process.platform;
  const appData = plat === 'win32'
    ? process.env.APPDATA || path.join(home, 'AppData', 'Roaming')
    : plat === 'darwin' ? path.join(home, 'Library', 'Application Support') : path.join(home, '.config');
  const minecraft = plat === 'win32' ? path.join(appData, '.minecraft')
    : plat === 'darwin' ? path.join(appData, 'minecraft') : path.join(home, '.minecraft');
  const local = plat === 'win32' ? process.env.LOCALAPPDATA || path.join(home, 'AppData', 'Local') : appData;
  return { home, appData, minecraft, local };
}

/** Known sources. gameDirs: game folders; instances: folders holding many instances; config: client settings. */
function knownSources() {
  const { home, appData, minecraft, local } = dirs();
  return [
    { id: 'lunar', name: 'Lunar Client', gameDirs: [minecraft], config: [path.join(home, '.lunarclient', 'settings')] },
    { id: 'feather', name: 'Feather Client', gameDirs: [path.join(appData, '.feather'), minecraft], config: [path.join(appData, '.feather')] },
    { id: 'dawn', name: 'Dawn', gameDirs: [path.join(appData, '.dawn'), path.join(appData, 'Dawn'), path.join(appData, '.dawnclient')],
      config: [path.join(appData, '.dawn'), path.join(appData, 'Dawn'), path.join(appData, '.dawnclient')] },
    { id: 'badlion', name: 'Badlion Client', gameDirs: [minecraft], config: [path.join(appData, 'Badlion Client'), path.join(minecraft, 'Badlion Client')] },
    { id: 'labymod', name: 'LabyMod', gameDirs: [minecraft, path.join(minecraft, 'labymod-neo')], config: [path.join(minecraft, 'labymod-neo', 'configs')] },
    { id: 'minecraft', name: 'Minecraft Launcher', gameDirs: [minecraft], config: [] },
    { id: 'prism', name: 'Prism Launcher', instances: [path.join(appData, 'PrismLauncher', 'instances'), path.join(local, 'PrismLauncher', 'instances')] },
    { id: 'multimc', name: 'MultiMC', instances: [path.join(home, 'MultiMC', 'instances'), path.join(appData, 'MultiMC', 'instances')] },
    { id: 'modrinth', name: 'Modrinth App', instances: [path.join(appData, 'com.modrinth.theseus', 'profiles'), path.join(appData, 'ModrinthApp', 'profiles')] },
    { id: 'curseforge', name: 'CurseForge', instances: [path.join(home, 'curseforge', 'minecraft', 'Instances'), path.join(home, 'Documents', 'Curse', 'Minecraft', 'Instances')] },
    { id: 'atlauncher', name: 'ATLauncher', instances: [path.join(appData, 'ATLauncher', 'instances')] },
  ];
}

const exists = (p) => { try { fs.accessSync(p); return true; } catch { return false; } };
const isDir = (p) => { try { return fs.statSync(p).isDirectory(); } catch { return false; } };
const list = (p) => { try { return fs.readdirSync(p, { withFileTypes: true }); } catch { return []; } };

/** A folder counts as a game folder if it contains typical Minecraft files. */
function looksLikeGameDir(dir) {
  return ['options.txt', 'saves', 'servers.dat', 'resourcepacks', 'mods'].some((n) => exists(path.join(dir, n)));
}

/** Game folder inside an instance (Prism: .minecraft or minecraft, otherwise the folder itself). */
function gameDirOfInstance(dir) {
  for (const sub of ['.minecraft', 'minecraft', '']) {
    const p = sub ? path.join(dir, sub) : dir;
    if (isDir(p) && looksLikeGameDir(p)) return p;
  }
  return null;
}

// ------------------------------------------------------------------ analysis

function countEntries(dir, filter) {
  return list(dir).filter(filter).length;
}

/** Tiny version matcher for fabric.mod.json "depends.minecraft". */
function matchesVersion(range, version) {
  if (range == null) return true;
  if (Array.isArray(range)) return range.some((r) => matchesVersion(r, version));
  const v = version.split('.').map(Number);
  const cmp = (a, b) => {
    for (let i = 0; i < 3; i++) {
      const d = (a[i] || 0) - (b[i] || 0);
      if (d) return d;
    }
    return 0;
  };
  const parse = (s) => s.replace(/-.*$/, '').split('.').map((x) => (x === 'x' || x === '*' ? null : Number(x)));
  return String(range).split('||').some((alt) => alt.trim().split(/\s+/).filter(Boolean).every((token) => {
    if (token === '*' ) return true;
    const m = token.match(/^(>=|<=|>|<|=|~|\^)?(.+)$/);
    const op = m[1] || '=';
    const target = parse(m[2]);
    const t = target.map((x) => (x == null ? 0 : x));
    if (target.includes(null) || op === '~' || op === '^') {
      // Wildcards and ~/^: the major version (and for ~ the minor version) must match
      const fixed = op === '^' ? 1 : target.includes(null) ? target.indexOf(null) : 2;
      for (let i = 0; i < fixed; i++) if (target[i] != null && v[i] !== target[i]) return false;
      return cmp(v, t) >= 0;
    }
    const c = cmp(v, t);
    return { '>=': c >= 0, '<=': c <= 0, '>': c > 0, '<': c < 0, '=': c === 0 }[op];
  }));
}

/** Reads fabric.mod.json from a jar. */
function readFabricModJson(jarPath) {
  return new Promise((resolve) => {
    let yauzl;
    try {
      yauzl = require('yauzl');
    } catch {
      return resolve(null);
    }
    yauzl.open(jarPath, { lazyEntries: true }, (err, zip) => {
      if (err || !zip) return resolve(null);
      let found = false;
      zip.on('entry', (entry) => {
        if (entry.fileName === 'fabric.mod.json') {
          found = true;
          zip.openReadStream(entry, (e, stream) => {
            if (e) { zip.close(); return resolve(null); }
            const chunks = [];
            stream.on('data', (c) => chunks.push(c));
            stream.on('end', () => {
              zip.close();
              try {
                // some mods put line breaks inside strings: parse leniently
                resolve(JSON.parse(Buffer.concat(chunks).toString('utf8').replace(/\n/g, ' ')));
              } catch {
                resolve(null);
              }
            });
          });
        } else {
          zip.readEntry();
        }
      });
      zip.on('end', () => { if (!found) resolve(null); });
      zip.readEntry();
    });
  });
}

// ------------------------------------------------------------------ .dawnpack files

/** Dawn exports mod sets as .dawnpack files (zip archives with jars inside). */
function findDawnpacks(dirs) {
  const found = new Set();
  const walk = (dir, depth) => {
    if (depth > 2) return;
    for (const e of list(dir)) {
      const p = path.join(dir, e.name);
      if (e.isFile() && e.name.toLowerCase().endsWith('.dawnpack')) found.add(p);
      else if (e.isDirectory() && depth < 2 && !['saves', 'mods', 'screenshots', 'resourcepacks', 'shaderpacks', 'logs', 'crash-reports', 'versions', 'libraries', 'assets'].includes(e.name)) walk(p, depth + 1);
    }
  };
  for (const d of dirs) if (d && isDir(d)) walk(d, 0);
  return [...found];
}

/** Extracts every .jar from a .dawnpack into a temporary folder; returns their paths. */
function extractPackJars(packPath) {
  return new Promise((resolve) => {
    let yauzl;
    try {
      yauzl = require('yauzl');
    } catch {
      return resolve([]);
    }
    const out = path.join(os.tmpdir(), 'onix-dawnpack', require('crypto').createHash('sha1').update(packPath).digest('hex').slice(0, 12));
    fs.mkdirSync(out, { recursive: true });
    const jars = [];
    yauzl.open(packPath, { lazyEntries: true }, (err, zip) => {
      if (err || !zip) return resolve([]);
      zip.on('entry', (entry) => {
        const name = path.basename(entry.fileName);
        if (!/\.jar$/i.test(name) || /\/$/.test(entry.fileName)) return zip.readEntry();
        const target = path.join(out, name);
        zip.openReadStream(entry, (e, stream) => {
          if (e) return zip.readEntry();
          const ws = fs.createWriteStream(target);
          stream.pipe(ws);
          ws.on('finish', () => { jars.push(target); zip.readEntry(); });
          ws.on('error', () => zip.readEntry());
        });
      });
      zip.on('end', () => resolve(jars));
      zip.on('error', () => resolve(jars));
      zip.readEntry();
    });
  });
}

async function analyzeDawnpacks(dirs) {
  const compatible = [];
  const incompatible = [];
  const packs = findDawnpacks(dirs);
  for (const pack of packs) {
    const packName = path.basename(pack);
    for (const jar of await extractPackJars(pack)) {
      const meta = await readFabricModJson(jar);
      const file = path.basename(jar);
      if (!meta) { incompatible.push({ file, reason: `Not a Fabric mod (from ${packName})` }); continue; }
      if (SKIP_MOD_IDS.has(meta.id) || meta.environment === 'server') continue;
      const mc = meta.depends && meta.depends.minecraft;
      if (!matchesVersion(mc, MC_VERSION)) {
        incompatible.push({ file, name: meta.name || meta.id, reason: `For Minecraft ${[].concat(mc).join(', ')} (from ${packName})` });
        continue;
      }
      compatible.push({ file, id: meta.id, name: meta.name || meta.id, version: meta.version, srcPath: jar, fromPack: packName });
    }
  }
  return { packs: packs.length, compatible, incompatible };
}

async function analyzeMods(gameDir) {
  const modsDir = path.join(gameDir, 'mods');
  const jars = list(modsDir).filter((e) => e.isFile() && e.name.endsWith('.jar'));
  const compatible = [];
  const incompatible = [];
  for (const jar of jars) {
    const file = path.join(modsDir, jar.name);
    const meta = await readFabricModJson(file);
    if (!meta) {
      incompatible.push({ file: jar.name, reason: 'Not a Fabric mod (e.g. Forge)' });
      continue;
    }
    if (SKIP_MOD_IDS.has(meta.id) || /^onix-client-/.test(jar.name)) continue;
    if (meta.environment === 'server') continue;
    const mc = meta.depends && meta.depends.minecraft;
    if (!matchesVersion(mc, MC_VERSION)) {
      incompatible.push({ file: jar.name, name: meta.name || meta.id, reason: `For Minecraft ${[].concat(mc).join(', ')}` });
      continue;
    }
    compatible.push({ file: jar.name, id: meta.id, name: meta.name || meta.id, version: meta.version });
  }
  return { compatible, incompatible };
}

// ------------------------------------------------------------------ client modules (heuristic)

const MODULE_ALIASES = {
  // HUD
  fps: ['fps', 'fpsdisplay', 'fpscounter', 'fpsmod'],
  framegraph: ['framegraph', 'frametime', 'frametimegraph', 'fpsgraph'],
  cps: ['cps', 'cpsdisplay', 'cpscounter', 'cpsmod'],
  ping: ['ping', 'pingdisplay', 'pingmod'],
  tps: ['tps', 'tpsdisplay', 'servertps'],
  coords: ['coords', 'coordinates', 'coordinatesmod', 'coordsdisplay'],
  direction: ['direction', 'directionhud', 'facing'],
  biome: ['biome', 'biomedisplay'],
  chunkinfo: ['chunkinfo', 'chunkcoords', 'chunkdisplay'],
  lightlevel: ['lightlevel', 'lightdisplay'],
  speed: ['speedometer', 'speedindicator', 'speed', 'bps', 'speeddisplay'],
  falldistance: ['falldistance', 'falldisplay'],
  clock: ['clock', 'timedisplay', 'realtime', 'realtimeclock'],
  date: ['date', 'datedisplay', 'calendar'],
  gametime: ['gametime', 'ingametime', 'worldtime'],
  day: ['daycounter', 'daydisplay'],
  moonphase: ['moonphase'],
  weatherdisplay: ['weatherdisplay', 'weatherhud', 'weatherindicator'],
  memory: ['memory', 'memoryusage', 'memorydisplay'],
  server: ['serveraddress', 'serverip', 'serverdisplay', 'ipdisplay'],
  playercount: ['playercount', 'playercounter', 'onlineplayers'],
  sessiontime: ['sessiontime', 'playtime', 'sessiontimer'],
  stopwatch: ['stopwatch'],
  countdown: ['countdown', 'countdowntimer'],
  keystrokes: ['keystrokes', 'keystrokesmod', 'keydisplay'],
  armor: ['armorstatus', 'armorhud', 'armourstatus', 'armorstatusmod', 'armor', 'armour'],
  effects: ['potioneffects', 'potionstatus', 'potionhud', 'statuseffects', 'potioneffectsmod', 'effects'],
  helditem: ['helditem', 'helditeminfo', 'iteminfo'],
  compass: ['compass', 'compasshud'],
  inventory: ['inventoryviewer', 'inventoryhud', 'invhud', 'inventorydisplay'],
  hunger: ['hunger', 'hungerdisplay', 'saturation', 'saturationdisplay'],
  health: ['health', 'healthdisplay', 'hpdisplay'],
  experience: ['experience', 'xpdisplay'],
  air: ['air', 'airdisplay', 'oxygen'],
  packdisplay: ['packdisplay', 'resourcepackdisplay', 'packhud'],
  watermark: ['watermark', 'clientlogo'],
  modulelist: ['modulelist', 'arraylist', 'enabledmods', 'activemods'],
  deathcoords: ['deathcoords', 'deathcoordinates', 'lastdeath', 'deathposition'],
  waypoints: ['waypoints', 'waypoint'],
  elytra: ['elytra', 'elytrainfo', 'elytrahud'],
  mount: ['mountinfo', 'horsestats', 'mounthud'],
  durabilitywarning: ['durabilitywarning', 'lowdurability', 'durabilitynotifier'],
  pickups: ['pickups', 'pickupnotifier', 'itempickup', 'itempickups'],
  blockinfo: ['blockinfo', 'lookingat', 'waila', 'jade'],
  customtext: ['customtext', 'textdisplay'],
  // Combat
  togglesprint: ['togglesprint', 'togglesprintsneak'],
  togglesneak: ['togglesneak'],
  hiteffects: ['hiteffects', 'hitcolor', 'damagetint', 'hitcolour'],
  killeffects: ['killeffects', 'killeffect'],
  hitsound: ['hitsound', 'hitsounds'],
  crosshair: ['crosshair', 'customcrosshair'],
  nohurtcam: ['nohurtcam', 'hurtcam', 'minimalhurtcam'],
  lowhealth: ['lowhealth', 'lowhealthwarning', 'lowhpwarning'],
  damageflash: ['damageflash', 'damageindicator', 'damageoverlay'],
  totemwarning: ['totemwarning', 'totempop'],
  reach: ['reach', 'reachdisplay'],
  combo: ['combo', 'combocounter', 'combodisplay'],
  targethud: ['targethud', 'targetinfo', 'targetdisplay'],
  sessionstats: ['sessionstats', 'killcounter', 'kdr'],
  cooldown: ['cooldown', 'attackcooldown', 'cooldownindicator'],
  itemcooldowns: ['itemcooldowns', 'itemcooldown', 'pearlcooldown'],
  arrows: ['arrowcounter', 'arrowcount', 'arrows'],
  totems: ['totemcounter', 'totemcount', 'totems'],
  pearls: ['pearlcounter', 'pearls', 'enderpearls'],
  gapples: ['gapplecounter', 'gapples', 'goldenapples'],
  pots: ['potcounter', 'potioncounter', 'pots'],
  blocks: ['blockcounter', 'blockcount', 'blocks'],
  resources: ['resourcecounter', 'resources'],
  customcounter: ['itemcounter', 'customcounter'],
  // Visual
  atmosphere: ['atmosphere', 'skycolor', 'worldtint'],
  customsky: ['customsky', 'skybox', 'customskybox', 'skyboxes'],
  fullbright: ['fullbright', 'gamma'],
  weather: ['weatherchanger', 'weather', 'clearweather'],
  timechanger: ['timechanger', 'customtime'],
  trail: ['trail', 'trails', 'particletrail'],
  nametags: ['nametag', 'nametags', 'ownnametag', 'nametagmod', 'betternametags'],
  lowfire: ['lowfire', 'lowerfire', 'fireoverlay'],
  blockoutline: ['blockoutline', 'blockoverlay', 'blockselection'],
  chunkborders: ['chunkborders', 'chunkborder', 'chunkgrid'],
  hitboxes: ['hitboxes', 'hitbox', 'customhitboxes'],
  viewmodel: ['viewmodel', 'handviewmodel', 'itemphysics', 'oldanimations'],
  overlays: ['overlays', 'pumpkinoverlay', 'nopumpkin', 'clearoverlays'],
  scoreboard: ['scoreboard', 'customscoreboard', 'scoreboardmod'],
  bossbar: ['bossbar', 'bossbarmod', 'customboss'],
  tablist: ['tablist', 'playerlist', 'numericping'],
  // Camera
  zoom: ['zoom', 'zoommod', 'optifinezoom'],
  freelook: ['freelook', 'perspectivemod', 'snaplook', 'freelookmod'],
  perspective: ['perspective', 'perspectivekey', 'thirdperson'],
  cameratweaks: ['cameratweaks', 'cameradistance', 'cameraclip'],
  fov: ['fov', 'fovchanger', 'fovmod', 'dynamicfov', 'fovmodifier'],
  sensitivity: ['sensitivity', 'sensitivityswitch'],
  // Chat
  chatcleaner: ['chatcleaner', 'chattweaks', 'compactchat', 'stackchat'],
  timestamps: ['chattimestamps', 'timestamps', 'timestamp'],
  mentions: ['mentions', 'chatmentions', 'namehighlight'],
  chathistory: ['chathistory', 'longerchat', 'infinitechat'],
  chatfilter: ['chatfilter', 'chatblacklist'],
  macros: ['macros', 'chatmacros', 'quickchat'],
  autogg: ['autogg', 'autoglhf'],
  // Utility
  autotool: ['autotool', 'toolswitch'],
  panic: ['panic', 'panickey'],
  autorespawn: ['autorespawn'],
  autoreconnect: ['autoreconnect'],
  copycoords: ['copycoords', 'copycoordinates', 'sharecoords'],
  tooltips: ['tooltips', 'itemtooltips', 'bettertooltips', 'advancedtooltips'],
  // Performance
  unfocusedfps: ['unfocusedfps', 'backgroundfps', 'dynamicfps', 'fpslimiter'],
  backgroundmute: ['backgroundmute', 'mutewhenunfocused'],
  performance: ['performancepreset', 'performancemode'],
  entitydistance: ['entitydistance', 'entityculling', 'entityrenderdistance'],
  renderdistance: ['renderdistance', 'renderdistancetoggle', 'renderdistanceswitch'],
};

const ALIAS_TO_MODULE = new Map();
for (const [id, aliases] of Object.entries(MODULE_ALIASES)) for (const a of aliases) ALIAS_TO_MODULE.set(a, id);

const norm = (s) => String(s).toLowerCase().replace(/[^a-z0-9]/g, '').replace(/(mod|module|hud)$/, '') || String(s).toLowerCase();

function enabledFlag(value) {
  if (typeof value === 'boolean') return value;
  if (value && typeof value === 'object') {
    for (const k of ['enabled', 'isEnabled', 'toggled', 'active', 'state', 'visible']) {
      if (typeof value[k] === 'boolean') return value[k];
    }
  }
  return null;
}

function lookup(key) {
  const n = String(key).toLowerCase().replace(/[^a-z0-9]/g, '');
  return ALIAS_TO_MODULE.get(n) || ALIAS_TO_MODULE.get(norm(key)) || null;
}

/** Searches JSON recursively for known module names with an on/off value. */
function collectModules(json, out, depth = 0) {
  if (!json || typeof json !== 'object' || depth > 8) return;
  if (Array.isArray(json)) {
    for (const item of json) collectModules(item, out, depth + 1);
    return;
  }
  const named = json.id || json.name || json.module || json.key;
  if (typeof named === 'string') {
    const id = lookup(named);
    const flag = enabledFlag(json);
    if (id && flag !== null && !(id in out)) out[id] = flag;
  }
  for (const [k, v] of Object.entries(json)) {
    const id = lookup(k);
    if (id && !(id in out)) {
      const flag = enabledFlag(v);
      if (flag !== null) out[id] = flag;
    }
    if (v && typeof v === 'object') collectModules(v, out, depth + 1);
  }
}

function scanClientModules(configDirs) {
  const out = {};
  let files = 0;
  const walk = (dir, depth) => {
    if (depth > 4 || files > 400) return;
    for (const e of list(dir)) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) walk(p, depth + 1);
      else if (e.name.endsWith('.json')) {
        try {
          if (fs.statSync(p).size > 2 * 1024 * 1024) continue;
          files++;
          collectModules(JSON.parse(fs.readFileSync(p, 'utf8')), out);
        } catch {
          // skip invalid JSON
        }
      }
    }
  };
  for (const d of configDirs) if (isDir(d)) walk(d, 0);
  return out;
}

// ------------------------------------------------------------------ finding sources

async function analyzeGameDir(gameDir, configDirs = []) {
  const mods = await analyzeMods(gameDir);
  const packs = await analyzeDawnpacks([gameDir, ...configDirs]);
  const ids = new Set(mods.compatible.map((m) => m.id));
  for (const m of packs.compatible) if (!ids.has(m.id)) { mods.compatible.push(m); ids.add(m.id); }
  mods.incompatible.push(...packs.incompatible);
  const modules = scanClientModules(configDirs);
  return {
    gameDir,
    options: exists(path.join(gameDir, 'options.txt')),
    servers: exists(path.join(gameDir, 'servers.dat')),
    worlds: countEntries(path.join(gameDir, 'saves'), (e) => e.isDirectory()),
    resourcepacks: countEntries(path.join(gameDir, 'resourcepacks'), (e) => !e.name.startsWith('.')),
    shaderpacks: countEntries(path.join(gameDir, 'shaderpacks'), (e) => !e.name.startsWith('.') && e.name !== 'shaders.properties'),
    screenshots: countEntries(path.join(gameDir, 'screenshots'), (e) => e.isFile() && /\.png$/i.test(e.name)),
    modsCompatible: mods.compatible,
    modsIncompatible: mods.incompatible,
    dawnpacks: packs.packs,
    modules,
    configDirs: configDirs.filter(isDir),
  };
}

function hasContent(a) {
  return a.options || a.servers || a.worlds || a.resourcepacks || a.shaderpacks || a.screenshots
    || a.modsCompatible.length || Object.keys(a.modules).length;
}

/** Finds every known source on this computer. */
async function detect() {
  const results = [];
  for (const src of knownSources()) {
    if (src.instances) {
      for (const root of src.instances) {
        for (const e of list(root)) {
          if (!e.isDirectory()) continue;
          const gameDir = gameDirOfInstance(path.join(root, e.name));
          if (!gameDir) continue;
          const a = await analyzeGameDir(gameDir, [path.join(gameDir, 'config')]);
          if (hasContent(a)) results.push({ id: `${src.id}:${e.name}`, client: src.id, name: src.name, label: e.name, ...a });
        }
      }
      continue;
    }
    const configDirs = (src.config || []).filter(isDir);
    const gameDir = (src.gameDirs || []).find((d) => isDir(d) && looksLikeGameDir(d));
    // Clients like Lunar/Badlion use .minecraft: only list them when the client itself is installed
    if (src.id !== 'minecraft' && configDirs.length === 0) continue;
    if (!gameDir && configDirs.length === 0) continue;
    const a = gameDir ? await analyzeGameDir(gameDir, configDirs) : {
      gameDir: null, options: false, servers: false, worlds: 0, resourcepacks: 0, shaderpacks: 0, screenshots: 0,
      modsCompatible: [], modsIncompatible: [], modules: scanClientModules(configDirs), configDirs,
    };
    if (hasContent(a)) results.push({ id: src.id, client: src.id, name: src.name, label: gameDir ? shortPath(gameDir) : shortPath(configDirs[0]), ...a });
  }
  return results;
}

/** A folder picked by the user (also searched for instances). */
async function analyzeFolder(folder) {
  const out = [];
  const direct = gameDirOfInstance(folder);
  if (direct) {
    const a = await analyzeGameDir(direct, [path.join(direct, 'config'), folder]);
    out.push({ id: `custom:${folder}`, client: 'custom', name: path.basename(folder), label: shortPath(folder), ...a });
  } else {
    for (const e of list(folder)) {
      if (!e.isDirectory()) continue;
      const g = gameDirOfInstance(path.join(folder, e.name));
      if (!g) continue;
      const a = await analyzeGameDir(g, [path.join(g, 'config')]);
      if (hasContent(a)) out.push({ id: `custom:${g}`, client: 'custom', name: e.name, label: shortPath(g), ...a });
    }
  }
  if (out.length === 0) {
    // maybe a folder that only holds client settings
    const modules = scanClientModules([folder]);
    if (Object.keys(modules).length) {
      out.push({ id: `custom:${folder}`, client: 'custom', name: path.basename(folder), label: shortPath(folder), gameDir: null,
        options: false, servers: false, worlds: 0, resourcepacks: 0, shaderpacks: 0, screenshots: 0,
        modsCompatible: [], modsIncompatible: [], modules, configDirs: [folder] });
    }
  }
  return out;
}

function shortPath(p) {
  const home = os.homedir();
  return p && p.startsWith(home) ? `~${p.slice(home.length)}` : p;
}

// ------------------------------------------------------------------ Import

function backup(file) {
  if (exists(file)) fs.copyFileSync(file, `${file}.before-import`);
}

function copyDirContents(src, dst, { rename = false } = {}) {
  let copied = 0;
  if (!isDir(src)) return 0;
  fs.mkdirSync(dst, { recursive: true });
  for (const e of list(src)) {
    let target = path.join(dst, e.name);
    if (exists(target)) {
      if (!rename) continue;
      let i = 2;
      while (exists(`${target} (Import ${i})`)) i++;
      target = `${target} (Import ${i})`;
    }
    fs.cpSync(path.join(src, e.name), target, { recursive: true });
    copied++;
  }
  return copied;
}

/**
 * Runs the import into profileDir.
 * what: { options, servers, worlds, resourcepacks, shaderpacks, screenshots, mods, modConfigs, modules }
 */
async function runImport(source, profileDir, what, report) {
  const g = source.gameDir;
  const done = [];
  const steps = Object.entries(what).filter(([, on]) => on).map(([k]) => k);
  let step = 0;
  const progress = (label) => report({ label, progress: step++ / Math.max(1, steps.length) });

  fs.mkdirSync(profileDir, { recursive: true });

  if (what.options && g && exists(path.join(g, 'options.txt'))) {
    progress('Settings and key binds');
    const target = path.join(profileDir, 'options.txt');
    backup(target);
    fs.copyFileSync(path.join(g, 'options.txt'), target);
    for (const extra of ['optionsof.txt', 'optionsshaders.txt']) {
      if (exists(path.join(g, extra))) fs.copyFileSync(path.join(g, extra), path.join(profileDir, extra));
    }
    done.push('Settings & key binds');
  }
  if (what.servers && g && exists(path.join(g, 'servers.dat'))) {
    progress('Server list');
    const target = path.join(profileDir, 'servers.dat');
    backup(target);
    fs.copyFileSync(path.join(g, 'servers.dat'), target);
    done.push('Server list');
  }
  if (what.worlds && g) {
    progress('Worlds');
    const n = copyDirContents(path.join(g, 'saves'), path.join(profileDir, 'saves'), { rename: true });
    done.push(`${n} world${n === 1 ? '' : 's'}`);
  }
  if (what.resourcepacks && g) {
    progress('Resource packs');
    const n = copyDirContents(path.join(g, 'resourcepacks'), path.join(profileDir, 'resourcepacks'));
    done.push(`${n} resource pack${n === 1 ? '' : 's'}`);
  }
  if (what.shaderpacks && g) {
    progress('Shader packs');
    const n = copyDirContents(path.join(g, 'shaderpacks'), path.join(profileDir, 'shaderpacks'));
    done.push(`${n} shader pack${n === 1 ? '' : 's'}`);
  }
  if (what.screenshots && g) {
    progress('Screenshots');
    const n = copyDirContents(path.join(g, 'screenshots'), path.join(profileDir, 'screenshots'));
    done.push(`${n} screenshot${n === 1 ? '' : 's'}`);
  }
  if (what.mods && source.modsCompatible.length) {
    progress('Mods');
    const modsDir = path.join(profileDir, 'mods');
    fs.mkdirSync(modsDir, { recursive: true });
    let n = 0;
    for (const m of source.modsCompatible) {
      const target = path.join(modsDir, m.file);
      if (!exists(target)) {
        fs.copyFileSync(m.srcPath || path.join(g, 'mods', m.file), target);
        n++;
      }
    }
    done.push(`${n} mod${n === 1 ? '' : 's'}`);
  }
  if (what.modConfigs && g && isDir(path.join(g, 'config'))) {
    progress('Mod settings');
    const src = path.join(g, 'config');
    const dst = path.join(profileDir, 'config');
    let n = 0;
    for (const e of list(src)) {
      if (e.name === 'onix') continue;
      const target = path.join(dst, e.name);
      if (exists(target)) continue;
      fs.mkdirSync(dst, { recursive: true });
      fs.cpSync(path.join(src, e.name), target, { recursive: true });
      n++;
    }
    done.push(`${n} mod config${n === 1 ? '' : 's'}`);
  }
  if (what.modules && Object.keys(source.modules).length) {
    progress('Client modules');
    const n = writeModules(profileDir, source.modules);
    done.push(`${n} client module${n === 1 ? '' : 's'}`);
  }
  report({ label: 'Done', progress: 1 });
  return done;
}

/** Writes module on/off states into the active Onix profile of the game folder. */
function writeModules(profileDir, modules) {
  const base = path.join(profileDir, 'config', 'onix');
  let client = {};
  try {
    client = JSON.parse(fs.readFileSync(path.join(base, 'client.json'), 'utf8'));
  } catch {
    client = {};
  }
  const profileName = client.activeProfile || 'Default';
  const file = path.join(base, 'profiles', `${profileName}.json`);
  let profile = {};
  try {
    profile = JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    profile = {};
  }
  profile.version = profile.version || 2;
  profile.modules = profile.modules || {};
  for (const [id, enabled] of Object.entries(modules)) {
    profile.modules[id] = { ...(profile.modules[id] || {}), enabled };
  }
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(profile, null, 2));
  return Object.keys(modules).length;
}

module.exports = { detect, analyzeFolder, runImport, matchesVersion, readFabricModJson, MODULE_ALIASES, knownSources, dirs, gameDirOfInstance };
