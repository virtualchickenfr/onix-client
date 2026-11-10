const fs = require('fs');
const path = require('path');

/**
 * One click shaders: a few well known Iris shader packs from Modrinth that the launcher installs together with
 * Iris and switches on right away (config/iris.properties). Nothing is bundled – everything comes from Modrinth.
 */
const QUICK = [
  {
    slug: 'complementary-reimagined',
    name: 'Complementary Reimagined',
    text: 'Wet ground and puddles that reflect the world when it rains, soft light, water reflections.',
    tag: 'Recommended',
  },
  {
    slug: 'bsl-shaders',
    name: 'BSL Shaders',
    text: 'Warm light, sunsets and shiny water. A classic.',
  },
  {
    slug: 'photon-shader',
    name: 'Photon',
    text: 'Realistic light and clouds. Needs a strong graphics card.',
  },
];

/** The pack every Fabric profile gets on its first start. */
const DEFAULT = 'complementary-reimagined';

const isQuick = (slug) => QUICK.some((s) => s.slug === slug);

// ------------------------------------------------------------------ is this PC strong enough for shaders?

/** Graphics cards that run Complementary at playable frame rates (mid-range desktop cards and up). */
const STRONG_GPU = new RegExp([
  'RTX\\s?\\d{4}', 'RTX\\s?A\\d', 'GTX\\s?(1070|1080|1660|1650 SUPER)', 'TITAN',
  'RX\\s?(56|57|66|67|68|69|76|77|78|79|90)\\d\\d', 'RX\\s?Vega\\s?(56|64)', 'Radeon\\s?(VII|Pro W[5-7])',
  'Arc\\s?(A7|A5|B5|B7)', 'Apple M\\d+ (Pro|Max|Ultra)',
].join('|'), 'i');

let gpu = { strong: false, name: 'unknown', checked: false };

/** Electron's app.getGPUInfo('complete'): the renderer string and device names decide; 8 GB RAM at least. */
function setGpu(info) {
  const os = require('os');
  const names = [];
  if (info && info.auxAttributes && info.auxAttributes.glRenderer) names.push(info.auxAttributes.glRenderer);
  for (const d of (info && info.gpuDevice) || []) if (d.deviceString) names.push(d.deviceString);
  const name = names.find((n) => STRONG_GPU.test(n)) || names[0] || 'unknown';
  const enoughRam = os.totalmem() >= 8 * 1024 ** 3;
  gpu = { strong: STRONG_GPU.test(names.join(' | ')) && enoughRam, name, checked: true };
  return gpu;
}

const gpuInfo = () => gpu;

/** Escapes a value for a Java .properties file (backslashes and line breaks). */
const propValue = (v) => String(v).replace(/\\/g, '\\\\').replace(/\r?\n/g, ' ');

/** Turns shaders on in Iris with this pack (a file in shaderpacks/), keeping every other Iris setting. */
function enableInIris(gameDir, packFile) {
  const file = path.join(gameDir, 'config', 'iris.properties');
  let lines = [];
  try {
    lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
  } catch {
    // no Iris settings yet
  }
  lines = lines.filter((l) => l && !/^\s*(shaderPack|enableShaders)\s*[=:]/.test(l));
  lines.push(`enableShaders=true`, `shaderPack=${propValue(path.basename(packFile))}`);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, lines.join('\n') + '\n');
}

module.exports = { QUICK, DEFAULT, isQuick, enableInIris, setGpu, gpuInfo, STRONG_GPU };
