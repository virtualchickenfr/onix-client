'use strict';

// Desktop shortcuts for profiles (Windows). The icon is drawn by the page and arrives as PNGs in several sizes, which
// are packed into one .ico (PNG-compressed ICO entries, supported since Windows Vista). Taken over from the Carbon
// Launcher.
const fsp = require('fs').promises;
const path = require('path');
const crypto = require('crypto');
const { app, shell } = require('electron');

function ico(pngs) {
  const header = Buffer.alloc(6 + 16 * pngs.length);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(pngs.length, 4);
  let offset = header.length;
  pngs.forEach(({ size, data }, i) => {
    const e = 6 + 16 * i;
    header.writeUInt8(size >= 256 ? 0 : size, e);
    header.writeUInt8(size >= 256 ? 0 : size, e + 1);
    header.writeUInt8(0, e + 2);
    header.writeUInt8(0, e + 3);
    header.writeUInt16LE(1, e + 4);
    header.writeUInt16LE(32, e + 6);
    header.writeUInt32LE(data.length, e + 8);
    header.writeUInt32LE(offset, e + 12);
    offset += data.length;
  });
  return Buffer.concat([header, ...pngs.map((p) => p.data)]);
}

const quote = (a) => (/[\s"]/.test(a) ? `"${a.replace(/"/g, '')}"` : a);

/** spec: { name, server?, world?, icons: { [size]: dataUrl } } for profile → path of the .lnk */
async function create(profile, spec, dataDir) {
  if (process.platform !== 'win32') throw new Error('Desktop shortcuts are only available on Windows.');
  const name = String(spec.name || profile.name).replace(/[<>:"/\\|?*\u0000-\u001f]/g, '').trim().slice(0, 60) || 'Onix';
  const server = spec.server ? String(spec.server).trim() : '';
  if (server && !/^[A-Za-z0-9.\-_]+(:\d{1,5})?$/.test(server)) throw new Error('Invalid server address (for example play.example.net or 1.2.3.4:25565)');
  const world = spec.world ? path.basename(String(spec.world)).replace(/"/g, '') : '';

  const pngs = Object.entries(spec.icons || {})
    .map(([size, url]) => ({ size: Number(size), data: Buffer.from(String(url).split(',')[1] || '', 'base64') }))
    .filter((p) => p.data.length && p.data.subarray(1, 4).toString() === 'PNG')
    .sort((a, b) => b.size - a.size);
  if (!pngs.length) throw new Error('The icon is missing.');
  const dir = path.join(dataDir, 'shortcut-icons');
  await fsp.mkdir(dir, { recursive: true });
  const iconFile = path.join(dir, `${profile.id}-${crypto.createHash('sha1').update(pngs[0].data).digest('hex').slice(0, 8)}.ico`);
  await fsp.writeFile(iconFile, ico(pngs));

  const args = [`--launch=${profile.id}`];
  if (server) args.push(`--server=${server}`);
  else if (world) args.push(`--world=${world}`);
  if (!app.isPackaged) args.unshift(app.getAppPath());

  const lnk = path.join(app.getPath('desktop'), `${name}.lnk`);
  const ok = shell.writeShortcutLink(lnk, 'create', {
    target: process.execPath,
    args: args.map(quote).join(' '),
    cwd: path.dirname(process.execPath),
    icon: iconFile,
    iconIndex: 0,
    description: server ? `${profile.name} – join ${server}` : world ? `${profile.name} – world "${world}"` : `Start ${profile.name}`,
  });
  if (!ok) throw new Error('The shortcut could not be created.');
  return lnk;
}

/** --launch=<profile> [--server=host | --world=name] from a shortcut. */
function launchArgs(argv) {
  const get = (k) => {
    const a = (argv || []).find((x) => String(x).startsWith(`--${k}=`));
    return a ? String(a).slice(k.length + 3) : null;
  };
  const id = get('launch');
  return id ? { id, server: get('server'), world: get('world') } : null;
}

module.exports = { create, launchArgs };
