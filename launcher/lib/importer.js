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

const versions = require('./versions');
const modrinth = require('./modrinth');
const { download } = require('./download');

const MC_VERSION = versions.DEFAULT;
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
  // Linux apps keep their data in ~/.local/share (XDG), Windows in %LOCALAPPDATA%
  const local = plat === 'win32' ? process.env.LOCALAPPDATA || path.join(home, 'AppData', 'Local')
    : plat === 'linux' ? process.env.XDG_DATA_HOME || path.join(home, '.local', 'share') : appData;
  return { home, appData, minecraft, local };
}

/** Known sources. gameDirs: game folders; instances: folders holding many instances; config: client settings. */
function knownSources() {
  const { home, appData, minecraft, local } = dirs();
  return [
    { id: 'lunar', name: 'Lunar Client', gameDirs: [minecraft], config: [path.join(home, '.lunarclient', 'settings')] },
    { id: 'feather', name: 'Feather Client', gameDirs: [path.join(appData, '.feather'), minecraft], config: [path.join(appData, '.feather')] },
    // Dawn keeps one folder per profile in .dawn/profiles (see dawnProfiles); this entry only carries its client settings
    { id: 'dawn', name: 'Dawn', gameDirs: [path.join(appData, 'Dawn'), path.join(appData, '.dawnclient')],
      config: [path.join(appData, '.dawn'), path.join(appData, 'Dawn'), path.join(appData, '.dawnclient')] },
    { id: 'badlion', name: 'Badlion Client', gameDirs: [minecraft], config: [path.join(appData, 'Badlion Client'), path.join(minecraft, 'Badlion Client')] },
    { id: 'labymod', name: 'LabyMod', gameDirs: [minecraft, path.join(minecraft, 'labymod-neo')], config: [path.join(minecraft, 'labymod-neo', 'configs')] },
    { id: 'minecraft', name: 'Minecraft Launcher', gameDirs: [minecraft], config: [] },
    { id: 'prism', name: 'Prism Launcher', instances: [path.join(appData, 'PrismLauncher', 'instances'), path.join(local, 'PrismLauncher', 'instances')] },
    { id: 'multimc', name: 'MultiMC', instances: [path.join(home, 'MultiMC', 'instances'), path.join(appData, 'MultiMC', 'instances')] },
    { id: 'modrinth', name: 'Modrinth App', instances: [path.join(appData, 'com.modrinth.theseus', 'profiles'), path.join(appData, 'ModrinthApp', 'profiles'), path.join(local, 'ModrinthApp', 'profiles'), path.join(local, 'com.modrinth.theseus', 'profiles')] },
    { id: 'curseforge', name: 'CurseForge', instances: [path.join(home, 'curseforge', 'minecraft', 'Instances'), path.join(home, 'Documents', 'Curse', 'Minecraft', 'Instances')] },
    { id: 'atlauncher', name: 'ATLauncher', instances: [path.join(appData, 'ATLauncher', 'instances'), path.join(home, 'ATLauncher', 'instances')] },
    { id: 'polymc', name: 'PolyMC', instances: [path.join(appData, 'PolyMC', 'instances'), path.join(local, 'PolyMC', 'instances')] },
    { id: 'carbon', name: 'Carbon Launcher', instances: [path.join(appData, 'CarbonLauncher', 'instances')] },
    { id: 'gdlauncher', name: 'GDLauncher', instances: [path.join(appData, 'gdlauncher_next', 'instances'), path.join(appData, 'gdlauncher_carbon', 'data', 'instances')] },
    { id: 'norisk', name: 'NoRisk Client', instances: [path.join(appData, 'norisk', 'NoRiskClientV3', 'profiles'), path.join(appData, 'norisk', 'profiles'), path.join(home, '.norisk', 'profiles')] },
    { id: 'technic', name: 'Technic', instances: [path.join(appData, '.technic', 'modpacks')] },
    { id: 'tlauncher', name: 'TLauncher', gameDirs: [path.join(appData, '.tlauncher', 'legacy', 'Minecraft', 'game')], config: [path.join(appData, '.tlauncher')] },
  ];
}

/** Folders that never hold client settings or .dawnpack files, but can hold thousands of files. */
const HEAVY_DIRS = new Set(['assets', 'libraries', 'versions', 'natives', 'saves', 'mods', 'resourcepacks', 'shaderpacks',
  'screenshots', 'logs', 'crash-reports', 'cache', 'caches', 'Cache', 'GPUCache', 'Code Cache', 'blob_storage', 'node_modules',
  'runtime', 'jre', 'java', 'tmp', 'temp', 'update', 'updates', 'sentry', 'browser', 'backups', 'Distant_Horizons_server_data',
  'xaero', 'moddata', '.git', '.mixin.out', '.fabric', 'skin', 'themes', 'hosted-servers', '.minecraft', 'auto-sync', 'private-game-content',
  'pre-auto-sync-game', 'downloads', 'schematics', 'emotes', 'essential', 'debug', 'defaultconfigs']);

const exists = (p) => { try { fs.accessSync(p); return true; } catch { return false; } };
const isDir = (p) => { try { return fs.statSync(p).isDirectory(); } catch { return false; } };
const list = (p) => { try { return fs.readdirSync(p, { withFileTypes: true }); } catch { return []; } };

/** A folder counts as a game folder if it contains typical Minecraft files. */
function looksLikeGameDir(dir) {
  return ['options.txt', 'saves', 'servers.dat', 'resourcepacks', 'mods'].some((n) => exists(path.join(dir, n)));
}

/** Game folder inside an instance (Prism: .minecraft or minecraft, GDLauncher: instance, otherwise the folder itself). */
function gameDirOfInstance(dir) {
  for (const sub of ['.minecraft', 'minecraft', 'instance', 'game', '']) {
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

/**
 * fabric.mod.json of jars seen before, keyed by path, size and modification time. Scanning other launchers
 * opens hundreds of jars; with the cache a second scan only opens new or changed ones.
 */
let modCache = null;
let modCacheFile = null;
let modCacheDirty = false;

function setCacheDir(dir) {
  modCacheFile = path.join(dir, 'mod-info-cache.json');
}

function cacheGet(key) {
  if (!modCache) {
    try {
      modCache = modCacheFile ? JSON.parse(fs.readFileSync(modCacheFile, 'utf8')) : {};
    } catch {
      modCache = {};
    }
  }
  return Object.prototype.hasOwnProperty.call(modCache, key) ? modCache[key] : undefined;
}

function cachePut(key, value) {
  cacheGet(key);
  modCache[key] = value;
  modCacheDirty = true;
}

function flushCache() {
  if (!modCacheDirty || !modCacheFile || !modCache) return;
  try {
    // keep it small: only the essentials of each mod
    fs.mkdirSync(path.dirname(modCacheFile), { recursive: true });
    fs.writeFileSync(modCacheFile, JSON.stringify(modCache));
    modCacheDirty = false;
  } catch {
    // a cache is optional
  }
}

/** The parts of fabric.mod.json the launcher uses. */
const slimMeta = (m) => (m ? {
  id: m.id, name: m.name, version: m.version, environment: m.environment,
  depends: m.depends && m.depends.minecraft !== undefined ? { minecraft: m.depends.minecraft } : undefined,
} : null);

/** Reads fabric.mod.json from a jar (cached by path, size and modification time). */
async function readFabricModJson(jarPath) {
  let key = null;
  try {
    const st = fs.statSync(jarPath);
    key = `${jarPath}|${st.size}|${Math.round(st.mtimeMs)}`;
    const hit = cacheGet(key);
    if (hit !== undefined) return hit;
  } catch {
    return null;
  }
  const meta = slimMeta(await readFabricModJsonUncached(jarPath));
  cachePut(key, meta);
  return meta;
}

/** Runs fn over items with at most `limit` at a time. */
async function mapLimit(items, limit, fn) {
  const out = new Array(items.length);
  let next = 0;
  const worker = async () => {
    while (next < items.length) {
      const i = next++;
      out[i] = await fn(items[i], i);
    }
  };
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return out;
}

function readFabricModJsonUncached(jarPath) {
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
  let budget = 3000;
  const walk = (dir, depth) => {
    if (depth > 2 || budget <= 0) return;
    for (const e of list(dir)) {
      if (--budget <= 0) return;
      const p = path.join(dir, e.name);
      if (e.isFile() && e.name.toLowerCase().endsWith('.dawnpack')) found.add(p);
      else if (e.isDirectory() && depth < 2 && !HEAVY_DIRS.has(e.name)) walk(p, depth + 1);
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
  const fabric = [];
  const other = [];
  const packs = findDawnpacks(dirs);
  for (const pack of packs) {
    const packName = path.basename(pack);
    for (const jar of await extractPackJars(pack)) {
      const meta = await readFabricModJson(jar);
      const file = path.basename(jar);
      if (!meta) { other.push({ file, reason: `Not a Fabric mod (from ${packName})`, srcPath: jar }); continue; }
      if (SKIP_MOD_IDS.has(meta.id) || meta.environment === 'server') continue;
      fabric.push({ file, id: meta.id, name: meta.name || meta.id, version: meta.version, srcPath: jar, fromPack: packName, mc: meta.depends && meta.depends.minecraft });
    }
  }
  return { packs: packs.length, fabric, other };
}

/** Fabric mods of a mods folder (with the Minecraft versions they accept) and the jars that are not Fabric mods. */
async function analyzeMods(modsDir, skipFiles = new Set()) {
  const jars = list(modsDir).filter((e) => e.isFile() && e.name.endsWith('.jar') && !skipFiles.has(e.name));
  const fabric = [];
  const other = [];
  const metas = await mapLimit(jars, 8, (jar) => readFabricModJson(path.join(modsDir, jar.name)));
  for (const [i, jar] of jars.entries()) {
    const meta = metas[i];
    if (!meta) {
      other.push({ file: jar.name, reason: 'Not a Fabric mod (e.g. Forge)', srcPath: path.join(modsDir, jar.name) });
      continue;
    }
    if (SKIP_MOD_IDS.has(meta.id) || /^onix-client-/.test(jar.name) || /^(dawn|feather|carbon)/i.test(meta.id)) continue;
    if (meta.environment === 'server') continue;
    fabric.push({ file: jar.name, id: meta.id, name: meta.name || meta.id, version: meta.version, srcPath: path.join(modsDir, jar.name), mc: meta.depends && meta.depends.minecraft });
  }
  return { fabric, other };
}

/** Which mods of a source load on a Minecraft version. */
function classifyMods(source, mc) {
  const compatible = [];
  const incompatible = [...(source.modsOther || [])];
  for (const m of source.modsAll || []) {
    if (matchesVersion(m.mc, mc)) compatible.push(m);
    else incompatible.push({ file: m.file, name: m.name, reason: `For Minecraft ${[].concat(m.mc).join(', ')}${m.fromPack ? ` (from ${m.fromPack})` : ''}` });
  }
  return { compatible, incompatible };
}

/** The Onix version that fits a source best: its own version when Onix plays it, else the one most of its mods load on. */
function bestTarget(mcVersion, fabric) {
  if (versions.isSupported(mcVersion)) return mcVersion;
  let best = versions.DEFAULT;
  let bestCount = -1;
  for (const v of versions.LIST) {
    const n = fabric.filter((m) => matchesVersion(m.mc, v)).length;
    if (n > bestCount || (n === bestCount && v === versions.DEFAULT)) { best = v; bestCount = n; }
  }
  return best;
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
  let entries = 0;
  const walk = (dir, depth) => {
    if (depth > 4 || files > 400 || entries > 5000) return;
    for (const e of list(dir)) {
      if (++entries > 5000 || files > 400) return;
      const p = path.join(dir, e.name);
      if (e.isDirectory()) {
        if (!HEAVY_DIRS.has(e.name)) walk(p, depth + 1);
      } else if (e.name.endsWith('.json')) {
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

/**
 * The folder a part of the game lives in. Most clients keep everything in one game folder; Dawn splits a
 * profile into layers (its own mods/options, plus worlds and servers shared by a sync group).
 */
function layerWith(layers, name) {
  return layers.find((d) => exists(path.join(d, name))) || null;
}
const inLayer = (layers, name) => {
  const d = layerWith(layers, name);
  return d ? path.join(d, name) : null;
};

async function analyzeGameDir(gameDir, configDirs = [], opts = {}) {
  const layers = opts.layers || [gameDir];
  const modsDir = layerWith(layers, 'mods');
  const mods = modsDir ? await analyzeMods(path.join(modsDir, 'mods'), opts.skipFiles) : { fabric: [], other: [] };
  const packs = await analyzeDawnpacks([gameDir, ...configDirs]);
  const ids = new Set(mods.fabric.map((m) => m.id));
  for (const m of packs.fabric) if (!ids.has(m.id)) { mods.fabric.push(m); ids.add(m.id); }
  mods.other.push(...packs.other);
  const target = bestTarget(opts.mcVersion, mods.fabric);
  const sorted = classifyMods({ modsAll: mods.fabric, modsOther: mods.other }, target);
  const modules = scanClientModules(configDirs);
  const count = (name, filter) => {
    const d = inLayer(layers, name);
    return d ? countEntries(d, filter) : 0;
  };
  return {
    gameDir,
    layers,
    mcVersion: opts.mcVersion || null,
    options: !!inLayer(layers, 'options.txt'),
    servers: !!inLayer(layers, 'servers.dat'),
    modConfigs: !!inLayer(layers, 'config'),
    worlds: count('saves', (e) => e.isDirectory()),
    resourcepacks: count('resourcepacks', (e) => !e.name.startsWith('.')),
    shaderpacks: count('shaderpacks', (e) => !e.name.startsWith('.') && e.name !== 'shaders.properties'),
    screenshots: count('screenshots', (e) => e.isFile() && /\.png$/i.test(e.name)),
    modsAll: mods.fabric,
    modsOther: mods.other,
    targetVersion: target,
    modsCompatible: sorted.compatible,
    modsIncompatible: sorted.incompatible,
    dawnpacks: packs.packs,
    modules,
    configDirs: configDirs.filter(isDir),
  };
}

function hasContent(a) {
  return a.options || a.servers || a.worlds || a.resourcepacks || a.shaderpacks || a.screenshots
    || a.modsCompatible.length || (a.modsAll || []).length || (a.modsOther || []).length || Object.keys(a.modules).length;
}

// ------------------------------------------------------------------ Dawn profiles

const readJson = (file) => {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return null;
  }
};

/**
 * Dawn (%APPDATA%/.dawn/profiles/<id>): profile.json names the profile and its Minecraft version.
 * A profile in a sync group keeps its mods, packs and options in private-game-content and shares worlds,
 * servers and configs through auto-sync/groups/<group>; other profiles use their own .minecraft folder.
 */
async function dawnProfiles(dawnDir) {
  const root = path.join(dawnDir, 'profiles');
  const found = await Promise.all(list(root).filter((e) => e.isDirectory()).map((e) => dawnProfile(dawnDir, root, e).catch(() => null)));
  return found.filter(Boolean);
}

async function dawnProfile(dawnDir, root, e) {
  {
    const dir = path.join(root, e.name);
    const json = readJson(path.join(dir, 'profile.json'));
    const profile = json && json.profile;
    if (!profile) return null;
    const layers = [];
    const sync = profile.settings && profile.settings.autoSyncMembership;
    for (const sub of ['private-game-content', '.minecraft', 'minecraft']) if (isDir(path.join(dir, sub))) layers.push(path.join(dir, sub));
    if (sync && sync.kind === 'Member' && sync.groupId) {
      const shared = path.join(dawnDir, 'auto-sync', 'groups', sync.groupId);
      if (isDir(shared)) layers.push(shared);
    }
    if (!layers.length) return null;
    // Dawn's own client jar is not a mod to carry over
    const managed = readJson(path.join(dir, 'dawn-managed-client-mods.json'));
    const skipFiles = new Set(['dawn-client.jar', ...((managed && managed.mods) || []).map((m) => m.fileName).filter(Boolean)]);
    const mcVersion = profile.minecraftVersion || null;
    const configDirs = layers.flatMap((l) => ['config', 'dawn', 'feather'].map((n) => path.join(l, n)));
    const a = await analyzeGameDir(layers[0], configDirs, { layers, skipFiles, mcVersion });
    if (!hasContent(a)) return null;
    const name = profile.name || e.name;
    const mem = profile.settings && profile.settings.javaOverrides && profile.settings.javaOverrides.memoryAllocationMiB;
    a.exact = { dawnDir: dir, layers, skipFiles: [...skipFiles], memoryMb: mem && mem.enabled && Number(mem.value) > 0 ? Number(mem.value) : null,
      loaderVersion: profile.loader && profile.loader.version || null, loader: profile.loader && profile.loader.kind ? String(profile.loader.kind).toLowerCase() : 'fabric' };
    return { id: `dawn:${e.name}`, client: 'dawn', name: `Dawn · ${name}`, profileName: name,
      label: `Minecraft ${mcVersion || '?'}${profile.loader && profile.loader.kind ? ` · ${profile.loader.kind}` : ''}`, ...a };
  }
}

/** Finds every known source on this computer (all sources are looked at in parallel). */
async function detect() {
  const started = Date.now();
  const tasks = [dawnProfiles(path.join(dirs().appData, '.dawn'))];
  for (const src of knownSources()) {
    if (src.instances) {
      for (const root of src.instances) {
        for (const e of list(root)) {
          if (!e.isDirectory()) continue;
          tasks.push((async () => {
            const gameDir = gameDirOfInstance(path.join(root, e.name));
            if (!gameDir) return [];
            const a = await analyzeGameDir(gameDir, [path.join(gameDir, 'config')]);
            return hasContent(a) ? [{ id: `${src.id}:${e.name}`, client: src.id, name: src.name, label: e.name, ...a }] : [];
          })());
        }
      }
      continue;
    }
    tasks.push((async () => {
      const configDirs = (src.config || []).filter(isDir);
      const gameDir = (src.gameDirs || []).find((d) => isDir(d) && looksLikeGameDir(d));
      // Clients like Lunar/Badlion use .minecraft: only list them when the client itself is installed
      if (src.id !== 'minecraft' && configDirs.length === 0) return [];
      if (!gameDir && configDirs.length === 0) return [];
      const a = gameDir ? await analyzeGameDir(gameDir, configDirs) : {
        gameDir: null, options: false, servers: false, worlds: 0, resourcepacks: 0, shaderpacks: 0, screenshots: 0,
        modsCompatible: [], modsIncompatible: [], modules: scanClientModules(configDirs), configDirs,
      };
      return hasContent(a) ? [{ id: src.id, client: src.id, name: src.name, label: gameDir ? shortPath(gameDir) : shortPath(configDirs[0]), ...a }] : [];
    })());
  }
  let results = (await Promise.all(tasks.map((t) => t.catch(() => [])))).flat();
  // launchers Onix does not know by name: any folder under AppData / the home folder that looks like a game folder
  const known = new Set(results.map((r) => r.gameDir && path.resolve(r.gameDir).toLowerCase()).filter(Boolean));
  const extra = await Promise.all(discoverGameDirs(known).map(async ({ gameDir, owner, label }) => {
    try {
      const a = await analyzeGameDir(gameDir, [path.join(gameDir, 'config')]);
      return hasContent(a) ? [{ id: `found:${gameDir}`, client: 'custom', name: owner, label, ...a }] : [];
    } catch {
      return [];
    }
  }));
  results = results.concat(extra.flat());
  // the one you played last comes first
  for (const r of results) r.lastUsed = lastUsed(r.gameDir);
  results.sort((a, b) => (b.lastUsed || 0) - (a.lastUsed || 0));
  flushCache();
  detect.lastMs = Date.now() - started;
  return results;
}

/** When a game folder was last played: the newest of options.txt, logs/latest.log and the saves. */
function lastUsed(gameDir) {
  if (!gameDir) return 0;
  let t = 0;
  for (const f of ['options.txt', path.join('logs', 'latest.log'), 'saves', 'servers.dat']) {
    try {
      t = Math.max(t, fs.statSync(path.join(gameDir, f)).mtimeMs);
    } catch {
      // not there
    }
  }
  return t;
}

/** Looks a few levels deep under AppData and the home folder for game folders of launchers not listed above. */
function discoverGameDirs(known) {
  const { home, appData, local } = dirs();
  const roots = [...new Set([appData, local, home, path.join(home, 'Documents'), path.join(home, 'Games')])].filter(isDir);
  const skip = new Set(['node_modules', 'Microsoft', 'Google', 'Mozilla', 'Packages', 'Temp', 'npm', 'npm-cache', 'Code', 'Discord', 'Spotify',
    'Adobe', 'JetBrains', 'Steam', 'steamapps', 'AppData', 'OneDrive', 'Library', 'Pictures', 'Music', 'Videos', 'Downloads', 'Desktop']);
  const found = [];
  let budget = 4000;
  const walk = (dir, depth, owner) => {
    if (budget <= 0 || depth > 4) return;
    for (const e of list(dir)) {
      if (--budget <= 0) return;
      if (!e.isDirectory() || skip.has(e.name) || HEAVY_DIRS.has(e.name) && e.name !== '.minecraft') continue;
      const p = path.join(dir, e.name);
      const key = path.resolve(p).toLowerCase();
      if (known.has(key)) continue;
      if (depth > 0 && looksLikeGameDir(p) && (exists(path.join(p, 'options.txt')) || exists(path.join(p, 'mods')))) {
        known.add(key);
        found.push({ gameDir: p, owner: owner || e.name, label: shortPath(p) });
        continue;
      }
      walk(p, depth + 1, owner || e.name.replace(/^\./, ''));
    }
  };
  for (const r of roots) walk(r, 0, null);
  return found.slice(0, 40);
}

/** A folder picked by the user (also searched for instances). */
async function analyzeFolder(folder) {
  try {
    return await analyzeFolderUncached(folder);
  } finally {
    flushCache();
  }
}

async function analyzeFolderUncached(folder) {
  // a Dawn folder (.dawn or .dawn/profiles) or one Dawn profile
  for (const dawnDir of [folder, path.dirname(folder), path.dirname(path.dirname(folder))]) {
    if (!isDir(path.join(dawnDir, 'profiles')) || !exists(path.join(dawnDir, 'launcher-settings.json'))) continue;
    let found = await dawnProfiles(dawnDir);
    if (path.basename(path.dirname(folder)) === 'profiles') found = found.filter((s) => s.id === `dawn:${path.basename(folder)}`);
    if (found.length) return found;
  }
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

// ------------------------------------------------------------------ plan: which mods come along for a Minecraft version

/**
 * What happens to the mods when they move to Minecraft `mc`: ready (load as they are), update (a version for mc is
 * downloaded from Modrinth instead), missing (no version for mc anywhere). Cached per source and version.
 */
async function plan(source, mc, loader = 'fabric') {
  source.plans = source.plans || {};
  if (source.plans[mc]) return source.plans[mc];
  const { compatible, incompatible } = classifyMods(source, mc);
  const ready = compatible.map((m) => ({ name: m.name || m.file, file: m.file }));
  const byFile = new Map();
  for (const m of source.modsAll || []) if (!compatible.includes(m) && m.srcPath) byFile.set(m.srcPath, m);
  for (const m of source.modsOther || []) if (m.srcPath) byFile.set(m.srcPath, m);
  let found = new Map();
  let offline = false;
  try {
    found = await modrinth.versionsFor([...byFile.keys()], mc, loader);
  } catch {
    offline = true;
  }
  const update = [];
  const missing = [];
  const seenProjects = new Set();
  for (const [file, m] of byFile) {
    const v = found.get(file);
    if (v && !seenProjects.has(v.projectId) && !modrinth.MANAGED[v.projectId]) {
      seenProjects.add(v.projectId);
      update.push({ name: m.name || m.file, from: m.version || m.file, to: v.version, url: v.url, filename: v.filename, hashes: v.hashes, projectId: v.projectId });
    } else if (!v) {
      missing.push({ name: m.name || m.file, reason: offline ? 'Modrinth could not be reached' : `No version for Minecraft ${mc} on Modrinth` });
    }
  }
  const out = { mc, ready, update, missing, offline };
  source.plans[mc] = out;
  return out;
}

// ------------------------------------------------------------------ exact copy (Dawn): everything 1:1

/** Files and folders of a game folder that are machine-specific caches or logs – everything else is copied. */
const EXACT_SKIP = new Set(['logs', 'crash-reports', '.mixin.out', '.fabric', 'GPUCache', 'debug', 'downloads', 'dawn', 'dawn-log4j2.xml',
  'debug-profile.json', '.dawn', '.dawn-trash', 'natives', 'versions', 'libraries', 'assets']);
const skipTop = (name) => EXACT_SKIP.has(name) || /^hs_err_pid\d+\.log$/.test(name) || /^\.Backup before/.test(name);

function walkFiles(root, rel = '', out = []) {
  for (const e of list(path.join(root, rel))) {
    const r = rel ? path.join(rel, e.name) : e.name;
    if (!rel && skipTop(e.name)) continue;
    if (e.isDirectory()) walkFiles(root, r, out);
    else if (e.isFile()) out.push(r);
  }
  return out;
}

/**
 * Copies a Dawn profile 1:1 into profileDir: every mod in the exact version (also Sodium, Iris, Fabric API …, enabled and
 * disabled as they were), all resource and shader packs, options, key binds, servers, worlds, configs and mod data.
 * Shared (auto-sync) content first, then the profile's own files on top. Two enabled copies of one mod would stop Fabric,
 * so the copy the game loaded last time (otherwise the newest) stays enabled and the others are copied disabled.
 */
async function exactCopy(source, profileDir, report) {
  const x = source.exact;
  if (!x) throw new Error('This source cannot be copied 1:1.');
  const skipMods = new Set(x.skipFiles || []);
  const layers = [...x.layers].reverse(); // shared group first, the profile's own content last (it wins)
  const jobs = new Map(); // target rel → source file
  for (const layer of layers) {
    for (const rel of walkFiles(layer)) {
      const parts = rel.split(path.sep);
      if (parts[0] === 'mods' && parts.length === 2 && skipMods.has(parts[1])) continue;
      if (parts[0] === 'mods' && /^onix-client-/i.test(parts[1] || '')) continue;
      jobs.set(rel, path.join(layer, rel));
    }
  }
  let total = 0;
  for (const src of jobs.values()) {
    try {
      total += fs.statSync(src).size;
    } catch {
      // vanished meanwhile
    }
  }
  let done = 0;
  let files = 0;
  let last = 0;
  const failed = [];
  fs.mkdirSync(profileDir, { recursive: true });
  for (const [rel, src] of jobs) {
    const dst = path.join(profileDir, rel);
    try {
      await fs.promises.mkdir(path.dirname(dst), { recursive: true });
      await fs.promises.copyFile(src, dst);
      const size = (await fs.promises.stat(dst)).size;
      done += size;
      files++;
    } catch (e) {
      failed.push(rel);
    }
    if (Date.now() - last > 150) {
      last = Date.now();
      report({ label: `Copying ${files} of ${jobs.size} files (${(done / 1048576).toFixed(0)} of ${(total / 1048576).toFixed(0)} MB)`, progress: total ? (done / total) * 0.95 : 0 });
    }
  }

  // one enabled copy per mod
  report({ label: 'Checking mods', progress: 0.96 });
  const modsDir = path.join(profileDir, 'mods');
  // what the game really loaded last time (Fabric lists every mod with its version in latest.log)
  const loaded = new Map();
  for (const layer of x.layers) {
    try {
      const log = fs.readFileSync(path.join(layer, 'logs', 'latest.log'), 'utf8');
      for (const m of log.matchAll(/^\t- ([a-z0-9_.-]+) (\S+)\s*$/gm)) loaded.set(m[1], m[2]);
    } catch {
      // no log
    }
  }
  const byId = new Map();
  for (const f of list(modsDir).filter((en) => en.isFile() && en.name.endsWith('.jar')).map((en) => en.name)) {
    const meta = await readFabricModJson(path.join(modsDir, f));
    if (!meta || !meta.id) continue;
    if (!byId.has(meta.id)) byId.set(meta.id, []);
    byId.get(meta.id).push({ f, version: String(meta.version || ''), mtime: fs.statSync(path.join(modsDir, f)).mtimeMs });
  }
  const disabledDupes = [];
  for (const [id, copies] of byId) {
    if (copies.length < 2) continue;
    // the copy the game loaded last time wins, otherwise the newest version (Fabric does the same)
    const was = loaded.get(id);
    copies.sort((a, b) => Number(b.version === was) - Number(a.version === was) || compareVersions(b.version, a.version) || b.mtime - a.mtime);
    for (const c of copies.slice(1)) {
      fs.renameSync(path.join(modsDir, c.f), path.join(modsDir, `${c.f}.disabled`));
      disabledDupes.push(`${id} ${c.version}`);
    }
  }
  // mods the launcher added by itself (Dawn loads Lithium and ModernFix without a file in the mods folder):
  // the same versions come from Modrinth
  const added = [];
  const notAdded = [];
  // mods you turned off since then stay off: they are not downloaded again
  const offIds = new Set();
  for (const en of list(modsDir).filter((e2) => e2.name.endsWith('.jar.disabled'))) {
    const meta = await readFabricModJson(path.join(modsDir, en.name)).catch(() => null);
    if (meta && meta.id) offIds.add(meta.id);
  }
  const builtIn = new Set(['minecraft', 'java', 'fabricloader', 'dawn', 'mixinextras', 'onix']);
  for (const [id, version] of loaded) {
    if (builtIn.has(id) || byId.has(id) || offIds.has(id) || /^fabric-/.test(id)) continue;
    try {
      const v = await modrinth.exactVersion(id, version, source.mcVersion || versions.DEFAULT);
      const f = v && (v.files.find((ff) => ff.primary) || v.files[0]);
      if (!f) {
        notAdded.push(id);
        continue;
      }
      const target = path.join(modsDir, modrinth.safeFileName(f.filename));
      report({ label: `Getting ${id} ${v.version_number}`, progress: 0.97 });
      await download(f.url, target);
      modrinth.verify(target, f.hashes);
      added.push(`${id} ${v.version_number}`);
    } catch {
      notAdded.push(id);
    }
  }
  flushCache();
  const mods = list(modsDir).filter((en) => en.name.endsWith('.jar')).length;
  const off = list(modsDir).filter((en) => en.name.endsWith('.jar.disabled')).length;
  const out = [
    `${files} files copied (${(done / 1048576).toFixed(0)} MB) – everything 1:1`,
    `${mods} mods on, ${off} off – exactly the versions you had`,
    `${countEntries(path.join(profileDir, 'resourcepacks'), (en) => !en.name.startsWith('.'))} resource packs, ${countEntries(path.join(profileDir, 'shaderpacks'), (en) => !en.name.startsWith('.'))} shader packs`,
  ];
  if (added.length) out.push(`Added what Dawn loads by itself: ${added.join(', ')}`);
  if (notAdded.length) out.push(`Could not download (add them under Mods): ${notAdded.join(', ')}`);
  if (disabledDupes.length) out.push(`Turned off older duplicates: ${disabledDupes.join(', ')}`);
  if (failed.length) out.push(`Could not copy ${failed.length} file${failed.length === 1 ? '' : 's'}: ${failed.slice(0, 3).join(', ')}${failed.length > 3 ? ' …' : ''}`);
  report({ label: 'Done', progress: 1 });
  return out;
}

/** Compares two mod version strings by their numbers (1.10.2 > 1.9). */
function compareVersions(a, b) {
  const pa = String(a).split(/[^0-9]+/).filter(Boolean).map(Number);
  const pb = String(b).split(/[^0-9]+/).filter(Boolean).map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d) return d;
  }
  return 0;
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
async function runImport(source, profileDir, what, report, mc = source.targetVersion || versions.DEFAULT) {
  const g = source.gameDir;
  const layers = source.layers || (g ? [g] : []);
  const at = (name) => inLayer(layers, name);
  const done = [];
  const steps = Object.entries(what).filter(([, on]) => on).map(([k]) => k);
  let step = 0;
  const progress = (label) => report({ label, progress: step++ / Math.max(1, steps.length) });

  fs.mkdirSync(profileDir, { recursive: true });

  if (what.options && at('options.txt')) {
    progress('Settings and key binds');
    const target = path.join(profileDir, 'options.txt');
    backup(target);
    fs.copyFileSync(at('options.txt'), target);
    for (const extra of ['optionsof.txt', 'optionsshaders.txt']) {
      if (at(extra)) fs.copyFileSync(at(extra), path.join(profileDir, extra));
    }
    done.push('Settings & key binds');
  }
  if (what.servers && at('servers.dat')) {
    progress('Server list');
    const target = path.join(profileDir, 'servers.dat');
    backup(target);
    fs.copyFileSync(at('servers.dat'), target);
    done.push('Server list');
  }
  if (what.worlds && layers.length) {
    progress('Worlds');
    const n = copyDirContents(at('saves'), path.join(profileDir, 'saves'), { rename: true });
    done.push(`${n} world${n === 1 ? '' : 's'}`);
  }
  if (what.resourcepacks && layers.length) {
    progress('Resource packs');
    const n = copyDirContents(at('resourcepacks'), path.join(profileDir, 'resourcepacks'));
    done.push(`${n} resource pack${n === 1 ? '' : 's'}`);
  }
  if (what.shaderpacks && layers.length) {
    progress('Shader packs');
    const n = copyDirContents(at('shaderpacks'), path.join(profileDir, 'shaderpacks'));
    done.push(`${n} shader pack${n === 1 ? '' : 's'}`);
  }
  if (what.screenshots && layers.length) {
    progress('Screenshots');
    const n = copyDirContents(at('screenshots'), path.join(profileDir, 'screenshots'));
    done.push(`${n} screenshot${n === 1 ? '' : 's'}`);
  }
  const mods = source.modsAll ? classifyMods(source, mc).compatible : source.modsCompatible;
  if (what.mods && mods.length) {
    progress('Mods');
    const modsDir = path.join(profileDir, 'mods');
    fs.mkdirSync(modsDir, { recursive: true });
    let n = 0;
    for (const m of mods) {
      const target = path.join(modsDir, m.file);
      if (!exists(target)) {
        fs.copyFileSync(m.srcPath || path.join(g, 'mods', m.file), target);
        n++;
      }
    }
    done.push(`${n} mod${n === 1 ? '' : 's'}`);
  }
  if (what.mods && what.updateMods !== false) {
    const p = source.plans && source.plans[mc];
    if (p && p.update.length) {
      const modsDir = path.join(profileDir, 'mods');
      fs.mkdirSync(modsDir, { recursive: true });
      let n = 0;
      const failed = [];
      for (const [i, u] of p.update.entries()) {
        report({ label: `Getting ${u.name} for ${mc} (${i + 1}/${p.update.length})`, progress: (step - 1 + i / p.update.length) / Math.max(1, steps.length) });
        const target = path.join(modsDir, modrinth.safeFileName(u.filename));
        if (exists(target)) continue;
        try {
          await download(u.url, target);
          modrinth.verify(target, u.hashes);
          n++;
        } catch (e) {
          fs.rmSync(target, { force: true });
          failed.push(u.name);
        }
      }
      if (n) done.push(`${n} mod${n === 1 ? '' : 's'} updated to Minecraft ${mc}`);
      if (failed.length) done.push(`Could not download: ${failed.slice(0, 5).join(', ')}${failed.length > 5 ? ' …' : ''}`);
    }
  }
  if (what.modConfigs && at('config')) {
    progress('Mod settings');
    const src = at('config');
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

/** True for a Minecraft version newer than the one Onix plays (worlds from it may not open). */
function isNewerThanGame(version, game = MC_VERSION) {
  const a = String(version).split('.').map((x) => parseInt(x, 10) || 0);
  const b = String(game).split('.').map(Number);
  for (let i = 0; i < 3; i++) if ((a[i] || 0) !== (b[i] || 0)) return (a[i] || 0) > (b[i] || 0);
  return false;
}

module.exports = { plan, exactCopy, classifyMods, setCacheDir, flushCache, mapLimit, isNewerThanGame, detect, analyzeFolder, runImport, matchesVersion, readFabricModJson, MODULE_ALIASES, knownSources, dirs, gameDirOfInstance };
