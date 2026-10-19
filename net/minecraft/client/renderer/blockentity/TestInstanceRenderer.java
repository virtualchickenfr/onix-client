package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class TestInstanceRenderer implements BlockEntityRenderer<TestInstanceBlockEntity> {
	private final BeaconRenderer<TestInstanceBlockEntity> beacon;
	private final BlockEntityWithBoundingBoxRenderer<TestInstanceBlockEntity> box;

	public TestInstanceRenderer(BlockEntityRendererProvider.Context context) {
		this.beacon = new BeaconRenderer<>(context);
		this.box = new BlockEntityWithBoundingBoxRenderer<>(context);
	}

	public void render(TestInstanceBlockEntity testInstanceBlockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, Vec3 vec3) {
		this.beacon.render(testInstanceBlockEntity, f, poseStack, multiBufferSource, i, j, vec3);
		this.box.render(testInstanceBlockEntity, f, poseStack, multiBufferSource, i, j, vec3);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return this.beacon.shouldRenderOffScreen() || this.box.shouldRenderOffScreen();
	}

	@Override
	public int getViewDistance() {
		return Math.max(this.beacon.getViewDistance(), this.box.getViewDistance());
	}

	public boolean shouldRender(TestInstanceBlockEntity testInstanceBlockEntity, Vec3 vec3) {
		return this.beacon.shouldRender(testInstanceBlockEntity, vec3) || this.box.shouldRender(testInstanceBlockEntity, vec3);
	}
}
