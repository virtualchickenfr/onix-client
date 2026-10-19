package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import java.util.Objects;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class ShieldSpecialRenderer implements SpecialModelRenderer<DataComponentMap> {
	private final ShieldModel model;

	public ShieldSpecialRenderer(ShieldModel shieldModel) {
		this.model = shieldModel;
	}

	@Nullable
	public DataComponentMap extractArgument(ItemStack itemStack) {
		return itemStack.immutableComponents();
	}

	public void render(
		@Nullable DataComponentMap dataComponentMap,
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		int j,
		boolean bl
	) {
		BannerPatternLayers bannerPatternLayers = dataComponentMap != null
			? dataComponentMap.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
			: BannerPatternLayers.EMPTY;
		DyeColor dyeColor = dataComponentMap != null ? dataComponentMap.get(DataComponents.BASE_COLOR) : null;
		boolean bl2 = !bannerPatternLayers.layers().isEmpty() || dyeColor != null;
		poseStack.pushPose();
		poseStack.scale(1.0F, -1.0F, -1.0F);
		Material material = bl2 ? ModelBakery.SHIELD_BASE : ModelBakery.NO_PATTERN_SHIELD;
		VertexConsumer vertexConsumer = material.sprite()
			.wrap(ItemRenderer.getFoilBuffer(multiBufferSource, this.model.renderType(material.atlasLocation()), itemDisplayContext == ItemDisplayContext.GUI, bl));
		this.model.handle().render(poseStack, vertexConsumer, i, j);
		if (bl2) {
			BannerRenderer.renderPatterns(
				poseStack,
				multiBufferSource,
				i,
				j,
				this.model.plate(),
				material,
				false,
				Objects.requireNonNullElse(dyeColor, DyeColor.WHITE),
				bannerPatternLayers,
				bl,
				false
			);
		} else {
			this.model.plate().render(poseStack, vertexConsumer, i, j);
		}

		poseStack.popPose();
	}

	@Override
	public void getExtents(Set<Vector3f> set) {
		PoseStack poseStack = new PoseStack();
		poseStack.scale(1.0F, -1.0F, -1.0F);
		this.model.root().getExtentsForGui(poseStack, set);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked() implements SpecialModelRenderer.Unbaked {
		public static final ShieldSpecialRenderer.Unbaked INSTANCE = new ShieldSpecialRenderer.Unbaked();
		public static final MapCodec<ShieldSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

		@Override
		public MapCodec<ShieldSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(EntityModelSet entityModelSet) {
			return new ShieldSpecialRenderer(new ShieldModel(entityModelSet.bakeLayer(ModelLayers.SHIELD)));
		}
	}
}
