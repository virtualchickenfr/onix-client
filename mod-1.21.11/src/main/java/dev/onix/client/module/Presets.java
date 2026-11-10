package dev.onix.client.module;

import dev.onix.client.OnixClient;
import java.util.ArrayList;
import java.util.List;

/**
 * One-click setups per game mode. A preset is shown like a module card in the right shift menu: switching it on
 * turns on its modules, switching it off turns them off again. It counts as "on" while all of its modules are on.
 * Presets are not registered as modules, so they are never saved or counted.
 */
public final class Presets {
    private Presets() {}

    public static final class Preset extends Module {
        private final String[] ids;
        private final String icon;
        private boolean syncing;

        Preset(String id, String name, String description, String icon, String... ids) {
            super("preset_" + id, name, description, Category.UTILITY, false);
            this.ids = ids;
            this.icon = icon;
        }

        public String icon() {
            return icon;
        }

        public List<Module> modules() {
            List<Module> out = new ArrayList<>();
            for (String id : ids) {
                Module m = OnixClient.MODULES.get(id);
                if (m != null) out.add(m);
            }
            return out;
        }

        @Override
        public boolean isEnabled() {
            List<Module> mods = modules();
            boolean on = !mods.isEmpty();
            for (Module m : mods) on &= m.isEnabled();
            if (on != super.isEnabled()) {
                syncing = true;
                setEnabled(on);
                syncing = false;
            }
            return on;
        }

        @Override
        protected void onEnable() {
            if (syncing) return;
            for (Module m : modules()) m.setEnabled(true);
            OnixClient.save();
        }

        @Override
        protected void onDisable() {
            if (syncing) return;
            for (Module m : modules()) m.setEnabled(false);
            OnixClient.save();
        }

        @Override
        public String getName() {
            return dev.onix.client.util.I18n.ui(super.getName());
        }

        @Override
        public String getDescription() {
            return modules().size() + " " + dev.onix.client.util.I18n.ui("modules") + " · " + dev.onix.client.util.I18n.ui(super.getDescription());
        }
    }

    private static List<Preset> all;

    public static List<Preset> all() {
        if (all != null) return all;
        List<Preset> p = new ArrayList<>();
        p.add(new Preset("essentials", "Essentials", "The basics for every mode: FPS, CPS, ping, coordinates, keystrokes, armor and potion effects.", "star",
                "fps", "cps", "ping", "coords", "keystrokes", "armor", "effects", "togglesprint", "zoom"));
        p.add(new Preset("crystal", "Crystal PvP", "Totems, crystals, pops and your enemy at a glance, plus pearl and anchor warnings.", "crystal",
                "fps", "cps", "ping", "keystrokes", "totems", "armor", "effects", "targethud", "cb_popcounter", "cb_enemyarmor", "cb_enemyhands", "cb_surround", "cb_targethole",
                "cb_lastpop", "cb_crystalspeed", "cb_pearlalert", "cb_anchoralert", "cb_offhandtotem", "vx_popfeed"));
        p.add(new Preset("sword", "Sword PvP", "Combo, reach, crits and your sword stats with a charge bar and hit sounds.", "sword",
                "fps", "cps", "ping", "keystrokes", "combo", "reach", "targethud", "armor", "cb_swaccuracy",
                "cb_swdamage", "cb_swhitstokill", "cb_swknockback", "cb_chargebar", "cb_swcritsound", "cb_jumpreset", "vx_dmgnumbers"));
        p.add(new Preset("mace", "Mace PvP", "Fall height, smash damage and a warning when a mace is falling at you.", "axes",
                "fps", "cps", "ping", "keystrokes", "armor", "effects", "falldistance", "cb_maceinfo", "cb_smashpredict", "cb_smashcounter", "cb_targetbelow", "cb_groundheight", "cb_macedanger", "cb_smashsound"));
        p.add(new Preset("smp", "Diamond SMP", "Potions, gapples, shields and combat log detection for SMP fights.", "gem",
                "fps", "ping", "coords", "direction", "armor", "effects", "pots", "gapples", "totems", "cb_combattimer", "cb_combatlog", "cb_bowalert",
                "cb_enemyeating", "cb_enemylow", "cb_lowpotions", "cb_shieldstatus", "cb_targetshield", "cb_splashalert", "cb_armorwarn"));
        p.add(new Preset("bedwars", "Bedwars", "Blocks, resources, session stats and your combat tag.", "flag",
                "fps", "cps", "ping", "keystrokes", "armor", "effects", "blocks", "resources", "sessionstats", "cb_combattimer", "vx_killfeed"));
        p.add(new Preset("skywars", "Skywars", "Arrows, pearls, a bow warning and your session stats.", "cloud",
                "fps", "cps", "ping", "keystrokes", "armor", "effects", "arrows", "pearls", "cb_bowalert", "sessionstats", "vx_killfeed"));
        p.add(new Preset("uhc", "UHC", "Health, gapples, border distance and a warning when your target is low.", "heart",
                "fps", "ping", "coords", "health", "armor", "effects", "gapples", "cb_border", "cb_enemylow"));
        p.add(new Preset("bridge", "Bridge & Parkour", "Speed, block count and snap-to-axis for straight bridging.", "cube",
                "fps", "cps", "keystrokes", "speed", "blocks", "cb_snapaxis"));
        p.add(new Preset("survival", "Survival", "Coordinates, biome, day and night, light level and the warnings you want while exploring.", "pickaxe",
                "fps", "coords", "direction", "biome", "clock", "lightlevel", "deathcoords", "waypoints", "cb_spawn", "cb_creeper", "cb_lava", "cb_drowning", "cb_hunger", "cb_elytra"));
        p.add(new Preset("tiktok", "TikTok Edit", "The viral effects for clips: streak announcer, kill cam, phonk kill, impact frames, camera punch and aura.", "sparkle",
                "vx_streak", "vx_killcam", "vx_phonk", "vx_impact", "vx_killzoom", "vx_shake", "vx_aura", "vx_lightning", "vx_dmgnumbers", "vx_combo"));
        p.add(new Preset("fps", "Max FPS", "Turns off clouds, shadows, smooth lighting and other costly effects for the most frames.", "bolt",
                "cb_noclouds", "cb_noshadows", "cb_nosmoothlighting", "cb_nobiomeblend", "cb_minparticles", "cb_vsyncoff", "cb_unlimitedfps",
                "cb_nomenublur", "unfocusedfps", "entitydistance", "particlelimiter"));
        all = p;
        return p;
    }
}
