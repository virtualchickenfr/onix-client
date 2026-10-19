package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;

@Environment(EnvType.CLIENT)
public class SpecialBlockModelRenderer {
	public static final SpecialBlockModelRenderer EMPTY = new SpecialBlockModelRenderer(Map.of());
	private final Map<Block, SpecialModelRenderer<?>> renderers;

	public SpecialBlockModelRenderer(Map<Block, SpecialModelRenderer<?>> map) {
		this.renderers = map;
	}

	public static SpecialBlockModelRenderer vanilla(EntityModelSet entityModelSet) {
		return new SpecialBlockModelRenderer(SpecialModelRenderers.createBlockRenderers(entityModelSet));
	}

	public void renderByBlock(Block block, ItemDisplayContext itemDisplayContext, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j) {
		SpecialModelRenderer<?> specialModelRenderer = this.renderers.get(block);
		if (specialModelRenderer != null) {
			specialModelRenderer.render(null, itemDisplayContext, poseStack, multiBufferSource, i, j, false);
		}
	}
}
