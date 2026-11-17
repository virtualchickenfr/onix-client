package dev.onix.client.mixin;

import dev.onix.client.module.impl.visual.ItemPhysicsModule;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Whether the item lies on the ground, for Item Physics. */
@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicsModule.State {
    @Unique
    private boolean onix$onGround;

    @Override
    public boolean onix$onGround() {
        return onix$onGround;
    }

    @Override
    public void onix$setOnGround(boolean onGround) {
        onix$onGround = onGround;
    }
}
