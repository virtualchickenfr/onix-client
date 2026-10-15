// Writes latest.yml (what electron-updater on Windows reads) for a signed installer:
//   node latest-yml.js <installer.exe> <version> <out/latest.yml>
const fs = require('fs');
const crypto = require('crypto');
const path = require('path');

const [file, version, out] = process.argv.slice(2);
const data = fs.readFileSync(file);
const sha512 = crypto.createHash('sha512').update(data).digest('base64');
const name = path.basename(file);
fs.writeFileSync(out, [
  `version: ${version}`,
  'files:',
  `  - url: ${name}`,
  `    sha512: ${sha512}`,
  `    size: ${data.length}`,
  `path: ${name}`,
  `sha512: ${sha512}`,
  `releaseDate: '${new Date().toISOString()}'`,
  '',
].join('\n'));
console.log(`latest.yml for ${name} (${data.length} bytes)`);
