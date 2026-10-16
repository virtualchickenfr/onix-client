package gg.carbon.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.21.11 keeps the world FOV private in GameRenderer (26.3 exposed it on Camera). */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
	@Invoker("getFov")
	float carbon$getFov(Camera camera, float partialTicks, boolean useFovSetting);
}
