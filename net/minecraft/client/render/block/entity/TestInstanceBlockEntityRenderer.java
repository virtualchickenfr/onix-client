package net.minecraft.client.render.block.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.TestInstanceBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class TestInstanceBlockEntityRenderer implements BlockEntityRenderer<TestInstanceBlockEntity> {
	private final BeaconBlockEntityRenderer<TestInstanceBlockEntity> beaconBlockEntityRenderer;
	private final StructureBlockBlockEntityRenderer<TestInstanceBlockEntity> structureBlockBlockEntityRenderer;

	public TestInstanceBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.beaconBlockEntityRenderer = new BeaconBlockEntityRenderer<>(context);
		this.structureBlockBlockEntityRenderer = new StructureBlockBlockEntityRenderer<>(context);
	}

	public void render(
		TestInstanceBlockEntity testInstanceBlockEntity, float f, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, int j, Vec3d vec3d
	) {
		this.beaconBlockEntityRenderer.render(testInstanceBlockEntity, f, matrixStack, vertexConsumerProvider, i, j, vec3d);
		this.structureBlockBlockEntityRenderer.render(testInstanceBlockEntity, f, matrixStack, vertexConsumerProvider, i, j, vec3d);
	}

	public boolean rendersOutsideBoundingBox(TestInstanceBlockEntity testInstanceBlockEntity) {
		return this.beaconBlockEntityRenderer.rendersOutsideBoundingBox(testInstanceBlockEntity)
			|| this.structureBlockBlockEntityRenderer.rendersOutsideBoundingBox(testInstanceBlockEntity);
	}

	@Override
	public int getRenderDistance() {
		return Math.max(this.beaconBlockEntityRenderer.getRenderDistance(), this.structureBlockBlockEntityRenderer.getRenderDistance());
	}

	public boolean isInRenderDistance(TestInstanceBlockEntity testInstanceBlockEntity, Vec3d vec3d) {
		return this.beaconBlockEntityRenderer.isInRenderDistance(testInstanceBlockEntity, vec3d)
			|| this.structureBlockBlockEntityRenderer.isInRenderDistance(testInstanceBlockEntity, vec3d);
	}
}
