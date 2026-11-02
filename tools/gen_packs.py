#!/usr/bin/env python3
"""
Generates the game-mode module packs (mod-1.21.11/.../module/impl/pack/Pack*.java): about 66 modules for each of
Crystal PvP, Sword PvP, Mace PvP, NethPot, Diamond SMP, Survival, Bedwars, Skywars, UHC and Bridge/Parkour, plus 100
visual effects. Only information, reminders and visuals – nothing plays for you.

    python3 tools/gen_packs.py [path to Items.java of the oldest supported version]

Kinds: item counters, info lines (Java expressions using P.*), alerts, event statistics and screen effects.
Every id is unique ("gm2_<mode>_<name>"); effects use each (event, style, particle) only once across all packs.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'mod-1.21.11/src/main/java/dev/onix/client/module/impl/pack')
ITEMS_JAVA = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/mc-1.21.6/net/minecraft/world/item/Items.java'
ITEMS = set(re.findall(r'public static final Item ([A-Z0-9_]+) =', open(ITEMS_JAVA).read())) if os.path.exists(ITEMS_JAVA) else None

# items that already have a counter (existing modules and pack 1)
USED_ITEMS = set('''END_CRYSTAL OBSIDIAN RESPAWN_ANCHOR GLOWSTONE EXPERIENCE_BOTTLE ENCHANTED_GOLDEN_APPLE CHORUS_FRUIT SHULKER_BOX ENDER_CHEST
COBWEB TNT WIND_CHARGE SNOWBALL EGG FIREWORK_ROCKET WATER_BUCKET LAVA_BUCKET GOLDEN_CARROT COOKED_BEEF BREAD WHITE_WOOL LADDER SCAFFOLDING
TORCH COAL REDSTONE SLIME_BALL BONE_MEAL WHEAT SUGAR_CANE ENDER_EYE BLAZE_ROD NETHER_WART GUNPOWDER STRING LEATHER PAPER BOOK NAME_TAG
AMETHYST_SHARD COPPER_INGOT QUARTZ ANCIENT_DEBRIS NETHERITE_SCRAP DIAMOND_BLOCK SHULKER_SHELL PHANTOM_MEMBRANE BREEZE_ROD MILK_BUCKET
ARROW GOLDEN_APPLE ENDER_PEARL POTION TOTEM_OF_UNDYING IRON_INGOT GOLD_INGOT DIAMOND EMERALD NETHERITE_INGOT LAPIS_LAZULI'''.split())

EVENTS = {
    'HIT': ('HIT!', '⚔'), 'CRIT': ('CRIT!', '✦'), 'KILL': ('KILL!', '☠'), 'DOUBLE_KILL': ('DOUBLE KILL', '☠'),
    'TRIPLE_KILL': ('TRIPLE KILL', '☠'), 'COMBO3': ('COMBO x3', '★'), 'COMBO5': ('COMBO x5', '★'), 'COMBO10': ('COMBO x10', '★'),
    'SELF_POP': ('POPPED!', '✚'), 'ENEMY_POP': ('POP!', '✚'), 'DAMAGE': ('OUCH', '♥'), 'LOW_HP': ('LOW HP', '♥'),
    'DEATH': ('WASTED', '☠'), 'JUMP': ('JUMP', '⬆'), 'BIG_LAND': ('LANDED', '⬇'), 'SPRINT': ('GO!', '»'),
    'PEARL': ('PEARL', '◉'), 'CRYSTAL': ('BOOM', '✦'), 'BLOCK': ('PLACE', '■'), 'EAT': ('YUM', '✿'),
    'LEVEL_UP': ('LEVEL UP', '✪'), 'HEAL': ('HEAL', '✚'), 'BOW': ('SHOT', '➶'), 'WIND_CHARGE': ('WHOOSH', '≈'),
    'ROCKET': ('BOOST', '➹'), 'GAPPLE': ('GAPPLE', '✪'), 'POTION': ('SPLASH', '✿'), 'ARMOR_BREAK': ('ARMOR BROKE', '✖'),
}
STYLE_NAMES = {'FLASH': 'Flash', 'VIGNETTE': 'Glow', 'TEXT': 'Text', 'HITMARKER': 'Hit Marker', 'RING': 'Ring', 'CONFETTI': 'Confetti',
               'SPEEDLINES': 'Speed Lines', 'SYMBOLS': 'Symbols', 'SOUND': 'Sound', 'SHAKE_TEXT': 'Shake Text', 'BORDER': 'Border',
               'PARTICLES': 'Particles'}
PARTICLES = {'HEART': 'Hearts', 'CRIT': 'Crit Stars', 'ENCHANTED_HIT': 'Magic Sparks', 'TOTEM_OF_UNDYING': 'Totem Burst', 'FLAME': 'Flames',
             'SOUL_FIRE_FLAME': 'Soul Flames', 'END_ROD': 'Light Sparks', 'NOTE': 'Music Notes', 'HAPPY_VILLAGER': 'Green Sparkles',
             'ANGRY_VILLAGER': 'Rage Clouds', 'FIREWORK': 'Fireworks', 'SNOWFLAKE': 'Snowflakes', 'CLOUD': 'Clouds', 'POOF': 'Poof',
             'EXPLOSION': 'Explosion', 'WITCH': 'Purple Magic', 'GLOW': 'Glow Dots', 'ELECTRIC_SPARK': 'Electric Sparks',
             'SCULK_SOUL': 'Souls', 'SOUL': 'Soul Wisps', 'SWEEP_ATTACK': 'Sweep', 'PORTAL': 'Portal Dust', 'LAVA': 'Lava Pops',
             'SMALL_FLAME': 'Embers', 'WAX_ON': 'Golden Shine', 'WAX_OFF': 'White Shine', 'SCRAPE': 'Teal Shine',
             'DAMAGE_INDICATOR': 'Damage Hearts', 'GUST': 'Gusts', 'SONIC_BOOM': 'Sonic Boom'}
SOUNDS = {'TEXT': 'SoundEvents.PLAYER_ATTACK_CRIT', 'SOUND': None}
STYLE_COLORS = {'FLASH': 0x55FFFFFF, 'VIGNETTE': 0xFFFF3355, 'TEXT': 0xFFFFD84D, 'HITMARKER': 0xFFFFFFFF, 'RING': 0xFF8B7CFF,
                'CONFETTI': 0xFFFFFFFF, 'SPEEDLINES': 0xFFFFFFFF, 'SYMBOLS': 0xFFFF7AA8, 'SOUND': 0xFFFFFFFF, 'SHAKE_TEXT': 0xFFFF4D4D,
                'BORDER': 0xFF8B7CFF, 'PARTICLES': 0xFFFFFFFF}
EVENT_SOUNDS = ['SoundEvents.EXPERIENCE_ORB_PICKUP', 'SoundEvents.PLAYER_LEVELUP', 'SoundEvents.AMETHYST_BLOCK_CHIME', 'SoundEvents.ANVIL_LAND',
                'SoundEvents.FIREWORK_ROCKET_BLAST', 'SoundEvents.FIREWORK_ROCKET_TWINKLE', 'SoundEvents.ARROW_HIT_PLAYER', 'SoundEvents.TOTEM_USE',
                'SoundEvents.BEACON_ACTIVATE', 'SoundEvents.PLAYER_ATTACK_STRONG', 'SoundEvents.PLAYER_ATTACK_SWEEP', 'SoundEvents.UI_TOAST_CHALLENGE_COMPLETE',
                'SoundEvents.NOTE_BLOCK_PLING.value()', 'SoundEvents.NOTE_BLOCK_BELL.value()', 'SoundEvents.NOTE_BLOCK_CHIME.value()',
                'SoundEvents.NOTE_BLOCK_BIT.value()', 'SoundEvents.NOTE_BLOCK_XYLOPHONE.value()', 'SoundEvents.NOTE_BLOCK_HARP.value()']
SOUND_NAMES = ['Ding', 'Level Up', 'Chime', 'Anvil', 'Boom', 'Twinkle', 'Bow Ding', 'Totem', 'Beacon', 'Smack', 'Swoosh', 'Challenge',
               'Pling', 'Bell', 'Bells', '8-Bit', 'Xylophone', 'Harp']

USED_EFFECTS = set()
IDS = set()


def jstr(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'


def slug(s):
    return re.sub(r'[^a-z0-9]+', '', s.lower())


class Mode:
    def __init__(self, key, title, tags, category='COMBAT'):
        self.key, self.title, self.tags, self.category = key, title, tags, category
        self.lines = []
        self.counters = 0
        self.infos = 0

    def _id(self, name):
        i = f'gm2_{self.key}_{slug(name)}'
        assert i not in IDS, i
        IDS.add(i)
        return i

    def _tags(self, extra=()):
        return ', '.join(jstr(t) for t in list(self.tags) + list(extra))

    def counter(self, name, items, desc, icon=None, tags=()):
        items = [i for i in items.split() if ITEMS is None or i in ITEMS]
        items = [i for i in items if i not in USED_ITEMS or icon == 'keep']
        if not items:
            return
        for i in items:
            USED_ITEMS.add(i)
        match = 's -> P.isItem(s, ' + ', '.join('Items.' + i for i in items) + ')'
        self.lines.append(f'new Kinds.Counter({jstr(self._id(name))}, {jstr(name)}, {jstr(self.title + ": " + desc)}, Items.{items[0]}, {match}, '
                          f'{self.counters}, {self._tags(tags)})')
        self.counters += 1

    def counter_tag(self, name, tag, icon, desc, tags=()):
        self.lines.append(f'new Kinds.Counter({jstr(self._id(name))}, {jstr(name)}, {jstr(self.title + ": " + desc)}, Items.{icon}, '
                          f's -> s.is(ItemTags.{tag}), {self.counters}, {self._tags(tags)})')
        self.counters += 1

    def info(self, name, label, expr, desc, cat=None, tags=()):
        self.lines.append(f'new Kinds.Info({jstr(self._id(name))}, {jstr(name)}, {jstr(self.title + ": " + desc)}, Category.{cat or self.category}, '
                          f'{jstr(label)}, () -> {expr}, {self.infos}, {self._tags(tags)})')
        self.infos += 1

    def stat(self, event, kind, cat=None):
        label = EVENTS and {'SESSION': event_label(event) + 's', 'PER_MINUTE': event_label(event) + 's', 'SINCE': 'Last ' + event_label(event)}[kind]
        name = {'SESSION': f'{event_label(event)} Counter', 'PER_MINUTE': f'{event_label(event)}s per Minute', 'SINCE': f'Time Since {event_label(event)}'}[kind]
        desc = {'SESSION': f'how many times this happened this session ({event_label(event)}).', 'PER_MINUTE': f'{event_label(event)}s in the last minute.',
                'SINCE': f'how long ago the last {event_label(event).lower()} was.'}[kind]
        self.lines.append(f'new Kinds.Stat({jstr(self._id(name))}, {jstr(name)}, {jstr(self.title + ": " + desc)}, Category.{cat or self.category}, '
                          f'{jstr(label)}, Events.Event.{event}, Kinds.StatKind.{kind}, {self.infos}, {self._tags()})')
        self.infos += 1

    def alert(self, name, cond, text, desc, cat=None):
        self.lines.append(f'new Kinds.Alert({jstr(self._id(name))}, {jstr(name)}, {jstr(self.title + ": " + desc)}, Category.{cat or self.category}, '
                          f'() -> {cond}, () -> {jstr(text)}, {self._tags()})')

    def alert_event(self, name, event, text, desc, cat=None):
        self.lines.append(f'Kinds.Alert.of({jstr(self._id(name))}, {jstr(name)}, {jstr(self.title + ": " + desc)}, Category.{cat or self.category}, '
                          f'Events.Event.{event}, {jstr(text)}, {self._tags()})')

    def effect(self, event, style, particle=None, sound=None, name=None, color=None, text=None, cat='VISUAL'):
        key = (event, style, particle, sound)
        if key in USED_EFFECTS:
            return False
        USED_EFFECTS.add(key)
        word, symbol = EVENTS[event]
        if style == 'PARTICLES':
            auto = f'{event_label(event)} {PARTICLES[particle]}'
        elif style == 'SOUND':
            auto = f'{event_label(event)} Sound: {SOUND_NAMES[sound]}'
        else:
            auto = f'{event_label(event)} {STYLE_NAMES[style]}'
        name = name or auto
        if text is None:
            text = symbol if style == 'SYMBOLS' else word
        col = color if color is not None else STYLE_COLORS[style]
        snd = 'null'
        if style == 'SOUND':
            snd = EVENT_SOUNDS[sound]
        elif style in ('TEXT', 'SHAKE_TEXT'):
            snd = 'SoundEvents.PLAYER_ATTACK_CRIT'
        part = f'ParticleTypes.{particle}' if particle else 'null'
        desc = describe(event, style, particle)
        self.lines.append(f'new Kinds.Effect({jstr(self._id(name))}, {jstr(name)}, {jstr(self.title + ": " + desc)}, Category.{cat}, '
                          f'Events.Event.{event}, Kinds.Style.{style}, 0x{col & 0xFFFFFFFF:08X}, {jstr(text)}, {part}, {snd}, {self._tags()})')
        return True

    def fill_effects(self, events, target):
        styles = ['TEXT', 'FLASH', 'VIGNETTE', 'RING', 'SYMBOLS', 'CONFETTI', 'HITMARKER', 'SPEEDLINES', 'BORDER', 'SHAKE_TEXT']
        parts = list(PARTICLES)
        while len(self.lines) < target:
            added = False
            for ev in events:
                for st in styles:
                    if len(self.lines) >= target:
                        return
                    if self.effect(ev, st):
                        added = True
                        break
                else:
                    for pa in parts:
                        if len(self.lines) >= target:
                            return
                        if self.effect(ev, 'PARTICLES', pa):
                            added = True
                            break
                    else:
                        for so in range(len(EVENT_SOUNDS)):
                            if len(self.lines) >= target:
                                return
                            if self.effect(ev, 'SOUND', None, so):
                                added = True
                                break
            if not added:
                raise SystemExit(f'{self.key}: ran out of effects at {len(self.lines)}')

    def java(self, cls):
        body = ',\n                '.join(self.lines)
        return f'''package dev.onix.client.module.impl.pack;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

/** {self.title}: {len(self.lines)} modules. Generated by tools/gen_packs.py – edit the generator, not this file. */
@SuppressWarnings("all")
public final class {cls} {{
    private {cls}() {{}}

    public static List<Module> modules() {{
        return List.of(
                {body});
    }}
}}
'''


def event_label(e):
    return {'HIT': 'Hit', 'CRIT': 'Crit', 'KILL': 'Kill', 'DOUBLE_KILL': 'Double Kill', 'TRIPLE_KILL': 'Triple Kill', 'COMBO3': '3 Combo',
            'COMBO5': '5 Combo', 'COMBO10': '10 Combo', 'SELF_POP': 'Totem Pop', 'ENEMY_POP': 'Enemy Pop', 'DAMAGE': 'Damage',
            'LOW_HP': 'Low HP', 'DEATH': 'Death', 'JUMP': 'Jump', 'BIG_LAND': 'Landing', 'SPRINT': 'Sprint', 'PEARL': 'Pearl',
            'CRYSTAL': 'Crystal', 'BLOCK': 'Block', 'EAT': 'Eat', 'LEVEL_UP': 'Level Up', 'HEAL': 'Heal', 'BOW': 'Bow Shot',
            'WIND_CHARGE': 'Wind Charge', 'ROCKET': 'Rocket', 'GAPPLE': 'Gapple', 'POTION': 'Potion', 'ARMOR_BREAK': 'Armor Break'}[e]


def describe(event, style, particle):
    when = {'HIT': 'when you hit someone', 'CRIT': 'on a critical hit', 'KILL': 'when you get a kill', 'DOUBLE_KILL': 'on two kills in 10 seconds',
            'TRIPLE_KILL': 'on three kills in 10 seconds', 'COMBO3': 'at a 3 hit combo', 'COMBO5': 'at a 5 hit combo', 'COMBO10': 'at a 10 hit combo',
            'SELF_POP': 'when your totem pops', 'ENEMY_POP': "when an enemy's totem pops", 'DAMAGE': 'when you take damage',
            'LOW_HP': 'when your health drops low', 'DEATH': 'when you die', 'JUMP': 'when you jump', 'BIG_LAND': 'when you land from high up',
            'SPRINT': 'when you start sprinting', 'PEARL': 'when you throw a pearl', 'CRYSTAL': 'when you place a crystal',
            'BLOCK': 'when you place a block', 'EAT': 'when you eat', 'LEVEL_UP': 'when you level up', 'HEAL': 'when you heal',
            'BOW': 'when you shoot an arrow', 'WIND_CHARGE': 'when you use a wind charge', 'ROCKET': 'when you boost with a rocket',
            'GAPPLE': 'when you eat a golden apple', 'POTION': 'when you throw a potion', 'ARMOR_BREAK': 'when a piece of armor breaks'}[event]
    what = {'FLASH': 'A quick screen flash', 'VIGNETTE': 'A coloured glow around the screen edges', 'TEXT': 'A big popping word',
            'HITMARKER': 'A hit marker at the crosshair', 'RING': 'A ring that grows from the crosshair', 'CONFETTI': 'Confetti rains down',
            'SPEEDLINES': 'Anime speed lines', 'SYMBOLS': 'Symbols float up', 'SOUND': 'A sound', 'SHAKE_TEXT': 'A shaking word',
            'BORDER': 'A coloured screen border', 'PARTICLES': (PARTICLES.get(particle, '') + ' burst out') if particle else ''}[style]
    return f'{what} {when}.'


# ====================================================================================================== the modes

def crystal():
    m = Mode('crystal', 'Crystal PvP', ['crystal pvp', 'cpvp'])
    m.counter('Crying Obsidian Counter', 'CRYING_OBSIDIAN', 'crying obsidian.')
    m.counter('Anvil Counter', 'ANVIL CHIPPED_ANVIL DAMAGED_ANVIL', 'anvils for anvil pvp.')
    m.counter('Piston Counter', 'PISTON STICKY_PISTON', 'pistons for piston crystal.')
    m.counter('Redstone Block Counter', 'REDSTONE_BLOCK', 'redstone blocks (piston crystal).')
    m.counter_tag('Bed Counter', 'BEDS', 'RED_BED', 'beds for bed pvp in the nether/end.')
    m.counter('Button Counter', 'STONE_BUTTON POLISHED_BLACKSTONE_BUTTON', 'buttons for surround tricks.')
    m.counter('Hopper Counter', 'HOPPER', 'hoppers.')
    m.counter('Netherite Block Counter', 'NETHERITE_BLOCK', 'netherite blocks.')
    m.info('Totem In Offhand', 'Offhand Totem', 'P.yesNo(P.offhand(Items.TOTEM_OF_UNDYING))', 'whether a totem is in your off hand.')
    m.info('Lowest Armor', 'Armor', 'P.armorLowest() < 0 ? null : P.armorLowest() + "%"', 'durability of your most damaged armor piece.')
    m.info('Armor Pieces', 'Pieces', 'P.armorPieces() + "/4"', 'how many armor pieces you wear.')
    m.info('Resistance Timer', 'Resistance', 'P.effect(MobEffects.RESISTANCE)', 'time left on resistance.')
    m.info('Fire Resistance Timer', 'Fire Res', 'P.effect(MobEffects.FIRE_RESISTANCE)', 'time left on fire resistance.')
    m.info('Crystals Per Stack', 'Stacks', 'P.f1(P.count(Items.END_CRYSTAL) / 64.0)', 'end crystals as stacks.')
    for k in ('SESSION', 'PER_MINUTE', 'SINCE'):
        m.stat('CRYSTAL', k)
        m.stat('SELF_POP', k)
        m.stat('ENEMY_POP', k)
    m.alert('Low Armor Alert', 'P.lowArmor()', 'Armor below 20% – mend now!', 'warns when an armor piece is almost broken.')
    m.alert('No Obsidian Alert', 'P.count(Items.OBSIDIAN) == 0 && P.count(Items.END_CRYSTAL) > 0', 'Crystals but no obsidian!', 'you have crystals but no obsidian.')
    m.alert('Empty Offhand Alert', 'P.p().getOffhandItem().isEmpty() && P.count(Items.TOTEM_OF_UNDYING) > 0', 'Your off hand is empty', 'your off hand is empty while you carry totems.')
    m.alert_event('Totem Pop Alert', 'SELF_POP', 'Your totem popped!', 'tells you when your totem pops.')
    m.alert_event('Enemy Pop Alert', 'ENEMY_POP', 'Enemy popped a totem!', 'tells you when an enemy pops.')
    m.alert_event('Armor Broke Alert', 'ARMOR_BREAK', 'A piece of armor broke!', 'tells you when an armor piece breaks.')
    m.effect('ENEMY_POP', 'TEXT', name='Pop Announcer', text='POPPED!', color=0xFFFFD84D)
    m.effect('ENEMY_POP', 'CONFETTI', name='Pop Confetti')
    m.effect('SELF_POP', 'VIGNETTE', name='Pop Danger Glow', color=0xFFFFAA00)
    m.effect('CRYSTAL', 'RING', name='Crystal Ring', color=0xFFB46CFF)
    m.effect('CRYSTAL', 'PARTICLES', 'END_ROD', name='Crystal Sparks')
    m.fill_effects(['CRYSTAL', 'ENEMY_POP', 'SELF_POP', 'KILL', 'ARMOR_BREAK', 'LOW_HP'], 67)
    return m


def sword():
    m = Mode('sword', 'Sword PvP', ['sword pvp', 'sword'])
    m.counter_tag('Sword Counter', 'SWORDS', 'DIAMOND_SWORD', 'swords you carry.')
    m.counter('Shield Counter', 'SHIELD', 'shields.')
    m.counter('Fishing Rod Counter', 'FISHING_ROD', 'rods for rod combos.')
    m.counter('Flint and Steel Counter', 'FLINT_AND_STEEL', 'flint and steel.')
    m.counter('Cooked Chicken Counter', 'COOKED_CHICKEN COOKED_MUTTON', 'cooked chicken and mutton.')
    m.info('Sword Durability', 'Sword', 'P.holdingSword() ? P.heldDurability() : null', 'durability of the sword in your hand.')
    m.info('Combo Now', 'Combo', 'String.valueOf(dev.onix.client.util.CombatTracker.combo())', 'your current combo.')
    m.info('Best Combo', 'Best', 'String.valueOf(dev.onix.client.util.CombatTracker.bestCombo())', 'your best combo this session.')
    m.info('Hits This Session', 'Hits', 'String.valueOf(dev.onix.client.util.CombatTracker.hits())', 'all your hits this session.')
    m.info('Kills This Session', 'Kills', 'String.valueOf(dev.onix.client.util.CombatTracker.kills())', 'kills this session.')
    m.info('K/D Ratio', 'K/D', 'P.f1(dev.onix.client.util.CombatTracker.kills() / (double) Math.max(1, dev.onix.client.util.CombatTracker.deaths()))', 'kills per death this session.')
    m.info('Last Reach', 'Reach', 'dev.onix.client.util.CombatTracker.lastHitTime() == 0 ? null : P.f1(dev.onix.client.util.CombatTracker.lastReach())', 'distance of your last hit.')
    m.info('Strength Timer', 'Strength', 'P.effect(MobEffects.STRENGTH)', 'time left on strength.')
    m.info('Speed Timer', 'Speed', 'P.effect(MobEffects.SPEED)', 'time left on speed.')
    m.info('Weakness Timer', 'Weakness', 'P.effect(MobEffects.WEAKNESS)', 'time left on weakness.')
    m.info('Sprint State', 'Sprint', 'P.yesNo(P.p().isSprinting())', 'whether you are sprinting (W-tap practice).')
    m.info('Target Health', 'Target', 'dev.onix.client.util.CombatTracker.target() instanceof net.minecraft.world.entity.LivingEntity l && System.currentTimeMillis() - dev.onix.client.util.CombatTracker.lastHitTime() < 5000 ? P.f1(l.getHealth() / 2) + " ♥" : null', 'health of the player you last hit.')
    for e in ('HIT', 'CRIT', 'KILL'):
        for k in ('SESSION', 'PER_MINUTE', 'SINCE'):
            m.stat(e, k)
    m.stat('COMBO5', 'SESSION')
    m.stat('COMBO3', 'SESSION')
    m.stat('DAMAGE', 'SESSION')
    m.alert_event('Combo Alert', 'COMBO5', '5 hit combo!', 'tells you when you reach a 5 hit combo.')
    m.alert('Weakness Alert', 'P.has(MobEffects.WEAKNESS)', 'You have weakness!', 'warns when you get weakness.')
    m.alert('Sword Low Alert', 'P.holdingSword() && P.heldDurability() != null && Integer.parseInt(P.heldDurability().replace("%", "")) <= 10', 'Your sword is almost broken', 'warns when your sword is almost broken.')
    m.alert_event('Kill Alert', 'KILL', 'Kill!', 'tells you about every kill.')
    m.effect('HIT', 'HITMARKER', name='Hit Marker', color=0xFFFFFFFF)
    m.effect('CRIT', 'TEXT', name='Crit Pop', color=0xFFFFD84D)
    m.effect('KILL', 'TEXT', name='Kill Text', color=0xFFFF4D4D)
    m.effect('COMBO5', 'SHAKE_TEXT', name='Combo Shake')
    m.effect('CRIT', 'PARTICLES', 'CRIT', name='Extra Crit Stars')
    m.effect('KILL', 'PARTICLES', 'SOUL', name='Kill Souls')
    m.fill_effects(['HIT', 'CRIT', 'KILL', 'COMBO3', 'COMBO5', 'COMBO10', 'DAMAGE'], 67)
    return m


def mace():
    m = Mode('mace', 'Mace PvP', ['mace pvp', 'mace'])
    m.counter('Mace Counter', 'MACE', 'maces.')
    m.counter('Heavy Core Counter', 'HEAVY_CORE', 'heavy cores.')
    m.counter('Elytra Counter', 'ELYTRA', 'elytras (mace + elytra combo).')
    m.counter('Trident Counter', 'TRIDENT', 'tridents.')
    m.counter('Slow Falling Counter', 'FEATHER', 'feathers.')
    m.info('Fall Height', 'Fall', 'P.p().fallDistance > 0.5 ? P.f1(P.p().fallDistance) + "m" : null', 'how far you are falling right now (smash damage).')
    m.info('Smash Ready', 'Smash', 'P.holding(Items.MACE) && P.p().fallDistance > 1.5 ? "Yes" : null', 'a mace hit now would be a smash attack.')
    m.info('Mace Bonus', 'Bonus', 'P.holding(Items.MACE) && P.p().fallDistance > 1.5 ? "+" + P.f1(Math.min(3, P.p().fallDistance) * 4 + Math.max(0, Math.min(5, P.p().fallDistance - 3)) * 2 + Math.max(0, P.p().fallDistance - 8)) : null', 'extra damage of a smash hit from this height (before enchantments).')
    m.info('Height Above Ground', 'Air', '(P.p().getY() - P.mc().level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, P.p().getBlockX(), P.p().getBlockZ())) > 0.5 ? P.f1(P.p().getY() - P.mc().level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, P.p().getBlockX(), P.p().getBlockZ())) + "m" : null', 'how high above the ground you are.')
    m.info('Slow Falling Timer', 'Slow Fall', 'P.effect(MobEffects.SLOW_FALLING)', 'time left on slow falling.')
    m.info('Wind Burst', 'Wind', 'P.count(Items.WIND_CHARGE) > 0 ? P.count(Items.WIND_CHARGE) + " charges" : null', 'wind charges left for launches.')
    m.info('Vertical Velocity', 'Up/Down', 'P.f1(P.p().getDeltaMovement().y * 20) + " b/s"', 'blocks per second up or down.')
    for e in ('WIND_CHARGE', 'BIG_LAND'):
        for k in ('SESSION', 'PER_MINUTE', 'SINCE'):
            m.stat(e, k)
    m.alert('Smash Height Alert', 'P.holding(Items.MACE) && P.p().fallDistance > 6', 'Big smash ready!', 'tells you when a smash would hit hard.')
    m.alert('No Wind Charges', 'P.holding(Items.MACE) && P.count(Items.WIND_CHARGE) == 0', 'No wind charges left', 'mace in hand but no wind charges.')
    m.alert_event('Wind Charge Used', 'WIND_CHARGE', 'Launched!', 'tells you when you use a wind charge.')
    m.effect('WIND_CHARGE', 'SPEEDLINES', name='Launch Speed Lines')
    m.effect('BIG_LAND', 'RING', name='Smash Shockwave', color=0xFFFFFFFF)
    m.effect('BIG_LAND', 'FLASH', name='Smash Flash')
    m.effect('WIND_CHARGE', 'PARTICLES', 'GUST', name='Wind Gusts')
    m.effect('BIG_LAND', 'PARTICLES', 'EXPLOSION', name='Smash Explosion')
    m.fill_effects(['WIND_CHARGE', 'BIG_LAND', 'HIT', 'KILL', 'JUMP'], 67)
    return m


def nethpot():
    m = Mode('nethpot', 'NethPot', ['nethpot', 'netherite pot', 'axe', 'shield'])
    m.counter('Splash Potion Counter', 'SPLASH_POTION', 'splash potions.')
    m.counter('Lingering Potion Counter', 'LINGERING_POTION', 'lingering potions.')
    m.counter('Glass Bottle Counter', 'GLASS_BOTTLE', 'empty bottles.')
    m.counter_tag('Axe Counter', 'AXES', 'NETHERITE_AXE', 'axes to break shields.')
    m.counter('Crossbow Counter', 'CROSSBOW', 'crossbows.')
    m.counter('Bow Counter', 'BOW', 'bows.')
    m.info('Axe Durability', 'Axe', 'P.holdingAxe() ? P.heldDurability() : null', 'durability of the axe in your hand.')
    m.info('Shield Cooldown', 'Shield', 'P.p().getCooldowns().isOnCooldown(new net.minecraft.world.item.ItemStack(Items.SHIELD)) ? "Disabled" : "Ready"', 'whether your shield is disabled.')
    m.info('Blocking', 'Block', 'P.p().isBlocking() ? "Blocking" : null', 'shows while you block with a shield.')
    m.info('Regeneration Timer', 'Regen', 'P.effect(MobEffects.REGENERATION)', 'time left on regeneration.')
    m.info('Absorption Hearts', 'Gold', 'P.p().getAbsorptionAmount() > 0 ? P.f1(P.p().getAbsorptionAmount() / 2) : null', 'golden hearts.')
    m.info('Instant Health Ready', 'Pots', 'String.valueOf(P.count(Items.SPLASH_POTION))', 'splash potions ready to throw.')
    m.info('Effects Active', 'Effects', 'String.valueOf(P.p().getActiveEffects().size())', 'number of active effects.')
    for e in ('POTION', 'HEAL', 'GAPPLE'):
        for k in ('SESSION', 'PER_MINUTE', 'SINCE'):
            m.stat(e, k)
    m.alert('No Potions Alert', 'P.count(Items.SPLASH_POTION) == 0 && P.p().getHealth() < 10', 'Low health and no potions!', 'low health and no splash potions left.')
    m.alert('Shield Ready Alert', '!P.p().getCooldowns().isOnCooldown(new net.minecraft.world.item.ItemStack(Items.SHIELD)) && P.offhand(Items.SHIELD)', 'Shield ready again', 'tells you when your shield works again.')
    m.alert_event('Gapple Eaten', 'GAPPLE', 'Golden apple eaten', 'confirms a golden apple.')
    m.effect('POTION', 'PARTICLES', 'WITCH', name='Potion Magic')
    m.effect('HEAL', 'VIGNETTE', name='Heal Glow', color=0xFFFF4D8D)
    m.effect('GAPPLE', 'SYMBOLS', name='Gapple Hearts', color=0xFFFFD84D, text='♥')
    m.fill_effects(['POTION', 'HEAL', 'GAPPLE', 'DAMAGE', 'HIT', 'KILL'], 67)
    return m


def dsmp():
    m = Mode('dsmp', 'Diamond SMP', ['diamond smp', 'smp', 'lifesteal'], 'UTILITY')
    m.counter('Diamond Ore Counter', 'DIAMOND_ORE DEEPSLATE_DIAMOND_ORE', 'diamond ore you mined with silk touch.')
    m.counter('Netherite Upgrade Counter', 'NETHERITE_UPGRADE_SMITHING_TEMPLATE', 'netherite upgrade templates.')
    m.counter('Emerald Block Counter', 'EMERALD_BLOCK', 'emerald blocks.')
    m.counter('Gold Block Counter', 'GOLD_BLOCK', 'gold blocks.')
    m.counter('Iron Block Counter', 'IRON_BLOCK', 'iron blocks.')
    m.counter('Beacon Counter', 'BEACON', 'beacons.')
    m.counter('Nether Star Counter', 'NETHER_STAR', 'nether stars.')
    m.counter('Elytra Wings Counter', 'TURTLE_SCUTE ARMADILLO_SCUTE', 'scutes.')
    m.counter('Trial Key Counter', 'TRIAL_KEY OMINOUS_TRIAL_KEY', 'trial keys.')
    m.counter('Enchanted Book Pile', 'ENCHANTED_BOOK', 'enchanted books.')
    m.counter('Lapis Block Counter', 'LAPIS_BLOCK', 'lapis blocks.')
    m.counter('Raw Gold Counter', 'RAW_GOLD RAW_GOLD_BLOCK', 'raw gold.')
    m.counter('Raw Iron Counter', 'RAW_IRON RAW_IRON_BLOCK', 'raw iron.')
    m.counter('Raw Copper Counter', 'RAW_COPPER RAW_COPPER_BLOCK', 'raw copper.')
    m.info('Diamond Worth', 'Diamonds', 'String.valueOf(P.count(Items.DIAMOND) + P.count(Items.DIAMOND_BLOCK) * 9)', 'all your diamonds incl. blocks.')
    m.info('Netherite Progress', 'Netherite', 'P.count(Items.NETHERITE_SCRAP) + "/4 scrap · " + P.count(Items.NETHERITE_INGOT) + " ingots"', 'scrap towards the next netherite ingot.')
    m.info('Diamond Layer', 'Layer', 'P.p().getBlockY() <= -50 && P.p().getBlockY() >= -64 ? "Diamond layer ✔" : "Go to Y -58"', 'whether you are at the best diamond height.')
    m.info('Debris Layer', 'Debris', 'P.nether() ? (Math.abs(P.p().getBlockY() - 15) <= 3 ? "Best layer ✔" : "Go to Y 15") : null', 'nether: best height for ancient debris.')
    m.info('Lifesteal Hearts', 'Max HP', 'P.f1(P.p().getMaxHealth() / 2) + " ♥"', 'your max hearts (lifesteal servers change them).')
    m.info('Inventory Value', 'Ores', 'String.valueOf(P.count(Items.DIAMOND) + P.count(Items.EMERALD) + P.count(Items.GOLD_INGOT) + P.count(Items.IRON_INGOT))', 'diamonds, emeralds, gold and iron together.')
    m.info('Players Online', 'Online', 'String.valueOf(P.tabPlayers())', 'players in the tab list.')
    m.info('Mining Fatigue', 'Fatigue', 'P.effect(MobEffects.MINING_FATIGUE)', 'mining fatigue time left (ocean monuments).')
    m.info('Haste Timer', 'Haste', 'P.effect(MobEffects.HASTE)', 'haste time left (beacons).')
    m.info('Pickaxe Durability', 'Pickaxe', 'P.p().getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES) ? P.heldDurability() : null', 'durability of your pickaxe.')
    for e in ('DEATH', 'LEVEL_UP', 'EAT'):
        for k in ('SESSION', 'SINCE'):
            m.stat(e, k, 'UTILITY')
    m.alert('Diamond Layer Alert', 'P.overworld() && P.p().getBlockY() == -58', 'You are at diamond level (Y -58)', 'tells you when you reach Y -58.')
    m.alert('Pickaxe Low Alert', 'P.p().getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES) && P.heldDurability() != null && Integer.parseInt(P.heldDurability().replace("%", "")) <= 5', 'Pickaxe almost broken!', 'warns before your pickaxe breaks.')
    m.alert('Netherite Ready Alert', 'P.count(Items.NETHERITE_SCRAP) >= 4 && P.count(Items.GOLD_INGOT) >= 4', 'You can craft netherite!', 'tells you when you have 4 scrap and 4 gold.')
    m.alert_event('Death Alert', 'DEATH', 'You died – remember your coords', 'reminds you after a death.')
    m.effect('LEVEL_UP', 'CONFETTI', name='Level Up Party')
    m.effect('DEATH', 'TEXT', name='Wasted Screen', color=0xFFFF3B3B, text='WASTED')
    m.fill_effects(['LEVEL_UP', 'DEATH', 'EAT', 'HEAL', 'KILL', 'HIT'], 67)
    return m


def survival():
    m = Mode('survival', 'Survival', ['survival', 'speedrun', 'hardcore'], 'UTILITY')
    for name, items, d in [
        ('Oak Log Counter', 'OAK_LOG SPRUCE_LOG BIRCH_LOG JUNGLE_LOG ACACIA_LOG DARK_OAK_LOG MANGROVE_LOG CHERRY_LOG PALE_OAK_LOG', 'logs.'),
        ('Plank Counter', 'OAK_PLANKS SPRUCE_PLANKS BIRCH_PLANKS JUNGLE_PLANKS ACACIA_PLANKS DARK_OAK_PLANKS', 'planks.'),
        ('Cobblestone Counter', 'COBBLESTONE COBBLED_DEEPSLATE', 'cobblestone.'), ('Stone Counter', 'STONE DEEPSLATE', 'stone.'),
        ('Dirt Counter', 'DIRT', 'dirt.'), ('Sand Counter', 'SAND RED_SAND', 'sand.'), ('Gravel Counter', 'GRAVEL', 'gravel.'),
        ('Flint Counter', 'FLINT', 'flint.'), ('Clay Counter', 'CLAY_BALL', 'clay balls.'), ('Glass Counter', 'GLASS', 'glass.'),
        ('Iron Nugget Counter', 'IRON_NUGGET', 'iron nuggets.'), ('Gold Nugget Counter', 'GOLD_NUGGET', 'gold nuggets.'),
        ('Rotten Flesh Counter', 'ROTTEN_FLESH', 'rotten flesh.'), ('Bone Counter', 'BONE', 'bones.'), ('Spider Eye Counter', 'SPIDER_EYE', 'spider eyes.'),
        ('Ender Pearl Stack', 'ENDER_EYE', 'eyes.'), ('Feather Counter', 'FEATHER', 'feathers.'), ('Egg Basket', 'BROWN_EGG BLUE_EGG', 'colored eggs.'),
        ('Carrot Counter', 'CARROT', 'carrots.'), ('Potato Counter', 'POTATO BAKED_POTATO', 'potatoes.'), ('Apple Counter', 'APPLE', 'apples.'),
        ('Melon Counter', 'MELON_SLICE', 'melon slices.'), ('Sweet Berry Counter', 'SWEET_BERRIES GLOW_BERRIES', 'berries.'),
        ('Cooked Fish Counter', 'COOKED_COD COOKED_SALMON', 'cooked fish.'), ('Seed Counter', 'WHEAT_SEEDS', 'seeds.'),
        ('Saddle Counter', 'SADDLE', 'saddles.'), ('Lead Counter', 'LEAD', 'leads.'), ('Bucket Counter', 'BUCKET', 'empty buckets.'),
        ('Crafting Table Counter', 'CRAFTING_TABLE', 'crafting tables.'), ('Furnace Counter', 'FURNACE BLAST_FURNACE SMOKER', 'furnaces.'),
        ('Chest Counter', 'CHEST BARREL', 'chests and barrels.'), ('Bed Spare Counter', 'WHITE_BED', 'white beds.'),
    ]:
        m.counter(name, items, d)
    m.info('Time Of Day', 'Time', 'String.format(java.util.Locale.ROOT, "%02d:%02d", (P.dayTime() / 1000 + 6) % 24, P.dayTime() % 1000 * 60 / 1000)', 'in-game time as a 24 hour clock.')
    m.info('Day Or Night', 'Sky', 'P.dayTime() < 12500 ? "Day" : P.dayTime() < 13000 ? "Sunset" : P.dayTime() < 23000 ? "Night" : "Sunrise"', 'day, sunset, night or sunrise.')
    m.info('Can Sleep', 'Sleep', 'P.yesNo(P.dayTime() >= 12542 && P.dayTime() <= 23459 || P.mc().level.isThundering())', 'whether you can sleep right now.')
    m.info('Distance From Spawn', 'From 0,0', 'P.distanceTo(0, 0)', 'blocks from 0, 0.')
    m.info('Nether Roof Height', 'Roof', 'P.nether() ? (P.p().getY() > 128 ? "Above roof" : (128 - P.p().getBlockY()) + " below roof") : null', 'nether: position relative to the bedrock roof.')
    m.info('Overworld Height Zone', 'Zone', 'P.overworld() ? (P.p().getY() > 100 ? "Mountains" : P.p().getY() > 50 ? "Surface" : P.p().getY() > 0 ? "Caves" : "Deep Dark") : null', 'surface, caves or deep dark.')
    m.info('Speedrun Timer', 'Run', 'P.clock(P.mc().level.getGameTime())', 'world age as a speedrun timer.')
    m.info('Held Durability', 'Tool', 'P.heldDurability()', 'durability of the held tool.')
    m.info('Night Vision Timer', 'Night Vision', 'P.effect(MobEffects.NIGHT_VISION)', 'time left on night vision.')
    m.info('Water Breathing Timer', 'Water Breathing', 'P.effect(MobEffects.WATER_BREATHING)', 'time left on water breathing.')
    for e in ('JUMP', 'EAT', 'HEAL'):
        for k in ('SESSION', 'SINCE'):
            m.stat(e, k, 'UTILITY')
    m.alert('Sleep Time Alert', 'P.dayTime() >= 12542 && P.dayTime() < 12600', 'You can sleep now', 'tells you when beds work.')
    m.alert('Starving Alert', 'P.p().getFoodData().getFoodLevel() == 0', 'You are starving!', 'warns when your food bar is empty.')
    m.alert('Tool Break Alert', 'P.heldDurability() != null && Integer.parseInt(P.heldDurability().replace("%", "")) <= 3', 'Your tool is about to break', 'warns before your held tool breaks.')
    m.effect('JUMP', 'PARTICLES', 'CLOUD', name='Jump Puff')
    m.fill_effects(['JUMP', 'EAT', 'HEAL', 'LEVEL_UP', 'SPRINT', 'DAMAGE'], 66)
    return m


def bedwars():
    m = Mode('bedwars', 'Bedwars', ['bedwars', 'hypixel'])
    for name, items, d in [
        ('End Stone Counter', 'END_STONE', 'end stone.'), ('Terracotta Counter', 'TERRACOTTA WHITE_TERRACOTTA', 'hardened clay.'),
        ('Fireball Counter', 'FIRE_CHARGE', 'fireballs.'), ('Sponge Counter', 'SPONGE', 'sponges.'), ('Bridge Egg Counter', 'TURTLE_EGG', 'turtle eggs.'),
        ('Golem Egg Counter', 'IRON_GOLEM_SPAWN_EGG', 'iron golem eggs.'), ('Silverfish Counter', 'SILVERFISH_SPAWN_EGG', 'silverfish eggs.'),
        ('Shears Counter', 'SHEARS', 'shears.'), ('Compass Counter', 'COMPASS', 'tracker compasses.'), ('Stone Sword Counter', 'STONE_SWORD', 'stone swords.'),
        ('Iron Sword Counter', 'IRON_SWORD', 'iron swords.'), ('Wood Pickaxe Counter', 'WOODEN_PICKAXE', 'wooden pickaxes.'),
        ('Glass Pane Counter', 'WHITE_STAINED_GLASS', 'blast glass.'), ('Oak Plank Stack', 'OAK_SLAB', 'slabs.'),
    ]:
        m.counter(name, items, d)
    m.info('Hotbar Blocks', 'Blocks', 'String.valueOf(P.hotbarBlocks())', 'blocks in your hotbar.')
    m.info('Iron For Wool', 'Wool Buys', 'String.valueOf(P.count(Items.IRON_INGOT) / 4)', 'how many 16-wool stacks your iron buys (4 iron each).')
    m.info('Gold Buys', 'Gold', 'String.valueOf(P.count(Items.GOLD_INGOT))', 'gold for armor and items.')
    m.info('Emerald Buys', 'Emeralds', 'String.valueOf(P.count(Items.EMERALD))', 'emeralds for upgrades.')
    m.info('Diamond Upgrades', 'Diamonds', 'String.valueOf(P.count(Items.DIAMOND))', 'diamonds for team upgrades.')
    m.info('Invisibility Timer', 'Invis', 'P.effect(MobEffects.INVISIBILITY)', 'time left on invisibility.')
    m.info('Jump Boost Timer', 'Jump', 'P.effect(MobEffects.JUMP_BOOST)', 'time left on jump boost.')
    m.info('Void Distance', 'Void', 'String.valueOf(Math.round(P.p().getY() - P.mc().level.getMinY()))', 'blocks above the void.')
    for e in ('BLOCK', 'KILL', 'DEATH'):
        for k in ('SESSION', 'PER_MINUTE', 'SINCE'):
            m.stat(e, k)
    m.alert('Low Blocks Alert', 'P.hotbarBlocks() > 0 && P.hotbarBlocks() < 16', 'Less than 16 blocks!', 'warns when you run low on blocks.')
    m.alert('Fireball Ready Alert', 'P.count(Items.FIRE_CHARGE) > 0 && P.p().getHealth() < 8', 'Low HP – fireball out!', 'reminds you of your fireball when low.')
    m.alert_event('Final Kill Alert', 'TRIPLE_KILL', 'Triple kill!', 'cheers three kills in a row.')
    m.effect('BLOCK', 'PARTICLES', 'POOF', name='Block Poof')
    m.effect('DOUBLE_KILL', 'TEXT', name='Double Kill Announcer', color=0xFFFF7A2E)
    m.effect('TRIPLE_KILL', 'TEXT', name='Triple Kill Announcer', color=0xFFFF2E5E)
    m.fill_effects(['BLOCK', 'KILL', 'DOUBLE_KILL', 'TRIPLE_KILL', 'DEATH', 'HIT'], 67)
    return m


def skywars():
    m = Mode('skywars', 'Skywars', ['skywars', 'sky wars'])
    for name, items, d in [
        ('Iron Armor Counter', 'IRON_CHESTPLATE IRON_HELMET IRON_LEGGINGS IRON_BOOTS', 'iron armor pieces.'),
        ('Diamond Armor Counter', 'DIAMOND_CHESTPLATE DIAMOND_HELMET DIAMOND_LEGGINGS DIAMOND_BOOTS', 'diamond armor pieces.'),
        ('Chain Armor Counter', 'CHAINMAIL_CHESTPLATE CHAINMAIL_HELMET CHAINMAIL_LEGGINGS CHAINMAIL_BOOTS', 'chain armor pieces.'),
        ('Spectral Arrow Counter', 'SPECTRAL_ARROW', 'spectral arrows.'), ('Tipped Arrow Counter', 'TIPPED_ARROW', 'tipped arrows.'),
        ('Stone Block Counter', 'STONE_BRICKS', 'stone bricks.'), ('Oak Log Bridge', 'STRIPPED_OAK_LOG', 'stripped logs.'),
        ('Snow Block Counter', 'SNOW_BLOCK', 'snow blocks.'), ('Enchant Table Counter', 'ENCHANTING_TABLE', 'enchanting tables.'),
        ('Lapis For Enchant', 'LAPIS_BLOCK', 'lapis blocks.'),
    ]:
        m.counter(name, items, d)
    m.info('Island Edge', 'Edge', 'P.p().getY() > P.mc().level.getMinY() + 5 && !P.p().onGround() && P.p().fallDistance > 2 ? "Falling!" : null', 'warns while you fall off an island.')
    m.info('Bow Ready', 'Bow', 'P.count(Items.BOW) > 0 && P.count(Items.ARROW) > 0 ? P.count(Items.ARROW) + " shots" : null', 'how many bow shots you have.')
    m.info('Armor Value', 'Armor', 'String.valueOf(P.p().getArmorValue())', 'armor points (compare loot).')
    m.info('Players Left', 'Alive', 'String.valueOf(P.tabPlayers())', 'players in the tab list.')
    m.info('Height', 'Y', 'String.valueOf(P.p().getBlockY())', 'your height (stay above the void).')
    for e in ('BOW', 'KILL', 'PEARL'):
        for k in ('SESSION', 'PER_MINUTE', 'SINCE'):
            m.stat(e, k)
    m.alert('Better Armor Alert', 'P.count(Items.DIAMOND_CHESTPLATE) > 0 && !P.p().getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)', 'Put on the diamond chestplate!', 'you carry a diamond chestplate you are not wearing.')
    m.alert_event('Pearl Thrown', 'PEARL', 'Pearl thrown', 'confirms a pearl throw.')
    m.effect('BOW', 'HITMARKER', name='Bow Hit Marker', color=0xFF7AE0FF)
    m.effect('PEARL', 'PARTICLES', 'PORTAL', name='Pearl Portal Dust')
    m.fill_effects(['BOW', 'PEARL', 'KILL', 'HIT', 'BIG_LAND', 'DEATH'], 66)
    return m


def uhc():
    m = Mode('uhc', 'UHC', ['uhc', 'ultra hardcore'])
    for name, items, d in [
        ('Golden Head Counter', 'PLAYER_HEAD', 'player heads (golden heads).'), ('Gold Ore Counter', 'GOLD_ORE DEEPSLATE_GOLD_ORE', 'gold ore.'),
        ('Iron Ore Counter', 'IRON_ORE DEEPSLATE_IRON_ORE', 'iron ore.'), ('Sugar Counter', 'SUGAR', 'sugar for speed potions.'),
        ('Glistering Melon Counter', 'GLISTERING_MELON_SLICE', 'glistering melons for healing.'), ('Ghast Tear Counter', 'GHAST_TEAR', 'ghast tears.'),
        ('Magma Cream Counter', 'MAGMA_CREAM', 'magma cream.'), ('Blaze Powder Counter', 'BLAZE_POWDER', 'blaze powder.'),
        ('Brewing Stand Counter', 'BREWING_STAND', 'brewing stands.'), ('Anvil Spare Counter', 'SMITHING_TABLE', 'smithing tables.'),
        ('Cooked Rabbit Counter', 'COOKED_RABBIT RABBIT_STEW', 'rabbit food.'), ('Mushroom Stew Counter', 'MUSHROOM_STEW BEETROOT_SOUP', 'soups.'),
    ]:
        m.counter(name, items, d)
    m.info('Health Exact', 'HP', 'P.f1(P.p().getHealth()) + " / " + P.f1(P.p().getMaxHealth())', 'exact health (no natural regen in UHC).')
    m.info('Hearts Lost', 'Lost', 'P.f1((P.p().getMaxHealth() - P.p().getHealth()) / 2) + " ♥"', 'hearts you are missing.')
    m.info('Gold For Apples', 'Apples', 'String.valueOf(P.count(Items.GOLD_INGOT) / 8)', 'golden apples you can craft from your gold.')
    m.info('World Border Distance', 'Border', 'String.valueOf(Math.round(P.mc().level.getWorldBorder().getDistanceToBorder(P.p())))', 'blocks to the world border.')
    m.info('Border Size', 'Size', 'String.valueOf(Math.round(P.mc().level.getWorldBorder().getSize()))', 'world border size.')
    m.info('Absorption Gold', 'Absorb', 'P.p().getAbsorptionAmount() > 0 ? P.f1(P.p().getAbsorptionAmount()) : null', 'absorption points.')
    m.info('Regen Left', 'Regen', 'P.effect(MobEffects.REGENERATION)', 'regeneration time from apples.')
    for e in ('GAPPLE', 'KILL', 'DAMAGE'):
        for k in ('SESSION', 'PER_MINUTE', 'SINCE'):
            m.stat(e, k)
    m.alert('Border Close Alert', 'P.mc().level.getWorldBorder().getDistanceToBorder(P.p()) < 25', 'The border is close!', 'warns when the world border is near.')
    m.alert('Half Health Alert', 'P.p().getHealth() <= P.p().getMaxHealth() / 2', 'Half health – eat a golden apple', 'warns at half health.')
    m.alert_event('Gapple Timer Start', 'GAPPLE', 'Golden apple – regen running', 'confirms a golden apple.')
    m.effect('GAPPLE', 'PARTICLES', 'HEART', name='Gapple Hearts Burst')
    m.effect('DAMAGE', 'VIGNETTE', name='Damage Vignette', color=0xFFFF2020)
    m.fill_effects(['GAPPLE', 'KILL', 'DAMAGE', 'HEAL', 'BOW', 'LOW_HP'], 66)
    return m


def bridge():
    m = Mode('bridge', 'Bridge & Parkour', ['bridge', 'parkour', 'movement', 'speedbridge'], 'HUD')
    for name, items, d in [
        ('Blue Clay Counter', 'BLUE_TERRACOTTA', 'blue clay (The Bridge).'), ('Red Clay Counter', 'RED_TERRACOTTA', 'red clay (The Bridge).'),
        ('Sandstone Counter', 'SANDSTONE', 'sandstone.'), ('Quartz Block Counter', 'QUARTZ_BLOCK', 'quartz blocks.'),
        ('Slab Counter', 'STONE_SLAB', 'stone slabs.'), ('Stair Counter', 'STONE_STAIRS', 'stairs.'), ('Concrete Counter', 'WHITE_CONCRETE', 'white concrete.'),
    ]:
        m.counter(name, items, d)
    m.info('Horizontal Speed', 'Speed', 'P.f1(P.speed()) + " b/s"', 'blocks per second (bridging speed).')
    m.info('Sneak Edge', 'Sneak', 'P.p().isCrouching() ? "Sneaking" : null', 'shows while you sneak at an edge.')
    m.info('Air Time', 'Air', '!P.p().onGround() ? "In air" : null', 'shows while you are in the air.')
    m.info('Block In Hand', 'Hand', 'P.p().getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem ? String.valueOf(P.p().getMainHandItem().getCount()) : null', 'blocks left in the held stack.')
    m.info('Yaw Angle', 'Yaw', 'P.f1(net.minecraft.util.Mth.wrapDegrees(P.p().getYRot()))', 'exact yaw for diagonal bridging.')
    m.info('Pitch Angle', 'Pitch', 'P.f1(P.p().getXRot())', 'exact pitch for speedbridging.')
    m.info('Block X Z', 'Block', 'P.p().getBlockX() + ", " + P.p().getBlockZ()', 'block coordinates only.')
    m.info('Jump Boost Level', 'Jump', 'P.effect(MobEffects.JUMP_BOOST)', 'jump boost time left.')
    m.info('Speed Effect', 'Speed FX', 'P.effect(MobEffects.SPEED)', 'speed effect time left.')
    m.info('Momentum', 'Momentum', 'P.speed() > 5.5 ? "Fast" : P.speed() > 4 ? "Sprint" : P.speed() > 0.1 ? "Walk" : "Still"', 'still, walk, sprint or fast.')
    for e in ('JUMP', 'BLOCK', 'BIG_LAND', 'SPRINT'):
        for k in ('SESSION', 'PER_MINUTE', 'SINCE'):
            m.stat(e, k, 'HUD')
    m.alert('Falling Alert', 'P.p().fallDistance > 6', 'You are falling!', 'warns when you fall.')
    m.alert('Out Of Blocks Soon', 'P.p().getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem && P.p().getMainHandItem().getCount() <= 8', 'Switch stacks soon', 'warns when the held stack runs low.')
    m.effect('JUMP', 'RING', name='Jump Ring', color=0xFF7AE0FF)
    m.effect('SPRINT', 'SPEEDLINES', name='Sprint Speed Lines')
    m.effect('BIG_LAND', 'PARTICLES', 'CLOUD', name='Landing Dust')
    m.fill_effects(['JUMP', 'SPRINT', 'BIG_LAND', 'BLOCK', 'DEATH'], 66)
    return m


def visuals():
    m = Mode('fx', 'Visuals', ['tiktok', 'viral', 'visual', 'effects'], 'VISUAL')
    # the viral ones first, with fitting names and colours
    m.effect('KILL', 'CONFETTI', name='Kill Confetti')
    m.effect('KILL', 'SHAKE_TEXT', name='Kill Shake', color=0xFFFF2E5E, text='ELIMINATED')
    m.effect('COMBO10', 'TEXT', name='10 Combo Legend', color=0xFFFF7AE0, text='UNSTOPPABLE')
    m.effect('CRIT', 'FLASH', name='Crit Flash', color=0x66FFFFFF)
    m.effect('DAMAGE', 'BORDER', name='Damage Border', color=0xFFFF2020)
    m.effect('LOW_HP', 'VIGNETTE', name='Low HP Pulse', color=0xFFB00020)
    m.effect('KILL', 'PARTICLES', 'TOTEM_OF_UNDYING', name='Kill Totem Burst')
    m.effect('HIT', 'PARTICLES', 'HEART', name='Love Hits')
    m.effect('HIT', 'PARTICLES', 'NOTE', name='Music Hits')
    m.effect('HIT', 'PARTICLES', 'SNOWFLAKE', name='Frost Hits')
    m.effect('HIT', 'PARTICLES', 'FLAME', name='Fire Hits')
    m.effect('HIT', 'PARTICLES', 'SOUL_FIRE_FLAME', name='Soul Fire Hits')
    m.effect('HIT', 'PARTICLES', 'ELECTRIC_SPARK', name='Lightning Hits')
    m.effect('HIT', 'PARTICLES', 'WITCH', name='Magic Hits')
    m.effect('HIT', 'PARTICLES', 'END_ROD', name='Star Hits')
    m.effect('HIT', 'PARTICLES', 'GLOW', name='Glow Hits')
    m.effect('HIT', 'PARTICLES', 'SCULK_SOUL', name='Soul Hits')
    m.effect('HIT', 'PARTICLES', 'FIREWORK', name='Firework Hits')
    m.effect('KILL', 'PARTICLES', 'EXPLOSION', name='Kill Explosion')
    m.effect('KILL', 'PARTICLES', 'SONIC_BOOM', name='Warden Kill')
    m.effect('KILL', 'PARTICLES', 'FIREWORK', name='Kill Fireworks')
    m.effect('KILL', 'PARTICLES', 'LAVA', name='Kill Lava')
    m.effect('KILL', 'SOUND', None, 1, name='Kill Level Up Sound')
    m.effect('KILL', 'SOUND', None, 7, name='Kill Totem Sound')
    m.effect('HIT', 'SOUND', None, 0, name='Hit Ding')
    m.effect('CRIT', 'SOUND', None, 9, name='Crit Smack')
    m.effect('COMBO3', 'SOUND', None, 12, name='Combo Pling')
    m.fill_effects(list(EVENTS), 100)
    return m


# visuals first: the hand-named viral effects get their combinations before the modes fill up with the rest
MODES = [('PackVisuals', visuals), ('PackCrystal', crystal), ('PackSword', sword), ('PackMace', mace), ('PackNethPot', nethpot), ('PackDiamondSmp', dsmp),
         ('PackSurvival', survival), ('PackBedwars', bedwars), ('PackSkywars', skywars), ('PackUhc', uhc), ('PackBridge', bridge)]


def main():
    total = 0
    for cls, make in MODES:
        mode = make()
        with open(os.path.join(OUT, cls + '.java'), 'w') as f:
            f.write(mode.java(cls))
        print(f'{cls}: {len(mode.lines)}')
        total += len(mode.lines)
    print('total', total)


if __name__ == '__main__':
    main()
