package net.minecraft.client.renderer.blockentity;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BlockEntityRenderDispatcher implements ResourceManagerReloadListener {
	private Map<BlockEntityType<?>, BlockEntityRenderer<?>> renderers = ImmutableMap.of();
	private final Font font;
	private final Supplier<EntityModelSet> entityModelSet;
	public Level level;
	public Camera camera;
	public HitResult cameraHitResult;
	private final BlockRenderDispatcher blockRenderDispatcher;
	private final ItemModelResolver itemModelResolver;
	private final ItemRenderer itemRenderer;
	private final EntityRenderDispatcher entityRenderer;

	public BlockEntityRenderDispatcher(
		Font font,
		Supplier<EntityModelSet> supplier,
		BlockRenderDispatcher blockRenderDispatcher,
		ItemModelResolver itemModelResolver,
		ItemRenderer itemRenderer,
		EntityRenderDispatcher entityRenderDispatcher
	) {
		this.itemRenderer = itemRenderer;
		this.itemModelResolver = itemModelResolver;
		this.entityRenderer = entityRenderDispatcher;
		this.font = font;
		this.entityModelSet = supplier;
		this.blockRenderDispatcher = blockRenderDispatcher;
	}

	@Nullable
	public <E extends BlockEntity> BlockEntityRenderer<E> getRenderer(E blockEntity) {
		return (BlockEntityRenderer<E>)this.renderers.get(blockEntity.getType());
	}

	public void prepare(Level level, Camera camera, HitResult hitResult) {
		if (this.level != level) {
			this.setLevel(level);
		}

		this.camera = camera;
		this.cameraHitResult = hitResult;
	}

	public <E extends BlockEntity> void render(E blockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource) {
		BlockEntityRenderer<E> blockEntityRenderer = this.getRenderer(blockEntity);
		if (blockEntityRenderer != null) {
			if (blockEntity.hasLevel() && blockEntity.getType().isValid(blockEntity.getBlockState())) {
				if (blockEntityRenderer.shouldRender(blockEntity, this.camera.getPosition())) {
					try {
						setupAndRender(blockEntityRenderer, blockEntity, f, poseStack, multiBufferSource, this.camera.getPosition());
					} catch (Throwable throwable) {
						CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering Block Entity");
						CrashReportCategory crashReportCategory = crashReport.addCategory("Block Entity Details");
						blockEntity.fillCrashReportCategory(crashReportCategory);
						throw new ReportedException(crashReport);
					}
				}
			}
		}
	}

	private static <T extends BlockEntity> void setupAndRender(
		BlockEntityRenderer<T> blockEntityRenderer, T blockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource, Vec3 vec3
	) {
		Level level = blockEntity.getLevel();
		int i;
		if (level != null) {
			i = LevelRenderer.getLightColor(level, blockEntity.getBlockPos());
		} else {
			i = 15728880;
		}

		blockEntityRenderer.render(blockEntity, f, poseStack, multiBufferSource, i, OverlayTexture.NO_OVERLAY, vec3);
	}

	public void setLevel(@Nullable Level level) {
		this.level = level;
		if (level == null) {
			this.camera = null;
		}
	}

	@Override
	public void onResourceManagerReload(ResourceManager resourceManager) {
		BlockEntityRendererProvider.Context context = new BlockEntityRendererProvider.Context(
			this, this.blockRenderDispatcher, this.itemModelResolver, this.itemRenderer, this.entityRenderer, this.entityModelSet.get(), this.font
		);
		this.renderers = BlockEntityRenderers.createEntityRenderers(context);
	}
}
