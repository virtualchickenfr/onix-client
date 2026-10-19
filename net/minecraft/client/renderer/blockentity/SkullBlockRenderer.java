package net.minecraft.client.renderer.blockentity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PiglinHeadModel;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.dragon.DragonHeadModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SkullBlockRenderer implements BlockEntityRenderer<SkullBlockEntity> {
	private final Function<SkullBlock.Type, SkullModelBase> modelByType;
	private static final Map<SkullBlock.Type, ResourceLocation> SKIN_BY_TYPE = Util.make(Maps.newHashMap(), hashMap -> {
		hashMap.put(SkullBlock.Types.SKELETON, ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png"));
		hashMap.put(SkullBlock.Types.WITHER_SKELETON, ResourceLocation.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png"));
		hashMap.put(SkullBlock.Types.ZOMBIE, ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png"));
		hashMap.put(SkullBlock.Types.CREEPER, ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper.png"));
		hashMap.put(SkullBlock.Types.DRAGON, ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon.png"));
		hashMap.put(SkullBlock.Types.PIGLIN, ResourceLocation.withDefaultNamespace("textures/entity/piglin/piglin.png"));
		hashMap.put(SkullBlock.Types.PLAYER, DefaultPlayerSkin.getDefaultTexture());
	});

	@Nullable
	public static SkullModelBase createModel(EntityModelSet entityModelSet, SkullBlock.Type type) {
		if (type instanceof SkullBlock.Types types) {
			return switch (types) {
				case SKELETON -> new SkullModel(entityModelSet.bakeLayer(ModelLayers.SKELETON_SKULL));
				case WITHER_SKELETON -> new SkullModel(entityModelSet.bakeLayer(ModelLayers.WITHER_SKELETON_SKULL));
				case PLAYER -> new SkullModel(entityModelSet.bakeLayer(ModelLayers.PLAYER_HEAD));
				case ZOMBIE -> new SkullModel(entityModelSet.bakeLayer(ModelLayers.ZOMBIE_HEAD));
				case CREEPER -> new SkullModel(entityModelSet.bakeLayer(ModelLayers.CREEPER_HEAD));
				case DRAGON -> new DragonHeadModel(entityModelSet.bakeLayer(ModelLayers.DRAGON_SKULL));
				case PIGLIN -> new PiglinHeadModel(entityModelSet.bakeLayer(ModelLayers.PIGLIN_HEAD));
			};
		} else {
			return null;
		}
	}

	public SkullBlockRenderer(BlockEntityRendererProvider.Context context) {
		EntityModelSet entityModelSet = context.getModelSet();
		this.modelByType = Util.memoize(type -> createModel(entityModelSet, type));
	}

	public void render(SkullBlockEntity skullBlockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, Vec3 vec3) {
		float g = skullBlockEntity.getAnimation(f);
		BlockState blockState = skullBlockEntity.getBlockState();
		boolean bl = blockState.getBlock() instanceof WallSkullBlock;
		Direction direction = bl ? blockState.getValue(WallSkullBlock.FACING) : null;
		int k = bl ? RotationSegment.convertToSegment(direction.getOpposite()) : blockState.getValue(SkullBlock.ROTATION);
		float h = RotationSegment.convertToDegrees(k);
		SkullBlock.Type type = ((AbstractSkullBlock)blockState.getBlock()).getType();
		SkullModelBase skullModelBase = this.modelByType.apply(type);
		RenderType renderType = getRenderType(type, skullBlockEntity.getOwnerProfile());
		renderSkull(direction, h, g, poseStack, multiBufferSource, i, skullModelBase, renderType);
	}

	public static void renderSkull(
		@Nullable Direction direction,
		float f,
		float g,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		SkullModelBase skullModelBase,
		RenderType renderType
	) {
		poseStack.pushPose();
		if (direction == null) {
			poseStack.translate(0.5F, 0.0F, 0.5F);
		} else {
			float h = 0.25F;
			poseStack.translate(0.5F - direction.getStepX() * 0.25F, 0.25F, 0.5F - direction.getStepZ() * 0.25F);
		}

		poseStack.scale(-1.0F, -1.0F, 1.0F);
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(renderType);
		skullModelBase.setupAnim(g, f, 0.0F);
		skullModelBase.renderToBuffer(poseStack, vertexConsumer, i, OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
	}

	public static RenderType getRenderType(SkullBlock.Type type, @Nullable ResolvableProfile resolvableProfile) {
		return type == SkullBlock.Types.PLAYER && resolvableProfile != null
			? getPlayerSkinRenderType(Minecraft.getInstance().getSkinManager().getInsecureSkin(resolvableProfile.gameProfile()).texture())
			: getSkullRenderType(type, null);
	}

	public static RenderType getSkullRenderType(SkullBlock.Type type, @Nullable ResourceLocation resourceLocation) {
		return RenderType.entityCutoutNoCullZOffset(resourceLocation != null ? resourceLocation : SKIN_BY_TYPE.get(type));
	}

	public static RenderType getPlayerSkinRenderType(ResourceLocation resourceLocation) {
		return RenderType.entityTranslucent(resourceLocation);
	}
}
