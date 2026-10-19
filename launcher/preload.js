const { contextBridge, ipcRenderer, webUtils } = require('electron');

const invoke = (channel, ...args) => ipcRenderer.invoke(channel, ...args);

contextBridge.exposeInMainWorld('onix', {
  info: () => invoke('app:info'),
  versions: () => invoke('versions:list'),

  account: () => invoke('auth:get'),
  login: () => invoke('auth:login'),
  logout: () => invoke('auth:logout'),
  accounts: () => invoke('auth:list'),
  switchAccount: (uuid) => invoke('auth:switch', uuid),
  removeAccount: (uuid) => invoke('auth:remove', uuid),

  getSettings: () => invoke('settings:get'),
  setSettings: (value) => invoke('settings:set', value),

  getSky: () => invoke('sky:get'),
  setSky: (value) => invoke('sky:set', value),

  listThemes: () => invoke('themes:list'),
  saveTheme: (theme) => invoke('themes:save', theme),
  deleteTheme: (id) => invoke('themes:delete', id),

  getProfiles: () => invoke('profiles:get'),
  selectProfile: (id) => invoke('profiles:select', id),
  saveProfile: (profile) => invoke('profiles:save', profile),
  createProfile: (name, copyFrom, mcVersion) => invoke('profiles:create', name, copyFrom, mcVersion),
  deleteProfile: (id) => invoke('profiles:delete', id),

  getCosmetics: () => invoke('cosmetics:get'),
  setCosmetic: (slot, id) => invoke('cosmetics:set', slot, id),
  setCosmetics: (outfit) => invoke('cosmetics:setAll', outfit),
  wallet: () => invoke('wallet:get'),
  buyCosmetic: (item) => invoke('wallet:buy', item),
  giftCosmetic: (item, to, message) => invoke('wallet:gift', item, to, message),
  giftsSeen: (ids) => invoke('wallet:seen', ids),

  listMods: (profileId) => invoke('mods:list', profileId),
  toggleMod: (file, profileId) => invoke('mods:toggle', file, profileId),
  addMods: (paths, profileId) => invoke('mods:add', paths, profileId),
  removeMod: (profileId, file) => invoke('mods:remove', profileId, file),
  searchModrinth: (opts) => invoke('modrinth:search', opts),
  installedModrinth: (profileId) => invoke('modrinth:installed', profileId),
  installModrinth: (profileId, projectId) => invoke('modrinth:install', profileId, projectId),
  modUpdates: (profileId) => invoke('modrinth:updates', profileId),
  updateMods: (profileId, projectIds) => invoke('modrinth:update', profileId, projectIds),
  installModpack: (projectId) => invoke('modrinth:installPack', projectId),
  exportParts: () => invoke('profile:parts'),
  exportProfile: (profileId, opts) => invoke('profile:export', profileId, opts),
  importProfile: () => invoke('profile:import'),
  copyTargets: () => invoke('profile:targets'),
  copyProfileTo: (profileId, targetId, opts) => invoke('profile:copyTo', profileId, targetId, opts),
  onModsProgress: (cb) => ipcRenderer.on('mods:progress', (_e, p) => cb(p)),
  /** Real path of a dropped file (Electron 32+ no longer exposes File.path). */
  pathForFile: (file) => webUtils.getPathForFile(file),
  openFolder: (which) => invoke('folder:open', which),

  detectImports: () => invoke('import:detect'),
  pickImportFolder: () => invoke('import:pick'),
  runImport: (sourceId, target, what) => invoke('import:run', sourceId, target, what),
  onImportProgress: (cb) => ipcRenderer.on('import:progress', (_e, v) => cb(v)),

  installInfo: (mc) => invoke('install:bundled', mc),
  runningGames: () => invoke('install:running'),
  installTargets: () => invoke('install:targets'),
  installInto: (targetId, options) => invoke('install:run', targetId, options),
  installIntoRunning: (gameDir, options) => invoke('install:runningGame', gameDir, options),
  pushProfile: (gameDir, profileId) => invoke('install:push', gameDir, profileId),
  exportMod: (withFabricApi, mc) => invoke('install:export', withFabricApi, mc),
  onInstallProgress: (cb) => ipcRenderer.on('install:progress', (_e, v) => cb(v)),

  launch: () => invoke('game:launch'),
  prepareProfile: (id) => invoke('profile:prepare', id),
  onPrepare: (cb) => ipcRenderer.on('prepare:progress', (_e, v) => cb(v)),
  minimize: () => ipcRenderer.send('win:minimize'),
  close: () => ipcRenderer.send('win:close'),

  onProgress: (cb) => ipcRenderer.on('game:progress', (_e, v) => cb(v)),
  onLog: (cb) => ipcRenderer.on('game:log', (_e, v) => cb(v)),
  onState: (cb) => ipcRenderer.on('game:state', (_e, v) => cb(v)),

  updateStatus: () => invoke('update:get'),
  checkUpdate: () => invoke('update:check'),
  installUpdate: () => invoke('update:install'),
  openUpdateDownload: () => invoke('update:download'),
  onUpdate: (cb) => ipcRenderer.on('update:status', (_e, v) => cb(v)),
});
