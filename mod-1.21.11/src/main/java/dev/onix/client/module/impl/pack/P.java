package dev.onix.client.module.impl.pack;

import dev.onix.client.util.InventoryUtil;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Small helpers the generated pack modules use for their values and conditions. */
public final class P {
    private P() {}

    public static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static LocalPlayer p() {
        return mc().player;
    }

    public static int count(Item... items) {
        return p() == null ? 0 : InventoryUtil.count(items);
    }

    public static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    public static String clock(long ticks) {
        long s = Math.max(0, ticks / 20);
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    public static long dayTime() {
        return mc().level.getDayTime() % 24000L;
    }

    /** Remaining time of an effect, or null. */
    public static String effect(Holder<MobEffect> effect) {
        MobEffectInstance e = p().getEffect(effect);
        if (e == null) return null;
        return e.isInfiniteDuration() ? "∞" : clock(e.getDuration());
    }

    public static boolean has(Holder<MobEffect> effect) {
        return p().hasEffect(effect);
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Lowest durability left of the worn armor in percent, or -1 without armor. */
    public static int armorLowest() {
        int low = -1;
        for (EquipmentSlot s : ARMOR) {
            ItemStack st = p().getItemBySlot(s);
            if (st.isEmpty() || !st.isDamageableItem()) continue;
            int pct = Math.round(100f * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage());
            low = low < 0 ? pct : Math.min(low, pct);
        }
        return low;
    }

    public static int armorPieces() {
        int n = 0;
        for (EquipmentSlot s : ARMOR) if (!p().getItemBySlot(s).isEmpty()) n++;
        return n;
    }

    /** Durability left of the held item in percent, or null. */
    public static String heldDurability() {
        ItemStack st = p().getMainHandItem();
        if (st.isEmpty() || !st.isDamageableItem()) return null;
        return Math.round(100f * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage()) + "%";
    }

    public static boolean holding(Item item) {
        return p().getMainHandItem().is(item) || p().getOffhandItem().is(item);
    }

    public static boolean holdingSword() {
        return p().getMainHandItem().is(ItemTags.SWORDS);
    }

    public static boolean holdingAxe() {
        return p().getMainHandItem().is(ItemTags.AXES);
    }

    public static int freeSlots() {
        int free = 0;
        for (int i = 0; i < 36; i++) if (p().getInventory().getItem(i).isEmpty()) free++;
        return free;
    }

    public static boolean nether() {
        return mc().level.dimension() == Level.NETHER;
    }

    public static boolean end() {
        return mc().level.dimension() == Level.END;
    }

    public static boolean overworld() {
        return mc().level.dimension() == Level.OVERWORLD;
    }

    /** Horizontal speed in blocks per second. */
    public static double speed() {
        var v = p().getDeltaMovement();
        return Math.hypot(v.x, v.z) * 20;
    }

    public static int hotbarBlocks() {
        int n = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack st = p().getInventory().getItem(i);
            if (st.getItem() instanceof net.minecraft.world.item.BlockItem) n += st.getCount();
        }
        return n;
    }

    public static int tabPlayers() {
        return mc().getConnection() == null ? 0 : mc().getConnection().getOnlinePlayers().size();
    }

    public static String distanceTo(double x, double z) {
        return String.valueOf(Math.round(Math.hypot(p().getX() - x, p().getZ() - z)));
    }

    public static boolean offhand(Item item) {
        return p().getOffhandItem().is(item);
    }

    public static String yesNo(boolean b) {
        return b ? "Yes" : "No";
    }

    public static boolean isItem(ItemStack s, Item... items) {
        for (Item i : items) if (s.is(i)) return true;
        return false;
    }

    public static boolean lowArmor() {
        int low = armorLowest();
        return low >= 0 && low <= 20;
    }

    public static boolean noneOf(Item item) {
        return count(item) == 0;
    }

    public static Item air() {
        return Items.AIR;
    }
}
