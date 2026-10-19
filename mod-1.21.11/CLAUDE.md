# Onix Client 1.21.11 (Onix + Carbon) – notes for Claude

Fabric client mod for **Minecraft 1.21.11** (official Mojang mappings via `fabric-loom-remap`, Loom 1.18, Fabric API 0.141.6, Java 21).
It is Onix (`dev.onix.client`, ported from the 1.21.1 build in `/mod`) combined with Carbon Client's menu designs and
Carbon-only modules.

## What comes from where
- `dev.onix.client` – everything of Onix: 124 modules, cosmetics, coins/shop (`network/`), menus, HUD editor, profiles.
  Modules ported from Carbon: Mousestrokes, Dark Mode, Title Tweaker, Auto Perspective, Shulker Preview, Drop Prevention,
  Toast Control, Particle Limiter (registered at the end of `ModuleManager.registerDefaults`).
- `gg.carbon.client` – Carbon's UI kit (`ui/`: R2D, Fonts/TextDraw, anim) and its main menu designs (`screen/menu`,
  `CarbonMainMenu`, `ThemePicker`). `gg.carbon.client.CarbonClient` is a small stand-in that maps what the designs read
  (menu design, light mode, menu key …) onto Onix's config. Carbon's own module system, ClickGui and HUD are not included.
- `OnixConfig.menuDesign`: Onix (OnixTitleScreen) / Carbon / Voxel / Portal / Classic / Vanilla. `MinecraftClientMixin`
  opens the right title screen; Onix's title screen links to the Carbon picker ("More designs").
- Carbon's PvP macros (crystal/anchor macros, trigger bot, auto totem, W-tap, hover totem, aim assist) are deliberately left out.

## Checking
- `tools/check_mixins.py` (repo root `/home/claude/tools` in the dev container) compares mixin targets with decompiled sources.
- `.github/workflows/devtest.yml` runs `gradlew runDevtest` (util/DevTest.java) in CI and pushes screenshots and logs to the
  branch `devtest-results` (touch `.github/devtest-trigger` to run it).
- 1.21.11 GUI rendering is deferred: no `RenderSystem.setShaderColor`, no global alpha in GUI; world rendering goes through
  render passes (see `render/SkyRenderer`).

## Other Minecraft versions (ports/)
- 1.21.10 and 1.21.8 are generated from this folder by `python3 tools/port.py <version>` → `ports/build/mod-<version>` (git-ignored).
- `ports/<version>/port.json` holds regex rules (renames like Identifier → ResourceLocation), per-file rules, dropped files/mixins;
  `ports/<version>/overrides/` holds files that differ too much (e.g. button/sky mixins, `dev.onix.compat` input event stand-ins for 1.21.8).
- Edit code here, then regenerate and compile-check each port; CI builds them (build.yml `mod-ports`) and dev-tests them (devtest.yml, branches devtest-results-<version>).
- A new port: add ports/<v>/port.json, the version to mc-libs.yml/mc-sources.yml (sources + classpath branches), build.yml, devtest.yml,
  launcher/package.json extraResources and launcher/lib/versions.js ONIX.
