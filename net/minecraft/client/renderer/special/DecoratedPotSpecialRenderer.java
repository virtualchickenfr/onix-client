package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.Objects;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.DecoratedPotRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.PotDecorations;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class DecoratedPotSpecialRenderer implements SpecialModelRenderer<PotDecorations> {
	private final DecoratedPotRenderer decoratedPotRenderer;

	public DecoratedPotSpecialRenderer(DecoratedPotRenderer decoratedPotRenderer) {
		this.decoratedPotRenderer = decoratedPotRenderer;
	}

	@Nullable
	public PotDecorations extractArgument(ItemStack itemStack) {
		return itemStack.get(DataComponents.POT_DECORATIONS);
	}

	public void render(
		@Nullable PotDecorations potDecorations,
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		int j,
		boolean bl
	) {
		this.decoratedPotRenderer.renderInHand(poseStack, multiBufferSource, i, j, Objects.requireNonNullElse(potDecorations, PotDecorations.EMPTY));
	}

	@Override
	public void getExtents(Set<Vector3f> set) {
		this.decoratedPotRenderer.getExtents(set);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked() implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<DecoratedPotSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new DecoratedPotSpecialRenderer.Unbaked());

		@Override
		public MapCodec<DecoratedPotSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(EntityModelSet entityModelSet) {
			return new DecoratedPotSpecialRenderer(new DecoratedPotRenderer(entityModelSet));
		}
	}
}
