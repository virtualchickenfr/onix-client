package dev.onix.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.onix.client.cosmetics.CosmeticIcons;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.21.6/1.21.7: one picture-in-picture renderer draws every state of its kind into the same texture, so all
 * cosmetic icons on a page would show the last one. Each icon gets its own renderer from a small pool instead
 * (Fabric API does the same from 1.21.8 on).
 */
@Mixin(GuiRenderer.class)
public abstract class CosmeticIconPoolMixin {
    @Shadow @Final private MultiBufferSource.BufferSource bufferSource;

    @Unique private final List<CosmeticIcons.Renderer> onix$pool = new ArrayList<>();
    @Unique private int onix$used;

    @Inject(method = "preparePictureInPicture", at = @At("HEAD"))
    private void onix$resetPool(CallbackInfo ci) {
        onix$used = 0;
    }

    @WrapOperation(method = "preparePictureInPictureState", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object onix$pooledRenderer(Map<?, ?> map, Object key, Operation<Object> original) {
        if (key != CosmeticIcons.State.class) return original.call(map, key);
        if (onix$used == onix$pool.size()) onix$pool.add(new CosmeticIcons.Renderer(bufferSource));
        return onix$pool.get(onix$used++);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onix$closePool(CallbackInfo ci) {
        onix$pool.forEach(CosmeticIcons.Renderer::close);
        onix$pool.clear();
    }
}
