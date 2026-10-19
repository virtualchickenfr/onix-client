package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerStateData;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class TrialSpawnerRenderer implements BlockEntityRenderer<TrialSpawnerBlockEntity> {
	private final EntityRenderDispatcher entityRenderer;

	public TrialSpawnerRenderer(BlockEntityRendererProvider.Context context) {
		this.entityRenderer = context.getEntityRenderer();
	}

	public void render(TrialSpawnerBlockEntity trialSpawnerBlockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, Vec3 vec3) {
		Level level = trialSpawnerBlockEntity.getLevel();
		if (level != null) {
			TrialSpawner trialSpawner = trialSpawnerBlockEntity.getTrialSpawner();
			TrialSpawnerStateData trialSpawnerStateData = trialSpawner.getStateData();
			Entity entity = trialSpawnerStateData.getOrCreateDisplayEntity(trialSpawner, level, trialSpawner.getState());
			if (entity != null) {
				SpawnerRenderer.renderEntityInSpawner(
					f, poseStack, multiBufferSource, i, entity, this.entityRenderer, trialSpawnerStateData.getOSpin(), trialSpawnerStateData.getSpin()
				);
			}
		}
	}
}
