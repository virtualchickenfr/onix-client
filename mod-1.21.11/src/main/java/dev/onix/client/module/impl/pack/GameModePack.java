package dev.onix.client.module.impl.pack;

import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.module.ItemCounterModule;
import dev.onix.client.module.Module;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.InventoryUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Pack 1: 100 small modules for Crystal PvP, Sword PvP, SMP / Diamond SMP, UHC, Bedwars and Survival.
 * Only information and reminders – nothing that plays for you (no aim, no auto-clicking, no automatic totems).
 * Three kinds: item counters, info lines and alerts (a notification and a sound when something happens).
 */
public final class GameModePack {
    private GameModePack() {}

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static LocalPlayer player() {
        return mc().player;
    }

    // ================================================================== kinds

    /** Icon and count of matching items in the inventory. */
    static final class Counter extends ItemCounterModule {
        private final Item icon;
        private final Predicate<ItemStack> match;

        Counter(String id, String name, String description, Item icon, Predicate<ItemStack> match, int slot, String... tags) {
            super("gm_" + id, name, description, Anchor.BOTTOM_RIGHT, -4 - (slot / 8) * 34, -120 - (slot % 8) * 14);
            this.icon = icon;
            this.match = match;
            tags(tags);
        }

        @Override
        protected Item icon() {
            return icon;
        }

        @Override
        protected int count() {
            return player() == null ? 0 : InventoryUtil.count(match);
        }
    }

    /** One "label value" line; the value is null when there is nothing to show. */
    static final class Info extends TextHudModule {
        private final String label;
        private final Supplier<String> value;

        Info(String id, String name, String description, Category category, String label, Supplier<String> value, int slot, String... tags) {
            super("gm_" + id, name, description, category, false, Anchor.TOP_LEFT, 4 + (slot / 12 % 4) * 96, 64 + (slot % 12) * 13);
            this.label = label;
            this.value = value;
            tags(tags);
        }

        private String safe() {
            if (player() == null || mc().level == null) return null;
            try {
                return value.get();
            } catch (RuntimeException e) {
                return null;
            }
        }

        @Override
        public boolean hasContent() {
            return safe() != null;
        }

        @Override
        protected void lines(List<Line> out, boolean preview) {
            String v = safe();
            out.add(new Line(label, v == null ? (preview ? "–" : "") : v));
        }
    }

    /** Shows a notification (and plays a sound) when the condition turns true. */
    static final class Alert extends Module {
        private final BooleanSupplier condition;
        private final Supplier<String> text;
        private final BoolSetting sound = add(new BoolSetting("sound", "Sound", true));
        private final NumberSetting cooldown = add(new NumberSetting("cooldown", "Not Again For", 10, 0, 120, 5).unit("s")
                .describe("Waits this long before the same alert can show again."));
        private boolean was;
        private long last;

        Alert(String id, String name, String description, Category category, BooleanSupplier condition, Supplier<String> text, String... tags) {
            super("gm_" + id, name, description, category, false);
            this.condition = condition;
            this.text = text;
            tags(tags);
        }

        @Override
        public void onTick() {
            if (player() == null || mc().level == null) {
                was = false;
                return;
            }
            boolean now;
            try {
                now = condition.getAsBoolean();
            } catch (RuntimeException e) {
                now = false;
            }
            long t = System.currentTimeMillis();
            if (now && !was && t - last > cooldown.get() * 1000) {
                last = t;
                Notifications.show(getName(), text.get());
                if (sound.isOn()) mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.4f, 0.8f));
            }
            was = now;
        }
    }

    // ================================================================== helpers

    private static Predicate<ItemStack> is(Item... items) {
        return s -> {
            for (Item i : items) if (s.is(i)) return true;
            return false;
        };
    }

    private static int count(Item... items) {
        return player() == null ? 0 : InventoryUtil.count(items);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static long dayTime() {
        return mc().level.getDayTime() % 24000L;
    }

    private static String clock(long ticks) {
        long s = Math.max(0, ticks / 20);
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    private static int freeSlots() {
        int free = 0;
        for (int i = 0; i < 36; i++) if (player().getInventory().getItem(i).isEmpty()) free++;
        return free;
    }

    private static boolean hasEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        return player().hasEffect(effect);
    }

    private static String bestOre(int y) {
        if (y >= 80) return "Emerald (mountains)";
        if (y >= 40) return "Coal · Copper";
        if (y >= 0) return "Iron · Lapis · Gold";
        if (y >= -64 && y <= -50) return "Diamond (best: -58)";
        if (y < 0) return "Diamond · Redstone";
        return "-";
    }

    // ================================================================== the 100 modules

    public static List<Module> modules() {
        List<Module> m = new ArrayList<>();
        int c = 0;
        // ---- item counters (50)
        m.add(new Counter("crystals", "Crystal Counter", "Crystal PvP: end crystals in your inventory.", Items.END_CRYSTAL, is(Items.END_CRYSTAL), c++, "crystal pvp", "cpvp"));
        m.add(new Counter("obsidian", "Obsidian Counter", "Crystal PvP: obsidian to place crystals on.", Items.OBSIDIAN, is(Items.OBSIDIAN), c++, "crystal pvp", "obby"));
        m.add(new Counter("anchors", "Anchor Counter", "Crystal PvP: respawn anchors.", Items.RESPAWN_ANCHOR, is(Items.RESPAWN_ANCHOR), c++, "crystal pvp", "anchor pvp"));
        m.add(new Counter("glowstone", "Glowstone Counter", "Crystal PvP: glowstone to charge anchors.", Items.GLOWSTONE, is(Items.GLOWSTONE), c++, "crystal pvp", "anchor"));
        m.add(new Counter("xpbottles", "XP Bottle Counter", "Crystal PvP / SMP: bottles o' enchanting for mending.", Items.EXPERIENCE_BOTTLE, is(Items.EXPERIENCE_BOTTLE), c++, "crystal pvp", "mending", "exp"));
        m.add(new Counter("godapples", "God Apple Counter", "Enchanted golden apples.", Items.ENCHANTED_GOLDEN_APPLE, is(Items.ENCHANTED_GOLDEN_APPLE), c++, "crystal pvp", "uhc", "notch apple"));
        m.add(new Counter("chorus", "Chorus Counter", "Chorus fruit to escape.", Items.CHORUS_FRUIT, is(Items.CHORUS_FRUIT), c++, "crystal pvp", "escape"));
        m.add(new Counter("shulkers", "Shulker Counter", "SMP / Crystal PvP: shulker boxes you carry.", Items.SHULKER_BOX,
                s -> s.getItem() instanceof BlockItem b && b.getBlock() instanceof ShulkerBoxBlock, c++, "smp", "crystal pvp", "kits"));
        m.add(new Counter("echests", "Ender Chest Counter", "SMP: ender chests you carry.", Items.ENDER_CHEST, is(Items.ENDER_CHEST), c++, "smp", "diamond smp"));
        m.add(new Counter("cobwebs", "Cobweb Counter", "Sword PvP / UHC: cobwebs.", Items.COBWEB, is(Items.COBWEB), c++, "sword pvp", "uhc", "webs"));
        m.add(new Counter("tnt", "TNT Counter", "Bedwars / Crystal PvP: TNT.", Items.TNT, is(Items.TNT), c++, "bedwars", "tnt"));
        m.add(new Counter("windcharges", "Wind Charge Counter", "Mace PvP: wind charges.", Items.WIND_CHARGE, is(Items.WIND_CHARGE), c++, "mace pvp", "wind charge"));
        m.add(new Counter("snowballs", "Snowball Counter", "Snowballs for knockback.", Items.SNOWBALL, is(Items.SNOWBALL), c++, "sword pvp", "bedwars"));
        m.add(new Counter("eggs", "Egg Counter", "Eggs (bridge eggs in some modes).", Items.EGG, is(Items.EGG), c++, "bedwars", "skywars"));
        m.add(new Counter("fireworks", "Firework Counter", "Elytra rockets.", Items.FIREWORK_ROCKET, is(Items.FIREWORK_ROCKET), c++, "elytra", "smp", "rockets"));
        m.add(new Counter("waterbuckets", "Water Bucket Counter", "MLG / UHC: water buckets.", Items.WATER_BUCKET, is(Items.WATER_BUCKET), c++, "uhc", "mlg", "sword pvp"));
        m.add(new Counter("lavabuckets", "Lava Bucket Counter", "UHC / Sword PvP: lava buckets.", Items.LAVA_BUCKET, is(Items.LAVA_BUCKET), c++, "uhc", "sword pvp"));
        m.add(new Counter("goldencarrots", "Golden Carrot Counter", "Best everyday food.", Items.GOLDEN_CARROT, is(Items.GOLDEN_CARROT), c++, "food", "smp", "pvp"));
        m.add(new Counter("steak", "Steak Counter", "Cooked beef and porkchops.", Items.COOKED_BEEF, is(Items.COOKED_BEEF, Items.COOKED_PORKCHOP), c++, "food", "smp"));
        m.add(new Counter("bread", "Bread Counter", "Bread.", Items.BREAD, is(Items.BREAD), c++, "food"));
        m.add(new Counter("food", "Food Counter", "All food in your inventory.", Items.APPLE, s -> s.has(DataComponents.FOOD), c++, "food", "survival", "hunger"));
        m.add(new Counter("wool", "Wool Counter", "Bedwars: wool for bridging.", Items.WHITE_WOOL, s -> s.is(ItemTags.WOOL), c++, "bedwars", "bridge"));
        m.add(new Counter("ladders", "Ladder Counter", "Ladders.", Items.LADDER, is(Items.LADDER), c++, "bedwars", "building"));
        m.add(new Counter("scaffolding", "Scaffolding Counter", "Scaffolding.", Items.SCAFFOLDING, is(Items.SCAFFOLDING), c++, "building", "smp"));
        m.add(new Counter("torches", "Torch Counter", "Torches for caving.", Items.TORCH, is(Items.TORCH, Items.SOUL_TORCH), c++, "caving", "survival"));
        m.add(new Counter("coal", "Coal Counter", "Coal and charcoal.", Items.COAL, is(Items.COAL, Items.CHARCOAL), c++, "survival", "fuel"));
        m.add(new Counter("redstone", "Redstone Counter", "Redstone dust.", Items.REDSTONE, is(Items.REDSTONE), c++, "redstone", "smp"));
        m.add(new Counter("slime", "Slime Ball Counter", "Slime balls.", Items.SLIME_BALL, is(Items.SLIME_BALL), c++, "redstone", "smp"));
        m.add(new Counter("bonemeal", "Bone Meal Counter", "Bone meal for farming.", Items.BONE_MEAL, is(Items.BONE_MEAL), c++, "farming", "smp"));
        m.add(new Counter("wheat", "Wheat Counter", "Wheat for breeding and bread.", Items.WHEAT, is(Items.WHEAT), c++, "farming"));
        m.add(new Counter("sugarcane", "Sugar Cane Counter", "Sugar cane for paper.", Items.SUGAR_CANE, is(Items.SUGAR_CANE), c++, "farming", "trading"));
        m.add(new Counter("endereyes", "Eye of Ender Counter", "Eyes of ender for the stronghold.", Items.ENDER_EYE, is(Items.ENDER_EYE), c++, "speedrun", "survival"));
        m.add(new Counter("blazerods", "Blaze Rod Counter", "Blaze rods.", Items.BLAZE_ROD, is(Items.BLAZE_ROD), c++, "speedrun", "nether"));
        m.add(new Counter("netherwart", "Nether Wart Counter", "Nether wart for brewing.", Items.NETHER_WART, is(Items.NETHER_WART), c++, "brewing"));
        m.add(new Counter("gunpowder", "Gunpowder Counter", "Gunpowder for rockets and TNT.", Items.GUNPOWDER, is(Items.GUNPOWDER), c++, "elytra", "smp"));
        m.add(new Counter("string", "String Counter", "String.", Items.STRING, is(Items.STRING), c++, "survival"));
        m.add(new Counter("leather", "Leather Counter", "Leather for books.", Items.LEATHER, is(Items.LEATHER), c++, "survival", "enchanting"));
        m.add(new Counter("paper", "Paper Counter", "Paper for rockets and trading.", Items.PAPER, is(Items.PAPER), c++, "trading", "elytra"));
        m.add(new Counter("books", "Book Counter", "Books and enchanted books.", Items.BOOK, is(Items.BOOK, Items.ENCHANTED_BOOK), c++, "enchanting", "trading"));
        m.add(new Counter("nametags", "Name Tag Counter", "Name tags.", Items.NAME_TAG, is(Items.NAME_TAG), c++, "smp"));
        m.add(new Counter("amethyst", "Amethyst Counter", "Amethyst shards.", Items.AMETHYST_SHARD, is(Items.AMETHYST_SHARD), c++, "smp", "building"));
        m.add(new Counter("copper", "Copper Counter", "Copper ingots.", Items.COPPER_INGOT, is(Items.COPPER_INGOT), c++, "smp", "resources"));
        m.add(new Counter("quartz", "Quartz Counter", "Nether quartz.", Items.QUARTZ, is(Items.QUARTZ), c++, "nether", "building"));
        m.add(new Counter("debris", "Ancient Debris Counter", "Diamond SMP / SMP: ancient debris.", Items.ANCIENT_DEBRIS, is(Items.ANCIENT_DEBRIS), c++, "diamond smp", "netherite"));
        m.add(new Counter("scrap", "Netherite Scrap Counter", "Netherite scrap.", Items.NETHERITE_SCRAP, is(Items.NETHERITE_SCRAP), c++, "diamond smp", "netherite"));
        m.add(new Counter("diamondblocks", "Diamond Block Counter", "Diamond SMP: diamond blocks (9 diamonds each).", Items.DIAMOND_BLOCK, is(Items.DIAMOND_BLOCK), c++, "diamond smp"));
        m.add(new Counter("shells", "Shulker Shell Counter", "Shulker shells.", Items.SHULKER_SHELL, is(Items.SHULKER_SHELL), c++, "end", "smp"));
        m.add(new Counter("membranes", "Membrane Counter", "Phantom membranes for elytra repair.", Items.PHANTOM_MEMBRANE, is(Items.PHANTOM_MEMBRANE), c++, "elytra"));
        m.add(new Counter("breezerods", "Breeze Rod Counter", "Breeze rods (wind charges, mace).", Items.BREEZE_ROD, is(Items.BREEZE_ROD), c++, "mace pvp"));
        m.add(new Counter("milk", "Milk Counter", "Milk buckets to clear effects.", Items.MILK_BUCKET, is(Items.MILK_BUCKET), c++, "uhc", "pvp"));

        // ---- info lines (30)
        int i = 0;
        m.add(new Info("attackcooldown", "Attack Cooldown", "Sword PvP: how charged your next hit is.", Category.COMBAT, "Hit",
                () -> Math.round(player().getAttackStrengthScale(0f) * 100) + "%", i++, "sword pvp", "1.9 pvp"));
        m.add(new Info("armorpoints", "Armor Points", "Your armor value (max 20).", Category.COMBAT, "Armor",
                () -> String.valueOf(player().getArmorValue()), i++, "pvp", "armor"));
        m.add(new Info("toughness", "Armor Toughness", "Armor toughness (netherite and diamond).", Category.COMBAT, "Toughness",
                () -> fmt(player().getAttributeValue(Attributes.ARMOR_TOUGHNESS)), i++, "pvp", "crystal pvp"));
        m.add(new Info("absorption", "Absorption", "Golden hearts from apples and totems.", Category.COMBAT, "Absorption",
                () -> player().getAbsorptionAmount() > 0 ? fmt(player().getAbsorptionAmount() / 2) + " ♥" : null, i++, "pvp", "uhc"));
        m.add(new Info("bowcharge", "Bow Charge", "How far your bow is drawn.", Category.COMBAT, "Bow",
                () -> player().isUsingItem() && player().getUseItem().is(Items.BOW)
                        ? Math.min(100, Math.round(player().getTicksUsingItem() / 20f * 100)) + "%" : null, i++, "bow", "uhc", "skywars"));
        m.add(new Info("portalcoords", "Portal Coordinates", "Where you would be in the other dimension (Overworld ↔ Nether).", Category.HUD, "Other",
                () -> {
                    LocalPlayer p = player();
                    if (mc().level.dimension() == Level.NETHER) return (int) Math.floor(p.getX() * 8) + ", " + (int) Math.floor(p.getZ() * 8) + " (OW)";
                    if (mc().level.dimension() == Level.OVERWORLD) return (int) Math.floor(p.getX() / 8) + ", " + (int) Math.floor(p.getZ() / 8) + " (Nether)";
                    return null;
                }, i++, "nether", "portal", "smp"));
        m.add(new Info("orelevel", "Ore Level", "Diamond SMP: what ores are best at your height.", Category.HUD, "Ores",
                () -> bestOre(player().getBlockY()), i++, "diamond smp", "mining", "caving"));
        m.add(new Info("dimension", "Dimension", "Which dimension you are in.", Category.HUD, "World",
                () -> mc().level.dimension() == Level.NETHER ? "Nether" : mc().level.dimension() == Level.END ? "The End" : "Overworld", i++, "smp"));
        m.add(new Info("difficulty", "Difficulty", "The world's difficulty.", Category.HUD, "Difficulty",
                () -> { String d = mc().level.getDifficulty().name().toLowerCase(Locale.ROOT); return d.substring(0, 1).toUpperCase(Locale.ROOT) + d.substring(1); }, i++, "survival"));
        m.add(new Info("gamemode", "Game Mode", "Survival, Creative, Adventure or Spectator.", Category.HUD, "Mode",
                () -> { if (mc().gameMode == null) return null; String g = mc().gameMode.getPlayerMode().name().toLowerCase(Locale.ROOT); return g.substring(0, 1).toUpperCase(Locale.ROOT) + g.substring(1); }, i++, "creative"));
        m.add(new Info("movement", "Movement State", "Sprinting, sneaking, swimming, gliding or flying.", Category.HUD, "Move",
                () -> {
                    LocalPlayer p = player();
                    if (p.getAbilities().flying) return "Flying";
                    if (p.isFallFlying()) return "Gliding";
                    if (p.isSwimming()) return "Swimming";
                    if (p.isCrouching()) return "Sneaking";
                    if (p.isSprinting()) return "Sprinting";
                    return "Walking";
                }, i++, "sword pvp", "movement"));
        m.add(new Info("rotation", "Yaw & Pitch", "Exact view angles (for builds and portals).", Category.HUD, "Rot",
                () -> fmt(net.minecraft.util.Mth.wrapDegrees(player().getYRot())) + " / " + fmt(player().getXRot()), i++, "building", "angles"));
        m.add(new Info("nightin", "Time Until Night", "Real time until mobs spawn (or until morning).", Category.HUD, "Night",
                () -> { long t = dayTime(); return t < 12500 ? "in " + clock(12500 - t) : t < 23000 ? "Morning in " + clock(23000 - t) : "in " + clock(24000 - t + 12500); },
                i++, "survival", "smp", "time"));
        m.add(new Info("effectcount", "Effect Count", "How many effects you have.", Category.COMBAT, "Effects",
                () -> player().getActiveEffects().isEmpty() ? null : String.valueOf(player().getActiveEffects().size()), i++, "potions", "pvp"));
        m.add(new Info("firetime", "Burning Time", "How long you keep burning.", Category.COMBAT, "Fire",
                () -> player().getRemainingFireTicks() > 0 && !hasEffect(MobEffects.FIRE_RESISTANCE) ? clock(player().getRemainingFireTicks()) : null, i++, "uhc", "pvp"));
        m.add(new Info("freeze", "Freezing", "Powder snow freeze progress.", Category.HUD, "Freeze",
                () -> player().getTicksFrozen() > 0 ? Math.round(player().getPercentFrozen() * 100) + "%" : null, i++, "survival"));
        m.add(new Info("freeslots", "Free Slots", "Free inventory slots.", Category.UTILITY, "Free",
                () -> String.valueOf(freeSlots()), i++, "smp", "mining", "inventory"));
        m.add(new Info("offhand", "Offhand Item", "What you hold in your off hand.", Category.COMBAT, "Offhand",
                () -> player().getOffhandItem().isEmpty() ? "Empty" : player().getOffhandItem().getHoverName().getString(), i++, "crystal pvp", "totem"));
        m.add(new Info("regionfile", "Region File", "SMP admins: the region file you stand in.", Category.UTILITY, "Region",
                () -> "r." + Math.floorDiv(player().getBlockX(), 512) + "." + Math.floorDiv(player().getBlockZ(), 512) + ".mca", i++, "smp", "admin"));
        m.add(new Info("serverbrand", "Server Software", "What the server runs on (Paper, Fabric, …).", Category.UTILITY, "Server",
                () -> mc().getConnection() == null || mc().getConnection().serverBrand() == null ? null : mc().getConnection().serverBrand(), i++, "server"));
        m.add(new Info("version", "Game Version", "Your Minecraft version.", Category.HUD, "Version",
                () -> SharedConstants.getCurrentVersion().name(), i++, "version"));
        m.add(new Info("modcount", "Mod Count", "How many mods are loaded.", Category.UTILITY, "Mods",
                () -> String.valueOf(net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().size()), i++, "mods"));
        m.add(new Info("temperature", "Biome Temperature", "Temperature of the biome (snow, rain).", Category.HUD, "Temp",
                () -> fmt(mc().level.getBiome(player().blockPosition()).value().getBaseTemperature()), i++, "biome", "farming"));
        m.add(new Info("selectedslot", "Selected Slot", "Your selected hotbar slot (1–9).", Category.HUD, "Slot",
                () -> String.valueOf(player().getInventory().getSelectedSlot() + 1), i++, "hotbar", "pvp"));
        m.add(new Info("heldcount", "Held Stack", "How many of the held item you have in total.", Category.HUD, "Held",
                () -> player().getMainHandItem().isEmpty() ? null : String.valueOf(InventoryUtil.count(player().getMainHandItem().getItem())), i++, "building", "bedwars"));
        m.add(new Info("velocityy", "Vertical Speed", "Blocks per second up or down.", Category.HUD, "Y Speed",
                () -> fmt(player().getDeltaMovement().y * 20), i++, "elytra", "parkour"));
        m.add(new Info("onground", "On Ground", "Whether you stand on the ground (parkour, crits).", Category.COMBAT, "Ground",
                () -> player().onGround() ? "Yes" : "No", i++, "sword pvp", "crits", "parkour"));
        m.add(new Info("critready", "Crit Ready", "Sword PvP: a hit now would be a critical hit.", Category.COMBAT, "Crit",
                () -> {
                    LocalPlayer p = player();
                    boolean crit = p.fallDistance > 0 && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.isPassenger()
                            && !hasEffect(MobEffects.BLINDNESS) && p.getAttackStrengthScale(0f) > 0.9f;
                    return crit ? "Yes" : null;
                }, i++, "sword pvp", "crits"));
        m.add(new Info("hunger", "Hunger Bar", "Food points out of 20.", Category.HUD, "Food",
                () -> player().getFoodData().getFoodLevel() + "/20", i++, "survival", "uhc"));
        m.add(new Info("level", "XP Level", "Your experience level only.", Category.HUD, "Level",
                () -> String.valueOf(player().experienceLevel), i++, "enchanting", "mending"));

        // ---- alerts (20)
        m.add(new Alert("lowtotems", "Low Totem Alert", "Crystal PvP: warns when your last totem is in use.", Category.COMBAT,
                () -> count(Items.TOTEM_OF_UNDYING) == 1, () -> "Only one totem left!", "crystal pvp", "totem"));
        m.add(new Alert("nototems", "No Totems Alert", "Crystal PvP: you just used your last totem.", Category.COMBAT,
                new BooleanSupplier() {
                    private int before;

                    @Override
                    public boolean getAsBoolean() {
                        int n = count(Items.TOTEM_OF_UNDYING);
                        boolean out = before > 0 && n == 0;
                        before = n;
                        return out;
                    }
                }, () -> "Out of totems – leave the fight!", "crystal pvp", "totem"));
        m.add(new Alert("nocrystals", "Out of Crystals", "Crystal PvP: you have no end crystals left.", Category.COMBAT,
                new LostLast(Items.END_CRYSTAL), () -> "No end crystals left", "crystal pvp"));
        m.add(new Alert("nopearls", "Out of Pearls", "You threw your last ender pearl.", Category.COMBAT,
                new LostLast(Items.ENDER_PEARL), () -> "No ender pearls left", "pvp", "pearls"));
        m.add(new Alert("noarrows", "Out of Arrows", "Your last arrow is gone.", Category.COMBAT,
                new LostLast(Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW), () -> "No arrows left", "bow", "uhc"));
        m.add(new Alert("noblocks", "Out of Blocks", "Bedwars: you placed your last wool.", Category.COMBAT,
                new LostLastMatch(s -> s.is(ItemTags.WOOL)), () -> "No wool left", "bedwars"));
        m.add(new Alert("nofood", "Out of Food", "You have no food left.", Category.UTILITY,
                new LostLastMatch(s -> s.has(DataComponents.FOOD)), () -> "No food left", "survival", "uhc"));
        m.add(new Alert("invfull", "Inventory Full", "Mining: your inventory just filled up.", Category.UTILITY,
                () -> freeSlots() == 0, () -> "Inventory full", "mining", "smp", "diamond smp"));
        m.add(new Alert("lowhunger", "Low Hunger Alert", "Eat before you stop sprinting.", Category.UTILITY,
                () -> player().getFoodData().getFoodLevel() <= 6, () -> "Eat something – you can't sprint soon", "survival", "pvp"));
        m.add(new Alert("nightfall", "Nightfall Alert", "Tells you when night begins.", Category.UTILITY,
                () -> { long t = dayTime(); return t >= 12500 && t < 13500; }, () -> "Night is falling – mobs spawn now", "survival", "smp"));
        m.add(new Alert("thunder", "Thunder Alert", "A thunderstorm started (charged creepers, tridents).", Category.UTILITY,
                () -> mc().level.isThundering(), () -> "Thunderstorm!", "smp", "weather"));
        m.add(new Alert("onfire", "On Fire Alert", "You started burning.", Category.COMBAT,
                () -> player().getRemainingFireTicks() > 0 && !hasEffect(MobEffects.FIRE_RESISTANCE), () -> "You are on fire!", "uhc", "pvp"));
        m.add(new Alert("void", "Void Alert", "You are falling into the void.", Category.UTILITY,
                () -> player().getY() < mc().level.getMinY() + 4, () -> "You are falling into the void!", "skywars", "bedwars", "end"));
        m.add(new Alert("bigfall", "Fall Damage Alert", "Warns while you fall far (time for water).", Category.UTILITY,
                () -> player().fallDistance > 12 && !player().isFallFlying(), () -> "Big fall – place water!", "uhc", "mlg"));
        m.add(new Alert("effectending", "Effect Ending", "An effect runs out in 5 seconds.", Category.COMBAT,
                () -> {
                    for (MobEffectInstance e : player().getActiveEffects()) {
                        if (!e.isInfiniteDuration() && e.getDuration() > 0 && e.getDuration() <= 100) return true;
                    }
                    return false;
                }, () -> "An effect runs out soon", "potions", "pvp"));
        m.add(new Alert("wither", "Wither Alert", "You got the wither effect.", Category.COMBAT,
                () -> hasEffect(MobEffects.WITHER), () -> "You are withering!", "pvp", "boss"));
        m.add(new Alert("poison", "Poison Alert", "You got poisoned.", Category.COMBAT,
                () -> hasEffect(MobEffects.POISON), () -> "You are poisoned!", "pvp", "uhc"));
        m.add(new Alert("badomen", "Bad Omen Alert", "Raid omen: a raid starts in the next village.", Category.UTILITY,
                () -> hasEffect(MobEffects.BAD_OMEN) || hasEffect(MobEffects.RAID_OMEN), () -> "Bad omen – careful in villages", "smp", "raid"));
        m.add(new Alert("shieldbroken", "Shield Disabled Alert", "Sword PvP: your shield got disabled by an axe.", Category.COMBAT,
                () -> player().getCooldowns().isOnCooldown(new ItemStack(Items.SHIELD)), () -> "Shield disabled!", "sword pvp", "axe"));
        m.add(new Alert("playerjoin", "Player Join Alert", "Someone joined the server (from the tab list).", Category.UTILITY,
                new BooleanSupplier() {
                    private final Set<String> known = new HashSet<>();
                    private boolean first = true;

                    @Override
                    public boolean getAsBoolean() {
                        if (mc().getConnection() == null) return false;
                        Set<String> now = new HashSet<>();
                        for (var e : mc().getConnection().getOnlinePlayers()) now.add(e.getProfile().name());
                        boolean joined = !first && !known.containsAll(now);
                        known.clear();
                        known.addAll(now);
                        first = false;
                        return joined;
                    }
                }, () -> "A player joined", "smp", "server"));
        return m;
    }

    /** True once when the last item of a kind is gone. */
    private static final class LostLast implements BooleanSupplier {
        private final Item[] items;
        private int before;

        LostLast(Item... items) {
            this.items = items;
        }

        @Override
        public boolean getAsBoolean() {
            int n = count(items);
            boolean out = before > 0 && n == 0;
            before = n;
            return out;
        }
    }

    private static final class LostLastMatch implements BooleanSupplier {
        private final Predicate<ItemStack> match;
        private int before;

        LostLastMatch(Predicate<ItemStack> match) {
            this.match = match;
        }

        @Override
        public boolean getAsBoolean() {
            int n = InventoryUtil.count(match);
            boolean out = before > 0 && n == 0;
            before = n;
            return out;
        }
    }
}
