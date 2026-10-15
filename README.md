# Onix Client

A PvP client for Minecraft **Java Edition 1.21.1** (Fabric), in two parts:

| Folder | Contents |
|---|---|
| `mod/` | The in-game client: **116 modules** with about 1,900 settings, the Onix menu, HUD editor, themes, cosmetics, profiles and a custom title screen |
| `launcher/` | Desktop launcher: Microsoft sign-in, profiles, import from other clients, install into other launchers, mod download, cosmetics, mods, themes |
| `server/` | Onix server: badge presence, coins, cosmetics shop and gifts (Node, no dependencies) |

> **About the name:** a well-known "Onix Client" already exists for Minecraft Bedrock Edition. This project is
> unrelated and uses its own logo and design. Pick your own name before publishing; it lives in
> `OnixClient.java` (`NAME`), `launcher/package.json` and the interface texts.

## In game

**Right Shift** opens the Onix menu.

- **Classic** (default): a window with a sidebar – All Modules, the seven categories, Favorites, Cosmetics,
  Profiles, Design and Settings. Press **Ctrl+F** or just start typing to search; the search also finds
  modules through their settings ("opacity" finds every module with an opacity setting).
- **Bloom**: a flower of categories as an alternative (*Design → Menu Style*).
- **Module cards**: click to toggle, right-click (or the settings count) to configure, star or middle-click to
  favorite. Filter by All / Enabled / Disabled / Server Rules. Arrow keys move between cards, Enter toggles,
  Shift+Enter opens the settings.
- **Keys**: every Onix key setting also accepts the middle and side mouse buttons.
- **Settings rows**: scroll on a slider or a mode control to step through values (Ctrl = bigger steps), click a
  slider's value to type an exact number, right-click any row to reset it.
- **Settings pages** are grouped into sections. Every module has its own **toggle key** and toggle notification.
  Controls: switches, sliders, segmented buttons and dropdowns, text fields, key binds and a full **color picker**
  (saturation/value, hue, opacity, 16 presets, *Theme Accent* and *Chroma*). Modified settings are marked with a dot;
  **right-click any setting to reset it**.
- **HUD editor**: drag to move, scroll to scale, double-click or right-click for settings, arrow keys to nudge,
  Delete to hide, **Ctrl+Z** to undo (also undoes *Reset Layout*).
  Elements snap to edges, the center and each other, and keep their place when the window size changes.
- **Design** page: themes, light mode, button style, menu style and size, title screen design and background,
  loading screens.
- **Custom themes**: *New Theme* or *Customize* on any theme, then pick accent, background, sky tint, particle color and
  style with live preview. Themes are files in `config/onix/themes` and are shared with the launcher.
- **Title screen backgrounds**: 13 built-in scenes (Dusk Ridge, Aurora, Golden Hour, Midnight Sea, Misty Pines,
  Cherry Grove, Dunes, Glacier, Lavender, Nether, The End, Deep Space, Onix Glow), your own PNGs from
  `config/onix/backgrounds`, an optional slideshow with crossfades and subtle motion.
- **Live preview**: settings pages of HUD modules show the element live while you change it.
- **Title screen**: five designs (Onix, Centered, Split, Minimal, Cards) – switch with ← →, by dragging sideways or
  with the dots at the bottom. Right Shift opens the Onix menu there too.
- **Loading screens**: game start and resource reloads, world loading, joining a server, loading terrain and saving
  all use the animated Onix loader (gem, orbit or minimal style, optional tips). Can be turned off under Design.
- **Profiles**: switch whole setups at once, and link a profile to a server so it loads when you join.
- **Notifications**: choose the corner (Settings → Notifications → Position); warnings are marked orange.
- **CWT mode**: the **CWT** button at the top of the Onix menu (or a key you bind under *Options → Controls →
  Onix Client*) switches to your clanwar setup and back. The first time it creates a profile called "CWT" from your
  current one, with every ⚠ module and distracting visuals off (Custom Sky, Atmosphere, Trail, Weather/Time Changer, Fullbright, Chunk
  Borders, Hitboxes, Freelook, Auto Tool, Kill Effects) and the usual PvP HUD on (FPS, Ping, CPS, Keystrokes, Armor,
  Potion Effects). After that it is a normal profile: whatever you change while CWT mode is on stays in it.
  Clicking the button again returns to the profile you came from; right-click opens the profile list.
- **Server rules**: modules that some servers or tournaments do not allow carry an orange ⚠ note – on their card,
  on their page (with the reason) and in the *Server Rules* filter. Under *Settings → Server Rules* you choose what
  happens when you join a server: a reminder (default), pausing them until you leave, or nothing. Servers you list
  under *Skip These Servers* are left alone. The notes are a guide – each server's own rules always decide.
- **End**: Panic – every module off; press again to restore them.

Every HUD module shares a common set of options (scale, background, border, corner radius, padding, text shadow,
font, anchor) on top of its own; text HUD modules add label style, colors, alignment and line spacing.

### Custom Sky

The **Custom Sky** module replaces the overworld sky with one of 18 sky boxes:

- **Day:** Clear Day, Morning Mist, Arctic Day, Golden Hour, Sunset Bloom, Pastel Dawn, Cotton Candy, Lavender Dusk, Ember Sky, Overcast, Thunderstorm
- **Night:** Starry Night, Midnight Clouds, Aurora, Emerald Night, Nebula, Deep Space, Blood Moon

Pick a **day sky** (click) and a **night sky** (right-click) in the gallery; they fade into each other with the time of
day. The vanilla sun and moon stay, the distance fog blends into the sky's horizon, and the sky can slowly rotate.

The launcher has the same gallery under **Settings → In-game sky** (day and night tabs). A choice there is written to
every profile and, if Minecraft is already running with Onix, shows up in the game right away.

Your own skies: PNG files in `config/onix/skies`, laid out as 3 × 2 cube faces (any size with a 3:2 ratio).
`tools/skies.py` generates the built-in ones and documents the exact face order.

### Onix badge

Players who use Onix get a small Onix gem next to their name – in nametags, the tab list and chat. You always see your
own badge. To see other Onix players, everyone needs to point the **Onix Badge** module at the same presence server
(*Onix Badge → Network → Badge Server*):

```bash
cd server
node index.js                  # or: docker build -t onix-presence . && docker run -p 8080:8080 onix-presence
```

The server keeps only UUID, name and last heartbeat in memory. With `VERIFY=1` (default) players prove account ownership
through Mojang's session check – the same one Minecraft servers use – so nobody can fake someone else's badge.
`Show Others I Use Onix` turns your own visibility off. Run it anywhere Node 18+ runs (VPS, Render, Railway, Fly.io);
serverless hosts that do not keep memory between requests are not suitable. Tests: `npm test`.
Behind a reverse proxy (nginx, Caddy, a platform router) set `TRUST_PROXY=1` so rate limits use the real client
address from `X-Forwarded-For`; without a proxy leave it off, because anyone can send that header.

### Cosmetics

**1,801 original cosmetics** in seven slots – wear one per slot, all at once:

| Slot | Items | Examples |
|---|---|---|
| Cape | 924 | 6 hand-drawn capes plus 34 animated patterns (flowing waves, flickering flames, twinkling stars, code rain, storm, circuit, nebula, dragon scales, emblems …) in 27 colour palettes |
| Head | 220 | crowns, top hats, witch and wizard hats, halos, horns, antlers, cat/fox/bunny ears, beanies, viking helmet, propeller cap |
| Face | 108 | glasses, shades, goggles, bandanas, ninja and masquerade masks, cyber visor, eyepatch, mustache |
| Neck | 72 | scarves, bow ties, ties, pendants, medals, neck bandanas |
| Back & Wings | 261 | feather, angel, bat, dragon, butterfly, fairy, crystal and mech wings (they flap, faster while gliding), backpack, jetpack, quiver, sword, shield |
| Aura | 156 | orbiting orbs and glyphs, ground rings, rune circles, rising sparks, embers, bubbles, hearts and notes, snowfall, falling leaves, spirals, star crown |
| Pet | 60 | shoulder buddies: cat, fox, owl, penguin, frog, bee, snail, mini dragon, ghost, slime cube, drone … |

In game: *Cosmetics* in the Onix menu – slot tabs, search (just start typing), colour filter, *Random*, *None*.
**Hover an item to try it on** the turning preview, click to wear it; models in the list are drawn in 3D and turn
while hovered. Capes need *Cape* enabled in *Skin Customization*; wings and backpacks are hidden while you wear an
elytra. Cosmetics are only visible to you.

**3D capes**: Onix capes are not flat boards. Each one is a cloth with thickness (8 x 12 segments) that hangs from a
collar, bends away from your back, flutters and has soft folds; walking, running, falling and sneaking swing it like the
vanilla cape, only more. The face is painted at four times the vanilla resolution with woven cloth, light from above,
raised edges between colours and – from Rare up – a stitched seam; the collar gets rivets (Rare), a gold band with a
gem (Epic) and a glowing gem (Legendary). The 3D cape replaces the vanilla one only for Onix capes; elytras still use
the cape's pattern. In the menu and the launcher the capes are shown in 3D as well.

**Wings**: feather and angel wings are built from single feathers in rows (coverts, secondaries and a fan of long
flight feathers with shafts); shoulder, elbow and wrist move one after another, and every flight feather flutters on
its own. Bat and dragon wings have finger bones with claws, membranes with a scalloped trailing edge, dragon spikes
and scales. Butterfly, fairy, mech (with pistons and flickering thrusters) and crystal wings got more detail too.
Models use six extra in-between shades per palette for this.

**Detail and animation**: models are built from many parts (about 23 cubes each on average) and the parts move on
their own – wings flap with moving tips, tails wag, eyes blink, ribbons and pendants sway, gems twinkle, flames
flicker, propellers spin, auras orbit, rise, fall and spiral. Cube faces get soft top-to-bottom shading. Every cape
pattern is animated (4–20 frames: waves flow, flames flicker, stars twinkle, rain falls, a band of light sweeps across).

Everything is generated from one catalog (`tools/cosmetics.py` → `catalog.json` in the mod and launcher). The mod and
the launcher use the same random numbers and maths, so the launcher preview shows exactly what you get in game (checked
pixel for pixel for every cape frame and position for position for every animated part). Older selections
(`hat`/`extra` from earlier versions) are converted automatically.

### Coins, shop and gifts

- **1 coin for every minute you play with Onix** (in a world – singleplayer or any server). The Onix server counts the
  time between the game's heartbeats itself, so coins cannot be faked by sending more requests; gaps over 3 minutes
  count as a break.
- **Prices**: Common 60 · Rare 150 · Epic 400 · Legendary 1,000 coins. The 14 original cosmetics (the first six capes,
  crown, top hat, halo, party hat, comb, both feather wings and the shoulder chicken) stay free for everyone.
- **Buy** in game (click a cosmetic with a price tag) or in the launcher; it is worn right away. *Owned* shows only
  what you have.
- **Gifts**: right-click a cosmetic (launcher) or use *Gift* in the dialog (game), enter a Minecraft name and an
  optional message (up to 80 characters). The player gets it immediately and sees a notification with your message the
  next time they play or open the launcher. They need to have played with Onix on the same server once.
- **Unlimited coins**: `Virtualchickenfr` and `Fuzed_d` have unlimited coins (∞) – they can buy and gift everything.
  Change the list with `UNLIMITED=name,name` on the server. It only applies while the server checks accounts with
  Mojang (`VERIFY=1`, the default), otherwise anyone could simply use those names.
- Coins need an **Onix server** (the same one as for the badge, see below): set it in the launcher (*Settings → Onix
  Server*, also used in game) or in game under *Settings*. Without a server only the free cosmetics can be worn.
- The server keeps coins, purchases and gifts in `server/data/economy.json` (`DATA_FILE`); `ECONOMY=0` turns coins off.
  The game keeps a copy of what you own for offline starts. Purchased cosmetics are still only visible to you.

**Cosmetica** (optional): the free [Cosmetica](https://modrinth.com/mod/cosmetica) mod can run next to Onix – the
launcher's Cosmetics page adds or removes it in the active profile with one click. Its cosmetics stay on Cosmetica's
own servers and are chosen in its own menu; players who also use Cosmetica see them. While it is installed, the
Cosmetica cape is shown instead of the Onix cape (*Use Onix cape* / *Use Cosmetica cape* at the bottom of the cape tab).

### Modules (116)

⚠ = may not be allowed on every server (see *Server rules* above).

<details>
<summary><b>HUD</b> (45)</summary>

| Module | What it does |
|---|---|
| Air | Remaining breath underwater. |
| Armor Status | Your armor and held items with durability. |
| Biome | The biome you are standing in. |
| Block Info | Details about the block you are looking at. |
| Chunk Info | Chunk and region coordinates, handy for farms and builds. |
| Clock | Real-world time. |
| Compass | A horizontal compass strip. |
| Coordinates | Your position, direction and nether coordinates. |
| Countdown | A timer that counts down and alerts you. |
| CPS | Clicks per second for left and right mouse button. |
| Custom Text | Your own text with live placeholders. |
| Date | Today's date. |
| Day Counter | How many Minecraft days have passed in this world. |
| Death Coordinates | Remembers where you died so you can get your items back. |
| Direction | Cardinal direction plus exact yaw and pitch. |
| Durability Warning | Alerts you before your gear breaks. |
| Elytra Info | Speed, altitude, pitch and durability while gliding. |
| Experience | Level, progress and points. |
| Fall Distance | How far you have fallen – turns red when it will hurt. |
| FPS | Frames per second with optional min, max and average. |
| Frame Graph | Live graph of frame times to spot stutters. |
| Game Time | Minecraft time of day as a clock. |
| Health | Your exact health as a number. |
| Held Item | Name, durability and total count of the item in your hand. |
| Hunger | Food level, hidden saturation and exhaustion. |
| Inventory Viewer | Your inventory on screen at all times. |
| Keystrokes | Shows WASD, mouse buttons and space bar as you press them. |
| Light Level | Block and sky light where you stand. |
| Memory | Java memory (RAM) used by the game. |
| Module List | A list of all enabled modules. |
| Moon Phase | Current moon phase. |
| Mount Info | Speed, jump height and health of the horse you ride. |
| Pack Display | Which resource packs are active. |
| Pickup Notifier | Shows the items you just picked up. |
| Ping | Your latency to the server. |
| Player Count | Number of players in the tab list. |
| Potion Effects | Active effects with level and time left. |
| Server Address | The server you are playing on. |
| Session Time | How long you have been playing. |
| Speed | How fast you are moving. |
| Stopwatch | Time anything with a key – parkour, speedruns, farms. |
| TPS | Estimated server ticks per second (20 = no lag). |
| Watermark | Client name with optional FPS and time. |
| Waypoints | Mark places with a key and see how far away they are. |
| Weather | Current weather in the world. |

</details>

<details>
<summary><b>Combat</b> (24)</summary>

| Module | What it does |
|---|---|
| Arrow Counter | How many arrows you carry. |
| Block Counter | Placeable blocks you carry – great for bridging. |
| Combo Counter | Hits in a row without getting hit back. |
| Cooldown Indicator | Shows your attack cooldown under the crosshair. |
| Crosshair | A custom crosshair with styles, colors and dynamic gap. |
| Custom Item Counter | Count any item you choose. |
| Damage Flash | Screen flash when you take damage. |
| Golden Apple Counter | Golden apples in your inventory. |
| Hit Effects | Extra particles every time you hit. |
| Hit Sound | Plays a sound whenever you land a hit. |
| Item Cooldowns | Cooldown bars for pearls, shields and more. |
| Kill Effects | Particles and a sound when your target dies. |
| Low Health Warning | Red screen edges when you are about to die. |
| No Hurt Cam | Less camera shake when hit, and calmer screen effects. |
| Pearl Counter | Ender pearls in your inventory. |
| Potion Counter | Healing or other potions in your inventory. |
| Reach Display | Distance of your last hit. |
| Resource Counter | Iron, gold, diamonds or emeralds you carry – for Bedwars and more. |
| Session Stats | Kills, deaths and K/D since you started playing. |
| Target HUD ⚠ | Health and info about the player you are fighting. |
| Toggle Sneak | Tap sneak once instead of holding it. |
| Toggle Sprint | Tap sprint once instead of holding it. |
| Totem Counter | Totems of Undying in your inventory. |
| Totem Warning | Reminds you to put a totem in your off hand. |

</details>

<details>
<summary><b>Visual</b> (17)</summary>

| Module | What it does |
|---|---|
| Atmosphere | Animated sky tint, color grading and ambient particles. |
| Block Outline | Color and fill for the block selection outline. |
| Boss Bar | Hide, move, fade or resize boss bars. |
| Chunk Borders | Show chunk borders without F3 + G, in your own colors. |
| Custom Sky | Pick from many skies, with separate day and night skies. |
| Fullbright ⚠ | See in caves and at night without torches. |
| Hand View Model | Position, size and rotation of held items. |
| Hitboxes ⚠ | Entity hitboxes with colors per type. |
| Low Fire | Less fire covering your screen. |
| Nametags ⚠ | Your own nametag in F5 and health next to names. |
| Onix Badge | An Onix logo next to every player who uses Onix – including you. |
| Overlays ⚠ | Remove pumpkin blur, portal swirl and other overlays. |
| Scoreboard | Hide, move, fade or shrink the sidebar scoreboard. |
| Tab List | Numeric, colored ping in the player list. |
| Time Changer | Set your own time of day and moon – client-side only. |
| Trail | Leave a particle trail behind you. |
| Weather Changer | Choose the weather you see – client-side only. |

</details>

<details>
<summary><b>Camera</b> (6)</summary>

| Module | What it does |
|---|---|
| Camera Tweaks ⚠ | Third-person distance, clipping and shoulder view. |
| FOV Effects | Stop the FOV from changing when you sprint and set fixed FOVs. |
| Freelook ⚠ | Look around your character while moving straight. |
| Perspective Key | Quick look behind or at yourself, and a custom F5 cycle. |
| Sensitivity Switch | A second mouse sensitivity on a key. |
| Zoom | Zoom in with a key; scroll to adjust. |

</details>

<details>
<summary><b>Chat</b> (7)</summary>

| Module | What it does |
|---|---|
| Auto GG ⚠ | Sends \"gg\" automatically when a game ends. |
| Chat Cleaner | Hide repeated messages, empty and separator lines. |
| Chat Filter | Hide or censor messages with certain words. |
| Chat History & Size | Scroll back further than 100 lines and size the chat box. |
| Chat Macros ⚠ | Bind messages and commands to keys. |
| Chat Mentions | Highlight and sound when someone mentions you. |
| Chat Timestamps | Shows when each message arrived. |

</details>

<details>
<summary><b>Utility</b> (12)</summary>

| Module | What it does |
|---|---|
| Auto Reconnect ⚠ | Reconnect button and automatic reconnect after a kick. |
| Auto Respawn ⚠ | Skip the death screen. |
| Auto Screenshot | Saves a screenshot when chat says your words ("victory", "you won" …), with delay, cooldown and optional hidden HUD. |
| Auto Tool ⚠ | Picks the best tool from your hotbar while mining. |
| Copy Coordinates | One key copies your position to the clipboard. |
| Extra Keybinds | Up to two extra keys or mouse buttons for every vanilla action (movement, attack/use, drop, inventory, hotbar slots, chat …); warns when a key is already used. |
| Friends | Friend list: join/leave alerts, who is online when you join, a star next to their chat messages. |
| Item Tooltips | More information in item tooltips. |
| Keybind Profiles | Save whole key layouts (PvP, building, elytra …), apply them with a click or a next/previous key, optionally linked to the Onix profile of the same name. |
| Panic | Turn every module off instantly with one key. |
| Streamer Mode | Hides coordinates, waypoints, server address and the F3 screen while you stream. |
| Tab-out Alerts | Flashes the taskbar and plays a sound when a game starts, someone says your name or you take damage while tabbed out. |

</details>

<details>
<summary><b>Performance</b> (5)</summary>

| Module | What it does |
|---|---|
| Background Mute | Mute or lower sound when you tab out. |
| Entity Distance | Render entities closer or further away. |
| Performance Preset | One switch for FPS-friendly video settings. |
| Render Distance Switch | Toggle between two render distances. |
| Unfocused FPS | Saves power by limiting FPS when you tab out. |

</details>

Atmosphere, Weather Changer, Time Changer, Trail, Hit Effects and cosmetics are only visible to you.
Auto Tool is not allowed on some servers. Onix contains no cheats: no reach, aim, auto-clicking or x-ray.

## Launcher

- **Home**: pick a profile and play; Java 21, Fabric, Fabric API and Sodium are installed automatically.
- **Command palette**: **Ctrl+K** (or the search box at the top) – type to play, open a page, switch profile or
  account, pick a theme, background or in-game sky, flip settings or open folders. Shortcuts: **Ctrl+Enter** play,
  **Ctrl+1…8** pages, **Ctrl+,** settings (⌘ on macOS).
- **Drag & drop**: drop `.jar` files anywhere on the launcher – Fabric mods for 1.21.1 go into the active profile,
  anything else is skipped with the reason.
- **Accounts**: every Microsoft account that signs in is remembered. Click your name at the bottom left to see how
  many accounts have signed in, switch between them with one click (no new sign-in), add another or remove one.
  Tokens are stored encrypted with the operating system's key store.
- **Fast start** (Settings, on by default): the Minecraft session is reused for a few hours and renewed in the
  background when the launcher opens; sign-in, Java and game files are checked at the same time; Fabric API and
  Sodium are checked for updates twice a day instead of on every start; Java keeps a class-data cache between
  starts; G1 is tuned for short pauses.
- **Profiles**: each one has its own folder (mods, worlds, settings, cosmetics), memory and quick-join server.
- **Cosmetics**: all 1,801 items with tabs per slot, search, tier, colour and *Owned/Shop* filters. A mannequin shows the
  whole outfit and turns by dragging; hovering an item tries it on (it turns around for capes and wings). Your coins are
  shown at the top; click a price tag to buy, right-click to gift. *Random Outfit* (from what you own) and *Remove All*;
  the list on the left shows what you wear. Applies to the active profile.
- **Mods per profile** (*Mods* on a profile card, or *Add from Modrinth* on the Mods page):
  - *Browse Modrinth* searches Fabric mods for 1.21.1 (sort by relevance, downloads, followers, newest, updated) and
    installs them with all required dependencies. Every download is checked against Modrinth's SHA-512/SHA-1 hashes;
    Fabric API and Sodium are left to the launcher, so nothing is installed twice.
  - *Installed* lists every mod with its Modrinth name and version, turns mods on/off, removes them and checks
    for updates (*Update all* or one by one; a disabled mod stays disabled).
- **Modpacks** (Profiles page): Fabric 1.21.1 modpacks from Modrinth – one click creates a new profile with the pack
  and Onix.
- **Export** (on a profile card):
  - *Modrinth pack* (`.mrpack`) for the Modrinth App, Prism Launcher, ATLauncher, GDLauncher and other Onix launchers.
    Mods that are on Modrinth become download links, everything else (your own jars, Onix itself) is packed inside.
  - *CurseForge pack* (`.zip`) for the CurseForge App, with every mod packed inside.
  - *Into another launcher*: copies Onix, Fabric API, every mod and your Onix settings straight into an installed
    Fabric 1.21.1 instance (Prism, MultiMC, Modrinth App, CurseForge, ATLauncher or the official launcher). Mods the
    instance already has keep the instance's version.
  - Choose what to include: mod/Onix settings, options and keys, server list, resource packs, shader packs.
- **Import Pack** (Profiles page): `.mrpack` and CurseForge `.zip` files become a new profile. Packs for another
  Minecraft version or for Forge/NeoForge/Quilt are refused with the reason. Downloads only come from the hosts the
  Modrinth format allows (cdn.modrinth.com, GitHub, GitLab) and are hash-checked; file paths that would leave the
  profile folder are refused. CurseForge packs that list mods only by ID (their downloads need CurseForge's own API)
  bring their settings – open such a pack in the CurseForge App once and use *Import* to take the mods over.
- Other clients (Lunar, Feather, Badlion, LabyMod, Prism …) are still handled by the *Import* page.
- **Import** – move over from other clients and launchers:

  | Source | Searched in |
  |---|---|
  | Lunar Client | `.minecraft` + `~/.lunarclient/settings` |
  | Feather Client | `%APPDATA%/.feather` |
  | Dawn | `%APPDATA%/.dawn` and similar (or *Choose Folder*) |
  | Badlion | `.minecraft` + `%APPDATA%/Badlion Client` |
  | LabyMod | `.minecraft/labymod-neo` |
  | Minecraft Launcher | `.minecraft` |
  | Prism, MultiMC, Modrinth App, CurseForge, ATLauncher | every instance |

  Copied over: **settings including key binds**, **server list**, **worlds**, **resource and shader packs**,
  **screenshots**, **compatible Fabric mods** (checked against 1.21.1) with their **configs**, and – in beta –
  which **client modules** were enabled in Lunar, Feather, Badlion or LabyMod (matched to the Onix modules).
  Mods from Dawn's **`.dawnpack`** files are imported as well.
  Existing files are backed up as `*.before-import` first.

  Cosmetics from other clients belong to your account there and cannot be transferred; Forge and OptiFine mods
  are skipped. If a client lives somewhere else, use **Choose Folder**.

## Install page (Onix outside the Onix Launcher)

The launcher's **Install** page brings Onix into other installations:

- **Running now** – lists every Minecraft that is running (version, Fabric or not, folder, account).
  - Already has Onix → **Send Profile** copies your Onix profiles and cosmetics into that game and loads them
    **live, without a restart** (the mod picks up `config/onix/inbox.json` within a second).
  - Fabric 1.21.1 without Onix → **Install for Next Start**; restart Minecraft afterwards.
  - Onix is never injected into a running game. A Fabric mod has to be present when Minecraft starts, and
    runtime injection is what ghost clients use to hide from anti-cheat – Onix stays a normal, visible mod.
- **Other launchers** – one click installs Onix (and Fabric API) into the official Minecraft Launcher
  (adds Fabric plus an "Onix Client" installation with its own folder), Prism, MultiMC, Modrinth App, CurseForge
  and ATLauncher instances. Incompatible instances (other versions, no Fabric) are marked. Your Onix profiles come
  along; files that would be replaced are backed up in `config/onix/backups/`.
- **Download the mod** – saves `onix-client-*.jar` (optionally with Fabric API) into a folder of your choice
  for manual installation into any Fabric 1.21.1 `mods` folder.

## Assets

The title screen backgrounds, sky boxes and cosmetics are generated, not drawn by hand: `tools/backgrounds.py`,
`tools/skies.py` (Python 3, NumPy, Pillow) and `tools/cosmetics.py` (Python 3 only). Run them to tweak colors or add
new scenes and cosmetics:

```bash
python3 tools/backgrounds.py out/            # 1280x720 PNG + JPG per scene
python3 tools/skies.py out/ 384              # 3x2 cube-face atlas per sky, face size 384 px
python3 tools/cosmetics.py                   # writes catalog.json into the mod and the launcher
```

## Building

### On GitHub
1. Push the `onix-client` folder to a GitHub repository.
2. **Actions** runs "Build Onix Client".
3. **Artifacts** contains the Windows installer, the macOS DMG, the Linux AppImage and the mod jar.

### Releases and automatic updates
The launcher looks for a new version **every time it starts** (and every 6 hours while it stays open):
- **Windows and Linux (AppImage)**: the update downloads in the background (pill at the top: *Downloading update*).
  When it is ready, *Update … ready · Restart* installs it right away; otherwise it installs when you close the
  launcher. It does not restart while Minecraft is running.
- **macOS**: apps without an Apple developer signature cannot replace themselves, so the launcher shows
  *Update … available* and opens the download page.
- *Settings → Launcher updates* shows the state and has *Check now*.
- The Onix mod is inside the launcher, so every launcher update also updates the mod in all profiles on the next
  start. Profiles, settings, worlds, cosmetics and coins stay.

To publish a version, raise `version` in `launcher/package.json` (for example `1.0.1` → `1.0.2`) and push to
`main` – the workflow sees that this version has no release yet and publishes one. *Actions → Build Onix Client →
Run workflow* with a version, or pushing a tag (`git tag v1.1.0 && git push origin v1.1.0`), does the same. The workflow
builds everything, collects it in a draft release, adds the mod jar and then publishes the release – only then do
launchers see it. Launcher and mod get the same version. Pushes that keep the version only build (Actions → Artifacts).

The workflow needs *Settings → Actions → General → Workflow
permissions → Read and write*; it sets the repository in `package.json` so the launcher knows where to look.
Launchers built before this feature still need to be updated by hand once.

### Locally
You need **Java 21 (JDK)** and **Node.js 20+**.

```bash
cd mod
./gradlew build          # Windows: gradlew.bat build → mod/build/libs/onix-client-1.0.0.jar
./gradlew runClient      # start Minecraft with the mod for testing

cd ../launcher
npm install
npm start                # run the launcher
npm run dist             # build installers → launcher/dist/
```

Fabric versions live in `mod/gradle.properties`; current values for 1.21.1 are at <https://fabricmc.net/develop>.

## Files

- Mod settings: `config/onix/` – `client.json`, `profiles/*.json`, `cosmetics.json`, `themes/*.json`,
  `backgrounds/*.png` and `skies/*.png` (your own), `backups/` (made by the launcher before replacing files)
- Launcher game folder: `%APPDATA%/.onix` (Windows), one folder per profile in `profiles/<id>`
- Font: Inter (SIL Open Font License, license included)

## Writing a module

Create a class in `mod/src/main/java/dev/onix/client/module/impl/<category>/` that extends `Module`,
`HudModule`, `TextHudModule` or `ItemCounterModule`, declare settings as fields with `add(...)`, and register it in
`ModuleManager.registerDefaults()`. The menu, search, profiles, toggle keys and saving work automatically.

```java
public class ExampleModule extends Module {
    private final NumberSetting size = add(new NumberSetting("size", "Size", 4, 1, 10, 0.5).unit("px"));
    private final BoolSetting outline = add(new BoolSetting("outline", "Outline", true)
            .describe("Shown as a tooltip and in search."));

    {
        group("Colors"); // following settings go into a "Colors" section
    }

    private final ColorSetting color = add(ColorSetting.accent("color", "Color").visibleIf(outline::isOn));

    public ExampleModule() {
        super("example", "Example", "One line about what it does.", Category.VISUAL, false);
        tags("search", "words");
    }
}
```

To let the importer recognise the module, add its names from other clients to `MODULE_ALIASES` in
`launcher/lib/importer.js`.

## License

Onix Client is free and open source under the [MIT License](LICENSE). The Inter font keeps its own SIL Open Font
License (included next to the font files).

## Privacy

No analytics, tracking or advertising. The launcher and the mod only talk to Microsoft/Minecraft, Fabric, Modrinth,
Adoptium, GitHub (launcher updates, can be turned off), mc-heads.net and – only if you set one – your Onix server.
The full list, with what is sent and why, is in [PRIVACY.md](PRIVACY.md). The Windows installer shows this too.

## Code signing policy

Free code signing provided by [SignPath.io](https://about.signpath.io), certificate by
[SignPath Foundation](https://signpath.org).

- Committers and reviewers: [virtualchickenfr](https://github.com/virtualchickenfr)
- Approvers: [virtualchickenfr](https://github.com/virtualchickenfr)

Only installers built by this repository's GitHub workflow from its own source are signed, and every signing
request is approved by hand. Privacy policy: see [PRIVACY.md](PRIVACY.md).

**Setting up signing** (once): after SignPath has accepted the project, add the GitHub.com trusted build system to
the SignPath project, use [.signpath/artifact-configuration.xml](.signpath/artifact-configuration.xml) as its artifact
configuration, and in the GitHub repository add the secret `SIGNPATH_API_TOKEN` and the variable
`SIGNPATH_ORGANIZATION_ID` (*Settings → Secrets and variables → Actions*); `SIGNPATH_PROJECT_SLUG` and
`SIGNPATH_POLICY_SLUG` are optional (defaults `onix-client` and `release-signing`). From then on every release waits
for the approval in SignPath and publishes the signed Windows installer. Until then releases are unsigned.
