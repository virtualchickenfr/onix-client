#!/usr/bin/env python3
"""Generates the Onix mod for another Minecraft version from mod-1.21.11.

    python3 tools/port.py 1.21.10            -> ports/build/mod-1.21.10 (a normal Gradle project)

ports/<version>/port.json describes the version:
    minecraft     Minecraft version
    fabric_api    Fabric API version, or "auto" (latest build for that Minecraft version from maven.fabricmc.net)
    loader        Fabric Loader version (optional, defaults to the one of mod-1.21.11)
    depends       fabric.mod.json "minecraft" range
    rules         list of [regex, replacement] applied to every .java file
    file_rules    {relative path: [[regex, replacement], ...]} applied to single files
    drop          relative paths (files or folders) removed from the copy
    mixins_drop   mixin class names removed from onix.mixins.json / carbon.mixins.json
ports/<version>/overrides/ is copied over the result last (same layout as mod-1.21.11).
"""
import json, os, re, shutil, sys, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, 'mod-1.21.11')


def props(path):
    out = {}
    for line in open(path, encoding='utf8'):
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            k, v = line.split('=', 1)
            out[k.strip()] = v.strip()
    return out


def latest_fabric_api(mc):
    url = 'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml'
    xml = urllib.request.urlopen(url, timeout=60).read().decode()
    found = re.findall(r'<version>([0-9.]+\+' + re.escape(mc) + r')</version>', xml)
    if not found:
        raise SystemExit(f'no Fabric API build for {mc}')
    return max(found, key=lambda v: [int(x) for x in v.split('+')[0].split('.')])


def main(version, out=None):
    spec_dir = os.path.join(ROOT, 'ports', version)
    spec = json.load(open(os.path.join(spec_dir, 'port.json'), encoding='utf8'))
    out = out or os.path.join(ROOT, 'ports', 'build', 'mod-' + version)
    if os.path.exists(out):
        shutil.rmtree(out)
    shutil.copytree(BASE, out, ignore=shutil.ignore_patterns('.gradle', 'build', 'run', 'out', 'CLAUDE.md'))

    for rel in spec.get('drop', []):
        p = os.path.join(out, rel)
        if os.path.isdir(p):
            shutil.rmtree(p)
        elif os.path.exists(p):
            os.remove(p)

    rules = [(re.compile(a, re.M), b) for a, b in spec.get('rules', [])]
    file_rules = {k: [(re.compile(a, re.M | re.S), b) for a, b in v] for k, v in spec.get('file_rules', {}).items()}
    src = os.path.join(out, 'src', 'main', 'java')
    for dp, _, files in os.walk(src):
        for f in files:
            if not f.endswith('.java'):
                continue
            p = os.path.join(dp, f)
            rel = os.path.relpath(p, out).replace(os.sep, '/')
            s = orig = open(p, encoding='utf8').read()
            for rx, rep in rules:
                s = rx.sub(rep, s)
            for rx, rep in file_rules.get(rel, []):
                s2 = rx.sub(rep, s)
                if s2 == s:
                    print(f'warning: rule {rx.pattern[:60]!r} matched nothing in {rel}')
                s = s2
            if s != orig:
                open(p, 'w', encoding='utf8').write(s)

    res = os.path.join(out, 'src', 'main', 'resources')
    for name in ('onix.mixins.json', 'carbon.mixins.json'):
        p = os.path.join(res, name)
        if not os.path.exists(p):
            continue
        d = json.load(open(p, encoding='utf8'))
        d['client'] = [m for m in d['client'] if m not in spec.get('mixins_drop', [])]
        json.dump(d, open(p, 'w', encoding='utf8'), indent=2)

    over = os.path.join(spec_dir, 'overrides')
    if os.path.isdir(over):
        shutil.copytree(over, out, dirs_exist_ok=True)

    fmj = os.path.join(res, 'fabric.mod.json')
    d = json.load(open(fmj, encoding='utf8'))
    d['depends']['minecraft'] = spec.get('depends', '~' + spec['minecraft'])
    d['description'] = d['description'].replace('1.21.11', spec['minecraft'])
    json.dump(d, open(fmj, 'w', encoding='utf8'), indent=2)

    p = props(os.path.join(BASE, 'gradle.properties'))
    api = spec.get('fabric_api', 'auto')
    if api == 'auto':
        try:
            api = latest_fabric_api(spec['minecraft'])
        except OSError as e:
            print(f'warning: Fabric API lookup failed ({e}); fabric_api_version left unresolved')
            api = 'UNRESOLVED'
    gp = os.path.join(out, 'gradle.properties')
    s = open(gp, encoding='utf8').read()
    s = re.sub(r'(?m)^minecraft_version=.*$', 'minecraft_version=' + spec['minecraft'], s)
    s = re.sub(r'(?m)^fabric_api_version=.*$', 'fabric_api_version=' + api, s)
    if spec.get('loader'):
        s = re.sub(r'(?m)^loader_version=.*$', 'loader_version=' + spec['loader'], s)
    s = s.replace('+1.21.11.jar', '+' + spec['minecraft'] + '.jar')
    open(gp, 'w', encoding='utf8').write(s)
    print(f'{out} (Minecraft {spec["minecraft"]}, Fabric API {api}, mod {p.get("mod_version")})')
    return out


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
