package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.dragon.EnderDragonModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.entity.state.HitboxRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class EnderDragonRenderer extends EntityRenderer<EnderDragon, EnderDragonRenderState> {
	public static final ResourceLocation CRYSTAL_BEAM_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/end_crystal/end_crystal_beam.png");
	private static final ResourceLocation DRAGON_EXPLODING_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon_exploding.png");
	private static final ResourceLocation DRAGON_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
	private static final ResourceLocation DRAGON_EYES_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon_eyes.png");
	private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(DRAGON_LOCATION);
	private static final RenderType DECAL = RenderType.entityDecal(DRAGON_LOCATION);
	private static final RenderType EYES = RenderType.eyes(DRAGON_EYES_LOCATION);
	private static final RenderType BEAM = RenderType.entitySmoothCutout(CRYSTAL_BEAM_LOCATION);
	private static final float HALF_SQRT_3 = (float)(Math.sqrt(3.0) / 2.0);
	private final EnderDragonModel model;

	public EnderDragonRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5F;
		this.model = new EnderDragonModel(context.bakeLayer(ModelLayers.ENDER_DRAGON));
	}

	public void render(EnderDragonRenderState enderDragonRenderState, PoseStack poseStack, MultiBufferSource multiBufferSource, int i) {
		poseStack.pushPose();
		float f = enderDragonRenderState.getHistoricalPos(7).yRot();
		float g = (float)(enderDragonRenderState.getHistoricalPos(5).y() - enderDragonRenderState.getHistoricalPos(10).y());
		poseStack.mulPose(Axis.YP.rotationDegrees(-f));
		poseStack.mulPose(Axis.XP.rotationDegrees(g * 10.0F));
		poseStack.translate(0.0F, 0.0F, 1.0F);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		this.model.setupAnim(enderDragonRenderState);
		if (enderDragonRenderState.deathTime > 0.0F) {
			float h = enderDragonRenderState.deathTime / 200.0F;
			int j = ARGB.color(Mth.floor(h * 255.0F), -1);
			VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.dragonExplosionAlpha(DRAGON_EXPLODING_LOCATION));
			this.model.renderToBuffer(poseStack, vertexConsumer, i, OverlayTexture.NO_OVERLAY, j);
			VertexConsumer vertexConsumer2 = multiBufferSource.getBuffer(DECAL);
			this.model.renderToBuffer(poseStack, vertexConsumer2, i, OverlayTexture.pack(0.0F, enderDragonRenderState.hasRedOverlay));
		} else {
			VertexConsumer vertexConsumer3 = multiBufferSource.getBuffer(RENDER_TYPE);
			this.model.renderToBuffer(poseStack, vertexConsumer3, i, OverlayTexture.pack(0.0F, enderDragonRenderState.hasRedOverlay));
		}

		VertexConsumer vertexConsumer3 = multiBufferSource.getBuffer(EYES);
		this.model.renderToBuffer(poseStack, vertexConsumer3, i, OverlayTexture.NO_OVERLAY);
		if (enderDragonRenderState.deathTime > 0.0F) {
			float k = enderDragonRenderState.deathTime / 200.0F;
			poseStack.pushPose();
			poseStack.translate(0.0F, -1.0F, -2.0F);
			renderRays(poseStack, k, multiBufferSource.getBuffer(RenderType.dragonRays()));
			renderRays(poseStack, k, multiBufferSource.getBuffer(RenderType.dragonRaysDepth()));
			poseStack.popPose();
		}

		poseStack.popPose();
		if (enderDragonRenderState.beamOffset != null) {
			renderCrystalBeams(
				(float)enderDragonRenderState.beamOffset.x,
				(float)enderDragonRenderState.beamOffset.y,
				(float)enderDragonRenderState.beamOffset.z,
				enderDragonRenderState.ageInTicks,
				poseStack,
				multiBufferSource,
				i
			);
		}

		super.render(enderDragonRenderState, poseStack, multiBufferSource, i);
	}

	private static void renderRays(PoseStack poseStack, float f, VertexConsumer vertexConsumer) {
		poseStack.pushPose();
		float g = Math.min(f > 0.8F ? (f - 0.8F) / 0.2F : 0.0F, 1.0F);
		int i = ARGB.colorFromFloat(1.0F - g, 1.0F, 1.0F, 1.0F);
		int j = 16711935;
		RandomSource randomSource = RandomSource.create(432L);
		Vector3f vector3f = new Vector3f();
		Vector3f vector3f2 = new Vector3f();
		Vector3f vector3f3 = new Vector3f();
		Vector3f vector3f4 = new Vector3f();
		Quaternionf quaternionf = new Quaternionf();
		int k = Mth.floor((f + f * f) / 2.0F * 60.0F);

		for (int l = 0; l < k; l++) {
			quaternionf.rotationXYZ(
					randomSource.nextFloat() * (float) (Math.PI * 2), randomSource.nextFloat() * (float) (Math.PI * 2), randomSource.nextFloat() * (float) (Math.PI * 2)
				)
				.rotateXYZ(
					randomSource.nextFloat() * (float) (Math.PI * 2),
					randomSource.nextFloat() * (float) (Math.PI * 2),
					randomSource.nextFloat() * (float) (Math.PI * 2) + f * (float) (Math.PI / 2)
				);
			poseStack.mulPose(quaternionf);
			float h = randomSource.nextFloat() * 20.0F + 5.0F + g * 10.0F;
			float m = randomSource.nextFloat() * 2.0F + 1.0F + g * 2.0F;
			vector3f2.set(-HALF_SQRT_3 * m, h, -0.5F * m);
			vector3f3.set(HALF_SQRT_3 * m, h, -0.5F * m);
			vector3f4.set(0.0F, h, m);
			PoseStack.Pose pose = poseStack.last();
			vertexConsumer.addVertex(pose, vector3f).setColor(i);
			vertexConsumer.addVertex(pose, vector3f2).setColor(16711935);
			vertexConsumer.addVertex(pose, vector3f3).setColor(16711935);
			vertexConsumer.addVertex(pose, vector3f).setColor(i);
			vertexConsumer.addVertex(pose, vector3f3).setColor(16711935);
			vertexConsumer.addVertex(pose, vector3f4).setColor(16711935);
			vertexConsumer.addVertex(pose, vector3f).setColor(i);
			vertexConsumer.addVertex(pose, vector3f4).setColor(16711935);
			vertexConsumer.addVertex(pose, vector3f2).setColor(16711935);
		}

		poseStack.popPose();
	}

	public static void renderCrystalBeams(float f, float g, float h, float i, PoseStack poseStack, MultiBufferSource multiBufferSource, int j) {
		float k = Mth.sqrt(f * f + h * h);
		float l = Mth.sqrt(f * f + g * g + h * h);
		poseStack.pushPose();
		poseStack.translate(0.0F, 2.0F, 0.0F);
		poseStack.mulPose(Axis.YP.rotation((float)(-Math.atan2(h, f)) - (float) (Math.PI / 2)));
		poseStack.mulPose(Axis.XP.rotation((float)(-Math.atan2(k, g)) - (float) (Math.PI / 2)));
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(BEAM);
		float m = 0.0F - i * 0.01F;
		float n = l / 32.0F - i * 0.01F;
		int o = 8;
		float p = 0.0F;
		float q = 0.75F;
		float r = 0.0F;
		PoseStack.Pose pose = poseStack.last();

		for (int s = 1; s <= 8; s++) {
			float t = Mth.sin(s * (float) (Math.PI * 2) / 8.0F) * 0.75F;
			float u = Mth.cos(s * (float) (Math.PI * 2) / 8.0F) * 0.75F;
			float v = s / 8.0F;
			vertexConsumer.addVertex(pose, p * 0.2F, q * 0.2F, 0.0F)
				.setColor(-16777216)
				.setUv(r, m)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(j)
				.setNormal(pose, 0.0F, -1.0F, 0.0F);
			vertexConsumer.addVertex(pose, p, q, l).setColor(-1).setUv(r, n).setOverlay(OverlayTexture.NO_OVERLAY).setLight(j).setNormal(pose, 0.0F, -1.0F, 0.0F);
			vertexConsumer.addVertex(pose, t, u, l).setColor(-1).setUv(v, n).setOverlay(OverlayTexture.NO_OVERLAY).setLight(j).setNormal(pose, 0.0F, -1.0F, 0.0F);
			vertexConsumer.addVertex(pose, t * 0.2F, u * 0.2F, 0.0F)
				.setColor(-16777216)
				.setUv(v, m)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(j)
				.setNormal(pose, 0.0F, -1.0F, 0.0F);
			p = t;
			q = u;
			r = v;
		}

		poseStack.popPose();
	}

	public EnderDragonRenderState createRenderState() {
		return new EnderDragonRenderState();
	}

	public void extractRenderState(EnderDragon enderDragon, EnderDragonRenderState enderDragonRenderState, float f) {
		super.extractRenderState(enderDragon, enderDragonRenderState, f);
		enderDragonRenderState.flapTime = Mth.lerp(f, enderDragon.oFlapTime, enderDragon.flapTime);
		enderDragonRenderState.deathTime = enderDragon.dragonDeathTime > 0 ? enderDragon.dragonDeathTime + f : 0.0F;
		enderDragonRenderState.hasRedOverlay = enderDragon.hurtTime > 0;
		EndCrystal endCrystal = enderDragon.nearestCrystal;
		if (endCrystal != null) {
			Vec3 vec3 = endCrystal.getPosition(f).add(0.0, EndCrystalRenderer.getY(endCrystal.time + f), 0.0);
			enderDragonRenderState.beamOffset = vec3.subtract(enderDragon.getPosition(f));
		} else {
			enderDragonRenderState.beamOffset = null;
		}

		DragonPhaseInstance dragonPhaseInstance = enderDragon.getPhaseManager().getCurrentPhase();
		enderDragonRenderState.isLandingOrTakingOff = dragonPhaseInstance == EnderDragonPhase.LANDING || dragonPhaseInstance == EnderDragonPhase.TAKEOFF;
		enderDragonRenderState.isSitting = dragonPhaseInstance.isSitting();
		BlockPos blockPos = enderDragon.level()
			.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.getLocation(enderDragon.getFightOrigin()));
		enderDragonRenderState.distanceToEgg = blockPos.distToCenterSqr(enderDragon.position());
		enderDragonRenderState.partialTicks = enderDragon.isDeadOrDying() ? 0.0F : f;
		enderDragonRenderState.flightHistory.copyFrom(enderDragon.flightHistory);
	}

	protected void extractAdditionalHitboxes(EnderDragon enderDragon, Builder<HitboxRenderState> builder, float f) {
		super.extractAdditionalHitboxes(enderDragon, builder, f);
		double d = -Mth.lerp(f, enderDragon.xOld, enderDragon.getX());
		double e = -Mth.lerp(f, enderDragon.yOld, enderDragon.getY());
		double g = -Mth.lerp(f, enderDragon.zOld, enderDragon.getZ());

		for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
			AABB aABB = enderDragonPart.getBoundingBox();
			HitboxRenderState hitboxRenderState = new HitboxRenderState(
				aABB.minX - enderDragonPart.getX(),
				aABB.minY - enderDragonPart.getY(),
				aABB.minZ - enderDragonPart.getZ(),
				aABB.maxX - enderDragonPart.getX(),
				aABB.maxY - enderDragonPart.getY(),
				aABB.maxZ - enderDragonPart.getZ(),
				(float)(d + Mth.lerp(f, enderDragonPart.xOld, enderDragonPart.getX())),
				(float)(e + Mth.lerp(f, enderDragonPart.yOld, enderDragonPart.getY())),
				(float)(g + Mth.lerp(f, enderDragonPart.zOld, enderDragonPart.getZ())),
				0.25F,
				1.0F,
				0.0F
			);
			builder.add(hitboxRenderState);
		}
	}

	protected boolean affectedByCulling(EnderDragon enderDragon) {
		return false;
	}
}
