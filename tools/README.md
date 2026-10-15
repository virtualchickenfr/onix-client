# Asset tools

Generators for the artwork that ships with Onix. Requires Python 3, NumPy and Pillow (`cosmetics.py` needs only Python 3).

| Script | Output |
|---|---|
| `backgrounds.py OUT [scene …]` | Title screen backgrounds, 1280×720, as PNG (mod) and JPG (launcher). |
| `skies.py OUT [face_size] [sky …]` | Sky boxes for the Custom Sky module: one 3×2 cube-face atlas per sky plus `skies.json` with each sky's horizon color. |
| `cosmetics.py` | The cosmetics catalog (palettes, cape patterns, cube models, items). Writes `catalog.json` straight into the mod and the launcher plus `catalog.js` for the launcher page – no copying needed. |

Copy the results to:

- `mod/src/main/resources/assets/onix/textures/gui/backgrounds/` (PNG) and `launcher/src/assets/backgrounds/` (JPG)
- `mod/src/main/resources/assets/onix/textures/sky/` (atlas) and `textures/gui/skies/` (thumbnail), plus
  `launcher/src/assets/skies/` (JPG); add new skies to `render/Skies.java` (with the horizon color) and `SKIES` in
  `launcher/src/js/data.js`

Every sky pixel is computed from its 3D view direction, using the same face orientation as the mod's renderer
(vanilla's End sky box), so the faces always meet without seams.
