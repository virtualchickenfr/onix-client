'use strict';

/* Static data shared by every page: built-in themes, backgrounds, module names (cosmetics: assets/cosmetics/catalog.js). */

const api = window.onix;
const $ = (s) => document.querySelector(s);
const $$ = (s, root = document) => root.querySelectorAll(s);

const PROFILE_COLORS = ['#ececf1', '#8b7cff', '#4c8dff', '#5eead4', '#ff8fc4', '#ffb547', '#ff4d5e'];

/** Same built-in themes as the mod (ThemeDef.java). Custom themes come from the main process. */
const THEMES = [
  { id: 'onyx', name: 'Onyx', accent: '#ececf1', panel: '#0d0d11', sky: '#a4a4b0', ambient: 'Sparks', particle: '#ffffff' },
  { id: 'aurora', name: 'Aurora', accent: '#8b7cff', panel: '#0e0c17', sky: '#7c6cf6', ambient: 'Sparks', particle: '#c4baff' },
  { id: 'cobalt', name: 'Cobalt', accent: '#4c8dff', panel: '#0a0e17', sky: '#4f7be8', ambient: 'Sparks', particle: '#9db9ff' },
  { id: 'glacier', name: 'Glacier', accent: '#5eead4', panel: '#0a1312', sky: '#9ee6e0', ambient: 'Snow', particle: '#ffffff' },
  { id: 'sakura', name: 'Sakura', accent: '#ff8fc4', panel: '#140d12', sky: '#d88bd8', ambient: 'Petals', particle: '#ffb7d5' },
  { id: 'ember', name: 'Ember', accent: '#ffb547', panel: '#130e09', sky: '#ff9a3c', ambient: 'Embers', particle: '#ffc27a' },
  { id: 'crimson', name: 'Crimson', accent: '#ff4d5e', panel: '#130a0c', sky: '#e0404f', ambient: 'Embers', particle: '#ff7a86' },
];

/** Same title screen backgrounds as the mod (TitleBackgrounds.java). */
const BACKGROUNDS = [
  ['carbon', 'Carbon'], ['onix', 'Onix Glow'], ['dusk', 'Dusk Ridge'], ['aurora', 'Aurora'], ['golden_hour', 'Golden Hour'],
  ['midnight_sea', 'Midnight Sea'], ['misty_pines', 'Misty Pines'], ['cherry_grove', 'Cherry Grove'], ['dunes', 'Dunes'],
  ['glacier', 'Glacier'], ['lavender', 'Lavender'], ['nether', 'Nether'], ['the_end', 'The End'], ['deep_space', 'Deep Space'],
];

/** Same skies as the Custom Sky module in the mod (render/Skies.java). */
const SKIES = [
  ['clear_day', 'Clear Day'], ['golden_hour', 'Golden Hour'], ['sunset', 'Sunset Bloom'], ['pastel_dawn', 'Pastel Dawn'],
  ['cotton_candy', 'Cotton Candy'], ['morning_mist', 'Morning Mist'], ['arctic', 'Arctic Day'], ['overcast', 'Overcast'],
  ['storm', 'Thunderstorm'], ['lavender_dusk', 'Lavender Dusk'], ['ember', 'Ember Sky'], ['starry_night', 'Starry Night'],
  ['midnight_clouds', 'Midnight Clouds'], ['aurora', 'Aurora'], ['emerald_night', 'Emerald Night'], ['nebula', 'Nebula'],
  ['deep_space', 'Deep Space'], ['blood_moon', 'Blood Moon'],
];
const SAME_AS_DAY = 'Same as Day';

let customThemes = [];
let currentThemeId = 'onyx';
let currentBackground = 'carbon';
const allThemes = () => [...THEMES, ...customThemes];
const themeById = (id) => allThemes().find((t) => t.id === id || t.name.toLowerCase() === String(id).toLowerCase()) || THEMES[0];

const CLIENT_COLORS = {
  lunar: '#3a5bd9', feather: '#2bb673', dawn: '#f2994a', badlion: '#e5484d', labymod: '#1fa2ff', minecraft: '#5b8c3a',
  prism: '#7a5cff', multimc: '#6b6b76', modrinth: '#1bd96a', curseforge: '#f16436', atlauncher: '#4c8dff', custom: '#3a3a44',
};

const MODULE_NAMES = {
  air: 'Air', armor: 'Armor Status', arrows: 'Arrow Counter', atmosphere: 'Atmosphere', autogg: 'Auto GG',
  autoreconnect: 'Auto Reconnect', autorespawn: 'Auto Respawn', autotool: 'Auto Tool',
  backgroundmute: 'Background Mute', badge: 'Onix Badge', biome: 'Biome', blockinfo: 'Block Info',
  blockoutline: 'Block Outline', blocks: 'Block Counter', bossbar: 'Boss Bar', cameratweaks: 'Camera Tweaks',
  chatcleaner: 'Chat Cleaner', chatfilter: 'Chat Filter', chathistory: 'Chat History & Size',
  chunkborders: 'Chunk Borders', chunkinfo: 'Chunk Info', clock: 'Clock', combo: 'Combo Counter', compass: 'Compass',
  cooldown: 'Cooldown Indicator', coords: 'Coordinates', copycoords: 'Copy Coordinates', countdown: 'Countdown',
  cps: 'CPS', crosshair: 'Crosshair', customcounter: 'Custom Item Counter', customsky: 'Custom Sky',
  customtext: 'Custom Text', damageflash: 'Damage Flash', date: 'Date', day: 'Day Counter',
  deathcoords: 'Death Coordinates', direction: 'Direction', durabilitywarning: 'Durability Warning',
  effects: 'Potion Effects', elytra: 'Elytra Info', entitydistance: 'Entity Distance', experience: 'Experience',
  falldistance: 'Fall Distance', fov: 'FOV Effects', fps: 'FPS', framegraph: 'Frame Graph', freelook: 'Freelook',
  fullbright: 'Fullbright', gametime: 'Game Time', gapples: 'Golden Apple Counter', health: 'Health',
  helditem: 'Held Item', hitboxes: 'Hitboxes', hiteffects: 'Hit Effects', hitsound: 'Hit Sound', hunger: 'Hunger',
  inventory: 'Inventory Viewer', itemcooldowns: 'Item Cooldowns', keystrokes: 'Keystrokes',
  killeffects: 'Kill Effects', lightlevel: 'Light Level', lowfire: 'Low Fire', lowhealth: 'Low Health Warning',
  macros: 'Chat Macros', memory: 'Memory', mentions: 'Chat Mentions', modulelist: 'Module List',
  moonphase: 'Moon Phase', mount: 'Mount Info', nametags: 'Nametags', nohurtcam: 'No Hurt Cam', overlays: 'Overlays',
  packdisplay: 'Pack Display', panic: 'Panic', pearls: 'Pearl Counter', performance: 'Performance Preset',
  perspective: 'Perspective Key', pickups: 'Pickup Notifier', ping: 'Ping', playercount: 'Player Count',
  pots: 'Potion Counter', reach: 'Reach Display', renderdistance: 'Render Distance Switch',
  resources: 'Resource Counter', scoreboard: 'Scoreboard', sensitivity: 'Sensitivity Switch',
  server: 'Server Address', sessionstats: 'Session Stats', sessiontime: 'Session Time', speed: 'Speed',
  stopwatch: 'Stopwatch', tablist: 'Tab List', targethud: 'Target HUD', timechanger: 'Time Changer',
  timestamps: 'Chat Timestamps', togglesneak: 'Toggle Sneak', togglesprint: 'Toggle Sprint',
  tooltips: 'Item Tooltips', totems: 'Totem Counter', totemwarning: 'Totem Warning', tps: 'TPS', trail: 'Trail',
  unfocusedfps: 'Unfocused FPS', viewmodel: 'Hand View Model', watermark: 'Watermark', waypoints: 'Waypoints',
  weather: 'Weather Changer', weatherdisplay: 'Weather', zoom: 'Zoom',
};

let info = {};
let account = null;
let profileState = { profiles: [], active: null };
let cosmetics = {};
let state = 'idle';
let currentView = 'home';
/** Set by "Play" on a world: the next start goes straight into it. */
let pendingQuickPlay = null;
const logLines = [];
