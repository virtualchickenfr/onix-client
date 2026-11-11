package dev.onix.client.module.impl.visual;

import dev.onix.client.OnixClient;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;

/**
 * Item Physics: dropped items lie flat on the ground at a random angle instead of floating and spinning; blocks sit
 * on the ground. Items in the air tumble while they fall. Applied by ItemEntityRendererMixin.
 */
public class ItemPhysicsModule extends Module {
    private final BoolSetting tumble = add(new BoolSetting("tumble", "Tumble While Falling", true)
            .describe("Items turn over while they fly; off keeps them still."));

    /** Extra data on the item's render state (added by ItemEntityRendererMixin). */
    public interface State {
        boolean onix$onGround();

        void onix$setOnGround(boolean onGround);
    }

    public ItemPhysicsModule() {
        super("itemphysics", "Item Physics", "Dropped items lie flat on the ground instead of floating and spinning.", Category.VISUAL, false);
        tags("dropped items", "realistic", "physics", "floating");
    }

    public static ItemPhysicsModule active() {
        if (OnixClient.MODULES == null) return null;
        ItemPhysicsModule m = OnixClient.MODULES.get(ItemPhysicsModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    public boolean tumble() {
        return tumble.isOn();
    }
}
