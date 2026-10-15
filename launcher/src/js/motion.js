'use strict';

/* Small pointer effects: the home artwork follows the mouse a little, and cards light up
   where the pointer is. Both stay off when the system asks for reduced motion. */

const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;

if (!reducedMotion) {
  const hero = $('.hero');
  hero.addEventListener('mousemove', (e) => {
    const r = hero.getBoundingClientRect();
    hero.style.setProperty('--px', ((e.clientX - r.left) / r.width - 0.5).toFixed(3));
    hero.style.setProperty('--py', ((e.clientY - r.top) / r.height - 0.5).toFixed(3));
  });
  hero.addEventListener('mouseleave', () => {
    hero.style.setProperty('--px', '0');
    hero.style.setProperty('--py', '0');
  });
}

// spotlight: cards get the pointer position as CSS variables
document.addEventListener('pointermove', (e) => {
  const card = e.target.closest && e.target.closest('.home-card, .bg-tile, .theme');
  if (!card) return;
  const r = card.getBoundingClientRect();
  card.style.setProperty('--mx', `${e.clientX - r.left}px`);
  card.style.setProperty('--my', `${e.clientY - r.top}px`);
});
