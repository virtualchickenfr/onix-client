package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class BannerSpecialRenderer implements SpecialModelRenderer<BannerPatternLayers> {
	private final BannerRenderer bannerRenderer;
	private final DyeColor baseColor;

	public BannerSpecialRenderer(DyeColor dyeColor, BannerRenderer bannerRenderer) {
		this.bannerRenderer = bannerRenderer;
		this.baseColor = dyeColor;
	}

	@Nullable
	public BannerPatternLayers extractArgument(ItemStack itemStack) {
		return itemStack.get(DataComponents.BANNER_PATTERNS);
	}

	public void render(
		@Nullable BannerPatternLayers bannerPatternLayers,
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		int j,
		boolean bl
	) {
		this.bannerRenderer
			.renderInHand(poseStack, multiBufferSource, i, j, this.baseColor, Objects.requireNonNullElse(bannerPatternLayers, BannerPatternLayers.EMPTY));
	}

	@Override
	public void getExtents(Set<Vector3f> set) {
		this.bannerRenderer.getExtents(set);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(DyeColor baseColor) implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<BannerSpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(DyeColor.CODEC.fieldOf("color").forGetter(BannerSpecialRenderer.Unbaked::baseColor))
				.apply(instance, BannerSpecialRenderer.Unbaked::new)
		);

		@Override
		public MapCodec<BannerSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(EntityModelSet entityModelSet) {
			return new BannerSpecialRenderer(this.baseColor, new BannerRenderer(entityModelSet));
		}
	}
}
