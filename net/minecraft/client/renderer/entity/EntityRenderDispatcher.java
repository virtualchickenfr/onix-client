package net.minecraft.client.renderer.entity;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HitboxRenderState;
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.entity.state.ServerHitboxesRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class EntityRenderDispatcher implements ResourceManagerReloadListener {
	private static final RenderType SHADOW_RENDER_TYPE = RenderType.entityShadow(ResourceLocation.withDefaultNamespace("textures/misc/shadow.png"));
	private static final float MAX_SHADOW_RADIUS = 32.0F;
	private static final float SHADOW_POWER_FALLOFF_Y = 0.5F;
	private Map<EntityType<?>, EntityRenderer<?, ?>> renderers = ImmutableMap.of();
	private Map<PlayerSkin.Model, EntityRenderer<? extends Player, ?>> playerRenderers = Map.of();
	public final TextureManager textureManager;
	private Level level;
	public Camera camera;
	private Quaternionf cameraOrientation;
	public Entity crosshairPickEntity;
	private final ItemModelResolver itemModelResolver;
	private final MapRenderer mapRenderer;
	private final BlockRenderDispatcher blockRenderDispatcher;
	private final ItemInHandRenderer itemInHandRenderer;
	private final Font font;
	public final Options options;
	private final Supplier<EntityModelSet> entityModels;
	private final EquipmentAssetManager equipmentAssets;
	private boolean shouldRenderShadow = true;
	private boolean renderHitBoxes;

	public <E extends Entity> int getPackedLightCoords(E entity, float f) {
		return this.getRenderer(entity).getPackedLightCoords(entity, f);
	}

	public EntityRenderDispatcher(
		Minecraft minecraft,
		TextureManager textureManager,
		ItemModelResolver itemModelResolver,
		ItemRenderer itemRenderer,
		MapRenderer mapRenderer,
		BlockRenderDispatcher blockRenderDispatcher,
		Font font,
		Options options,
		Supplier<EntityModelSet> supplier,
		EquipmentAssetManager equipmentAssetManager
	) {
		this.textureManager = textureManager;
		this.itemModelResolver = itemModelResolver;
		this.mapRenderer = mapRenderer;
		this.itemInHandRenderer = new ItemInHandRenderer(minecraft, this, itemRenderer, itemModelResolver);
		this.blockRenderDispatcher = blockRenderDispatcher;
		this.font = font;
		this.options = options;
		this.entityModels = supplier;
		this.equipmentAssets = equipmentAssetManager;
	}

	public <T extends Entity> EntityRenderer<? super T, ?> getRenderer(T entity) {
		if (entity instanceof AbstractClientPlayer abstractClientPlayer) {
			PlayerSkin.Model model = abstractClientPlayer.getSkin().model();
			EntityRenderer<? extends Player, ?> entityRenderer = this.playerRenderers.get(model);
			return (EntityRenderer<? super T, ?>)(entityRenderer != null ? entityRenderer : this.playerRenderers.get(PlayerSkin.Model.WIDE));
		} else {
			return (EntityRenderer<? super T, ?>)this.renderers.get(entity.getType());
		}
	}

	public <S extends EntityRenderState> EntityRenderer<?, ? super S> getRenderer(S entityRenderState) {
		if (entityRenderState instanceof PlayerRenderState playerRenderState) {
			PlayerSkin.Model model = playerRenderState.skin.model();
			EntityRenderer<? extends Player, ?> entityRenderer = this.playerRenderers.get(model);
			return (EntityRenderer<?, ? super S>)(entityRenderer != null ? entityRenderer : (EntityRenderer)this.playerRenderers.get(PlayerSkin.Model.WIDE));
		} else {
			return (EntityRenderer<?, ? super S>)this.renderers.get(entityRenderState.entityType);
		}
	}

	public void prepare(Level level, Camera camera, Entity entity) {
		this.level = level;
		this.camera = camera;
		this.cameraOrientation = camera.rotation();
		this.crosshairPickEntity = entity;
	}

	public void overrideCameraOrientation(Quaternionf quaternionf) {
		this.cameraOrientation = quaternionf;
	}

	public void setRenderShadow(boolean bl) {
		this.shouldRenderShadow = bl;
	}

	public void setRenderHitBoxes(boolean bl) {
		this.renderHitBoxes = bl;
	}

	public boolean shouldRenderHitBoxes() {
		return this.renderHitBoxes;
	}

	public <E extends Entity> boolean shouldRender(E entity, Frustum frustum, double d, double e, double f) {
		EntityRenderer<? super E, ?> entityRenderer = this.getRenderer(entity);
		return entityRenderer.shouldRender(entity, frustum, d, e, f);
	}

	public <E extends Entity> void render(E entity, double d, double e, double f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, int i) {
		EntityRenderer<? super E, ?> entityRenderer = this.getRenderer(entity);
		this.render(entity, d, e, f, g, poseStack, multiBufferSource, i, entityRenderer);
	}

	private <E extends Entity, S extends EntityRenderState> void render(
		E entity, double d, double e, double f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, EntityRenderer<? super E, S> entityRenderer
	) {
		S entityRenderState;
		try {
			entityRenderState = entityRenderer.createRenderState(entity, g);
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Extracting render state for an entity in world");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being extracted");
			entity.fillCrashReportCategory(crashReportCategory);
			CrashReportCategory crashReportCategory2 = this.fillRendererDetails(d, e, f, entityRenderer, crashReport);
			crashReportCategory2.setDetail("Delta", g);
			throw new ReportedException(crashReport);
		}

		try {
			this.render(entityRenderState, d, e, f, poseStack, multiBufferSource, i, entityRenderer);
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering entity in world");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being rendered");
			entity.fillCrashReportCategory(crashReportCategory);
			throw new ReportedException(crashReport);
		}
	}

	public <S extends EntityRenderState> void render(
		S entityRenderState, double d, double e, double f, PoseStack poseStack, MultiBufferSource multiBufferSource, int i
	) {
		EntityRenderer<?, ? super S> entityRenderer = this.getRenderer(entityRenderState);
		this.render(entityRenderState, d, e, f, poseStack, multiBufferSource, i, entityRenderer);
	}

	private <S extends EntityRenderState> void render(
		S entityRenderState, double d, double e, double f, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, EntityRenderer<?, S> entityRenderer
	) {
		try {
			Vec3 vec3 = entityRenderer.getRenderOffset(entityRenderState);
			double g = d + vec3.x();
			double h = e + vec3.y();
			double j = f + vec3.z();
			poseStack.pushPose();
			poseStack.translate(g, h, j);
			entityRenderer.render(entityRenderState, poseStack, multiBufferSource, i);
			if (entityRenderState.displayFireAnimation) {
				this.renderFlame(poseStack, multiBufferSource, entityRenderState, Mth.rotationAroundAxis(Mth.Y_AXIS, this.cameraOrientation, new Quaternionf()));
			}

			if (entityRenderState instanceof PlayerRenderState) {
				poseStack.translate(-vec3.x(), -vec3.y(), -vec3.z());
			}

			if (this.options.entityShadows().get() && this.shouldRenderShadow && !entityRenderState.isInvisible) {
				float k = entityRenderer.getShadowRadius(entityRenderState);
				if (k > 0.0F) {
					double l = entityRenderState.distanceToCameraSq;
					float m = (float)((1.0 - l / 256.0) * entityRenderer.getShadowStrength(entityRenderState));
					if (m > 0.0F) {
						renderShadow(poseStack, multiBufferSource, entityRenderState, m, this.level, Math.min(k, 32.0F));
					}
				}
			}

			if (!(entityRenderState instanceof PlayerRenderState)) {
				poseStack.translate(-vec3.x(), -vec3.y(), -vec3.z());
			}

			if (entityRenderState.hitboxesRenderState != null) {
				this.renderHitboxes(poseStack, entityRenderState, entityRenderState.hitboxesRenderState, multiBufferSource);
			}

			poseStack.popPose();
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering entity in world");
			CrashReportCategory crashReportCategory = crashReport.addCategory("EntityRenderState being rendered");
			entityRenderState.fillCrashReportCategory(crashReportCategory);
			this.fillRendererDetails(d, e, f, entityRenderer, crashReport);
			throw new ReportedException(crashReport);
		}
	}

	private <S extends EntityRenderState> CrashReportCategory fillRendererDetails(
		double d, double e, double f, EntityRenderer<?, S> entityRenderer, CrashReport crashReport
	) {
		CrashReportCategory crashReportCategory = crashReport.addCategory("Renderer details");
		crashReportCategory.setDetail("Assigned renderer", entityRenderer);
		crashReportCategory.setDetail("Location", CrashReportCategory.formatLocation(this.level, d, e, f));
		return crashReportCategory;
	}

	private void renderHitboxes(
		PoseStack poseStack, EntityRenderState entityRenderState, HitboxesRenderState hitboxesRenderState, MultiBufferSource multiBufferSource
	) {
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.lines());
		renderHitboxesAndViewVector(poseStack, hitboxesRenderState, vertexConsumer, entityRenderState.eyeHeight);
		ServerHitboxesRenderState serverHitboxesRenderState = entityRenderState.serverHitboxesRenderState;
		if (serverHitboxesRenderState != null) {
			if (serverHitboxesRenderState.missing()) {
				HitboxRenderState hitboxRenderState = hitboxesRenderState.hitboxes().getFirst();
				DebugRenderer.renderFloatingText(poseStack, multiBufferSource, "Missing", entityRenderState.x, hitboxRenderState.y1() + 1.5, entityRenderState.z, -65536);
			} else if (serverHitboxesRenderState.hitboxes() != null) {
				poseStack.pushPose();
				poseStack.translate(
					serverHitboxesRenderState.serverEntityX() - entityRenderState.x,
					serverHitboxesRenderState.serverEntityY() - entityRenderState.y,
					serverHitboxesRenderState.serverEntityZ() - entityRenderState.z
				);
				renderHitboxesAndViewVector(poseStack, serverHitboxesRenderState.hitboxes(), vertexConsumer, serverHitboxesRenderState.eyeHeight());
				Vec3 vec3 = new Vec3(serverHitboxesRenderState.deltaMovementX(), serverHitboxesRenderState.deltaMovementY(), serverHitboxesRenderState.deltaMovementZ());
				ShapeRenderer.renderVector(poseStack, vertexConsumer, new Vector3f(), vec3, -256);
				poseStack.popPose();
			}
		}
	}

	private static void renderHitboxesAndViewVector(PoseStack poseStack, HitboxesRenderState hitboxesRenderState, VertexConsumer vertexConsumer, float f) {
		for (HitboxRenderState hitboxRenderState : hitboxesRenderState.hitboxes()) {
			renderHitbox(poseStack, vertexConsumer, hitboxRenderState);
		}

		Vec3 vec3 = new Vec3(hitboxesRenderState.viewX(), hitboxesRenderState.viewY(), hitboxesRenderState.viewZ());
		ShapeRenderer.renderVector(poseStack, vertexConsumer, new Vector3f(0.0F, f, 0.0F), vec3.scale(2.0), -16776961);
	}

	private static void renderHitbox(PoseStack poseStack, VertexConsumer vertexConsumer, HitboxRenderState hitboxRenderState) {
		poseStack.pushPose();
		poseStack.translate(hitboxRenderState.offsetX(), hitboxRenderState.offsetY(), hitboxRenderState.offsetZ());
		ShapeRenderer.renderLineBox(
			poseStack,
			vertexConsumer,
			hitboxRenderState.x0(),
			hitboxRenderState.y0(),
			hitboxRenderState.z0(),
			hitboxRenderState.x1(),
			hitboxRenderState.y1(),
			hitboxRenderState.z1(),
			hitboxRenderState.red(),
			hitboxRenderState.green(),
			hitboxRenderState.blue(),
			1.0F
		);
		poseStack.popPose();
	}

	private void renderFlame(PoseStack poseStack, MultiBufferSource multiBufferSource, EntityRenderState entityRenderState, Quaternionf quaternionf) {
		TextureAtlasSprite textureAtlasSprite = ModelBakery.FIRE_0.sprite();
		TextureAtlasSprite textureAtlasSprite2 = ModelBakery.FIRE_1.sprite();
		poseStack.pushPose();
		float f = entityRenderState.boundingBoxWidth * 1.4F;
		poseStack.scale(f, f, f);
		float g = 0.5F;
		float h = 0.0F;
		float i = entityRenderState.boundingBoxHeight / f;
		float j = 0.0F;
		poseStack.mulPose(quaternionf);
		poseStack.translate(0.0F, 0.0F, 0.3F - (int)i * 0.02F);
		float k = 0.0F;
		int l = 0;
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(Sheets.cutoutBlockSheet());
		PoseStack.Pose pose = poseStack.last();

		while (i > 0.0F) {
			TextureAtlasSprite textureAtlasSprite3 = l % 2 == 0 ? textureAtlasSprite : textureAtlasSprite2;
			float m = textureAtlasSprite3.getU0();
			float n = textureAtlasSprite3.getV0();
			float o = textureAtlasSprite3.getU1();
			float p = textureAtlasSprite3.getV1();
			if (l / 2 % 2 == 0) {
				float q = o;
				o = m;
				m = q;
			}

			fireVertex(pose, vertexConsumer, -g - 0.0F, 0.0F - j, k, o, p);
			fireVertex(pose, vertexConsumer, g - 0.0F, 0.0F - j, k, m, p);
			fireVertex(pose, vertexConsumer, g - 0.0F, 1.4F - j, k, m, n);
			fireVertex(pose, vertexConsumer, -g - 0.0F, 1.4F - j, k, o, n);
			i -= 0.45F;
			j -= 0.45F;
			g *= 0.9F;
			k -= 0.03F;
			l++;
		}

		poseStack.popPose();
	}

	private static void fireVertex(PoseStack.Pose pose, VertexConsumer vertexConsumer, float f, float g, float h, float i, float j) {
		vertexConsumer.addVertex(pose, f, g, h).setColor(-1).setUv(i, j).setUv1(0, 10).setLight(240).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	private static void renderShadow(
		PoseStack poseStack, MultiBufferSource multiBufferSource, EntityRenderState entityRenderState, float f, LevelReader levelReader, float g
	) {
		float h = Math.min(f / 0.5F, g);
		int i = Mth.floor(entityRenderState.x - g);
		int j = Mth.floor(entityRenderState.x + g);
		int k = Mth.floor(entityRenderState.y - h);
		int l = Mth.floor(entityRenderState.y);
		int m = Mth.floor(entityRenderState.z - g);
		int n = Mth.floor(entityRenderState.z + g);
		PoseStack.Pose pose = poseStack.last();
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(SHADOW_RENDER_TYPE);
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

		for (int o = m; o <= n; o++) {
			for (int p = i; p <= j; p++) {
				mutableBlockPos.set(p, 0, o);
				ChunkAccess chunkAccess = levelReader.getChunk(mutableBlockPos);

				for (int q = k; q <= l; q++) {
					mutableBlockPos.setY(q);
					float r = f - (float)(entityRenderState.y - mutableBlockPos.getY()) * 0.5F;
					renderBlockShadow(pose, vertexConsumer, chunkAccess, levelReader, mutableBlockPos, entityRenderState.x, entityRenderState.y, entityRenderState.z, g, r);
				}
			}
		}
	}

	private static void renderBlockShadow(
		PoseStack.Pose pose,
		VertexConsumer vertexConsumer,
		ChunkAccess chunkAccess,
		LevelReader levelReader,
		BlockPos blockPos,
		double d,
		double e,
		double f,
		float g,
		float h
	) {
		BlockPos blockPos2 = blockPos.below();
		BlockState blockState = chunkAccess.getBlockState(blockPos2);
		if (blockState.getRenderShape() != RenderShape.INVISIBLE && levelReader.getMaxLocalRawBrightness(blockPos) > 3) {
			if (blockState.isCollisionShapeFullBlock(chunkAccess, blockPos2)) {
				VoxelShape voxelShape = blockState.getShape(chunkAccess, blockPos2);
				if (!voxelShape.isEmpty()) {
					float i = LightTexture.getBrightness(levelReader.dimensionType(), levelReader.getMaxLocalRawBrightness(blockPos));
					float j = h * 0.5F * i;
					if (j >= 0.0F) {
						if (j > 1.0F) {
							j = 1.0F;
						}

						int k = ARGB.color(Mth.floor(j * 255.0F), 255, 255, 255);
						AABB aABB = voxelShape.bounds();
						double l = blockPos.getX() + aABB.minX;
						double m = blockPos.getX() + aABB.maxX;
						double n = blockPos.getY() + aABB.minY;
						double o = blockPos.getZ() + aABB.minZ;
						double p = blockPos.getZ() + aABB.maxZ;
						float q = (float)(l - d);
						float r = (float)(m - d);
						float s = (float)(n - e);
						float t = (float)(o - f);
						float u = (float)(p - f);
						float v = -q / 2.0F / g + 0.5F;
						float w = -r / 2.0F / g + 0.5F;
						float x = -t / 2.0F / g + 0.5F;
						float y = -u / 2.0F / g + 0.5F;
						shadowVertex(pose, vertexConsumer, k, q, s, t, v, x);
						shadowVertex(pose, vertexConsumer, k, q, s, u, v, y);
						shadowVertex(pose, vertexConsumer, k, r, s, u, w, y);
						shadowVertex(pose, vertexConsumer, k, r, s, t, w, x);
					}
				}
			}
		}
	}

	private static void shadowVertex(PoseStack.Pose pose, VertexConsumer vertexConsumer, int i, float f, float g, float h, float j, float k) {
		Vector3f vector3f = pose.pose().transformPosition(f, g, h, new Vector3f());
		vertexConsumer.addVertex(vector3f.x(), vector3f.y(), vector3f.z(), i, j, k, OverlayTexture.NO_OVERLAY, 15728880, 0.0F, 1.0F, 0.0F);
	}

	public void setLevel(@Nullable Level level) {
		this.level = level;
		if (level == null) {
			this.camera = null;
		}
	}

	public double distanceToSqr(Entity entity) {
		return this.camera.getPosition().distanceToSqr(entity.position());
	}

	public double distanceToSqr(double d, double e, double f) {
		return this.camera.getPosition().distanceToSqr(d, e, f);
	}

	public Quaternionf cameraOrientation() {
		return this.cameraOrientation;
	}

	public ItemInHandRenderer getItemInHandRenderer() {
		return this.itemInHandRenderer;
	}

	@Override
	public void onResourceManagerReload(ResourceManager resourceManager) {
		EntityRendererProvider.Context context = new EntityRendererProvider.Context(
			this, this.itemModelResolver, this.mapRenderer, this.blockRenderDispatcher, resourceManager, this.entityModels.get(), this.equipmentAssets, this.font
		);
		this.renderers = EntityRenderers.createEntityRenderers(context);
		this.playerRenderers = EntityRenderers.createPlayerRenderers(context);
	}
}
