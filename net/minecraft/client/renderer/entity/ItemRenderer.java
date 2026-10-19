package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.mojang.math.MatrixUtil;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ItemRenderer {
	public static final ResourceLocation ENCHANTED_GLINT_ARMOR = ResourceLocation.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");
	public static final ResourceLocation ENCHANTED_GLINT_ITEM = ResourceLocation.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
	public static final float SPECIAL_FOIL_UI_SCALE = 0.5F;
	public static final float SPECIAL_FOIL_FIRST_PERSON_SCALE = 0.75F;
	public static final float SPECIAL_FOIL_TEXTURE_SCALE = 0.0078125F;
	public static final int NO_TINT = -1;
	private final ItemModelResolver resolver;
	private final ItemStackRenderState scratchItemStackRenderState = new ItemStackRenderState();

	public ItemRenderer(ItemModelResolver itemModelResolver) {
		this.resolver = itemModelResolver;
	}

	public static void renderItem(
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		int j,
		int[] is,
		List<BakedQuad> list,
		RenderType renderType,
		ItemStackRenderState.FoilType foilType
	) {
		VertexConsumer vertexConsumer;
		if (foilType == ItemStackRenderState.FoilType.SPECIAL) {
			PoseStack.Pose pose = poseStack.last().copy();
			if (itemDisplayContext == ItemDisplayContext.GUI) {
				MatrixUtil.mulComponentWise(pose.pose(), 0.5F);
			} else if (itemDisplayContext.firstPerson()) {
				MatrixUtil.mulComponentWise(pose.pose(), 0.75F);
			}

			vertexConsumer = getSpecialFoilBuffer(multiBufferSource, renderType, pose);
		} else {
			vertexConsumer = getFoilBuffer(multiBufferSource, renderType, true, foilType != ItemStackRenderState.FoilType.NONE);
		}

		renderQuadList(poseStack, vertexConsumer, list, is, i, j);
	}

	public static VertexConsumer getArmorFoilBuffer(MultiBufferSource multiBufferSource, RenderType renderType, boolean bl) {
		return bl
			? VertexMultiConsumer.create(multiBufferSource.getBuffer(RenderType.armorEntityGlint()), multiBufferSource.getBuffer(renderType))
			: multiBufferSource.getBuffer(renderType);
	}

	private static VertexConsumer getSpecialFoilBuffer(MultiBufferSource multiBufferSource, RenderType renderType, PoseStack.Pose pose) {
		return VertexMultiConsumer.create(
			new SheetedDecalTextureGenerator(
				multiBufferSource.getBuffer(useTransparentGlint(renderType) ? RenderType.glintTranslucent() : RenderType.glint()), pose, 0.0078125F
			),
			multiBufferSource.getBuffer(renderType)
		);
	}

	public static VertexConsumer getFoilBuffer(MultiBufferSource multiBufferSource, RenderType renderType, boolean bl, boolean bl2) {
		if (bl2) {
			return useTransparentGlint(renderType)
				? VertexMultiConsumer.create(multiBufferSource.getBuffer(RenderType.glintTranslucent()), multiBufferSource.getBuffer(renderType))
				: VertexMultiConsumer.create(multiBufferSource.getBuffer(bl ? RenderType.glint() : RenderType.entityGlint()), multiBufferSource.getBuffer(renderType));
		} else {
			return multiBufferSource.getBuffer(renderType);
		}
	}

	private static boolean useTransparentGlint(RenderType renderType) {
		return Minecraft.useShaderTransparency() && renderType == Sheets.translucentItemSheet();
	}

	private static int getLayerColorSafe(int[] is, int i) {
		return i >= 0 && i < is.length ? is[i] : -1;
	}

	private static void renderQuadList(PoseStack poseStack, VertexConsumer vertexConsumer, List<BakedQuad> list, int[] is, int i, int j) {
		PoseStack.Pose pose = poseStack.last();

		for (BakedQuad bakedQuad : list) {
			float f;
			float g;
			float h;
			float l;
			if (bakedQuad.isTinted()) {
				int k = getLayerColorSafe(is, bakedQuad.tintIndex());
				f = ARGB.alpha(k) / 255.0F;
				g = ARGB.red(k) / 255.0F;
				h = ARGB.green(k) / 255.0F;
				l = ARGB.blue(k) / 255.0F;
			} else {
				f = 1.0F;
				g = 1.0F;
				h = 1.0F;
				l = 1.0F;
			}

			vertexConsumer.putBulkData(pose, bakedQuad, g, h, l, f, i, j);
		}
	}

	public void renderStatic(
		ItemStack itemStack,
		ItemDisplayContext itemDisplayContext,
		int i,
		int j,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		@Nullable Level level,
		int k
	) {
		this.renderStatic(null, itemStack, itemDisplayContext, poseStack, multiBufferSource, level, i, j, k);
	}

	public void renderStatic(
		@Nullable LivingEntity livingEntity,
		ItemStack itemStack,
		ItemDisplayContext itemDisplayContext,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		@Nullable Level level,
		int i,
		int j,
		int k
	) {
		this.resolver.updateForTopItem(this.scratchItemStackRenderState, itemStack, itemDisplayContext, level, livingEntity, k);
		this.scratchItemStackRenderState.render(poseStack, multiBufferSource, i, j);
	}
}
