package dev.onix.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.onix.client.module.impl.visual.ItemPhysicsModule;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Physics (ItemPhysicsModule): flat items lie on the ground, blocks sit on it, nothing bobs or spins there. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void onix$ground(ItemEntity entity, ItemEntityRenderState state, float partial, CallbackInfo ci) {
        ((ItemPhysicsModule.State) state).onix$setOnGround(entity.onGround());
    }

    private static boolean onix$flat(AABB box) {
        return box.maxZ - box.minZ < 0.2;
    }

    /** The lift: vanilla bobs the item up and down; on the ground it rests (flat items at half their thickness). */
    @WrapOperation(method = {"submit", "render"}, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0), require = 0)
    private void onix$lift(PoseStack pose, float x, float y, float z, Operation<Void> original,
                           @Local(argsOnly = true) ItemEntityRenderState state, @Local AABB box) {
        ItemPhysicsModule m = ItemPhysicsModule.active();
        if (m == null || !((ItemPhysicsModule.State) state).onix$onGround()) {
            original.call(pose, x, y, z);
            return;
        }
        float rest = onix$flat(box) ? (float) (box.maxZ - box.minZ) / 2f + 0.01f : -(float) box.minY;
        original.call(pose, x, rest, z);
    }

    /** The spin: on the ground a fixed angle per item (flat ones laid down), in the air an optional tumble. */
    @WrapOperation(method = {"submit", "render"}, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V", ordinal = 0), require = 0)
    private void onix$spin(PoseStack pose, org.joml.Quaternionfc rotation, Operation<Void> original,
                           @Local(argsOnly = true) ItemEntityRenderState state, @Local AABB box) {
        ItemPhysicsModule m = ItemPhysicsModule.active();
        if (m == null) {
            original.call(pose, rotation);
            return;
        }
        boolean flat = onix$flat(box);
        float angle = state.bobOffset;   // random per item, fixed
        if (((ItemPhysicsModule.State) state).onix$onGround()) {
            original.call(pose, Axis.YP.rotation(angle));
            if (flat) pose.mulPose(Axis.XP.rotationDegrees(90f));
        } else if (m.tumble()) {
            float t = state.ageInTicks;
            original.call(pose, Axis.YP.rotation(angle));
            pose.mulPose(Axis.XP.rotation(t * 0.35f + angle));
        } else {
            original.call(pose, Axis.YP.rotation(angle));
        }
    }
}
