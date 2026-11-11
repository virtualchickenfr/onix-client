package dev.onix.client.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Death Screens hide the vanilla title and draw their own. */
@Mixin(Screen.class)
public interface ScreenTitleAccessor {
    @Mutable
    @Accessor("title")
    void onix$setTitle(Component title);
}
