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

module.exports = { QUICK, DEFAULT, isQuick, enableInIris };
