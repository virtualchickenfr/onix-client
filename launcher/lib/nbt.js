'use strict';

/**
 * Minimal NBT reader/writer (Java edition, big-endian) for servers.dat: the file is an uncompressed root compound
 * with a list "servers" of compounds { ip, name, icon?, acceptTextures?, hidden? }. Unknown tags are kept as they are.
 */

const zlib = require('zlib');

function read(buf) {
  if (buf[0] === 0x1f && buf[1] === 0x8b) buf = zlib.gunzipSync(buf);
  let p = 0;
  const u8 = () => buf[p++];
  const i16 = () => { const v = buf.readInt16BE(p); p += 2; return v; };
  const u16 = () => { const v = buf.readUInt16BE(p); p += 2; return v; };
  const i32 = () => { const v = buf.readInt32BE(p); p += 4; return v; };
  const str = () => { const n = u16(); const s = buf.toString('utf8', p, p + n); p += n; return s; };
  function payload(type) {
    switch (type) {
      case 1: return { t: 1, v: buf.readInt8(p++) };
      case 2: return { t: 2, v: i16() };
      case 3: return { t: 3, v: i32() };
      case 4: { const v = buf.readBigInt64BE(p); p += 8; return { t: 4, v }; }
      case 5: { const v = buf.readFloatBE(p); p += 4; return { t: 5, v }; }
      case 6: { const v = buf.readDoubleBE(p); p += 8; return { t: 6, v }; }
      case 7: { const n = i32(); const v = buf.subarray(p, p + n); p += n; return { t: 7, v: Buffer.from(v) }; }
      case 8: return { t: 8, v: str() };
      case 9: {
        const et = u8();
        const n = i32();
        const items = [];
        for (let k = 0; k < n; k++) items.push(payload(et));
        return { t: 9, et, v: items };
      }
      case 10: {
        const entries = [];
        for (;;) {
          const tt = u8();
          if (tt === 0) break;
          const name = str();
          entries.push([name, payload(tt)]);
        }
        return { t: 10, v: entries };
      }
      case 11: { const n = i32(); const v = []; for (let k = 0; k < n; k++) v.push(i32()); return { t: 11, v }; }
      case 12: { const n = i32(); const v = []; for (let k = 0; k < n; k++) { v.push(buf.readBigInt64BE(p)); p += 8; } return { t: 12, v }; }
      default: throw new Error(`Unknown NBT tag ${type}`);
    }
  }
  const type = u8();
  if (type !== 10) throw new Error('Not an NBT compound');
  str();
  return payload(10);
}

function write(root) {
  const parts = [];
  const b = (n) => { const x = Buffer.alloc(n); parts.push(x); return x; };
  const u8 = (v) => { b(1).writeUInt8(v); };
  const str = (s) => { const d = Buffer.from(s, 'utf8'); b(2).writeUInt16BE(d.length); parts.push(d); };
  function payload(tag) {
    switch (tag.t) {
      case 1: b(1).writeInt8(tag.v); break;
      case 2: b(2).writeInt16BE(tag.v); break;
      case 3: b(4).writeInt32BE(tag.v); break;
      case 4: b(8).writeBigInt64BE(tag.v); break;
      case 5: b(4).writeFloatBE(tag.v); break;
      case 6: b(8).writeDoubleBE(tag.v); break;
      case 7: b(4).writeInt32BE(tag.v.length); parts.push(tag.v); break;
      case 8: str(tag.v); break;
      case 9: u8(tag.v.length ? tag.et : (tag.et || 10)); b(4).writeInt32BE(tag.v.length); tag.v.forEach(payload); break;
      case 10: for (const [name, child] of tag.v) { u8(child.t); str(name); payload(child); } u8(0); break;
      case 11: b(4).writeInt32BE(tag.v.length); tag.v.forEach((x) => b(4).writeInt32BE(x)); break;
      case 12: b(4).writeInt32BE(tag.v.length); tag.v.forEach((x) => b(8).writeBigInt64BE(x)); break;
      default: throw new Error(`Unknown NBT tag ${tag.t}`);
    }
  }
  u8(10);
  str('');
  payload(root);
  return Buffer.concat(parts);
}

const field = (compound, name) => (compound.v.find(([n]) => n === name) || [])[1];

/** The server entries of a servers.dat (compound tags). */
function servers(file) {
  const root = read(require('fs').readFileSync(file));
  const list = field(root, 'servers');
  return list && list.t === 9 ? list.v.filter((x) => x.t === 10) : [];
}

const ipOf = (entry) => {
  const ip = field(entry, 'ip');
  return ip && ip.t === 8 ? ip.v.trim().toLowerCase().replace(/:25565$/, '') : '';
};

/**
 * Writes one servers.dat with every server of `files` (in order, the first file's order wins), each address once.
 * Returns how many servers the result has.
 */
function mergeServers(target, files) {
  const fs = require('fs');
  const seen = new Set();
  const out = [];
  for (const f of files) {
    let list = [];
    try {
      list = servers(f);
    } catch {
      continue; // damaged or not a server list
    }
    for (const e of list) {
      const ip = ipOf(e);
      if (!ip || seen.has(ip)) continue;
      seen.add(ip);
      out.push(e);
    }
  }
  const root = { t: 10, v: [['servers', { t: 9, et: 10, v: out }]] };
  fs.writeFileSync(target, write(root));
  return out.length;
}

module.exports = { read, write, servers, mergeServers };
