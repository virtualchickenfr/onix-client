package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class BeaconRenderer<T extends BlockEntity & BeaconBeamOwner> implements BlockEntityRenderer<T> {
	public static final ResourceLocation BEAM_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
	public static final int MAX_RENDER_Y = 2048;
	private static final float BEAM_SCALE_THRESHOLD = 96.0F;
	public static final float SOLID_BEAM_RADIUS = 0.2F;
	public static final float BEAM_GLOW_RADIUS = 0.25F;

	public BeaconRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(T blockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, Vec3 vec3) {
		long l = blockEntity.getLevel().getGameTime();
		float g = (float)vec3.subtract(blockEntity.getBlockPos().getCenter()).horizontalDistance();
		LocalPlayer localPlayer = Minecraft.getInstance().player;
		float h = localPlayer != null && localPlayer.isScoping() ? 1.0F : Math.max(1.0F, g / 96.0F);
		List<BeaconBeamOwner.Section> list = blockEntity.getBeamSections();
		int k = 0;

		for (int m = 0; m < list.size(); m++) {
			BeaconBeamOwner.Section section = list.get(m);
			renderBeaconBeam(poseStack, multiBufferSource, f, h, l, k, m == list.size() - 1 ? 2048 : section.getHeight(), section.getColor());
			k += section.getHeight();
		}
	}

	private static void renderBeaconBeam(PoseStack poseStack, MultiBufferSource multiBufferSource, float f, float g, long l, int i, int j, int k) {
		renderBeaconBeam(poseStack, multiBufferSource, BEAM_LOCATION, f, 1.0F, l, i, j, k, 0.2F * g, 0.25F * g);
	}

	public static void renderBeaconBeam(
		PoseStack poseStack, MultiBufferSource multiBufferSource, ResourceLocation resourceLocation, float f, float g, long l, int i, int j, int k, float h, float m
	) {
		int n = i + j;
		poseStack.pushPose();
		poseStack.translate(0.5, 0.0, 0.5);
		float o = Math.floorMod(l, 40) + f;
		float p = j < 0 ? o : -o;
		float q = Mth.frac(p * 0.2F - Mth.floor(p * 0.1F));
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(o * 2.25F - 45.0F));
		float r = 0.0F;
		float s = h;
		float t = h;
		float u = 0.0F;
		float v = -h;
		float w = 0.0F;
		float x = 0.0F;
		float y = -h;
		float z = 0.0F;
		float aa = 1.0F;
		float ab = -1.0F + q;
		float ac = j * g * (0.5F / h) + ab;
		renderPart(
			poseStack, multiBufferSource.getBuffer(RenderType.beaconBeam(resourceLocation, false)), k, i, n, 0.0F, s, t, 0.0F, v, 0.0F, 0.0F, y, 0.0F, 1.0F, ac, ab
		);
		poseStack.popPose();
		r = -m;
		s = -m;
		t = m;
		u = -m;
		v = -m;
		w = m;
		x = m;
		y = m;
		z = 0.0F;
		aa = 1.0F;
		ab = -1.0F + q;
		ac = j * g + ab;
		renderPart(
			poseStack, multiBufferSource.getBuffer(RenderType.beaconBeam(resourceLocation, true)), ARGB.color(32, k), i, n, r, s, t, u, v, w, x, y, 0.0F, 1.0F, ac, ab
		);
		poseStack.popPose();
	}

	private static void renderPart(
		PoseStack poseStack,
		VertexConsumer vertexConsumer,
		int i,
		int j,
		int k,
		float f,
		float g,
		float h,
		float l,
		float m,
		float n,
		float o,
		float p,
		float q,
		float r,
		float s,
		float t
	) {
		PoseStack.Pose pose = poseStack.last();
		renderQuad(pose, vertexConsumer, i, j, k, f, g, h, l, q, r, s, t);
		renderQuad(pose, vertexConsumer, i, j, k, o, p, m, n, q, r, s, t);
		renderQuad(pose, vertexConsumer, i, j, k, h, l, o, p, q, r, s, t);
		renderQuad(pose, vertexConsumer, i, j, k, m, n, f, g, q, r, s, t);
	}

	private static void renderQuad(
		PoseStack.Pose pose, VertexConsumer vertexConsumer, int i, int j, int k, float f, float g, float h, float l, float m, float n, float o, float p
	) {
		addVertex(pose, vertexConsumer, i, k, f, g, n, o);
		addVertex(pose, vertexConsumer, i, j, f, g, n, p);
		addVertex(pose, vertexConsumer, i, j, h, l, m, p);
		addVertex(pose, vertexConsumer, i, k, h, l, m, o);
	}

	private static void addVertex(PoseStack.Pose pose, VertexConsumer vertexConsumer, int i, int j, float f, float g, float h, float k) {
		vertexConsumer.addVertex(pose, f, j, g).setColor(i).setUv(h, k).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
	}

	@Override
	public boolean shouldRender(T blockEntity, Vec3 vec3) {
		return Vec3.atCenterOf(blockEntity.getBlockPos()).multiply(1.0, 0.0, 1.0).closerThan(vec3.multiply(1.0, 0.0, 1.0), this.getViewDistance());
	}
}
