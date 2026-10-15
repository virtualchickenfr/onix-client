const fs = require('fs');
const zlib = require('zlib');

/** Small ZIP writer (deflate, no ZIP64) – enough for modpacks and profile exports. */

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function dosTime(date) {
  const time = (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2);
  const day = ((date.getFullYear() - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate();
  return { time, day };
}

/**
 * Writes a zip file.
 * @param {string} out target path (written to out + ".part" first, then renamed)
 * @param {{name: string, data?: Buffer|string, file?: string}[]} entries paths inside the zip use "/"
 */
function writeZip(out, entries) {
  const tmp = out + '.part';
  const fd = fs.openSync(tmp, 'w');
  const central = [];
  let offset = 0;
  const { time, day } = dosTime(new Date());
  const write = (buf) => {
    fs.writeSync(fd, buf);
    offset += buf.length;
  };
  try {
    for (const entry of entries) {
      const name = Buffer.from(entry.name.replace(/\\/g, '/'), 'utf8');
      const raw = entry.file ? fs.readFileSync(entry.file) : Buffer.isBuffer(entry.data) ? entry.data : Buffer.from(String(entry.data), 'utf8');
      const deflated = zlib.deflateRawSync(raw, { level: 6 });
      const stored = deflated.length >= raw.length; // already compressed (jars, pngs): store
      const body = stored ? raw : deflated;
      const crc = crc32(raw);
      if (offset + body.length > 0xfffffff0) throw new Error('The export is larger than 4 GB – leave out resource packs or worlds.');

      const local = Buffer.alloc(30);
      local.writeUInt32LE(0x04034b50, 0);
      local.writeUInt16LE(20, 4);
      local.writeUInt16LE(0x0800, 6); // UTF-8 names
      local.writeUInt16LE(stored ? 0 : 8, 8);
      local.writeUInt16LE(time, 10);
      local.writeUInt16LE(day, 12);
      local.writeUInt32LE(crc, 14);
      local.writeUInt32LE(body.length, 18);
      local.writeUInt32LE(raw.length, 22);
      local.writeUInt16LE(name.length, 26);
      local.writeUInt16LE(0, 28);
      const at = offset;
      write(local);
      write(name);
      write(body);

      const c = Buffer.alloc(46);
      c.writeUInt32LE(0x02014b50, 0);
      c.writeUInt16LE(20, 4);
      c.writeUInt16LE(20, 6);
      c.writeUInt16LE(0x0800, 8);
      c.writeUInt16LE(stored ? 0 : 8, 10);
      c.writeUInt16LE(time, 12);
      c.writeUInt16LE(day, 14);
      c.writeUInt32LE(crc, 16);
      c.writeUInt32LE(body.length, 20);
      c.writeUInt32LE(raw.length, 24);
      c.writeUInt16LE(name.length, 28);
      c.writeUInt32LE(at, 42);
      central.push(Buffer.concat([c, name]));
    }
    const start = offset;
    for (const c of central) write(c);
    const end = Buffer.alloc(22);
    end.writeUInt32LE(0x06054b50, 0);
    end.writeUInt16LE(central.length, 8);
    end.writeUInt16LE(central.length, 10);
    end.writeUInt32LE(offset - start, 12);
    end.writeUInt32LE(start, 16);
    write(end);
  } catch (err) {
    fs.closeSync(fd);
    fs.rmSync(tmp, { force: true });
    throw err;
  }
  fs.closeSync(fd);
  fs.renameSync(tmp, out);
  return out;
}

/**
 * Walks a zip without loading it all: `pick(name, size)` returns
 *   - 'buffer' to receive the entry as a Buffer in the result map,
 *   - an absolute path to stream the entry to that file,
 *   - or null to skip it.
 */
function walkZip(file, pick) {
  const yauzl = require('yauzl');
  const path = require('path');
  return new Promise((resolve, reject) => {
    yauzl.open(file, { lazyEntries: true }, (err, zip) => {
      if (err || !zip) return reject(new Error('This file is not a valid zip archive.'));
      const buffers = new Map();
      let written = 0;
      const fail = (e) => {
        zip.close();
        reject(e);
      };
      zip.on('error', (e) => reject(/invalid relative path|absolute path/i.test(String(e && e.message)) ? new Error(`The archive contains an unsafe path: ${e.message.replace(/^.*?: /, '')}`) : e));
      zip.on('entry', (entry) => {
        if (/\/$/.test(entry.fileName)) return zip.readEntry();
        let target;
        try {
          target = pick(entry.fileName, entry.uncompressedSize);
        } catch (e) {
          return fail(e);
        }
        if (!target) return zip.readEntry();
        zip.openReadStream(entry, (e, stream) => {
          if (e) return fail(e);
          if (target === 'buffer') {
            const chunks = [];
            stream.on('data', (chunk) => chunks.push(chunk));
            stream.on('error', fail);
            stream.on('end', () => {
              buffers.set(entry.fileName, Buffer.concat(chunks));
              zip.readEntry();
            });
            return;
          }
          fs.mkdirSync(path.dirname(target), { recursive: true });
          const outStream = fs.createWriteStream(target);
          stream.on('error', fail);
          outStream.on('error', fail);
          outStream.on('finish', () => {
            written++;
            zip.readEntry();
          });
          stream.pipe(outStream);
        });
      });
      zip.on('end', () => resolve({ buffers, written }));
      zip.readEntry();
    });
  });
}

module.exports = { writeZip, walkZip, crc32 };
