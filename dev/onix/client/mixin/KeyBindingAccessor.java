package dev.onix.client.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Extra Keybinds count a press the way vanilla does when the bound key is pressed. */
@Mixin(KeyMapping.class)
public interface KeyBindingAccessor {
    @Accessor("timesPressed")
    int onix$getTimesPressed();

    @Accessor("timesPressed")
    void onix$setTimesPressed(int value);
}
