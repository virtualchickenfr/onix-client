package net.minecraft.client.gui.render.pip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.state.pip.GuiBannerResultRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;

@Environment(EnvType.CLIENT)
public class GuiBannerResultRenderer extends PictureInPictureRenderer<GuiBannerResultRenderState> {
	public GuiBannerResultRenderer(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
	}

	@Override
	public Class<GuiBannerResultRenderState> getRenderStateClass() {
		return GuiBannerResultRenderState.class;
	}

	protected void renderToTexture(GuiBannerResultRenderState guiBannerResultRenderState, PoseStack poseStack) {
		Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_FLAT);
		poseStack.translate(0.0F, 0.25F, 0.0F);
		BannerRenderer.renderPatterns(
			poseStack,
			this.bufferSource,
			15728880,
			OverlayTexture.NO_OVERLAY,
			guiBannerResultRenderState.flag(),
			ModelBakery.BANNER_BASE,
			true,
			guiBannerResultRenderState.baseColor(),
			guiBannerResultRenderState.resultBannerPatterns()
		);
	}

	@Override
	protected String getTextureLabel() {
		return "banner result";
	}
}
