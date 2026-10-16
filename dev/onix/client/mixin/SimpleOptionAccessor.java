package dev.onix.client.mixin;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets an option value without range validation (used by Fullbright). */
@Mixin(OptionInstance.class)
public interface SimpleOptionAccessor<T> {
    @Accessor("value")
    void onix$setValue(T value);
}
