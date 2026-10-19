package net.minecraft.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.render.FabricLayerRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class ItemStackRenderState {
	ItemDisplayContext displayContext = ItemDisplayContext.NONE;
	private int activeLayerCount;
	private boolean animated;
	private boolean oversizedInGui;
	@Nullable
	private AABB cachedModelBoundingBox;
	private ItemStackRenderState.LayerRenderState[] layers = new ItemStackRenderState.LayerRenderState[]{new ItemStackRenderState.LayerRenderState()};

	public void ensureCapacity(int i) {
		int j = this.layers.length;
		int k = this.activeLayerCount + i;
		if (k > j) {
			this.layers = Arrays.copyOf(this.layers, k);

			for (int l = j; l < k; l++) {
				this.layers[l] = new ItemStackRenderState.LayerRenderState();
			}
		}
	}

	public ItemStackRenderState.LayerRenderState newLayer() {
		this.ensureCapacity(1);
		return this.layers[this.activeLayerCount++];
	}

	public void clear() {
		this.displayContext = ItemDisplayContext.NONE;

		for (int i = 0; i < this.activeLayerCount; i++) {
			this.layers[i].clear();
		}

		this.activeLayerCount = 0;
		this.animated = false;
		this.oversizedInGui = false;
		this.cachedModelBoundingBox = null;
	}

	public void setAnimated() {
		this.animated = true;
	}

	public boolean isAnimated() {
		return this.animated;
	}

	public void appendModelIdentityElement(Object object) {
	}

	private ItemStackRenderState.LayerRenderState firstLayer() {
		return this.layers[0];
	}

	public boolean isEmpty() {
		return this.activeLayerCount == 0;
	}

	public boolean usesBlockLight() {
		return this.firstLayer().usesBlockLight;
	}

	@Nullable
	public TextureAtlasSprite pickParticleIcon(RandomSource randomSource) {
		return this.activeLayerCount == 0 ? null : this.layers[randomSource.nextInt(this.activeLayerCount)].particleIcon;
	}

	public void visitExtents(Consumer<Vector3fc> consumer) {
		Vector3f vector3f = new Vector3f();
		PoseStack.Pose pose = new PoseStack.Pose();

		for (int i = 0; i < this.activeLayerCount; i++) {
			ItemStackRenderState.LayerRenderState layerRenderState = this.layers[i];
			layerRenderState.transform.apply(this.displayContext.leftHand(), pose);
			Matrix4f matrix4f = pose.pose();
			Vector3f[] vector3fs = layerRenderState.extents.get();

			for (Vector3f vector3f2 : vector3fs) {
				consumer.accept(vector3f.set(vector3f2).mulPosition(matrix4f));
			}

			pose.setIdentity();
		}
	}

	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j) {
		for (int k = 0; k < this.activeLayerCount; k++) {
			this.layers[k].render(poseStack, multiBufferSource, i, j);
		}
	}

	public AABB getModelBoundingBox() {
		if (this.cachedModelBoundingBox != null) {
			return this.cachedModelBoundingBox;
		}

		AABB.Builder builder = new AABB.Builder();
		this.visitExtents(builder::include);
		AABB aABB = builder.build();
		this.cachedModelBoundingBox = aABB;
		return aABB;
	}

	public void setOversizedInGui(boolean bl) {
		this.oversizedInGui = bl;
	}

	public boolean isOversizedInGui() {
		return this.oversizedInGui;
	}

	@Environment(EnvType.CLIENT)
	public enum FoilType {
		NONE,
		STANDARD,
		SPECIAL;
	}

	/**
	 * <p>Interface {@link net.fabricmc.fabric.api.renderer.v1.render.FabricLayerRenderState} injected by mod fabric-renderer-api-v1</p>
	 */
	@Environment(EnvType.CLIENT)
	public class LayerRenderState implements FabricLayerRenderState {
		private static final Vector3f[] NO_EXTENTS = new Vector3f[0];
		public static final Supplier<Vector3f[]> NO_EXTENTS_SUPPLIER = () -> NO_EXTENTS;
		private final List<BakedQuad> quads = new ArrayList<>();
		boolean usesBlockLight;
		@Nullable
		TextureAtlasSprite particleIcon;
		ItemTransform transform = ItemTransform.NO_TRANSFORM;
		@Nullable
		private RenderType renderType;
		private ItemStackRenderState.FoilType foilType = ItemStackRenderState.FoilType.NONE;
		private int[] tintLayers = new int[0];
		@Nullable
		private SpecialModelRenderer<Object> specialRenderer;
		@Nullable
		private Object argumentForSpecialRendering;
		Supplier<Vector3f[]> extents = NO_EXTENTS_SUPPLIER;

		public void clear() {
			this.quads.clear();
			this.renderType = null;
			this.foilType = ItemStackRenderState.FoilType.NONE;
			this.specialRenderer = null;
			this.argumentForSpecialRendering = null;
			Arrays.fill(this.tintLayers, -1);
			this.usesBlockLight = false;
			this.particleIcon = null;
			this.transform = ItemTransform.NO_TRANSFORM;
			this.extents = NO_EXTENTS_SUPPLIER;
		}

		public List<BakedQuad> prepareQuadList() {
			return this.quads;
		}

		public void setRenderType(RenderType renderType) {
			this.renderType = renderType;
		}

		public void setUsesBlockLight(boolean bl) {
			this.usesBlockLight = bl;
		}

		public void setExtents(Supplier<Vector3f[]> supplier) {
			this.extents = supplier;
		}

		public void setParticleIcon(TextureAtlasSprite textureAtlasSprite) {
			this.particleIcon = textureAtlasSprite;
		}

		public void setTransform(ItemTransform itemTransform) {
			this.transform = itemTransform;
		}

		public <T> void setupSpecialModel(SpecialModelRenderer<T> specialModelRenderer, @Nullable T object) {
			this.specialRenderer = eraseSpecialRenderer(specialModelRenderer);
			this.argumentForSpecialRendering = object;
		}

		private static SpecialModelRenderer<Object> eraseSpecialRenderer(SpecialModelRenderer<?> specialModelRenderer) {
			return (SpecialModelRenderer<Object>)specialModelRenderer;
		}

		public void setFoilType(ItemStackRenderState.FoilType foilType) {
			this.foilType = foilType;
		}

		public int[] prepareTintLayers(int i) {
			if (i > this.tintLayers.length) {
				this.tintLayers = new int[i];
				Arrays.fill(this.tintLayers, -1);
			}

			return this.tintLayers;
		}

		void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j) {
			poseStack.pushPose();
			this.transform.apply(ItemStackRenderState.this.displayContext.leftHand(), poseStack.last());
			if (this.specialRenderer != null) {
				this.specialRenderer
					.render(
						this.argumentForSpecialRendering,
						ItemStackRenderState.this.displayContext,
						poseStack,
						multiBufferSource,
						i,
						j,
						this.foilType != ItemStackRenderState.FoilType.NONE
					);
			} else if (this.renderType != null) {
				ItemRenderer.renderItem(
					ItemStackRenderState.this.displayContext, poseStack, multiBufferSource, i, j, this.tintLayers, this.quads, this.renderType, this.foilType
				);
			}

			poseStack.popPose();
		}
	}
}
