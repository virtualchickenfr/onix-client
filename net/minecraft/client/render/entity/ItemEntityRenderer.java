package net.minecraft.client.render.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;

@Environment(EnvType.CLIENT)
public class ItemEntityRenderer extends EntityRenderer<ItemEntity, ItemEntityRenderState> {
	private static final float field_56954 = 0.0625F;
	private static final float field_32924 = 0.15F;
	private static final float field_56955 = 0.0625F;
	private final ItemModelManager itemModelManager;
	private final Random random = Random.create();

	public ItemEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.itemModelManager = context.getItemModelManager();
		this.shadowRadius = 0.15F;
		this.shadowOpacity = 0.75F;
	}

	public ItemEntityRenderState createRenderState() {
		return new ItemEntityRenderState();
	}

	public void updateRenderState(ItemEntity itemEntity, ItemEntityRenderState itemEntityRenderState, float f) {
		super.updateRenderState(itemEntity, itemEntityRenderState, f);
		itemEntityRenderState.age = itemEntity.getItemAge() + f;
		itemEntityRenderState.uniqueOffset = itemEntity.uniqueOffset;
		itemEntityRenderState.update(itemEntity, itemEntity.getStack(), this.itemModelManager);
	}

	private static Box getBoundingBox(ItemRenderState state) {
		Box.Builder builder = new Box.Builder();
		state.load(builder::encompass);
		return builder.build();
	}

	public void render(ItemEntityRenderState itemEntityRenderState, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i) {
		if (!itemEntityRenderState.itemRenderState.isEmpty()) {
			matrixStack.push();
			Box box = getBoundingBox(itemEntityRenderState.itemRenderState);
			float f = -((float)box.minY) + 0.0625F;
			float g = MathHelper.sin(itemEntityRenderState.age / 10.0F + itemEntityRenderState.uniqueOffset) * 0.1F + 0.1F;
			matrixStack.translate(0.0F, g + f, 0.0F);
			float h = ItemEntity.getRotation(itemEntityRenderState.age, itemEntityRenderState.uniqueOffset);
			matrixStack.multiply(RotationAxis.POSITIVE_Y.rotation(h));
			renderStack(matrixStack, vertexConsumerProvider, i, itemEntityRenderState, this.random, box);
			matrixStack.pop();
			super.render(itemEntityRenderState, matrixStack, vertexConsumerProvider, i);
		}
	}

	public static void render(MatrixStack matrices, VertexConsumerProvider provider, int light, ItemStackEntityRenderState state, Random random) {
		renderStack(matrices, provider, light, state, random, getBoundingBox(state.itemRenderState));
	}

	public static void renderStack(
		MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ItemStackEntityRenderState state, Random random, Box box
	) {
		int i = state.renderedAmount;
		if (i != 0) {
			random.setSeed(state.seed);
			ItemRenderState itemRenderState = state.itemRenderState;
			float f = (float)box.getLengthZ();
			if (f > 0.0625F) {
				itemRenderState.render(matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);

				for (int j = 1; j < i; j++) {
					matrices.push();
					float g = (random.nextFloat() * 2.0F - 1.0F) * 0.15F;
					float h = (random.nextFloat() * 2.0F - 1.0F) * 0.15F;
					float k = (random.nextFloat() * 2.0F - 1.0F) * 0.15F;
					matrices.translate(g, h, k);
					itemRenderState.render(matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
					matrices.pop();
				}
			} else {
				float l = f * 1.5F;
				matrices.translate(0.0F, 0.0F, -(l * (i - 1) / 2.0F));
				itemRenderState.render(matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
				matrices.translate(0.0F, 0.0F, l);

				for (int m = 1; m < i; m++) {
					matrices.push();
					float h = (random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
					float k = (random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
					matrices.translate(h, k, 0.0F);
					itemRenderState.render(matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
					matrices.pop();
					matrices.translate(0.0F, 0.0F, l);
				}
			}
		}
	}
}
