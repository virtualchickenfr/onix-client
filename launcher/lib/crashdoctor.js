'use strict';

const fs = require('fs');
const path = require('path');
const { readFabricModJson } = require('./importer');

/**
 * Reads the game output of a failed start and names the mods that caused it:
 * Fabric's "incompatible mods" report, a mod whose start code failed, or a mod whose mixins did not apply.
 * Returns null when nothing specific is found.
 */

const MANAGED = new Set(['fabric-api', 'sodium', 'lithium', 'ferritecore', 'onix', 'minecraft', 'java', 'fabricloader']);

function analyze(lines) {
  const text = lines.join('\n');
  const mods = new Map(); // id -> { id, name, why }
  const add = (id, name, why) => {
    if (!id || MANAGED.has(id) || mods.has(id)) return;
    mods.set(id, { id, name: name || id, why });
  };

  // 1. Fabric could not resolve the mods (missing or wrong versions)
  const res = text.lastIndexOf('Some of your mods are incompatible with the game or each other!');
  if (res >= 0) {
    const block = text.slice(res).split('\n').slice(1);
    const details = [];
    for (const raw of block) {
      if (/^\s+at /.test(raw) || /^\[\d\d:\d\d:\d\d]/.test(raw)) break;
      const line = raw.trim();
      if (!line || /^(A potential solution|More details)/.test(line)) continue;
      details.push(line.replace(/^- /, ''));
    }
    for (const d of details) {
      // "Mod 'Voxy' (voxy) 0.2.16-beta requires version 0.8.4 … of mod 'Sodium' (sodium)"
      const first = d.match(/^(?:Replace |Remove )?mod '([^']+)' \(([\w.-]+)\)/i);
      if (first) add(first[2], first[1], d);
      const target = d.match(/of mod '([^']+)' \(([\w.-]+)\)/i);
      if (target && first && MANAGED.has(first[2])) add(target[2], target[1], d);
    }
    return {
      kind: 'incompatible',
      title: 'Some mods do not fit together',
      text: details.slice(0, 6).join('\n'),
      mods: [...mods.values()],
    };
  }

  // 2. a mod's start code crashed
  const entry = text.match(/Could not execute entrypoint stage '(\w+)' due to errors, provided by '([\w.-]+)'/);
  if (entry) {
    const cause = (text.slice(entry.index).match(/Caused by: ([^\n]+)/) || [])[1] || '';
    const oldApi = /IncompatibleClassChangeError|NoSuchMethodError|NoSuchFieldError|AbstractMethodError|NoClassDefFoundError/.test(cause);
    add(entry[2], null, cause.trim().slice(0, 300));
    return {
      kind: 'entrypoint',
      title: `The mod "${entry[2]}" crashed at start`,
      text: oldApi
        ? 'It was made for an older Fabric API or Minecraft version and does not fit this one. Update it or turn it off.'
        : cause.trim().slice(0, 300),
      mods: [...mods.values()],
    };
  }

  // 3. mixins of a mod did not apply
  const mixin = text.match(/Mixin apply for mod ([\w.-]+) failed/) || text.match(/Mixin transformation of [^\n]+ failed[\s\S]{0,400}?from mod ([\w.-]+)/);
  if (mixin) {
    add(mixin[1], null, 'mixin failed');
    return {
      kind: 'mixin',
      title: `The mod "${mixin[1]}" does not fit this Minecraft version`,
      text: 'Its changes to the game could not be applied. Update it or turn it off.',
      mods: [...mods.values()],
    };
  }

  // 4. the crash report names a suspected mod
  const suspected = text.match(/Suspected Mods?: ([^\n]+)/);
  if (suspected && !/None|Unknown/i.test(suspected[1])) {
    for (const m of suspected[1].matchAll(/([^,(]+) \(([\w.-]+)\)/g)) add(m[2], m[1].trim(), 'suspected in the crash report');
    if (mods.size) return { kind: 'suspected', title: 'Minecraft crashed', text: `The crash report suspects: ${suspected[1].trim()}`, mods: [...mods.values()] };
  }
  return null;
}

/** Finds the jar files of the named mods in a mods folder. */
async function attachFiles(diagnosis, modsDir) {
  if (!diagnosis || !diagnosis.mods.length) return diagnosis;
  const wanted = new Set(diagnosis.mods.map((m) => m.id));
  let jars = [];
  try {
    jars = fs.readdirSync(modsDir).filter((f) => f.endsWith('.jar'));
  } catch {
    return diagnosis;
  }
  for (const jar of jars) {
    const meta = await readFabricModJson(path.join(modsDir, jar)).catch(() => null);
    if (meta && wanted.has(meta.id)) {
      const m = diagnosis.mods.find((x) => x.id === meta.id);
      m.file = jar;
      m.name = m.name === m.id && meta.name ? meta.name : m.name;
      diagnosis.title = diagnosis.title.replace(`"${m.id}"`, `"${m.name}"`);
    }
  }
  return diagnosis;
}

module.exports = { analyze, attachFiles };
