const fs = require('fs');
const path = require('path');
const { Readable, Transform } = require('stream');
const { pipeline } = require('stream/promises');

const USER_AGENT = 'OnixClient-Launcher/1.0.0 (onix launcher)';

async function fetchJson(url) {
  const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
  if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
  return res.json();
}

/** Downloads a file and reports progress (0..1). */
async function download(url, dest, onProgress) {
  const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
  if (!res.ok || !res.body) throw new Error(`Download failed (HTTP ${res.status}): ${url}`);
  const total = Number(res.headers.get('content-length')) || 0;
  let received = 0;
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  const tmp = dest + '.part';
  const counter = new Transform({
    transform(chunk, _enc, cb) {
      received += chunk.length;
      if (onProgress && total) onProgress(received / total);
      cb(null, chunk);
    },
  });
  await pipeline(Readable.fromWeb(res.body), counter, fs.createWriteStream(tmp));
  fs.renameSync(tmp, dest);
  return dest;
}

module.exports = { download, fetchJson, USER_AGENT };
