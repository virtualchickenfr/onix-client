package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.BannerFlagModel;
import net.minecraft.client.model.BannerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class BannerRenderer implements BlockEntityRenderer<BannerBlockEntity> {
	private static final int MAX_PATTERNS = 16;
	private static final float SIZE = 0.6666667F;
	private final BannerModel standingModel;
	private final BannerModel wallModel;
	private final BannerFlagModel standingFlagModel;
	private final BannerFlagModel wallFlagModel;

	public BannerRenderer(BlockEntityRendererProvider.Context context) {
		this(context.getModelSet());
	}

	public BannerRenderer(EntityModelSet entityModelSet) {
		this.standingModel = new BannerModel(entityModelSet.bakeLayer(ModelLayers.STANDING_BANNER));
		this.wallModel = new BannerModel(entityModelSet.bakeLayer(ModelLayers.WALL_BANNER));
		this.standingFlagModel = new BannerFlagModel(entityModelSet.bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
		this.wallFlagModel = new BannerFlagModel(entityModelSet.bakeLayer(ModelLayers.WALL_BANNER_FLAG));
	}

	public void render(BannerBlockEntity bannerBlockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, Vec3 vec3) {
		BlockState blockState = bannerBlockEntity.getBlockState();
		BannerModel bannerModel;
		BannerFlagModel bannerFlagModel;
		float g;
		if (blockState.getBlock() instanceof BannerBlock) {
			g = -RotationSegment.convertToDegrees(blockState.getValue(BannerBlock.ROTATION));
			bannerModel = this.standingModel;
			bannerFlagModel = this.standingFlagModel;
		} else {
			g = -blockState.getValue(WallBannerBlock.FACING).toYRot();
			bannerModel = this.wallModel;
			bannerFlagModel = this.wallFlagModel;
		}

		long l = bannerBlockEntity.getLevel().getGameTime();
		BlockPos blockPos = bannerBlockEntity.getBlockPos();
		float h = ((float)Math.floorMod(blockPos.getX() * 7 + blockPos.getY() * 9 + blockPos.getZ() * 13 + l, 100L) + f) / 100.0F;
		renderBanner(poseStack, multiBufferSource, i, j, g, bannerModel, bannerFlagModel, h, bannerBlockEntity.getBaseColor(), bannerBlockEntity.getPatterns());
	}

	public void renderInHand(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, DyeColor dyeColor, BannerPatternLayers bannerPatternLayers) {
		renderBanner(poseStack, multiBufferSource, i, j, 0.0F, this.standingModel, this.standingFlagModel, 0.0F, dyeColor, bannerPatternLayers);
	}

	private static void renderBanner(
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		int j,
		float f,
		BannerModel bannerModel,
		BannerFlagModel bannerFlagModel,
		float g,
		DyeColor dyeColor,
		BannerPatternLayers bannerPatternLayers
	) {
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(f));
		poseStack.scale(0.6666667F, -0.6666667F, -0.6666667F);
		bannerModel.renderToBuffer(poseStack, ModelBakery.BANNER_BASE.buffer(multiBufferSource, RenderType::entitySolid), i, j);
		bannerFlagModel.setupAnim(g);
		renderPatterns(poseStack, multiBufferSource, i, j, bannerFlagModel.root(), ModelBakery.BANNER_BASE, true, dyeColor, bannerPatternLayers);
		poseStack.popPose();
	}

	public static void renderPatterns(
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		int j,
		ModelPart modelPart,
		Material material,
		boolean bl,
		DyeColor dyeColor,
		BannerPatternLayers bannerPatternLayers
	) {
		renderPatterns(poseStack, multiBufferSource, i, j, modelPart, material, bl, dyeColor, bannerPatternLayers, false, true);
	}

	public static void renderPatterns(
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		int j,
		ModelPart modelPart,
		Material material,
		boolean bl,
		DyeColor dyeColor,
		BannerPatternLayers bannerPatternLayers,
		boolean bl2,
		boolean bl3
	) {
		modelPart.render(poseStack, material.buffer(multiBufferSource, RenderType::entitySolid, bl3, bl2), i, j);
		renderPatternLayer(poseStack, multiBufferSource, i, j, modelPart, bl ? Sheets.BANNER_BASE : Sheets.SHIELD_BASE, dyeColor);

		for (int k = 0; k < 16 && k < bannerPatternLayers.layers().size(); k++) {
			BannerPatternLayers.Layer layer = bannerPatternLayers.layers().get(k);
			Material material2 = bl ? Sheets.getBannerMaterial(layer.pattern()) : Sheets.getShieldMaterial(layer.pattern());
			renderPatternLayer(poseStack, multiBufferSource, i, j, modelPart, material2, layer.color());
		}
	}

	private static void renderPatternLayer(
		PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, ModelPart modelPart, Material material, DyeColor dyeColor
	) {
		int k = dyeColor.getTextureDiffuseColor();
		modelPart.render(poseStack, material.buffer(multiBufferSource, RenderType::entityNoOutline), i, j, k);
	}

	public void getExtents(Set<Vector3f> set) {
		PoseStack poseStack = new PoseStack();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		poseStack.scale(0.6666667F, -0.6666667F, -0.6666667F);
		this.standingModel.root().getExtentsForGui(poseStack, set);
		this.standingFlagModel.setupAnim(0.0F);
		this.standingFlagModel.root().getExtentsForGui(poseStack, set);
	}
}
