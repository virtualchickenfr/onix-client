package dev.onix.client.module.impl.pack;

import dev.onix.client.util.CombatTracker;
import dev.onix.client.util.InventoryUtil;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Things that happen to you in game, for the module packs (stats, effects): hits, crits, kills, combos, totem pops,
 * damage, jumps, pearls, crystals, blocks and more. Hits and kills come from {@link CombatTracker}, totem pops from
 * the packet hook, the rest is noticed every tick by comparing with the tick before.
 */
public final class Events {
    private Events() {}

    public enum Event {
        HIT("Hit"), CRIT("Crit"), KILL("Kill"), DOUBLE_KILL("Double Kill"), TRIPLE_KILL("Triple Kill"),
        COMBO3("3 Combo"), COMBO5("5 Combo"), COMBO10("10 Combo"), SELF_POP("Totem Pop"), ENEMY_POP("Enemy Pop"),
        DAMAGE("Damage Taken"), LOW_HP("Low Health"), DEATH("Death"), JUMP("Jump"), BIG_LAND("Hard Landing"),
        SPRINT("Sprint Start"), PEARL("Pearl Throw"), CRYSTAL("Crystal Placed"), BLOCK("Block Placed"), EAT("Eat"),
        LEVEL_UP("Level Up"), HEAL("Heal"), BOW("Bow Shot"), WIND_CHARGE("Wind Charge"), ROCKET("Rocket Boost"),
        GAPPLE("Golden Apple"), POTION("Potion Thrown"), ARMOR_BREAK("Armor Broke");

        public final String label;

        Event(String label) {
            this.label = label;
        }
    }

    private static final Map<Event, List<BiConsumer<Event, Entity>>> LISTENERS = new EnumMap<>(Event.class);
    private static final Map<Event, Long> LAST = new EnumMap<>(Event.class);
    private static boolean hooked;

    public static void on(Event e, BiConsumer<Event, Entity> listener) {
        LISTENERS.computeIfAbsent(e, k -> new ArrayList<>()).add(listener);
    }

    public static void fire(Event e, Entity entity) {
        long now = System.currentTimeMillis();
        LAST.put(e, now);
        for (BiConsumer<Event, Entity> l : LISTENERS.getOrDefault(e, List.of())) {
            try {
                l.accept(e, entity);
            } catch (RuntimeException ignored) {
                // a broken listener must not stop the others
            }
        }
    }

    /** Milliseconds since it last happened, or -1. */
    public static long since(Event e) {
        Long t = LAST.get(e);
        return t == null ? -1 : System.currentTimeMillis() - t;
    }

    public static void reset() {
        LAST.clear();
    }

    // ------------------------------------------------------------------ sources

    /** Totem pop packet (any entity). */
    public static void pop(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == null || mc.player == null) return;
        if (entity == mc.player) fire(Event.SELF_POP, entity);
        else if (entity instanceof Player) fire(Event.ENEMY_POP, entity);
    }

    private static void hook() {
        if (hooked) return;
        hooked = true;
        CombatTracker.onHit(target -> {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null) return;
            fire(Event.HIT, target);
            boolean crit = p.fallDistance > 0 && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.isPassenger()
                    && !p.hasEffect(MobEffects.BLINDNESS) && p.getAttackStrengthScale(0f) > 0.9f;
            if (crit) fire(Event.CRIT, target);
            int combo = CombatTracker.combo();
            if (combo == 3) fire(Event.COMBO3, target);
            if (combo == 5) fire(Event.COMBO5, target);
            if (combo == 10) fire(Event.COMBO10, target);
        });
        CombatTracker.onKill(killed -> {
            long since = since(Event.KILL);
            fire(Event.KILL, killed);
            if (since >= 0 && since < 10_000) {
                kills++;
                if (kills == 2) fire(Event.DOUBLE_KILL, killed);
                if (kills == 3) fire(Event.TRIPLE_KILL, killed);
            } else {
                kills = 1;
            }
        });
    }

    private static int kills;
    private static boolean wasOnGround = true;
    private static boolean wasSprinting;
    private static boolean wasDead;
    private static int lastHurt;
    private static float lastHealth = -1;
    private static int lastLevel = -1;
    private static float fall;
    private static final Map<String, Integer> LAST_COUNT = new java.util.HashMap<>();
    private static int lastArmorPieces = -1;

    private static boolean dropped(String key, int now) {
        Integer before = LAST_COUNT.put(key, now);
        return before != null && now < before;
    }

    /** Every client tick. */
    public static void tick() {
        hook();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            LAST_COUNT.clear();
            lastHealth = -1;
            lastLevel = -1;
            lastArmorPieces = -1;
            return;
        }
        boolean dead = p.isDeadOrDying();
        if (dead && !wasDead) fire(Event.DEATH, p);
        wasDead = dead;
        if (dead) return;

        if (p.hurtTime > lastHurt) fire(Event.DAMAGE, p);
        lastHurt = p.hurtTime;
        float hp = p.getHealth();
        if (lastHealth >= 0) {
            if (hp < 6 && lastHealth >= 6) fire(Event.LOW_HP, p);
            if (hp > lastHealth + 0.5f) fire(Event.HEAL, p);
        }
        lastHealth = hp;
        if (lastLevel >= 0 && p.experienceLevel > lastLevel) fire(Event.LEVEL_UP, p);
        lastLevel = p.experienceLevel;

        boolean ground = p.onGround();
        if (!ground) fall = Math.max(fall, (float) p.fallDistance);
        if (ground && !wasOnGround && fall > 4) fire(Event.BIG_LAND, p);
        if (ground) fall = 0;
        if (!ground && wasOnGround && p.getDeltaMovement().y > 0.2 && !p.isFallFlying()) fire(Event.JUMP, p);
        wasOnGround = ground;
        boolean sprint = p.isSprinting();
        if (sprint && !wasSprinting) fire(Event.SPRINT, p);
        wasSprinting = sprint;

        // used items: the count went down
        if (dropped("pearl", InventoryUtil.count(Items.ENDER_PEARL))) fire(Event.PEARL, p);
        if (dropped("crystal", InventoryUtil.count(Items.END_CRYSTAL))) fire(Event.CRYSTAL, p);
        if (dropped("wind", InventoryUtil.count(Items.WIND_CHARGE))) fire(Event.WIND_CHARGE, p);
        if (dropped("rocket", InventoryUtil.count(Items.FIREWORK_ROCKET)) && p.isFallFlying()) fire(Event.ROCKET, p);
        if (dropped("gapple", InventoryUtil.count(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE))) fire(Event.GAPPLE, p);
        if (dropped("arrows", InventoryUtil.count(Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW))) fire(Event.BOW, p);
        if (dropped("potions", InventoryUtil.count(Items.SPLASH_POTION, Items.LINGERING_POTION))) fire(Event.POTION, p);
        if (dropped("food", InventoryUtil.count(s -> s.has(net.minecraft.core.component.DataComponents.FOOD))) && p.isUsingItem()) fire(Event.EAT, p);
        ItemStack held = p.getMainHandItem();
        if (held.getItem() instanceof BlockItem && dropped("block:" + held.getItem(), InventoryUtil.count(held.getItem()))) fire(Event.BLOCK, p);
        int armor = 0;
        for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            if (!p.getItemBySlot(slot).isEmpty()) armor++;
        }
        if (lastArmorPieces > armor && mc.screen == null) fire(Event.ARMOR_BREAK, p);
        lastArmorPieces = armor;
    }
}
