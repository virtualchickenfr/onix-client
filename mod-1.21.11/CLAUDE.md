# Carbon Client – notes for Claude

Fabric client mod for **Minecraft 1.21.11** (official Mojang mappings via `fabric-loom-remap`, Fabric Loader 0.19.3+, Loom 1.18, Fabric API 0.141.6, targets **Java 21**). Ported from the 26.3 original.
Package `gg.carbon.client`. Monochrome (black/grey/white) PvP + performance client; feature set modelled on Dawn's module list,
all code written from scratch.

## Build & run
- JDK: run `setup-jdk.ps1` once (portable JDK 25 into `.tools/`), then set `JAVA_HOME` to it.
- `gradlew build` → `build/libs/carbon-client-<version>.jar`
- `gradlew runClient` → dev game. `gradlew runDevtest` → automated walkthrough that saves screenshots of every Carbon screen to
  `run/screenshots` (see `util/DevTest.java`). Use this to verify UI changes visually — do not drive the user's real mouse/keyboard.
- `gradlew genSources` decompiles Minecraft; look up APIs there. 1.21.11 differs from 26.3: `GuiGraphics` + `render`/`renderBackground`,
  `mc.screen`/`mc.setScreen`, GLFW key codes in `InputConstants` (KeySetting stores GLFW codes), mouse buttons left=0/right=1.

## Rendering rules (important for quality)
- Never draw text with vanilla `g.text`; use `ui/Fonts` → `ui/text/TextDraw` (FreeType glyphs rasterised at the exact device pixel size).
- Draw shapes through `ui/R2D` (resolves the full pose matrix to device pixels; rounded shapes are generated nine-slices).
- No rotations / non-uniform transforms in UI (breaks pixel snapping). Animate with fade + translate; avoid scaling text.
- Pixelify Sans (`Face.DISPLAY`) only for big headlines; small text = Inter (`BODY`, `MEDIUM`, `BODY_BOLD`, `LIGHT`), values = JetBrains Mono.
- Shared components live in `screen/widget/Ui.java` (block, button, card, slot, toggle, keycap, eyebrow). Icons: `ui/PixelIcons` (9x9 pixel art).
- Brand motif: graphene — `ui/Carbon` (hexagon mark, lattice).

## Main menu designs
- `screen/menu/`: `MenuTheme` implementations (Carbon, Carbon Client, Voxel, Portal, Classic, Vanilla; "Carbon Client" is `CarbonTheme(true)` with the logo + character art) render into a virtual canvas via `MenuContext`;
  `CarbonMainMenu` hosts the active one (`ClientSettings.menuDesign`), `ThemePicker` is the swipeable iOS-style picker that renders
  every theme live as a preview (`preview=true` → no hover, no panorama, no shared state). Classic draws the real panorama via the host;
  Vanilla is preview-only — picking it hands over to vanilla `TitleScreen` (no button restyling there). Portal's hotbar uses the
  `MenuTheme.keyPressed` / `mouseScrolled` hooks. Slow motion (Voxel bob) uses `Iso.cube(float, float)`: linear-filtered textures
  shifted by sub-pixel offsets, so it glides instead of stepping.
- Light mode (`ClientSettings.menuLight`): menu designs use their own palettes (`Ui.light`, `menu/Ink`); dark-designed screens
  (ClickGui, picker chrome, HUD editor toolbar) set `R2D.invert`, which maps greys through `Theme.lightOf`. Use `R2D.tint` for
  colours that must not be remapped (textures, real colour swatches).
- `screen/MenuSkin` + `mixin/MenuSkin*`: Carbon backdrop and chrome for vanilla menu screens outside a world (toggle
  `menuBackgrounds`); in light mode vanilla text/fills are remapped to ink.
- The mod menu has a Design page (`ClickGui.design()`, `ClientSettings.designSettings()`) with an `ActionRow` that opens the picker.- No scissor around rounded shapes (GUI scissor snaps to whole GUI units and leaks past corners) — clip analytically, e.g. `R2D.sheen`.

- util/DawnImport: imports a Dawn (Feather-based) install's eather/mc_options_v2.txt, servers.dat (Dawn stores joined servers as hidden → unhidden on import) and enabled resource packs. Dawn module settings are account-side, not on disk. Not part of DevTest (it writes real files).

## Structure
- `module/` Module, HudModule, TextHudModule, ModuleManager, ClientSettings; `module/impl/{hud,pvp,visual,utility,performance}`
- `setting/` typed settings, persisted by `config/ConfigManager` to `config/carbon/config.json`
- `screen/` CarbonMainMenu, ClickGui (mod menu), HudEditor, CarbonBackground, `widget/` setting rows
- `mixin/` hooks into vanilla (camera/zoom/freelook, input, lightmap, buttons, title screen swap, …) – listed in `carbon.mixins.json`

## Ideas for next steps
- Performance: measure FPS (with/without the Modrinth performance pack in `install-carbon.ps1`), profile HUD rendering, cache text widths.
- Missing Dawn modules are listed in README.md.

## Menu artwork & Dawn import
- `ui/MenuImages`: optional "CARBON CLIENT" logo + character render (`assets/carbon/textures/gui/menu_*.png`), toggled by
  `ClientSettings.menuLogo` / `menuSkin` on the Design page. Carbon, Voxel and Portal swap their wordmark; Carbon also shows the figure.
- Two Dawn imports on the Settings page: `util/DawnImport` (settings import: options, keybinds, servers, packs) and
  `util/DawnPackImport` (.dawnpack mod installer, below).
- `util/DawnPackImport`: "Import Dawn Pack" row on the Settings page. Reads a `.dawnpack` (dawnpack.json + overrides/), downloads the
  referenced Modrinth versions (sha1-checked) into the game dir, skips projects already installed (identified via Modrinth hash
  lookup); for packs made for another Minecraft version it picks each mod's newest Fabric build for the running version.
