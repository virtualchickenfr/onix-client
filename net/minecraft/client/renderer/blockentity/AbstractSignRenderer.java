package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public abstract class AbstractSignRenderer implements BlockEntityRenderer<SignBlockEntity> {
	private static final int BLACK_TEXT_OUTLINE_COLOR = -988212;
	private static final int OUTLINE_RENDER_DISTANCE = Mth.square(16);
	private final Font font;

	public AbstractSignRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.getFont();
	}

	protected abstract Model getSignModel(BlockState blockState, WoodType woodType);

	protected abstract Material getSignMaterial(WoodType woodType);

	protected abstract float getSignModelRenderScale();

	protected abstract float getSignTextRenderScale();

	protected abstract Vec3 getTextOffset();

	protected abstract void translateSign(PoseStack poseStack, float f, BlockState blockState);

	public void render(SignBlockEntity signBlockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, Vec3 vec3) {
		BlockState blockState = signBlockEntity.getBlockState();
		SignBlock signBlock = (SignBlock)blockState.getBlock();
		Model model = this.getSignModel(blockState, signBlock.type());
		this.renderSignWithText(signBlockEntity, poseStack, multiBufferSource, i, j, blockState, signBlock, signBlock.type(), model);
	}

	private void renderSignWithText(
		SignBlockEntity signBlockEntity,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		int j,
		BlockState blockState,
		SignBlock signBlock,
		WoodType woodType,
		Model model
	) {
		poseStack.pushPose();
		this.translateSign(poseStack, -signBlock.getYRotationDegrees(blockState), blockState);
		this.renderSign(poseStack, multiBufferSource, i, j, woodType, model);
		this.renderSignText(
			signBlockEntity.getBlockPos(),
			signBlockEntity.getFrontText(),
			poseStack,
			multiBufferSource,
			i,
			signBlockEntity.getTextLineHeight(),
			signBlockEntity.getMaxTextLineWidth(),
			true
		);
		this.renderSignText(
			signBlockEntity.getBlockPos(),
			signBlockEntity.getBackText(),
			poseStack,
			multiBufferSource,
			i,
			signBlockEntity.getTextLineHeight(),
			signBlockEntity.getMaxTextLineWidth(),
			false
		);
		poseStack.popPose();
	}

	protected void renderSign(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, WoodType woodType, Model model) {
		poseStack.pushPose();
		float f = this.getSignModelRenderScale();
		poseStack.scale(f, -f, -f);
		Material material = this.getSignMaterial(woodType);
		VertexConsumer vertexConsumer = material.buffer(multiBufferSource, model::renderType);
		model.renderToBuffer(poseStack, vertexConsumer, i, j);
		poseStack.popPose();
	}

	private void renderSignText(BlockPos blockPos, SignText signText, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, int k, boolean bl) {
		poseStack.pushPose();
		this.translateSignText(poseStack, bl, this.getTextOffset());
		int l = getDarkColor(signText);
		int m = 4 * j / 2;
		FormattedCharSequence[] formattedCharSequences = signText.getRenderMessages(Minecraft.getInstance().isTextFilteringEnabled(), component -> {
			List<FormattedCharSequence> list = this.font.split(component, k);
			return list.isEmpty() ? FormattedCharSequence.EMPTY : list.get(0);
		});
		int n;
		boolean bl2;
		int o;
		if (signText.hasGlowingText()) {
			n = signText.getColor().getTextColor();
			bl2 = isOutlineVisible(blockPos, n);
			o = 15728880;
		} else {
			n = l;
			bl2 = false;
			o = i;
		}

		for (int p = 0; p < 4; p++) {
			FormattedCharSequence formattedCharSequence = formattedCharSequences[p];
			float f = -this.font.width(formattedCharSequence) / 2;
			if (bl2) {
				this.font.drawInBatch8xOutline(formattedCharSequence, f, p * j - m, n, l, poseStack.last().pose(), multiBufferSource, o);
			} else {
				this.font.drawInBatch(formattedCharSequence, f, p * j - m, n, false, poseStack.last().pose(), multiBufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, o);
			}
		}

		poseStack.popPose();
	}

	private void translateSignText(PoseStack poseStack, boolean bl, Vec3 vec3) {
		if (!bl) {
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		}

		float f = 0.015625F * this.getSignTextRenderScale();
		poseStack.translate(vec3);
		poseStack.scale(f, -f, f);
	}

	private static boolean isOutlineVisible(BlockPos blockPos, int i) {
		if (i == DyeColor.BLACK.getTextColor()) {
			return true;
		}

		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer localPlayer = minecraft.player;
		if (localPlayer != null && minecraft.options.getCameraType().isFirstPerson() && localPlayer.isScoping()) {
			return true;
		}

		Entity entity = minecraft.getCameraEntity();
		return entity != null && entity.distanceToSqr(Vec3.atCenterOf(blockPos)) < OUTLINE_RENDER_DISTANCE;
	}

	public static int getDarkColor(SignText signText) {
		int i = signText.getColor().getTextColor();
		return i == DyeColor.BLACK.getTextColor() && signText.hasGlowingText() ? -988212 : ARGB.scaleRGB(i, 0.4F);
	}
}
