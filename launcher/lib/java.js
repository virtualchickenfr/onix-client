const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { download, fetchJson } = require('./download');

/** Finds a java/javaw binary in a folder (recursive, limited depth). */
function findJava(dir, depth = 0) {
  if (!fs.existsSync(dir) || depth > 5) return null;
  const isWin = process.platform === 'win32';
  const names = isWin ? ['javaw.exe', 'java.exe'] : ['java'];
  if (path.basename(dir) === 'bin') {
    for (const name of names) {
      const candidate = path.join(dir, name);
      if (fs.existsSync(candidate)) return candidate;
    }
  }
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory() && !entry.name.startsWith('.')) {
      const found = findJava(path.join(dir, entry.name), depth + 1);
      if (found) return found;
    }
  }
  return null;
}

/**
 * Provides Java 21. Minecraft 1.21.1 needs Java 21; if it is missing we download an
 * Eclipse Temurin JRE from Adoptium into the game folder.
 */
async function ensureJava(root, onProgress) {
  const runtimeDir = path.join(root, 'runtime');
  const javaDir = path.join(runtimeDir, 'java21');
  const existing = findJava(javaDir);
  if (existing) return existing;

  const os = { win32: 'windows', darwin: 'mac', linux: 'linux' }[process.platform];
  if (!os) throw new Error(`Operating system ${process.platform} is not supported`);
  const arch = process.arch === 'arm64' ? 'aarch64' : 'x64';
  // the assets endpoint names the package and its SHA-256, so the download can be checked
  const assets = await fetchJson(`https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=${arch}&image_type=jre&os=${os}&vendor=eclipse`);
  const pkg = Array.isArray(assets) && assets[0] && assets[0].binary && assets[0].binary.package;
  if (!pkg || !pkg.link || !pkg.checksum) throw new Error('Java 21 could not be found for this system. Set a Java path in Settings.');
  const isZip = os === 'windows';
  const archive = path.join(runtimeDir, isZip ? 'java21.zip' : 'java21.tar.gz');
  const tmpDir = path.join(runtimeDir, 'java21-tmp');

  await download(pkg.link, archive, (p) => onProgress && onProgress('Downloading Java 21', p));
  const sha256 = crypto.createHash('sha256').update(fs.readFileSync(archive)).digest('hex');
  if (sha256 !== String(pkg.checksum).toLowerCase()) {
    fs.rmSync(archive, { force: true });
    throw new Error('The Java download was damaged (checksum mismatch). Please try again.');
  }
  onProgress && onProgress('Extracting Java 21', 1);

  fs.rmSync(tmpDir, { recursive: true, force: true });
  fs.mkdirSync(tmpDir, { recursive: true });
  if (isZip) {
    const extract = require('extract-zip');
    await extract(archive, { dir: path.resolve(tmpDir) });
  } else {
    const tar = require('tar');
    await tar.x({ file: archive, cwd: tmpDir });
  }
  fs.rmSync(archive, { force: true });
  fs.rmSync(javaDir, { recursive: true, force: true });
  fs.renameSync(tmpDir, javaDir);

  const java = findJava(javaDir);
  if (!java) throw new Error('Java was downloaded but could not be found');
  if (process.platform !== 'win32') fs.chmodSync(java, 0o755);
  return java;
}

module.exports = { ensureJava };
