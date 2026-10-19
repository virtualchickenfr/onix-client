package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultClientData;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class VaultRenderer implements BlockEntityRenderer<VaultBlockEntity> {
	private final ItemModelResolver itemModelResolver;
	private final RandomSource random = RandomSource.create();
	private final ItemClusterRenderState renderState = new ItemClusterRenderState();

	public VaultRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModelResolver = context.getItemModelResolver();
	}

	public void render(VaultBlockEntity vaultBlockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, Vec3 vec3) {
		if (VaultBlockEntity.Client.shouldDisplayActiveEffects(vaultBlockEntity.getSharedData())) {
			Level level = vaultBlockEntity.getLevel();
			if (level != null) {
				ItemStack itemStack = vaultBlockEntity.getSharedData().getDisplayItem();
				if (!itemStack.isEmpty()) {
					this.itemModelResolver.updateForTopItem(this.renderState.item, itemStack, ItemDisplayContext.GROUND, level, null, 0);
					this.renderState.count = ItemClusterRenderState.getRenderedAmount(itemStack.getCount());
					this.renderState.seed = ItemClusterRenderState.getSeedForItemStack(itemStack);
					VaultClientData vaultClientData = vaultBlockEntity.getClientData();
					poseStack.pushPose();
					poseStack.translate(0.5F, 0.4F, 0.5F);
					poseStack.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(f, vaultClientData.previousSpin(), vaultClientData.currentSpin())));
					ItemEntityRenderer.renderMultipleFromCount(poseStack, multiBufferSource, i, this.renderState, this.random);
					poseStack.popPose();
				}
			}
		}
	}
}
