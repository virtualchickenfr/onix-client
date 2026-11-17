'use strict';

/*
 * The look: one fixed colour set (deep navy, blue accent, Poppins – one.css) and three designs that set the scene of
 * the start page, the start screen and the main menu in game:
 *   water  – you sink through deep water, light falling in from above (js/fall.js)
 *   runner – a dark studio, light from the top right, your character runs in
 *   meadow – thousands of flowers on a grey meadow; on Play every petal flies up
 * The scenes themselves are in js/scenes.js.
 */

const DESIGNS = [
  { id: 'water', name: 'Water', about: 'You sink through deep, clear water; light falls in from above.', game: 'Water' },
  { id: 'runner', name: 'Runner', about: 'A dark studio with light from above – your character runs in.', game: 'Runner' },
  { id: 'meadow', name: 'Meadow', about: 'Thousands of flowers on a grey meadow. Press Play and the petals fly up.', game: 'Meadow' },
];
let currentDesign = 'water';
const designById = (id) => DESIGNS.find((d) => d.id === id) || DESIGNS[0];

/** The fixed colours (kept here too because older code reads them from the style). */
function applyTheme() {
  const r = document.documentElement.style;
  for (const k of ['--accent', '--on-accent', '--accent-soft', '--sky', '--particle', '--bg', '--s1', '--s2', '--s3', '--s4']) r.removeProperty(k);
  currentThemeId = 'onix';
}

/** Switches the design everywhere: start page scene, settings cards, and (through the main process) the game. */
async function selectDesign(id, save = true) {
  currentDesign = designById(id).id;
  document.body.dataset.design = currentDesign;
  renderDesigns();
  if (typeof Scenes !== 'undefined') Scenes.home(currentDesign);
  if (save) await api.setSettings({ design: currentDesign });
}

function designCard(d, onPick) {
  return el('button', {
    class: `design-card${d.id === currentDesign ? ' selected' : ''}`,
    'data-design': d.id,
    onclick: () => onPick(d),
  }, [
    el('div', { class: `design-thumb ${d.id}` }, [el('span', { class: 'design-check' }, [icon('check')])]),
    el('div', { class: 'design-text' }, [el('b', { text: d.name }), el('span', { text: d.about })]),
  ]);
}

function renderDesigns() {
  const box = $('#designs');
  if (!box) return;
  box.replaceChildren(...DESIGNS.map((d) => designCard(d, (x) => selectDesign(x.id).then(() => toast(`${x.name} is now your design – here and in game`)))));
}
