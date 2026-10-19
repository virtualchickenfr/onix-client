package net.minecraft.client.gui.render.pip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.state.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class GuiEntityRenderer extends PictureInPictureRenderer<GuiEntityRenderState> {
	private final EntityRenderDispatcher entityRenderDispatcher;

	public GuiEntityRenderer(MultiBufferSource.BufferSource bufferSource, EntityRenderDispatcher entityRenderDispatcher) {
		super(bufferSource);
		this.entityRenderDispatcher = entityRenderDispatcher;
	}

	@Override
	public Class<GuiEntityRenderState> getRenderStateClass() {
		return GuiEntityRenderState.class;
	}

	protected void renderToTexture(GuiEntityRenderState guiEntityRenderState, PoseStack poseStack) {
		Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		Vector3f vector3f = guiEntityRenderState.translation();
		poseStack.translate(vector3f.x, vector3f.y, vector3f.z);
		poseStack.mulPose(guiEntityRenderState.rotation());
		Quaternionf quaternionf = guiEntityRenderState.overrideCameraAngle();
		if (quaternionf != null) {
			this.entityRenderDispatcher.overrideCameraOrientation(quaternionf.conjugate(new Quaternionf()).rotateY((float) Math.PI));
		}

		this.entityRenderDispatcher.setRenderShadow(false);
		this.entityRenderDispatcher.render(guiEntityRenderState.renderState(), 0.0, 0.0, 0.0, poseStack, this.bufferSource, 15728880);
		this.entityRenderDispatcher.setRenderShadow(true);
	}

	@Override
	protected float getTranslateY(int i, int j) {
		return i / 2.0F;
	}

	@Override
	protected String getTextureLabel() {
		return "entity";
	}
}
