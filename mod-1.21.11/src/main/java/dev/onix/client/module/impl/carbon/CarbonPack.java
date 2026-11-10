package dev.onix.client.module.impl.carbon;

import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.module.impl.pack.Events;
import dev.onix.client.module.impl.pack.Events.Event;
import dev.onix.client.module.impl.pack.Kinds;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.ChatPipeline;
import dev.onix.client.util.CombatTracker;
import dev.onix.client.util.InventoryUtil;
import dev.onix.client.util.OptionOverride;
import dev.onix.client.util.OutgoingChat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The modules of Carbon Client 1.7 that Onix did not have yet: alerts, info lines, session stats, chat tools,
 * key actions, game option shortcuts and the PvP info of Carbon's Sword, Crystal, Mace and Diamond SMP packs.
 * Carbon's automation (auto armor, auto eat, swap macros, crystal/anchor helpers, hit guards) and the
 * see-through-walls renders (ESP, tracers, outlines) are left out on purpose: Onix only shows information.
 */
public final class CarbonPack {
    private CarbonPack() {}

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static LocalPlayer p() {
        return mc().player;
    }

    private static int slot = 400;

    private static Kinds.Info info(String id, String name, String desc, Category cat, String label, Supplier<String> value, String... tags) {
        return new Kinds.Info("cb_" + id, name, desc, cat, label, value, slot++, tags);
    }

    private static Kinds.Alert alert(String id, String name, String desc, Category cat, BooleanSupplier when, Supplier<String> text, String... tags) {
        return new Kinds.Alert("cb_" + id, name, desc, cat, when, text, tags);
    }

    private static Kinds.Alert alert(String id, String name, String desc, Category cat, BooleanSupplier when, String text, String... tags) {
        return alert(id, name, desc, cat, when, () -> text, tags);
    }

    public static List<Module> modules() {
        List<Module> m = new ArrayList<>();
        ClientTickEvents.END_CLIENT_TICK.register(c -> Track.tick());
        alerts(m);
        infos(m);
        stats(m);
        chat(m);
        keys(m);
        options(m);
        pvp(m);
        misc(m);
        m.addAll(CarbonVisuals.modules());
        return m;
    }

    // ================================================================== shared tracking (always running, cheap)

    static final class Track {
        static double distance, topSpeed;
        static int airTicks;
        static float damageTaken;
        static float lastHp = -1;
        static long lastMove = System.currentTimeMillis();
        static Vec3 lastPos;
        static float lastYaw, lastPitch;
        static final long sessionStart = System.currentTimeMillis();
        // sword
        static int swings, swordHits;
        static boolean wasSwinging;
        static float swordDamage, lastHitDamage = -1;
        static long lastHitAt, hitInterval = -1;
        static LivingEntity watching;
        static float watchHp;
        static int watchTicks;
        // mace
        static int smashes;
        static float bestSmash;
        // jump reset
        static long hurtTick = -1;
        static int jumpReset = -1;
        static long tick;
        // combat timer
        static long combatAt;
        // crystals per second
        static final List<Long> crystals = new ArrayList<>();
        static boolean hooked;

        static void hook() {
            if (hooked) return;
            hooked = true;
            Events.on(Event.HIT, (e, entity) -> {
                LocalPlayer pl = p();
                if (pl == null) return;
                combatAt = System.currentTimeMillis();
                if (pl.getMainHandItem().is(ItemTags.SWORDS)) {
                    swordHits++;
                    long now = System.currentTimeMillis();
                    if (lastHitAt > 0) hitInterval = now - lastHitAt;
                    lastHitAt = now;
                }
                if (entity instanceof LivingEntity l) {
                    watching = l;
                    watchHp = l.getHealth() + l.getAbsorptionAmount();
                    watchTicks = 10;
                }
                if (pl.getMainHandItem().is(Items.MACE) && pl.fallDistance > 1.5) {
                    smashes++;
                    bestSmash = Math.max(bestSmash, (float) pl.fallDistance);
                }
            });
            Events.on(Event.DAMAGE, (e, entity) -> {
                combatAt = System.currentTimeMillis();
                hurtTick = tick;
            });
            Events.on(Event.JUMP, (e, entity) -> {
                if (hurtTick >= 0 && tick - hurtTick < 20) jumpReset = (int) (tick - hurtTick);
                hurtTick = -1;
            });
            Events.on(Event.CRYSTAL, (e, entity) -> crystals.add(System.currentTimeMillis()));
        }

        static void tick() {
            hook();
            tick++;
            LocalPlayer pl = p();
            if (pl == null || mc().level == null) {
                lastPos = null;
                lastHp = -1;
                return;
            }
            Vec3 pos = pl.position();
            if (lastPos != null) {
                double d = pos.distanceTo(lastPos);
                if (d < 10) distance += d;
                topSpeed = Math.max(topSpeed, Math.hypot(pos.x - lastPos.x, pos.z - lastPos.z) * 20);
                if (d > 0.01 || pl.getYRot() != lastYaw || pl.getXRot() != lastPitch) lastMove = System.currentTimeMillis();
            }
            lastPos = pos;
            lastYaw = pl.getYRot();
            lastPitch = pl.getXRot();
            if (!pl.onGround() && !pl.isInWater()) airTicks++;
            float hp = pl.getHealth();
            if (lastHp >= 0 && hp < lastHp) damageTaken += lastHp - hp;
            lastHp = hp;
            // swings with a sword (hit or miss)
            boolean swinging = pl.swinging;
            if (swinging && !wasSwinging && pl.getMainHandItem().is(ItemTags.SWORDS)) swings++;
            wasSwinging = swinging;
            // what the last hit really did
            if (watching != null) {
                float now = watching.getHealth() + watching.getAbsorptionAmount();
                if (now < watchHp - 0.05f) {
                    lastHitDamage = watchHp - now;
                    if (pl.getMainHandItem().is(ItemTags.SWORDS)) swordDamage += lastHitDamage;
                    watching = null;
                } else if (--watchTicks <= 0) {
                    watching = null;
                }
            }
            long t = System.currentTimeMillis();
            crystals.removeIf(c -> t - c > 3000);
        }
    }

    // ================================================================== helpers

    private static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String pct(ItemStack s) {
        if (s.isEmpty() || !s.isDamageableItem()) return null;
        return Math.round(100f * (s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage()) + "%";
    }

    private static float left(ItemStack s) {
        if (s.isEmpty() || !s.isDamageableItem()) return 1;
        return (s.getMaxDamage() - s.getDamageValue()) / (float) s.getMaxDamage();
    }

    private static int ench(ItemStack s, ResourceKey<Enchantment> key) {
        for (var e : s.getEnchantments().entrySet()) if (e.getKey().is(key)) return e.getIntValue();
        return 0;
    }

    private static boolean dim(ResourceKey<Level> d) {
        return mc().level.dimension() == d;
    }

    private static LivingEntity target() {
        Entity t = CombatTracker.target();
        if (t instanceof LivingEntity l && l.isAlive() && System.currentTimeMillis() - CombatTracker.lastHitTime() < 15000) return l;
        HitResult hr = mc().hitResult;
        if (hr instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity l) return l;
        return null;
    }

    private static Player targetPlayer() {
        return target() instanceof Player pl ? pl : null;
    }

    private static boolean near(Class<?> type, double range) {
        for (Entity e : mc().level.entitiesForRendering()) {
            if (e != p() && type.isInstance(e) && e.distanceTo(p()) <= range) return true;
        }
        return false;
    }

    private static boolean nearType(EntityType<?> type, double range) {
        for (Entity e : mc().level.entitiesForRendering()) {
            if (e.getType() == type && e.distanceTo(p()) <= range) return true;
        }
        return false;
    }

    private static Player nearestPlayer(double range) {
        Player best = null;
        double bd = range;
        for (Player o : mc().level.players()) {
            if (o == p() || o.isSpectator()) continue;
            double d = o.distanceTo(p());
            if (d <= bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    /** Damage after armor with the vanilla formula. */
    private static float afterArmor(float dmg, LivingEntity t, float armorScale) {
        float armor = (float) t.getAttributeValue(Attributes.ARMOR) * armorScale;
        float tough = (float) t.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float f = Mth.clamp(armor - dmg / (2 + tough / 4f), armor * 0.2f, 20f);
        return dmg * (1 - f / 25f);
    }

    /** Damage of a full hit with the held weapon, sharpness and strength included. */
    private static float fullHit() {
        LocalPlayer pl = p();
        float base = (float) pl.getAttributeValue(Attributes.ATTACK_DAMAGE);
        int sharp = ench(pl.getMainHandItem(), Enchantments.SHARPNESS);
        if (sharp > 0) base += 0.5f * sharp + 0.5f;
        return base;
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        mc().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    private static String dir(Entity e) {
        double dx = e.getX() - p().getX();
        double dz = e.getZ() - p().getZ();
        String[] n = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double ang = Math.toDegrees(Math.atan2(-dx, dz));
        int i = Math.floorMod((int) Math.round(ang / 45.0), 8);
        return n[i] + ", " + Math.round(Math.hypot(dx, dz)) + " blocks";
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private static void local(String text) {
        mc().gui.getChat().addMessage(Component.literal(text));
    }

    // ================================================================== alerts

    private static void alerts(List<Module> m) {
        Category u = Category.UTILITY;
        Category c = Category.COMBAT;
        m.add(alert("afk", "AFK Reminder", "Reminds you when you haven't moved for 5 minutes.", u,
                () -> System.currentTimeMillis() - Track.lastMove > 300_000, "You have been AFK for 5 minutes", "afk", "idle"));
        long[] breakAt = {System.currentTimeMillis()};
        m.add(alert("break", "Break Reminder", "Reminds you to take a short break every hour.", u, () -> {
            if (System.currentTimeMillis() - breakAt[0] > 3_600_000L) {
                breakAt[0] = System.currentTimeMillis();
                return true;
            }
            return false;
        }, "You have played for an hour. Time for a short break?", "break", "health"));
        m.add(alert("blindness", "Blindness Alert", "Warns when you are blinded.", c, () -> p().hasEffect(MobEffects.BLINDNESS), "You are blinded!", "effect"));
        m.add(alert("darkness", "Darkness Alert", "Warns when a warden's darkness hits you.", u, () -> p().hasEffect(MobEffects.DARKNESS), "Darkness: a warden is close", "warden"));
        m.add(alert("levitation", "Levitation Alert", "Warns when you start levitating.", c, () -> p().hasEffect(MobEffects.LEVITATION), "You are levitating!", "shulker"));
        m.add(alert("creeper", "Creeper Warning", "Warns when a creeper is close.", u,
                () -> nearType(EntityType.CREEPER, 6), "Creeper nearby!", "creeper", "mob"));
        m.add(alert("hostile", "Hostile Mob Warning", "Warns when any hostile mob gets close.", u, () -> near(Enemy.class, 6), "Hostile mob nearby", "mob"));
        m.add(alert("phantom", "Phantom Warning", "Warns when phantoms are around.", u, () -> nearType(EntityType.PHANTOM, 24), "Phantoms are around", "phantom"));
        m.add(alert("warden", "Warden Warning", "Warns when a warden is nearby.", u, () -> nearType(EntityType.WARDEN, 32), "A warden is nearby!", "warden"));
        m.add(alert("tnt", "TNT Warning", "Warns about lit TNT close to you.", u, () -> nearType(EntityType.TNT, 8), "Lit TNT nearby!", "tnt"));
        String[] lastDim = {null};
        m.add(alert("dimension", "Dimension Change", "Tells you which dimension you just entered.", u, () -> {
            String d = mc().level.dimension() == Level.NETHER ? "the Nether" : mc().level.dimension() == Level.END ? "the End" : "the Overworld";
            boolean changed = lastDim[0] != null && !lastDim[0].equals(d);
            lastDim[0] = d;
            return changed;
        }, () -> "You entered " + lastDim[0], "dimension", "nether", "end"));
        m.add(alert("drowning", "Drowning Warning", "Warns when you are running out of air.", u, () -> p().getAirSupply() < 90, "Running out of air!", "water", "air"));
        m.add(alert("elytra", "Elytra Warning", "Warns when your elytra is nearly broken.", u, () -> {
            ItemStack chest = p().getItemBySlot(EquipmentSlot.CHEST);
            return chest.is(Items.ELYTRA) && left(chest) < 0.1f;
        }, "Your elytra is about to break!", "elytra"));
        m.add(alert("fall", "Fall Warning", "Warns when a fall is getting dangerous.", u,
                () -> p().fallDistance > 12 && !p().isFallFlying() && !p().isInWater() && !p().getAbilities().flying, "Dangerous fall!", "fall"));
        m.add(alert("burning", "Burning Warning", "Warns when you catch fire.", u,
                () -> p().isOnFire() && !p().hasEffect(MobEffects.FIRE_RESISTANCE), "You are burning!", "fire"));
        m.add(alert("freezing", "Freezing Warning", "Warns when powder snow starts freezing you.", u, () -> p().getTicksFrozen() > 40, "You are freezing!", "snow"));
        m.add(alert("hunger", "Hunger Warning", "Warns when your food bar runs low.", u, () -> p().getFoodData().getFoodLevel() <= 6, "You are getting hungry", "food"));
        m.add(alert("lava", "Lava Warning", "Warns when there is lava right next to you.", u, () -> {
            BlockPos b = p().blockPosition();
            for (BlockPos q : BlockPos.betweenClosed(b.offset(-1, -1, -1), b.offset(1, 1, 1))) {
                if (mc().level.getFluidState(q).is(FluidTags.LAVA)) return true;
            }
            return false;
        }, "Lava right next to you!", "lava"));
        m.add(Kinds.Alert.of("cb_levelup", "Level Up Alert", "Tells you when you reach a new XP level.", u, Event.LEVEL_UP, "Level up!", "xp"));
        m.add(alert("memory", "Memory Warning", "Warns when Minecraft is close to running out of memory.", Category.PERFORMANCE, () -> {
            Runtime r = Runtime.getRuntime();
            return (r.totalMemory() - r.freeMemory()) > r.maxMemory() * 0.92;
        }, "Memory is almost full, the game may stutter", "ram", "memory"));
        m.add(alert("lowfps", "Low FPS Warning", "Warns when your frame rate drops below 30.", Category.PERFORMANCE, () -> mc().getFps() < 30, () -> "FPS dropped to " + mc().getFps(), "fps", "lag"));
        m.add(alert("pingspike", "Ping Spike Warning", "Warns when your ping goes above 250 ms.", u, () -> {
            var c2 = mc().getConnection();
            var me = c2 == null ? null : c2.getPlayerInfo(p().getUUID());
            return me != null && me.getLatency() > 250;
        }, "Your ping spiked above 250 ms", "ping", "lag"));
        Set<UUID> seen = new HashSet<>();
        String[] newest = {""};
        m.add(alert("radar", "Player Radar", "Tells you whenever a new player enters your render distance.", c, () -> {
            boolean found = false;
            for (Player o : mc().level.players()) {
                if (o == p()) continue;
                if (seen.add(o.getUUID())) {
                    newest[0] = o.getName().getString();
                    found = true;
                }
            }
            if (seen.size() > 500) seen.clear();
            return found;
        }, () -> newest[0] + " is in render distance", "radar", "players"));
        String[] close = {""};
        m.add(alert("proximity", "Player Proximity", "Warns when another player comes within 10 blocks.", c, () -> {
            Player n = nearestPlayer(10);
            if (n != null) close[0] = n.getName().getString();
            return n != null;
        }, () -> close[0] + " is close to you", "players"));
        m.add(alert("offhandtotem", "Offhand Totem Missing", "Warns when your offhand holds no totem but you still carry one.", c,
                () -> !p().getOffhandItem().is(Items.TOTEM_OF_UNDYING) && InventoryUtil.count(Items.TOTEM_OF_UNDYING) > 0, "No totem in your offhand!", "totem", "crystal pvp"));
        m.add(alert("void", "Void Warning", "Warns when you fall towards the void.", u,
                () -> p().getY() < mc().level.getMinY() + 8 && p().getDeltaMovement().y < 0, "You are falling into the void!", "void", "end"));
        m.add(alert("armorwarn", "Armor Warning", "Warns you when a piece of armor is about to break.", c, () -> {
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack st = p().getItemBySlot(s);
                if (!st.is(Items.ELYTRA) && st.isDamageableItem() && left(st) < 0.1f) return true;
            }
            return false;
        }, "A piece of your armor is about to break!", "armor"));
    }

    // ================================================================== info lines

    private static void infos(List<Module> m) {
        Category h = Category.HUD;
        m.add(info("spawn", "Spawn Indicator", "Whether hostile mobs can spawn where you stand (block light 0).", h, "Spawns",
                () -> mc().level.getBrightness(LightLayer.BLOCK, p().blockPosition()) == 0 ? "Mobs can spawn" : "Safe", "mobs", "light"));
        m.add(info("nearby", "Nearby Players", "Players within render distance.", h, "Nearby", () -> String.valueOf(Math.max(0, mc().level.players().size() - 1)), "players"));
        m.add(info("targetblock", "Target Block", "The block under your crosshair.", h, "Block", () -> mc().hitResult instanceof BlockHitResult b && mc().hitResult.getType() == HitResult.Type.BLOCK
                ? mc().level.getBlockState(b.getBlockPos()).getBlock().getName().getString() : null, "block"));
        m.add(info("targetdistance", "Target Distance", "Distance to what you are looking at.", h, "Distance", () -> mc().hitResult == null || mc().hitResult.getType() == HitResult.Type.MISS
                ? null : f1(mc().hitResult.getLocation().distanceTo(p().getEyePosition())) + " m", "distance"));
        m.add(info("targetentity", "Target Entity", "The entity under your crosshair.", h, "Looking at",
                () -> mc().hitResult instanceof EntityHitResult e ? e.getEntity().getName().getString() : null, "entity"));
        m.add(info("border", "Border Distance", "Distance to the world border.", h, "Border",
                () -> Math.round(mc().level.getWorldBorder().getDistanceToBorder(p())) + " blocks", "border"));
        m.add(info("food", "Food Items", "How much food you carry.", h, "Food", () -> String.valueOf(InventoryUtil.count(s -> s.has(DataComponents.FOOD))), "food"));
        m.add(info("jumpreset", "Jump Reset", "How many ticks after a hit you jumped (0-2 is a good reset).", Category.COMBAT, "Reset",
                () -> Track.jumpReset < 0 ? "-" : Track.jumpReset + " ticks", "jump reset", "sword pvp"));
    }

    // ================================================================== session stats

    private static void stats(List<Module> m) {
        Category h = Category.HUD;
        m.add(info("distance", "Distance Travelled", "Distance you moved this session.", h, "Travelled", () -> Math.round(Track.distance) + " m", "stats"));
        m.add(info("topspeed", "Top Speed", "The fastest you moved this session.", h, "Top speed", () -> f1(Track.topSpeed) + " b/s", "stats", "speed"));
        m.add(info("damagetaken", "Damage Taken", "Health points lost this session.", h, "Damage taken", () -> f1(Track.damageTaken / 2) + " ❤", "stats"));
    }

    // ================================================================== chat

    static final class ChatMod extends Module {
        ChatMod(String id, String name, String desc, Function<ChatMod, ChatPipeline.Processor> processor, String... tags) {
            super("cb_" + id, name, desc, Category.CHAT, false);
            tags(tags);
            ChatPipeline.Processor inner = processor.apply(this);
            ChatPipeline.register(new ChatPipeline.Processor() {
                public int priority() {
                    return inner.priority();
                }

                public Component process(Component message) {
                    return isEnabled() ? inner.process(message) : message;
                }
            });
        }

        <S extends dev.onix.client.setting.Setting<?, ?>> S with(S s) {
            return add(s);
        }
    }

    static final class SendMod extends Module {
        SendMod(String id, String name, String desc, Function<SendMod, UnaryOperator<String>> fn, int priority, String... tags) {
            super("cb_" + id, name, desc, Category.CHAT, false);
            tags(tags);
            UnaryOperator<String> inner = fn.apply(this);
            OutgoingChat.register(priority, s -> isEnabled() ? inner.apply(s) : s);
        }

        <S extends dev.onix.client.setting.Setting<?, ?>> S with(S s) {
            return add(s);
        }
    }

    private static ChatPipeline.Processor proc(int priority, UnaryOperator<Component> fn) {
        return new ChatPipeline.Processor() {
            public int priority() {
                return priority;
            }

            public Component process(Component message) {
                return fn.apply(message);
            }
        };
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    /** Messages that look like a player talking: "<Name> hi", "Name: hi", "[Rank] Name » hi". */
    private static final java.util.regex.Pattern PLAYER = java.util.regex.Pattern.compile("^(?:\\[[^\\]]{1,24}\\]\\s*)*<?([A-Za-z0-9_]{3,16})>?\\s*(?:[:»>]|>>)\\s+(.*)$");

    private static String playerText(Component c) {
        if (key(c).startsWith("chat.type.text")) {
            Object[] args = ((TranslatableContents) c.getContents()).getArgs();
            return args.length > 1 ? String.valueOf(args[1] instanceof Component a ? a.getString() : args[1]) : c.getString();
        }
        var mt = PLAYER.matcher(c.getString());
        return mt.matches() ? mt.group(2) : null;
    }

    private static void chat(List<Module> m) {
        m.add(new ChatMod("hidejoin", "Hide Join Messages", "Hides \"player joined / left the game\" messages.", self -> proc(0, c -> {
            String k = key(c);
            String s = c.getString();
            return k.startsWith("multiplayer.player.joined") || k.equals("multiplayer.player.left") || s.endsWith(" joined the game") || s.endsWith(" left the game") ? null : c;
        }), "join", "leave"));
        m.add(new ChatMod("hidedeaths", "Hide Death Messages", "Hides other players' death messages.", self -> proc(0, c -> key(c).startsWith("death.") ? null : c), "death"));
        m.add(new ChatMod("hideadvancements", "Hide Advancements", "Hides \"has made the advancement\" messages.", self -> proc(0, c -> key(c).startsWith("chat.type.advancement") ? null : c), "advancement"));
        m.add(new ChatMod("linkfilter", "Link Filter", "Hides player messages that contain links or server ads.", self -> proc(0, c -> {
            String t = playerText(c);
            return t != null && t.toLowerCase(Locale.ROOT).matches(".*(https?://|www\\.|discord\\.gg|\\.(com|net|org|gg|de|eu|xyz)\\b).*") ? null : c;
        }), "links", "ads", "spam"));
        Map<String, Long> recent = new HashMap<>();
        m.add(new ChatMod("antispam", "Anti Spam", "Hides a message that was already shown in the last 10 seconds.", self -> proc(1, c -> {
            String s = c.getString();
            long now = System.currentTimeMillis();
            recent.values().removeIf(t -> now - t > 10_000);
            if (recent.containsKey(s)) return null;
            recent.put(s, now);
            return c;
        }), "spam"));
        m.add(new ChatMod("hideshouting", "Hide Shouting", "Hides player messages written mostly in capital letters.", self -> proc(0, c -> {
            String t = playerText(c);
            if (t == null) return c;
            int letters = 0, upper = 0;
            for (char ch : t.toCharArray()) {
                if (Character.isLetter(ch)) {
                    letters++;
                    if (Character.isUpperCase(ch)) upper++;
                }
            }
            return letters >= 8 && upper >= letters * 0.7 ? null : c;
        }), "caps"));
        m.add(new ChatMod("hideplayerchat", "Hide Player Chat", "Hides all player chat but keeps server and system messages.", self -> proc(0, c -> playerText(c) != null ? null : c), "chat"));
        m.add(new ChatMod("mentionping", "Mention Ping", "Plays a sound when someone writes your name.", self -> proc(60, c -> {
            String t = playerText(c);
            if (t != null && p() != null && t.toLowerCase(Locale.ROOT).contains(p().getName().getString().toLowerCase(Locale.ROOT))) {
                sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.8f);
            }
            return c;
        }), "mention", "name"));
        m.add(new ChatMod("keywordalert", "Keyword Alert", "Alerts you when chosen words appear in chat.", self -> {
            TextSetting words = self.with(new TextSetting("words", "Words", "diamond, help", 200).describe("Comma separated."));
            return proc(60, c -> {
                String s = c.getString().toLowerCase(Locale.ROOT);
                for (String w : words.get().split(",")) {
                    String k = w.trim().toLowerCase(Locale.ROOT);
                    if (!k.isEmpty() && s.contains(k)) {
                        Notifications.show("Keyword", c.getString());
                        sound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.4f, 0.8f);
                        break;
                    }
                }
                return c;
            });
        }, "keyword", "alert"));
        m.add(new ChatMod("friendalert", "Friend Join Alert", "Alerts you when one of your friends joins.", self -> {
            TextSetting names = self.with(new TextSetting("names", "Friends", "", 300).describe("Comma separated names."));
            return proc(60, c -> {
                String s = c.getString();
                if (key(c).startsWith("multiplayer.player.joined") || s.endsWith(" joined the game")) {
                    for (String n : names.get().split(",")) {
                        String k = n.trim();
                        if (!k.isEmpty() && s.toLowerCase(Locale.ROOT).contains(k.toLowerCase(Locale.ROOT))) {
                            Notifications.show("Friend online", k + " joined");
                            sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f, 0.8f);
                        }
                    }
                }
                return c;
            });
        }, "friends"));
        m.add(new ChatMod("chatsounds", "Chat Sounds", "Plays a soft sound for every player message.", self -> proc(60, c -> {
            if (playerText(c) != null) sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.8f, 0.3f);
            return c;
        }), "sound"));
        m.add(new ChatMod("whisperalert", "Whisper Alert", "Alerts you when you receive a private message.", self -> proc(60, c -> {
            String k = key(c);
            String s = c.getString().toLowerCase(Locale.ROOT);
            if (k.equals("commands.message.display.incoming") || s.contains("whispers to you") || s.contains("-> me") || s.contains("-> you")) {
                Notifications.show("Private message", c.getString());
                sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.8f);
            }
            return c;
        }), "whisper", "msg"));
        m.add(new ChatMod("chatlogger", "Chat Logger", "Saves every chat message to logs/onix-chat.log.", self -> proc(200, c -> {
            try {
                Path f = mc().gameDirectory.toPath().resolve("logs").resolve("onix-chat.log");
                Files.writeString(f, "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + c.getString() + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // logging is best effort
            }
            return c;
        }), "log"));

        // what you send
        m.add(new SendMod("calculator", "Chat Calculator", "Type =5*64 in chat: only you see the result, nothing is sent.", self -> s -> {
            if (!s.startsWith("=")) return s;
            try {
                double v = Calc.eval(s.substring(1));
                String r = v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.4f", v);
                local("§7" + s.substring(1).trim() + " = §f" + r + (Math.abs(v) >= 64 && v == Math.rint(v) ? " §8(" + ((long) v / 64) + " stacks + " + ((long) v % 64) + ")" : ""));
            } catch (RuntimeException e) {
                local("§cCan't calculate that");
            }
            return null;
        }, 0, "calculator", "math"));
    }

    /** Tiny calculator: + - * / % ^ and brackets. */
    static final class Calc {
        private final String s;
        private int i;

        private Calc(String s) {
            this.s = s.replace(" ", "").replace(",", ".").replace("x", "*");
        }

        static double eval(String text) {
            Calc c = new Calc(text);
            double v = c.sum();
            if (c.i != c.s.length()) throw new IllegalArgumentException();
            return v;
        }

        private double sum() {
            double v = product();
            while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) v = s.charAt(i++) == '+' ? v + product() : v - product();
            return v;
        }

        private double product() {
            double v = power();
            while (i < s.length() && "*/%".indexOf(s.charAt(i)) >= 0) {
                char op = s.charAt(i++);
                double r = power();
                v = op == '*' ? v * r : op == '/' ? v / r : v % r;
            }
            return v;
        }

        private double power() {
            double v = unary();
            if (i < s.length() && s.charAt(i) == '^') {
                i++;
                v = Math.pow(v, power());
            }
            return v;
        }

        private double unary() {
            if (i < s.length() && s.charAt(i) == '-') {
                i++;
                return -unary();
            }
            if (i < s.length() && s.charAt(i) == '(') {
                i++;
                double v = sum();
                if (i >= s.length() || s.charAt(i++) != ')') throw new IllegalArgumentException();
                return v;
            }
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (start == i) throw new IllegalArgumentException();
            return Double.parseDouble(s.substring(start, i));
        }
    }

    // ================================================================== key actions

    static class KeyAction extends Module {
        protected final KeybindSetting key = add(new KeybindSetting("key", "Key", KeybindSetting.NONE));
        private final Runnable action;

        KeyAction(String id, String name, String desc, Runnable action, String... tags) {
            super("cb_" + id, name, desc, Category.UTILITY, false);
            this.action = action;
            tags(tags);
            tags("key", "hotkey");
        }

        @Override
        public void onTick() {
            if (key.poll() && mc().player != null) {
                try {
                    action.run();
                } catch (RuntimeException ignored) {
                    // never crash on a hotkey
                }
            }
        }
    }

    private static void copy(String what, String text) {
        mc().keyboardHandler.setClipboard(text);
        Notifications.show("Copied", what);
    }

    private static void keys(List<Module> m) {
        m.add(new KeyAction("chatvisibility", "Chat Visibility Key", "Cycles chat between shown, commands only and hidden.", () -> {
            var opt = mc().options.chatVisibility();
            var all = net.minecraft.world.entity.player.ChatVisiblity.values();
            var next = all[(opt.get().ordinal() + 1) % all.length];
            opt.set(next);
            Notifications.show("Chat", next.name().charAt(0) + next.name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' '));
        }, "chat"));
        m.add(new KeyAction("clearchat", "Clear Chat", "Clears all chat messages.", () -> mc().gui.getChat().clearMessages(false), "chat"));
        m.add(new KeyAction("copyblockpos", "Copy Block Position", "Copies the coordinates of the block you are looking at.", () -> {
            if (mc().hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
                BlockPos q = b.getBlockPos();
                copy("Block position", q.getX() + " " + q.getY() + " " + q.getZ());
            }
        }, "coords"));
        m.add(new KeyAction("copyip", "Copy Server IP", "Copies the address of the server you are on.", () -> {
            var data = mc().getCurrentServer();
            if (data != null) copy("Server IP", data.ip);
        }, "server", "ip"));
        m.add(new KeyAction("copytp", "Copy Teleport Command", "Copies a /tp command for your exact position and rotation.", () -> copy("Teleport command",
                String.format(Locale.ROOT, "/tp @s %.2f %.2f %.2f %.1f %.1f", p().getX(), p().getY(), p().getZ(), Mth.wrapDegrees(p().getYRot()), p().getXRot())), "tp", "coords"));
        m.add(new KeyAction("snapaxis", "Snap To Axis", "Snaps your view to the nearest direction (great for building straight).", () -> {
            p().setYRot(Math.round(p().getYRot() / 90f) * 90f);
            p().setXRot(0);
        }, "building", "camera"));
        double[] volume = {-1};
        m.add(new KeyAction("mute", "Mute Key", "Mutes and unmutes all game sound.", () -> {
            var opt = mc().options.getSoundSourceOptionInstance(SoundSource.MASTER);
            if (volume[0] < 0) {
                volume[0] = opt.get();
                opt.set(0.0);
                Notifications.show("Sound", "Muted");
            } else {
                opt.set(volume[0]);
                volume[0] = -1;
                Notifications.show("Sound", "Unmuted");
            }
        }, "sound", "mute"));
        m.add(new KeyAction("rdup", "Render Distance +", "Increases the render distance by 2 chunks.", () -> {
            var opt = mc().options.renderDistance();
            opt.set(Math.min(32, opt.get() + 2));
            Notifications.show("Render distance", opt.get() + " chunks");
        }, "render distance"));
        m.add(new KeyAction("rddown", "Render Distance -", "Decreases the render distance by 2 chunks.", () -> {
            var opt = mc().options.renderDistance();
            opt.set(Math.max(2, opt.get() - 2));
            Notifications.show("Render distance", opt.get() + " chunks");
        }, "render distance"));
        m.add(new KeyAction("fullscreen", "Fullscreen Key", "Toggles fullscreen on a key of your choice.", () -> {
            mc().getWindow().toggleFullScreen();
            mc().options.fullscreen().set(mc().getWindow().isFullscreen());
        }, "fullscreen", "window"));
        m.add(new KeyAction("togglehud", "Toggle HUD", "Hides or shows the whole HUD (like F1).", () -> mc().options.hideGui = !mc().options.hideGui, "hud", "f1"));
        m.add(new KeyAction("autotext", "Auto Text", "Sends a chat message or command with a hotkey.", () -> {}, "macro", "chat") {
            private final TextSetting text = add(new TextSetting("text", "Message", "gg", 256).describe("Starts with / for a command."));

            @Override
            public void onTick() {
                if (key.poll() && mc().getConnection() != null) {
                    String t = text.get().trim();
                    if (t.isEmpty()) return;
                    if (t.startsWith("/")) mc().getConnection().sendCommand(t.substring(1));
                    else mc().getConnection().sendChat(t);
                }
            }
        });
    }

    // ================================================================== game options

    static final class OptionMod<T> extends Module {
        private final OptionOverride<T> override;
        private final Supplier<T> value;

        OptionMod(String id, String name, String desc, Category cat, Function<Options, OptionInstance<T>> getter, Function<OptionMod<T>, Supplier<T>> value, String... tags) {
            super("cb_" + id, name, desc, cat, false);
            this.override = new OptionOverride<>(getter);
            this.value = value.apply(this);
            tags(tags);
            tags("option", "setting");
        }

        <S extends dev.onix.client.setting.Setting<?, ?>> S with(S s) {
            return add(s);
        }

        @Override
        public void onTick() {
            override.apply(value.get());
        }

        @Override
        protected void onDisable() {
            override.restore();
        }
    }

    private static <T> OptionMod<T> fixed(String id, String name, String desc, Category cat, Function<Options, OptionInstance<T>> getter, T v, String... tags) {
        return new OptionMod<>(id, name, desc, cat, getter, self -> () -> v, tags);
    }

    private static OptionMod<Double> slider(String id, String name, String desc, Category cat, Function<Options, OptionInstance<Double>> getter,
                                            double def, double min, double max, double step, String unit, double factor, String... tags) {
        return new OptionMod<>(id, name, desc, cat, getter, self -> {
            NumberSetting n = self.with(new NumberSetting("value", name, def, min, max, step).unit(unit));
            return () -> n.get() * factor;
        }, tags);
    }

    private static OptionMod<Integer> intSlider(String id, String name, String desc, Category cat, Function<Options, OptionInstance<Integer>> getter,
                                                int def, int min, int max, String unit, String... tags) {
        return new OptionMod<>(id, name, desc, cat, getter, self -> {
            NumberSetting n = self.with(new NumberSetting("value", name, def, min, max, 1).unit(unit));
            return () -> n.getInt();
        }, tags);
    }

    private static void options(List<Module> m) {
        Category v = Category.VISUAL;
        Category pf = Category.PERFORMANCE;
        Category ch = Category.CHAT;
        m.add(fixed("noclouds", "No Clouds", "Hides the clouds.", pf, Options::cloudStatus, CloudStatus.OFF, "clouds"));
        m.add(fixed("nobobbing", "No View Bobbing", "Stops the camera from bobbing while you walk.", Category.CAMERA, Options::bobView, false, "bobbing"));
        m.add(fixed("vsyncoff", "VSync Off", "Turns off vertical sync for lower input delay.", pf, Options::enableVsync, false, "vsync"));
        m.add(fixed("unlimitedfps", "Unlimited FPS", "Removes the frame rate limit.", pf, Options::framerateLimit, 260, "fps"));
        m.add(fixed("subtitles", "Subtitles", "Always shows sound subtitles.", v, Options::showSubtitles, true, "subtitles", "sound"));
        m.add(fixed("noshadows", "No Entity Shadows", "Removes the round shadows under mobs and players.", pf, Options::entityShadows, false, "shadows"));
        m.add(fixed("hidelightning", "Hide Lightning Flash", "Stops lightning from flashing the sky.", v, Options::hideLightningFlash, true, "lightning"));
        m.add(fixed("darkloading", "Dark Loading Screen", "Uses a black instead of a red loading screen.", v, Options::darkMojangStudiosBackground, true, "loading"));
        m.add(fixed("nochatcolors", "No Chat Colors", "Shows chat messages without color codes.", ch, Options::chatColors, false, "colors"));
        m.add(fixed("contrastoutline", "High Contrast Outline", "Makes the block selection outline easier to see.", v, Options::highContrastBlockOutline, true, "outline"));
        m.add(fixed("nosmoothlighting", "No Smooth Lighting", "Turns off ambient occlusion for more FPS.", pf, Options::ambientOcclusion, false, "lighting"));
        m.add(fixed("nobiomeblend", "No Biome Blend", "Disables color blending between biomes (faster chunk building).", pf, Options::biomeBlendRadius, 0, "biome"));
        m.add(fixed("nomipmaps", "No Mipmaps", "Disables mipmaps for slightly more FPS.", pf, Options::mipmapLevels, 0, "mipmaps"));
        m.add(fixed("minparticles", "Minimal Particles", "Sets particles to minimal.", pf, Options::particles, ParticleStatus.MINIMAL, "particles"));
        m.add(fixed("nonausea", "No Nausea Warp", "Removes the nausea and portal screen distortion.", v, Options::screenEffectScale, 0.0, "nausea", "portal"));
        m.add(fixed("nodarknesspulse", "No Darkness Pulse", "Removes the pulsing of the warden's darkness effect.", v, Options::darknessEffectScale, 0.0, "darkness"));
        m.add(fixed("nomenublur", "No Menu Blur", "Removes the background blur behind menus.", pf, Options::menuBackgroundBlurriness, 0, "blur"));
        m.add(fixed("hotbarattack", "Hotbar Attack Indicator", "Shows the attack cooldown next to the hotbar.", Category.COMBAT, Options::attackIndicator, AttackIndicatorStatus.HOTBAR, "cooldown"));
        m.add(slider("chatscale", "Chat Scale", "Size of the chat.", ch, Options::chatScale, 100, 30, 100, 5, "%", 0.01, "chat"));
        m.add(slider("chatwidth", "Chat Width", "Width of the chat box.", ch, Options::chatWidth, 100, 20, 100, 5, "%", 0.01, "chat"));
        m.add(slider("chatopacity", "Chat Opacity", "Transparency of chat text.", ch, Options::chatOpacity, 100, 10, 100, 5, "%", 0.01, "chat"));
        m.add(slider("chatspacing", "Chat Line Spacing", "Space between chat lines.", ch, Options::chatLineSpacing, 0, 0, 100, 5, "%", 0.01, "chat"));
        m.add(slider("textbackground", "Text Background", "Opacity of the background behind chat text.", ch, Options::textBackgroundOpacity, 50, 0, 100, 5, "%", 0.01, "chat"));
        m.add(slider("chatdelay", "Chat Delay", "Delays incoming chat messages to make spam readable.", ch, Options::chatDelay, 0.5, 0, 6, 0.5, "s", 1, "spam"));
        m.add(slider("glintspeed", "Glint Speed", "Speed of the enchantment glint animation.", v, Options::glintSpeed, 50, 0, 100, 5, "%", 0.01, "glint"));
        m.add(slider("glintstrength", "Glint Strength", "Brightness of the enchantment glint.", v, Options::glintStrength, 75, 0, 100, 5, "%", 0.01, "glint"));
        m.add(slider("toastduration", "Toast Duration", "How long toasts and notifications stay on screen.", Category.UTILITY, Options::notificationDisplayTime, 1, 0.5, 10, 0.5, "x", 1, "toast"));
        m.add(intSlider("cloudrange", "Cloud Range", "How far away clouds are drawn.", pf, Options::cloudRange, 64, 2, 128, " chunks", "clouds"));
        m.add(intSlider("renderlock", "Render Distance Lock", "Holds the render distance at a fixed value.", pf, Options::renderDistance, 12, 2, 32, " chunks", "render distance"));
        m.add(intSlider("simulationlock", "Simulation Distance", "Holds the simulation distance at a fixed value.", pf, Options::simulationDistance, 8, 5, 32, " chunks", "simulation"));
    }

    // ================================================================== PvP info (Sword, Crystal, Mace, Diamond SMP)

    private static String sideOpen(BlockPos feet) {
        List<String> open = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState s = mc().level.getBlockState(feet.relative(d));
            if (!s.is(Blocks.OBSIDIAN) && !s.is(Blocks.BEDROCK) && !s.is(Blocks.CRYING_OBSIDIAN) && !s.is(Blocks.RESPAWN_ANCHOR) && !s.is(Blocks.ENDER_CHEST)) {
                open.add(d.getName().substring(0, 1).toUpperCase(Locale.ROOT));
            }
        }
        BlockState inside = mc().level.getBlockState(feet);
        if (inside.is(Blocks.OBSIDIAN) || inside.is(Blocks.BEDROCK) || inside.is(Blocks.ENDER_CHEST)) return "Burrowed";
        return open.isEmpty() ? "Safe hole" : "Open " + String.join(" ", open);
    }

    private static String itemLine(ItemStack s) {
        if (s.isEmpty()) return "Nothing";
        String d = pct(s);
        return s.getHoverName().getString() + (d == null ? "" : " " + d);
    }

    private static boolean isHealing(ItemStack s) {
        PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
        return pc != null && (pc.is(Potions.HEALING) || pc.is(Potions.STRONG_HEALING));
    }

    private static void pvp(List<Module> m) {
        Category c = Category.COMBAT;
        // sword
        m.add(info("swaccuracy", "Sword Accuracy", "How many of your sword swings actually hit.", c, "Accuracy",
                () -> Track.swings == 0 ? "-" : Math.min(100, Math.round(100f * Track.swordHits / Track.swings)) + "%", "sword pvp", "accuracy"));
        m.add(info("swdamage", "Sword Damage", "Damage of a full hit with your sword (strength and sharpness included).", c, "Hit",
                () -> p().getMainHandItem().is(ItemTags.SWORDS) ? f1(fullHit()) : null, "sword pvp", "damage"));
        m.add(info("swdealt", "Damage Dealt", "Total damage your sword dealt this session.", c, "Dealt", () -> f1(Track.swordDamage), "sword pvp", "damage"));
        m.add(info("swdps", "Sword DPS", "Your damage per second when every hit is fully charged.", c, "DPS",
                () -> p().getMainHandItem().is(ItemTags.SWORDS) ? f1(fullHit() * p().getAttributeValue(Attributes.ATTACK_SPEED)) : null, "sword pvp", "dps"));
        m.add(info("swenchants", "Sword Enchants", "Lists the enchantments on the sword you hold.", c, "Enchants", () -> {
            ItemStack s = p().getMainHandItem();
            if (!s.is(ItemTags.SWORDS)) return null;
            List<String> out = new ArrayList<>();
            for (var e : s.getEnchantments().entrySet()) out.add(Enchantment.getFullname(e.getKey(), e.getIntValue()).getString());
            return out.isEmpty() ? "None" : String.join(", ", out);
        }, "sword pvp", "enchant"));
        m.add(info("swenemy", "Enemy Sword", "Shows your target's sword, its Sharpness level and durability.", c, "Enemy", () -> {
            LivingEntity t = target();
            if (t == null) return null;
            ItemStack s = t.getMainHandItem();
            int sharp = ench(s, Enchantments.SHARPNESS);
            return itemLine(s) + (sharp > 0 ? " · Sharp " + sharp : "");
        }, "sword pvp", "enemy"));
        m.add(info("swinterval", "Hit Interval", "Time between your last two sword hits.", c, "Interval", () -> Track.hitInterval < 0 ? "-" : Track.hitInterval + " ms", "sword pvp"));
        m.add(info("swhitstokill", "Hits To Kill", "Estimates how many full hits your target still needs (after armor).", c, "To kill", () -> {
            LivingEntity t = target();
            if (t == null) return null;
            float per = afterArmor(fullHit(), t, 1);
            return per <= 0 ? "-" : String.valueOf((int) Math.ceil((t.getHealth() + t.getAbsorptionAmount()) / per));
        }, "sword pvp", "enemy"));
        m.add(info("swknockback", "Knockback Ready", "Shows when your next hit gives extra sprint knockback.", c, "Knockback",
                () -> p().isSprinting() && p().getAttackStrengthScale(0) > 0.9f ? "Ready" : "-", "sword pvp", "w-tap"));
        m.add(info("swlasthit", "Last Hit", "Shows how much damage your last hit really dealt.", c, "Last hit", () -> Track.lastHitDamage < 0 ? "-" : f1(Track.lastHitDamage), "damage"));
        m.add(info("swsweep", "Sweep Ready", "Shows when your next sword hit will be a sweeping attack.", c, "Sweep",
                () -> p().getMainHandItem().is(ItemTags.SWORDS) ? (p().onGround() && !p().isSprinting() && p().getAttackStrengthScale(0) > 0.9f ? "Ready" : "-") : null, "sword pvp"));
        m.add(info("swdefense", "Target Defense", "Shows how much your target's armor reduces your hits.", c, "Defense", () -> {
            LivingEntity t = target();
            if (t == null) return null;
            float full = fullHit();
            return Math.round(100 * (1 - afterArmor(full, t, 1) / full)) + "% blocked";
        }, "enemy", "armor"));
        m.add(new SoundOn("swchargesound", "Charge Ready Sound", "Plays a soft tick the moment your sword is fully charged again.", c, null, SoundEvents.NOTE_BLOCK_HAT.value(), 2f, "sword pvp", "cooldown") {
            private boolean was = true;

            @Override
            public void onTick() {
                boolean full = p() != null && p().getAttackStrengthScale(0) >= 1f;
                if (full && !was && p().getMainHandItem().is(ItemTags.SWORDS)) play(1);
                was = full;
            }
        });
        m.add(new SoundOn("swcombosound", "Combo Sound", "A note that climbs higher with every hit of your combo.", c, Event.HIT, SoundEvents.NOTE_BLOCK_PLING.value(), 1f, "combo", "sound") {
            @Override
            float pitch() {
                return 0.6f + Math.min(CombatTracker.combo(), 12) * 0.12f;
            }
        });
        m.add(new SoundOn("swcritsound", "Crit Sound", "Plays a crunchy sound when you land a critical hit.", c, Event.CRIT, SoundEvents.PLAYER_ATTACK_CRIT, 1.1f, "crit", "sound"));
        m.add(new ChargeBar());

        // crystal pvp
        Map<String, Integer> pops = new HashMap<>();
        m.add(new Module("cb_popcounter", "Pop Counter", "Counts the totems every player pops and shows it in chat.", c, false) {
            {
                tags("totem", "pop", "crystal pvp");
                Events.on(Event.ENEMY_POP, (e, en) -> {
                    if (!isEnabled() || en == null) return;
                    String n = en.getName().getString();
                    int k = pops.merge(n, 1, Integer::sum);
                    local("§6" + n + " §7popped §f" + k + (k == 1 ? " §7totem" : " §7totems"));
                });
            }

            @Override
            public void onTick() {
                if (mc().level == null) pops.clear();
            }
        });
        m.add(alert("armorbreak", "Armor Break Alert", "Tells you when an enemy's armor piece is about to break.", c, () -> {
            Player t = targetPlayer();
            if (t == null) return false;
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack st = t.getItemBySlot(s);
                if (st.isDamageableItem() && left(st) < 0.15f) return true;
            }
            return false;
        }, () -> "Your target's armor is about to break", "crystal pvp", "armor"));
        m.add(info("combattimer", "Combat Timer", "Counts down your combat tag after every hit you deal or take.", c, "Combat", () -> {
            long left = 15000 - (System.currentTimeMillis() - Track.combatAt);
            return left <= 0 ? "Safe" : f1(left / 1000.0) + "s";
        }, "combat tag", "combat log"));
        m.add(info("crystalspeed", "Crystal Speed", "Shows how many crystals you place per second.", c, "Crystals", () -> f1(Track.crystals.size() / 3.0) + "/s", "crystal pvp", "cps"));
        m.add(info("enemyarmor", "Enemy Armor", "Shows the durability of your target's helmet, chestplate, leggings and boots.", c, "Enemy armor", () -> {
            LivingEntity t = target();
            if (t == null) return null;
            List<String> out = new ArrayList<>();
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                String d = pct(t.getItemBySlot(s));
                out.add(d == null ? "-" : d);
            }
            return String.join(" ", out);
        }, "crystal pvp", "enemy", "armor"));
        m.add(info("enemyhands", "Enemy Hands", "Shows what your target holds, e.g. whether there is a totem in the offhand.", c, "Enemy holds", () -> {
            LivingEntity t = target();
            return t == null ? null : t.getMainHandItem().getHoverName().getString() + " | " + (t.getOffhandItem().isEmpty() ? "-" : t.getOffhandItem().getHoverName().getString());
        }, "crystal pvp", "enemy", "totem"));
        m.add(info("hurttime", "Hurt Time", "Shows whether your target can take full damage again (hurt ticks left).", c, "Hurt", () -> {
            LivingEntity t = target();
            return t == null ? null : t.hurtTime <= 0 ? "Ready" : t.hurtTime + " ticks";
        }, "crystal pvp", "enemy"));
        m.add(info("lastpop", "Last Pop", "Shows how long ago you and the last enemy popped a totem.", c, "Pops", () -> {
            long me = Events.since(Event.SELF_POP), en = Events.since(Event.ENEMY_POP);
            return "You " + (me < 0 ? "-" : clock(me)) + " · Enemy " + (en < 0 ? "-" : clock(en));
        }, "crystal pvp", "totem"));
        Set<Integer> pearls = new HashSet<>();
        String[] pearlFrom = {""};
        m.add(alert("pearlalert", "Pearl Alert", "Tells you when an enemy throws an ender pearl and from where.", c, () -> {
            boolean found = false;
            for (Entity e : mc().level.entitiesForRendering()) {
                if (e.getType() != EntityType.ENDER_PEARL || e.distanceTo(p()) > 64) continue;
                if (pearls.add(e.getId())) {
                    Entity owner = e instanceof net.minecraft.world.entity.projectile.Projectile pr ? pr.getOwner() : null;
                    if (owner != p()) {
                        pearlFrom[0] = (owner == null ? "Someone" : owner.getName().getString()) + " pearled (" + dir(e) + ")";
                        found = true;
                    }
                }
            }
            if (pearls.size() > 200) pearls.clear();
            return found;
        }, () -> pearlFrom[0], "pearl", "crystal pvp"));
        m.add(info("surround", "Surround Check", "Shows whether you stand in a safe obsidian or bedrock hole and which sides are open.", c, "Surround",
                () -> sideOpen(p().blockPosition()), "crystal pvp", "hole"));
        m.add(info("targethole", "Target Hole", "Shows whether your target is in a hole, burrowed, or which sides are open.", c, "Target", () -> {
            LivingEntity t = target();
            return t == null ? null : sideOpen(t.blockPosition());
        }, "crystal pvp", "hole"));
        int[] anchors = {-1};
        m.add(alert("anchoralert", "Anchor Alert", "Warns you when an enemy places or charges a respawn anchor near you.", c, () -> {
            if (Track.tick % 10 != 0) return false;
            int n = 0;
            BlockPos b = p().blockPosition();
            for (BlockPos q : BlockPos.betweenClosed(b.offset(-6, -3, -6), b.offset(6, 3, 6))) {
                BlockState s = mc().level.getBlockState(q);
                if (s.is(Blocks.RESPAWN_ANCHOR)) n += 1 + s.getValue(RespawnAnchorBlock.CHARGE);
            }
            boolean more = anchors[0] >= 0 && n > anchors[0] && !p().getMainHandItem().is(Items.RESPAWN_ANCHOR) && !p().getMainHandItem().is(Items.GLOWSTONE);
            anchors[0] = n;
            return more;
        }, "Respawn anchor near you!", "anchor", "crystal pvp"));

        // diamond smp
        m.add(alert("bowalert", "Bow Alert", "Warns you when a nearby enemy is drawing a bow or crossbow.", c, () -> {
            for (Player o : mc().level.players()) {
                if (o != p() && o.distanceTo(p()) < 40 && o.isUsingItem() && (o.getUseItem().is(Items.BOW) || o.getUseItem().is(Items.CROSSBOW))) return true;
            }
            return false;
        }, "Someone is drawing a bow at you", "bow", "diamond smp"));
        Set<String> fighting = new HashSet<>();
        String[] logged = {""};
        m.add(alert("combatlog", "Combat Log Detector", "Tells you when a player you were fighting leaves the server.", c, () -> {
            var conn = mc().getConnection();
            if (conn == null) return false;
            Player t = targetPlayer();
            if (t != null) fighting.add(t.getName().getString());
            if (System.currentTimeMillis() - Track.combatAt > 30000) fighting.clear();
            for (String n : fighting) {
                if (conn.getPlayerInfo(n) == null) {
                    logged[0] = n + " left during combat";
                    fighting.remove(n);
                    return true;
                }
            }
            return false;
        }, () -> logged[0], "combat log", "diamond smp"));
        String[] eater = {""};
        m.add(alert("enemyeating", "Enemy Eating", "Tells you when a nearby enemy starts eating a golden apple or drinking a potion.", c, () -> {
            for (Player o : mc().level.players()) {
                if (o == p() || o.distanceTo(p()) > 24 || !o.isUsingItem()) continue;
                ItemStack u = o.getUseItem();
                if (u.is(Items.GOLDEN_APPLE) || u.is(Items.ENCHANTED_GOLDEN_APPLE) || u.is(Items.POTION)) {
                    eater[0] = o.getName().getString() + (u.is(Items.POTION) ? " is drinking a potion" : " is eating a gapple");
                    return true;
                }
            }
            return false;
        }, () -> eater[0], "gapple", "diamond smp", "enemy"));
        m.add(alert("enemylow", "Enemy Low Health", "Tells you when your target is low, so you can go in for the kill.", c, () -> {
            LivingEntity t = target();
            return t instanceof Player && t.getHealth() + t.getAbsorptionAmount() <= 6;
        }, () -> "Your target is low!", "enemy", "diamond smp"));
        m.add(info("groundheight", "Ground Height", "Shows how high above the ground you are while in the air (for mace hits).", c, "Height", () -> {
            if (p().onGround()) return null;
            BlockPos b = p().blockPosition();
            for (int i = 1; i < 128; i++) {
                if (!mc().level.getBlockState(b.below(i)).isAir()) return f1(p().getY() - (b.getY() - i + 1)) + " blocks";
            }
            return "Void";
        }, "mace", "fall", "diamond smp"));
        m.add(alert("lowpotions", "Low Potions Alert", "Tells you when you are running out of healing potions.", c,
                () -> InventoryUtil.count(CarbonPack::isHealing) <= 2 && InventoryUtil.count(CarbonPack::isHealing) > 0, "Only a few healing potions left", "potion", "diamond smp"));
        m.add(alert("macedanger", "Mace Danger", "Warns you when an enemy with a mace is falling towards you from above.", c, () -> {
            for (Player o : mc().level.players()) {
                if (o == p() || !o.getMainHandItem().is(Items.MACE)) continue;
                double dy = o.getY() - p().getY();
                if (dy > 3 && Math.hypot(o.getX() - p().getX(), o.getZ() - p().getZ()) < 4 && o.getDeltaMovement().y < -0.3) return true;
            }
            return false;
        }, "Mace incoming from above!", "mace", "diamond smp"));
        m.add(alert("splashalert", "Splash Potion Alert", "Warns you when someone throws a splash potion near you.", c,
                () -> nearType(EntityType.SPLASH_POTION, 8), "Splash potion near you", "potion", "diamond smp"));
        m.add(info("targetshield", "Target Shield", "Shows whether your target holds a shield and is blocking right now.", c, "Shield", () -> {
            LivingEntity t = target();
            if (t == null) return null;
            boolean has = t.getOffhandItem().is(Items.SHIELD) || t.getMainHandItem().is(Items.SHIELD);
            return !has ? "None" : t.isBlocking() ? "Blocking" : "Down";
        }, "shield", "diamond smp"));
        m.add(info("shieldstatus", "Shield Status", "Shows your shield's durability and how long it stays disabled after an axe hit.", c, "Shield", () -> {
            ItemStack s = p().getOffhandItem().is(Items.SHIELD) ? p().getOffhandItem() : p().getMainHandItem();
            if (!s.is(Items.SHIELD)) return null;
            float cd = p().getCooldowns().getCooldownPercent(s, 0);
            return pct(s) + (cd > 0 ? " · disabled " + f1(cd * 5) + "s" : "");
        }, "shield", "diamond smp"));

        // mace
        m.add(info("maceinfo", "Mace Info", "Shows your mace's durability and its Density, Breach and Wind Burst levels.", c, "Mace", () -> {
            ItemStack s = p().getMainHandItem().is(Items.MACE) ? p().getMainHandItem() : ItemStack.EMPTY;
            if (s.isEmpty()) return null;
            return pct(s) + " · D" + ench(s, Enchantments.DENSITY) + " B" + ench(s, Enchantments.BREACH) + " W" + ench(s, Enchantments.WIND_BURST);
        }, "mace"));
        m.add(info("smashcounter", "Smash Counter", "Counts your mace smash hits and your highest smash this session.", c, "Smashes",
                () -> Track.smashes + " · best " + f1(Track.bestSmash) + " blocks", "mace"));
        m.add(info("smashpredict", "Smash Predict", "Predicts your smash damage on the player below you after their armor.", c, "Smash", () -> {
            if (!p().getMainHandItem().is(Items.MACE) || p().onGround()) return null;
            float fall = (float) p().fallDistance;
            float bonus = fall <= 3 ? 4 * fall : fall <= 8 ? 12 + 2 * (fall - 3) : 22 + (fall - 8);
            ItemStack s = p().getMainHandItem();
            bonus += 0.5f * ench(s, Enchantments.DENSITY) * fall;
            float dmg = (float) p().getAttributeValue(Attributes.ATTACK_DAMAGE) + bonus;
            Player below = null;
            for (Player o : mc().level.players()) {
                if (o != p() && o.getY() < p().getY() && Math.hypot(o.getX() - p().getX(), o.getZ() - p().getZ()) < 3) below = o;
            }
            if (below == null) return f1(dmg);
            float armorScale = Math.max(0, 1 - 0.15f * ench(s, Enchantments.BREACH));
            return f1(afterArmor(dmg, below, armorScale)) + " on " + below.getName().getString();
        }, "mace", "damage"));
        m.add(new SoundOn("smashsound", "Smash Sound", "Plays a heavy impact sound when you land a mace smash.", c, Event.HIT, SoundEvents.ANVIL_LAND, 0.6f, "mace", "sound") {
            @Override
            boolean when() {
                return p() != null && p().getMainHandItem().is(Items.MACE) && p().fallDistance > 1.5;
            }
        });
        m.add(info("targetbelow", "Target Below", "Shows the player under you and how far you would fall onto them.", c, "Below", () -> {
            for (Player o : mc().level.players()) {
                if (o != p() && o.getY() < p().getY() - 1 && Math.hypot(o.getX() - p().getX(), o.getZ() - p().getZ()) < 4) {
                    return o.getName().getString() + " · " + f1(p().getY() - o.getY()) + " blocks";
                }
            }
            return null;
        }, "mace", "enemy"));
    }

    /** Plays a sound when an event happens. */
    static class SoundOn extends Module {
        private final SoundEvent sound;
        private final float basePitch;
        private final NumberSetting volume = add(new NumberSetting("volume", "Volume", 70, 0, 100, 5).unit("%"));

        SoundOn(String id, String name, String desc, Category cat, Event event, SoundEvent sound, float pitch, String... tags) {
            super("cb_" + id, name, desc, cat, false);
            this.sound = sound;
            this.basePitch = pitch;
            tags(tags);
            if (event != null) Events.on(event, (e, en) -> {
                if (isEnabled() && when()) play(pitch());
            });
        }

        boolean when() {
            return true;
        }

        float pitch() {
            return basePitch;
        }

        void play(float pitch) {
            sound(sound, pitch, volume.getFloat() / 100f);
        }
    }

    /** A small bar under the crosshair that fills up as your hit recharges. */
    static final class ChargeBar extends Module implements dev.onix.client.module.OverlayRenderer {
        private final BoolSetting hideCharged = add(new BoolSetting("hide", "Hide When Charged", true).describe("Only shows the bar while recharging."));

        ChargeBar() {
            super("cb_chargebar", "Charge Bar", "A small bar that fills up as your sword hit recharges.", Category.COMBAT, false);
            tags("cooldown", "sword pvp", "attack");
        }

        @Override
        public void renderOverlay(net.minecraft.client.gui.GuiGraphics ctx) {
            float f = p().getAttackStrengthScale(0);
            if (f >= 1 && hideCharged.isOn()) return;
            int w = 30;
            int x = ctx.guiWidth() / 2 - w / 2;
            int y = ctx.guiHeight() / 2 + 9;
            ctx.fill(x - 1, y - 1, x + w + 1, y + 3, 0x90000000);
            ctx.fill(x, y, x + Math.round(w * Math.min(1, f)), y + 2, f >= 1 ? 0xFFFFFFFF : 0xFFB9A8FF);
        }
    }

    // ================================================================== misc

    private static void misc(List<Module> m) {
        m.add(new Module("cb_hitparticles", "Hit Particles", "Extra crit and magic particles when you hit something.", Category.VISUAL, false) {
            private final NumberSetting amount = add(new NumberSetting("amount", "Amount", 2, 1, 6, 1).unit("x"));

            {
                tags("particles", "hit", "crit");
                Events.on(Event.HIT, (e, en) -> {
                    if (!isEnabled() || en == null || mc().level == null) return;
                    java.util.Random r = new java.util.Random();
                    for (int i = 0; i < 6 * amount.getInt(); i++) {
                        mc().level.addParticle(i % 2 == 0 ? net.minecraft.core.particles.ParticleTypes.CRIT : net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                                en.getX() + (r.nextDouble() - 0.5) * en.getBbWidth(), en.getY() + r.nextDouble() * en.getBbHeight(), en.getZ() + (r.nextDouble() - 0.5) * en.getBbWidth(),
                                (r.nextDouble() - 0.5) * 0.4, r.nextDouble() * 0.3, (r.nextDouble() - 0.5) * 0.4);
                    }
                });
            }
        });
    }
}
