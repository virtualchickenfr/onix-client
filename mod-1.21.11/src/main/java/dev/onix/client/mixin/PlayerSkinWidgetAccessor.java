package dev.onix.client.mixin;

import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The title screen poses the menu player (walking in) and sets which way it faces. */
@Mixin(PlayerSkinWidget.class)
public interface PlayerSkinWidgetAccessor {
    @Accessor("wideModel")
    PlayerModel onix$wide();

    @Accessor("slimModel")
    PlayerModel onix$slim();

    @Accessor("rotationY")
    void onix$setRotationY(float value);

    @Accessor("rotationX")
    void onix$setRotationX(float value);
}
