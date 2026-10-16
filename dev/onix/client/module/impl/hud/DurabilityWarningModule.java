package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class DurabilityWarningModule extends TextHudModule {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private final NumberSetting threshold = add(new NumberSetting("threshold", "Warn Below", 10, 1, 50, 1).unit("%"));
    private final BoolSetting armor = add(new BoolSetting("armor", "Check Armor", true));
    private final BoolSetting hands = add(new BoolSetting("hands", "Check Held Items", true));
    private final BoolSetting sound = add(new BoolSetting("sound", "Play Sound", true));
    private final BoolSetting flash = add(new BoolSetting("flash", "Flash", true));
    private final ColorSetting warnColor = add(new ColorSetting("warnColor", "Warning Color", 0xFFFF5A5F));

    private final Set<String> warned = new HashSet<>();

    public DurabilityWarningModule() {
        super("durabilitywarning", "Durability Warning", "Alerts you before your gear breaks.", Category.HUD, false, Anchor.CENTER, 0, -40);
        tags("break", "repair", "mending");
    }

    private boolean low(ItemStack stack) {
        return stack.isDamageableItem() && (stack.getMaxDamage() - stack.getDamageValue()) * 100f / stack.getMaxDamage() < threshold.get();
    }

    private boolean checked(EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.HAND ? hands.isOn() : armor.isOn();
    }

    @Override
    public void onTick() {
        if (mc().player == null) return;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = mc().player.getItemBySlot(slot);
            String key = slot.getName() + ":" + stack.getItem();
            if (checked(slot) && low(stack)) {
                if (warned.add(key) && sound.isOn()) {
                    mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 1.4f, 0.8f));
                }
            } else {
                warned.remove(key);
            }
        }
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (mc().player == null) return;
        if (flash.isOn() && (System.currentTimeMillis() / 500) % 2 == 1 && !preview) return;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = mc().player.getItemBySlot(slot);
            if (checked(slot) && low(stack)) {
                int pct = Math.round((stack.getMaxDamage() - stack.getDamageValue()) * 100f / stack.getMaxDamage());
                out.add(new Line(stack.getHoverName().getString(), pct + "%", warnColor.color()));
            }
        }
        if (out.isEmpty() && preview) out.add(new Line("Diamond Chestplate", "6%", warnColor.color()));
    }

    @Override
    public boolean hasContent() {
        if (mc().player == null) return false;
        for (EquipmentSlot slot : SLOTS) if (checked(slot) && low(mc().player.getItemBySlot(slot))) return true;
        return false;
    }
}
