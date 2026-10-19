#!/usr/bin/env python3
"""Generates the Onix mod for another Minecraft version from mod-1.21.11.

    python3 tools/port.py 1.21.10            -> ports/build/mod-1.21.10 (a normal Gradle project)

ports/<version>/port.json describes the version:
    base          source folder (default mod-1.21.11; "mod" for the yarn-mapped 1.21.1 mod)
    minecraft     Minecraft version
    yarn, loom    yarn mappings / Loom version for a yarn-based port
    fabric_api    Fabric API version, or "auto" (latest build for that Minecraft version from maven.fabricmc.net)
    loader        Fabric Loader version (optional, defaults to the one of mod-1.21.11)
    depends       fabric.mod.json "minecraft" range
    rules         list of [regex, replacement] applied to every .java file
    file_rules    {relative path: [[regex, replacement], ...]} applied to single files
    drop          relative paths (files or folders) removed from the copy
    mixins_drop   mixin class names removed from onix.mixins.json / carbon.mixins.json
    mixins_add    {mixin config file: [class names]} added (the classes come from overrides/)
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


def latest_yarn(mc):
    url = 'https://maven.fabricmc.net/net/fabricmc/yarn/maven-metadata.xml'
    xml = urllib.request.urlopen(url, timeout=60).read().decode()
    found = re.findall(r'<version>(' + re.escape(mc) + r'\+build\.(\d+))</version>', xml)
    if not found:
        raise SystemExit(f'no yarn mappings for {mc}')
    return max(found, key=lambda v: int(v[1]))[0]


def main(version, out=None):
    spec_dir = os.path.join(ROOT, 'ports', version)
    spec = json.load(open(os.path.join(spec_dir, 'port.json'), encoding='utf8'))
    out = out or os.path.join(ROOT, 'ports', 'build', 'mod-' + version)
    base = os.path.join(ROOT, spec.get('base', 'mod-1.21.11'))
    if os.path.exists(out):
        shutil.rmtree(out)
    shutil.copytree(base, out, ignore=shutil.ignore_patterns('.gradle', 'build', 'run', 'out', 'CLAUDE.md'))

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
            # rules can turn two imports into the same one
            seen, lines = set(), []
            for line in s.split('\n'):
                if line.startswith('import '):
                    if line in seen:
                        continue
                    seen.add(line)
                lines.append(line)
            s = '\n'.join(lines)
            if s != orig:
                open(p, 'w', encoding='utf8').write(s)

    res = os.path.join(out, 'src', 'main', 'resources')
    for name in ('onix.mixins.json', 'carbon.mixins.json'):
        p = os.path.join(res, name)
        if not os.path.exists(p):
            continue
        d = json.load(open(p, encoding='utf8'))
        d['client'] = [m for m in d['client'] if m not in spec.get('mixins_drop', [])]
        d['client'] += [m for m in spec.get('mixins_add', {}).get(name, []) if m not in d['client']]
        json.dump(d, open(p, 'w', encoding='utf8'), indent=2)

    over = os.path.join(spec_dir, 'overrides')
    if os.path.isdir(over):
        shutil.copytree(over, out, dirs_exist_ok=True)

    fmj = os.path.join(res, 'fabric.mod.json')
    d = json.load(open(fmj, encoding='utf8'))
    d['depends']['minecraft'] = spec.get('depends', '~' + spec['minecraft'])
    d['description'] = d['description'].replace('1.21.11', spec['minecraft']).replace('1.21.1', spec['minecraft'])
    json.dump(d, open(fmj, 'w', encoding='utf8'), indent=2)

    p = props(os.path.join(base, 'gradle.properties'))
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
    s = re.sub(r'(?m)^fabric_version=.*$', 'fabric_version=' + api, s)
    if spec.get('yarn'):
        yarn = spec['yarn']
        if yarn == 'auto':
            try:
                yarn = latest_yarn(spec['minecraft'])
            except OSError as e:
                print(f'warning: yarn lookup failed ({e})')
                yarn = 'UNRESOLVED'
        s = re.sub(r'(?m)^yarn_mappings=.*$', 'yarn_mappings=' + yarn, s)
    if spec.get('loom'):
        s = re.sub(r'(?m)^loom_version=.*$', 'loom_version=' + spec['loom'], s)
    if spec.get('loader'):
        s = re.sub(r'(?m)^loader_version=.*$', 'loader_version=' + spec['loader'], s)
    s = s.replace('+1.21.11.jar', '+' + spec['minecraft'] + '.jar')
    if 'mod_version' in p and '+' not in p.get('mod_version', ''):
        pass
    open(gp, 'w', encoding='utf8').write(s)
    # every jar names its Minecraft version (the 1.21.1 mod's jar has none)
    bg = os.path.join(out, 'build.gradle')
    b = open(bg, encoding='utf8').read()
    b = re.sub(r'(?m)^version = project\.mod_version$', 'version = "${project.mod_version}+${project.minecraft_version}"', b)
    open(bg, 'w', encoding='utf8').write(b)
    print(f'{out} (Minecraft {spec["minecraft"]}, Fabric API {api}, mod {p.get("mod_version")})')
    return out


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
