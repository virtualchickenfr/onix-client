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
        public String getDescription() {
            return super.getDescription() + " (" + modules().size() + " modules)";
        }
    }

    private static List<Preset> all;

    public static List<Preset> all() {
        if (all != null) return all;
        List<Preset> p = new ArrayList<>();
        p.add(new Preset("essentials", "Essentials", "The basics for every mode: FPS, CPS, ping, coordinates, keystrokes, armor and potion effects.", "star",
                "fps", "cps", "ping", "coords", "keystrokes", "armor", "effects", "togglesprint", "zoom"));
        p.add(new Preset("crystal", "Crystal PvP", "Totems, crystals, pops and your enemy at a glance, plus pearl and anchor warnings.", "crystal",
                "fps", "cps", "ping", "keystrokes", "totems", "armor", "effects", "targethud", "gm2_crystal_crystalcounter", "gm2_crystal_totempopcounter",
                "gm2_crystal_enemypopcounter", "gm2_crystal_toteminoffhand", "cb_popcounter", "cb_enemyarmor", "cb_enemyhands", "cb_surround", "cb_targethole",
                "cb_lastpop", "cb_crystalspeed", "cb_pearlalert", "cb_anchoralert", "cb_offhandtotem", "vx_popfeed"));
        p.add(new Preset("sword", "Sword PvP", "Combo, reach, crits and your sword stats with a charge bar and hit sounds.", "sword",
                "fps", "cps", "ping", "keystrokes", "combo", "reach", "targethud", "armor", "gm2_sword_combonow", "gm2_sword_bestcombo", "cb_swaccuracy",
                "cb_swdamage", "cb_swhitstokill", "cb_swknockback", "cb_chargebar", "cb_swcritsound", "cb_jumpreset", "vx_dmgnumbers"));
        p.add(new Preset("mace", "Mace PvP", "Fall height, smash damage, wind charges and a warning when a mace is falling at you.", "axes",
                "fps", "cps", "ping", "keystrokes", "armor", "effects", "gm2_mace_fallheight", "gm2_mace_smashready", "gm2_mace_windchargecounter",
                "cb_maceinfo", "cb_smashpredict", "cb_smashcounter", "cb_targetbelow", "cb_groundheight", "cb_macedanger", "cb_smashsound"));
        p.add(new Preset("smp", "Diamond SMP", "Potions, gapples, shields and combat log detection for SMP fights.", "gem",
                "fps", "ping", "coords", "direction", "armor", "effects", "pots", "gapples", "totems", "cb_combattimer", "cb_combatlog", "cb_bowalert",
                "cb_enemyeating", "cb_enemylow", "cb_lowpotions", "cb_shieldstatus", "cb_targetshield", "cb_splashalert", "cb_armorwarn"));
        p.add(new Preset("bedwars", "Bedwars", "Blocks, resources, kills and deaths, with a fireball reminder.", "flag",
                "fps", "cps", "ping", "keystrokes", "armor", "effects", "blocks", "gm2_bedwars_ironforwool", "gm2_bedwars_hotbarblocks",
                "gm2_bedwars_killcounter", "gm2_bedwars_deathcounter", "gm2_bedwars_lowblocksalert", "gm2_bedwars_fireballreadyalert", "vx_killfeed"));
        p.add(new Preset("skywars", "Skywars", "Armor value, pearls, bow readiness and players left.", "cloud",
                "fps", "cps", "ping", "keystrokes", "armor", "effects", "arrows", "pearls", "gm2_skywars_armorvalue", "gm2_skywars_playersleft",
                "gm2_skywars_bowready", "gm2_skywars_islandedge", "gm2_skywars_killcounter", "vx_killfeed"));
        p.add(new Preset("uhc", "UHC", "Exact health, golden heads and apples, border distance and regeneration.", "heart",
                "fps", "ping", "coords", "armor", "effects", "gapples", "gm2_uhc_healthexact", "gm2_uhc_goldenheadcounter", "gm2_uhc_goldforapples",
                "gm2_uhc_worldborderdistance", "gm2_uhc_regenleft", "gm2_uhc_borderclosealert", "cb_border"));
        p.add(new Preset("bridge", "Bridge & Parkour", "Speed, momentum, air time and block counts for bridging.", "cube",
                "fps", "cps", "keystrokes", "speed", "gm2_bridge_horizontalspeed", "gm2_bridge_momentum", "gm2_bridge_airtime", "gm2_bridge_sneakedge",
                "gm2_bridge_blockcounter", "gm2_bridge_outofblockssoon", "cb_snapaxis", "cb_blocks"));
        p.add(new Preset("survival", "Survival", "Coordinates, biome, day and night, light level and the warnings you want while exploring.", "pickaxe",
                "fps", "coords", "direction", "biome", "clock", "lightlevel", "deathcoords", "waypoints", "gm2_survival_cansleep",
                "gm2_survival_distancefromspawn", "cb_spawn", "cb_creeper", "cb_lava", "cb_drowning", "cb_hunger", "cb_elytra"));
        p.add(new Preset("tiktok", "TikTok Edit", "The viral effects for clips: streak announcer, kill cam, phonk kill, impact frames, camera punch and aura.", "sparkle",
                "vx_streak", "vx_killcam", "vx_phonk", "vx_impact", "vx_killzoom", "vx_shake", "vx_aura", "vx_lightning", "vx_dmgnumbers", "vx_combo"));
        p.add(new Preset("fps", "Max FPS", "Turns off clouds, shadows, smooth lighting and other costly effects for the most frames.", "bolt",
                "cb_noclouds", "cb_noshadows", "cb_nosmoothlighting", "cb_nobiomeblend", "cb_minparticles", "cb_vsyncoff", "cb_unlimitedfps",
                "cb_nomenublur", "unfocusedfps", "entitydistance", "particlelimiter"));
        all = p;
        return p;
    }
}
