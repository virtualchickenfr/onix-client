'use strict';

/**
 * Minecraft versions Onix can play. Each one has its own Onix mod build (bundled under resources/mods/<version>)
 * and Fabric Loader. A profile picks one (profile.mcVersion); older profiles are 1.21.1.
 */
const VERSIONS = {
  '1.21.1': { mc: '1.21.1', loader: '0.16.5', note: 'Onix Classic' },
  '1.21.11': { mc: '1.21.11', loader: '0.19.5', note: 'Newest' },
};

const DEFAULT = '1.21.1';
const LIST = Object.keys(VERSIONS);

const isSupported = (v) => Object.prototype.hasOwnProperty.call(VERSIONS, String(v));
const get = (v) => VERSIONS[isSupported(v) ? v : DEFAULT];
const ofProfile = (profile) => get(profile && profile.mcVersion);
const versionId = (v) => {
  const x = get(v);
  return `fabric-loader-${x.loader}-${x.mc}`;
};

/** Compares two Minecraft versions like 1.21.1 and 1.21.11 (negative when a is older). */
function compare(a, b) {
  const pa = String(a).split('.').map((n) => parseInt(n, 10) || 0);
  const pb = String(b).split('.').map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < 3; i++) if ((pa[i] || 0) !== (pb[i] || 0)) return (pa[i] || 0) - (pb[i] || 0);
  return 0;
}

module.exports = { VERSIONS, DEFAULT, LIST, isSupported, get, ofProfile, versionId, compare };
