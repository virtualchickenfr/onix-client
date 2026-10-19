package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HitboxRenderState;
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.NewMinecartBehavior;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public abstract class EntityRenderer<T extends Entity, S extends EntityRenderState> {
	protected static final float NAMETAG_SCALE = 0.025F;
	public static final int LEASH_RENDER_STEPS = 24;
	public static final float LEASH_WIDTH = 0.05F;
	protected final EntityRenderDispatcher entityRenderDispatcher;
	private final Font font;
	protected float shadowRadius;
	protected float shadowStrength = 1.0F;
	private final S reusedState = this.createRenderState();

	protected EntityRenderer(EntityRendererProvider.Context context) {
		this.entityRenderDispatcher = context.getEntityRenderDispatcher();
		this.font = context.getFont();
	}

	public final int getPackedLightCoords(T entity, float f) {
		BlockPos blockPos = BlockPos.containing(entity.getLightProbePosition(f));
		return LightTexture.pack(this.getBlockLightLevel(entity, blockPos), this.getSkyLightLevel(entity, blockPos));
	}

	protected int getSkyLightLevel(T entity, BlockPos blockPos) {
		return entity.level().getBrightness(LightLayer.SKY, blockPos);
	}

	protected int getBlockLightLevel(T entity, BlockPos blockPos) {
		return entity.isOnFire() ? 15 : entity.level().getBrightness(LightLayer.BLOCK, blockPos);
	}

	public boolean shouldRender(T entity, Frustum frustum, double d, double e, double f) {
		if (!entity.shouldRender(d, e, f)) {
			return false;
		}

		if (!this.affectedByCulling(entity)) {
			return true;
		}

		AABB aABB = this.getBoundingBoxForCulling(entity).inflate(0.5);
		if (aABB.hasNaN() || aABB.getSize() == 0.0) {
			aABB = new AABB(entity.getX() - 2.0, entity.getY() - 2.0, entity.getZ() - 2.0, entity.getX() + 2.0, entity.getY() + 2.0, entity.getZ() + 2.0);
		}

		if (frustum.isVisible(aABB)) {
			return true;
		}

		if (entity instanceof Leashable leashable) {
			Entity entity2 = leashable.getLeashHolder();
			if (entity2 != null) {
				AABB aABB2 = this.entityRenderDispatcher.getRenderer(entity2).getBoundingBoxForCulling(entity2);
				return frustum.isVisible(aABB2) || frustum.isVisible(aABB.minmax(aABB2));
			}
		}

		return false;
	}

	protected AABB getBoundingBoxForCulling(T entity) {
		return entity.getBoundingBox();
	}

	protected boolean affectedByCulling(T entity) {
		return true;
	}

	public Vec3 getRenderOffset(S entityRenderState) {
		return entityRenderState.passengerOffset != null ? entityRenderState.passengerOffset : Vec3.ZERO;
	}

	public void render(S entityRenderState, PoseStack poseStack, MultiBufferSource multiBufferSource, int i) {
		if (entityRenderState.leashStates != null) {
			for (EntityRenderState.LeashState leashState : entityRenderState.leashStates) {
				renderLeash(poseStack, multiBufferSource, leashState);
			}
		}

		if (entityRenderState.nameTag != null) {
			this.renderNameTag(entityRenderState, entityRenderState.nameTag, poseStack, multiBufferSource, i);
		}
	}

	private static void renderLeash(PoseStack poseStack, MultiBufferSource multiBufferSource, EntityRenderState.LeashState leashState) {
		float f = (float)(leashState.end.x - leashState.start.x);
		float g = (float)(leashState.end.y - leashState.start.y);
		float h = (float)(leashState.end.z - leashState.start.z);
		float i = Mth.invSqrt(f * f + h * h) * 0.05F / 2.0F;
		float j = h * i;
		float k = f * i;
		poseStack.pushPose();
		poseStack.translate(leashState.offset);
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.leash());
		Matrix4f matrix4f = poseStack.last().pose();

		for (int l = 0; l <= 24; l++) {
			addVertexPair(vertexConsumer, matrix4f, f, g, h, 0.05F, 0.05F, j, k, l, false, leashState);
		}

		for (int l = 24; l >= 0; l--) {
			addVertexPair(vertexConsumer, matrix4f, f, g, h, 0.05F, 0.0F, j, k, l, true, leashState);
		}

		poseStack.popPose();
	}

	private static void addVertexPair(
		VertexConsumer vertexConsumer,
		Matrix4f matrix4f,
		float f,
		float g,
		float h,
		float i,
		float j,
		float k,
		float l,
		int m,
		boolean bl,
		EntityRenderState.LeashState leashState
	) {
		float n = m / 24.0F;
		int o = (int)Mth.lerp(n, leashState.startBlockLight, leashState.endBlockLight);
		int p = (int)Mth.lerp(n, leashState.startSkyLight, leashState.endSkyLight);
		int q = LightTexture.pack(o, p);
		float r = m % 2 == (bl ? 1 : 0) ? 0.7F : 1.0F;
		float s = 0.5F * r;
		float t = 0.4F * r;
		float u = 0.3F * r;
		float v = f * n;
		float w;
		if (leashState.slack) {
			w = g > 0.0F ? g * n * n : g - g * (1.0F - n) * (1.0F - n);
		} else {
			w = g * n;
		}

		float x = h * n;
		vertexConsumer.addVertex(matrix4f, v - k, w + j, x + l).setColor(s, t, u, 1.0F).setLight(q);
		vertexConsumer.addVertex(matrix4f, v + k, w + i - j, x - l).setColor(s, t, u, 1.0F).setLight(q);
	}

	protected boolean shouldShowName(T entity, double d) {
		return entity.shouldShowName() || entity.hasCustomName() && entity == this.entityRenderDispatcher.crosshairPickEntity;
	}

	public Font getFont() {
		return this.font;
	}

	protected void renderNameTag(S entityRenderState, Component component, PoseStack poseStack, MultiBufferSource multiBufferSource, int i) {
		Vec3 vec3 = entityRenderState.nameTagAttachment;
		if (vec3 != null) {
			boolean bl = !entityRenderState.isDiscrete;
			int j = "deadmau5".equals(component.getString()) ? -10 : 0;
			poseStack.pushPose();
			poseStack.translate(vec3.x, vec3.y + 0.5, vec3.z);
			poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
			poseStack.scale(0.025F, -0.025F, 0.025F);
			Matrix4f matrix4f = poseStack.last().pose();
			Font font = this.getFont();
			float f = -font.width(component) / 2.0F;
			int k = (int)(Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
			font.drawInBatch(component, f, j, -2130706433, false, matrix4f, multiBufferSource, bl ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, k, i);
			if (bl) {
				font.drawInBatch(component, f, j, -1, false, matrix4f, multiBufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.lightCoordsWithEmission(i, 2));
			}

			poseStack.popPose();
		}
	}

	@Nullable
	protected Component getNameTag(T entity) {
		return entity.getDisplayName();
	}

	protected float getShadowRadius(S entityRenderState) {
		return this.shadowRadius;
	}

	protected float getShadowStrength(S entityRenderState) {
		return this.shadowStrength;
	}

	public abstract S createRenderState();

	public final S createRenderState(T entity, float f) {
		S entityRenderState = this.reusedState;
		this.extractRenderState(entity, entityRenderState, f);
		return entityRenderState;
	}

	public void extractRenderState(T entity, S entityRenderState, float f) {
		entityRenderState.entityType = entity.getType();
		entityRenderState.x = Mth.lerp(f, entity.xOld, entity.getX());
		entityRenderState.y = Mth.lerp(f, entity.yOld, entity.getY());
		entityRenderState.z = Mth.lerp(f, entity.zOld, entity.getZ());
		entityRenderState.isInvisible = entity.isInvisible();
		entityRenderState.ageInTicks = entity.tickCount + f;
		entityRenderState.boundingBoxWidth = entity.getBbWidth();
		entityRenderState.boundingBoxHeight = entity.getBbHeight();
		entityRenderState.eyeHeight = entity.getEyeHeight();
		if (entity.isPassenger()
			&& entity.getVehicle() instanceof AbstractMinecart abstractMinecart
			&& abstractMinecart.getBehavior() instanceof NewMinecartBehavior newMinecartBehavior
			&& newMinecartBehavior.cartHasPosRotLerp()) {
			double d = Mth.lerp(f, abstractMinecart.xOld, abstractMinecart.getX());
			double e = Mth.lerp(f, abstractMinecart.yOld, abstractMinecart.getY());
			double g = Mth.lerp(f, abstractMinecart.zOld, abstractMinecart.getZ());
			entityRenderState.passengerOffset = newMinecartBehavior.getCartLerpPosition(f).subtract(new Vec3(d, e, g));
		} else {
			entityRenderState.passengerOffset = null;
		}

		entityRenderState.distanceToCameraSq = this.entityRenderDispatcher.distanceToSqr(entity);
		boolean bl = entityRenderState.distanceToCameraSq < 4096.0 && this.shouldShowName(entity, entityRenderState.distanceToCameraSq);
		if (bl) {
			entityRenderState.nameTag = this.getNameTag(entity);
			entityRenderState.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(f));
		} else {
			entityRenderState.nameTag = null;
		}

		entityRenderState.isDiscrete = entity.isDiscrete();
		if (entity instanceof Leashable leashable && leashable.getLeashHolder() instanceof Entity entity2) {
			float h = entity.getPreciseBodyRotation(f) * (float) (Math.PI / 180.0);
			Vec3 vec3 = leashable.getLeashOffset(f);
			BlockPos blockPos = BlockPos.containing(entity.getEyePosition(f));
			BlockPos blockPos2 = BlockPos.containing(entity2.getEyePosition(f));
			int i = this.getBlockLightLevel(entity, blockPos);
			int j = this.entityRenderDispatcher.getRenderer(entity2).getBlockLightLevel(entity2, blockPos2);
			int k = entity.level().getBrightness(LightLayer.SKY, blockPos);
			int l = entity.level().getBrightness(LightLayer.SKY, blockPos2);
			boolean bl2 = entity2.supportQuadLeashAsHolder() && leashable.supportQuadLeash();
			int m = bl2 ? 4 : 1;
			if (entityRenderState.leashStates == null || entityRenderState.leashStates.size() != m) {
				entityRenderState.leashStates = new ArrayList<>(m);

				for (int n = 0; n < m; n++) {
					entityRenderState.leashStates.add(new EntityRenderState.LeashState());
				}
			}

			if (bl2) {
				float o = entity2.getPreciseBodyRotation(f) * (float) (Math.PI / 180.0);
				Vec3 vec32 = entity2.getPosition(f);
				Vec3[] vec3s = leashable.getQuadLeashOffsets();
				Vec3[] vec3s2 = entity2.getQuadLeashHolderOffsets();

				for (int p = 0; p < m; p++) {
					EntityRenderState.LeashState leashState = entityRenderState.leashStates.get(p);
					leashState.offset = vec3s[p].yRot(-h);
					leashState.start = entity.getPosition(f).add(leashState.offset);
					leashState.end = vec32.add(vec3s2[p].yRot(-o));
					leashState.startBlockLight = i;
					leashState.endBlockLight = j;
					leashState.startSkyLight = k;
					leashState.endSkyLight = l;
					leashState.slack = false;
				}
			} else {
				Vec3 vec33 = vec3.yRot(-h);
				EntityRenderState.LeashState leashState2 = entityRenderState.leashStates.getFirst();
				leashState2.offset = vec33;
				leashState2.start = entity.getPosition(f).add(vec33);
				leashState2.end = entity2.getRopeHoldPosition(f);
				leashState2.startBlockLight = i;
				leashState2.endBlockLight = j;
				leashState2.startSkyLight = k;
				leashState2.endSkyLight = l;
			}
		} else {
			entityRenderState.leashStates = null;
		}

		entityRenderState.displayFireAnimation = entity.displayFireAnimation();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes() && !entityRenderState.isInvisible && !minecraft.showOnlyReducedInfo()) {
			this.extractHitboxes(entity, entityRenderState, f);
		} else {
			entityRenderState.hitboxesRenderState = null;
			entityRenderState.serverHitboxesRenderState = null;
		}
	}

	private void extractHitboxes(T entity, S entityRenderState, float f) {
		entityRenderState.hitboxesRenderState = this.extractHitboxes(entity, f, false);
		entityRenderState.serverHitboxesRenderState = null;
	}

	private HitboxesRenderState extractHitboxes(T entity, float f, boolean bl) {
		Builder<HitboxRenderState> builder = new Builder<>();
		AABB aABB = entity.getBoundingBox();
		HitboxRenderState hitboxRenderState;
		if (bl) {
			hitboxRenderState = new HitboxRenderState(
				aABB.minX - entity.getX(),
				aABB.minY - entity.getY(),
				aABB.minZ - entity.getZ(),
				aABB.maxX - entity.getX(),
				aABB.maxY - entity.getY(),
				aABB.maxZ - entity.getZ(),
				0.0F,
				1.0F,
				0.0F
			);
		} else {
			hitboxRenderState = new HitboxRenderState(
				aABB.minX - entity.getX(),
				aABB.minY - entity.getY(),
				aABB.minZ - entity.getZ(),
				aABB.maxX - entity.getX(),
				aABB.maxY - entity.getY(),
				aABB.maxZ - entity.getZ(),
				1.0F,
				1.0F,
				1.0F
			);
		}

		builder.add(hitboxRenderState);
		Entity entity2 = entity.getVehicle();
		if (entity2 != null) {
			float g = Math.min(entity2.getBbWidth(), entity.getBbWidth()) / 2.0F;
			float h = 0.0625F;
			Vec3 vec3 = entity2.getPassengerRidingPosition(entity).subtract(entity.position());
			HitboxRenderState hitboxRenderState2 = new HitboxRenderState(vec3.x - g, vec3.y, vec3.z - g, vec3.x + g, vec3.y + 0.0625, vec3.z + g, 1.0F, 1.0F, 0.0F);
			builder.add(hitboxRenderState2);
		}

		this.extractAdditionalHitboxes(entity, builder, f);
		Vec3 vec32 = entity.getViewVector(f);
		return new HitboxesRenderState(vec32.x, vec32.y, vec32.z, builder.build());
	}

	protected void extractAdditionalHitboxes(T entity, Builder<HitboxRenderState> builder, float f) {
	}

	@Nullable
	private static Entity getServerSideEntity(Entity entity) {
		IntegratedServer integratedServer = Minecraft.getInstance().getSingleplayerServer();
		if (integratedServer != null) {
			ServerLevel serverLevel = integratedServer.getLevel(entity.level().dimension());
			if (serverLevel != null) {
				return serverLevel.getEntity(entity.getId());
			}
		}

		return null;
	}
}
