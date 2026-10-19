package net.minecraft.client.render.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.render.FabricLayerRenderState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class ItemRenderState {
	ItemDisplayContext displayContext = ItemDisplayContext.NONE;
	private int layerCount;
	private ItemRenderState.LayerRenderState[] layers = new ItemRenderState.LayerRenderState[]{new ItemRenderState.LayerRenderState()};

	public void addLayers(int add) {
		int i = this.layers.length;
		int j = this.layerCount + add;
		if (j > i) {
			this.layers = (ItemRenderState.LayerRenderState[])Arrays.copyOf(this.layers, j);

			for (int k = i; k < j; k++) {
				this.layers[k] = new ItemRenderState.LayerRenderState();
			}
		}
	}

	public ItemRenderState.LayerRenderState newLayer() {
		this.addLayers(1);
		return this.layers[this.layerCount++];
	}

	public void clear() {
		this.displayContext = ItemDisplayContext.NONE;

		for (int i = 0; i < this.layerCount; i++) {
			this.layers[i].clear();
		}

		this.layerCount = 0;
	}

	private ItemRenderState.LayerRenderState getFirstLayer() {
		return this.layers[0];
	}

	public boolean isEmpty() {
		return this.layerCount == 0;
	}

	public boolean isSideLit() {
		return this.getFirstLayer().useLight;
	}

	@Nullable
	public Sprite getParticleSprite(Random random) {
		return this.layerCount == 0 ? null : this.layers[random.nextInt(this.layerCount)].particle;
	}

	public void load(Consumer<Vector3fc> posConsumer) {
		Vector3f vector3f = new Vector3f();
		MatrixStack.Entry entry = new MatrixStack.Entry();

		for (int i = 0; i < this.layerCount; i++) {
			ItemRenderState.LayerRenderState layerRenderState = this.layers[i];
			layerRenderState.transform.apply(this.displayContext.isLeftHand(), entry);
			Matrix4f matrix4f = entry.getPositionMatrix();
			Vector3f[] vector3fs = (Vector3f[])layerRenderState.vector.get();

			for (Vector3f vector3f2 : vector3fs) {
				posConsumer.accept(vector3f.set(vector3f2).mulPosition(matrix4f));
			}

			entry.loadIdentity();
		}
	}

	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		for (int i = 0; i < this.layerCount; i++) {
			this.layers[i].render(matrices, vertexConsumers, light, overlay);
		}
	}

	@Environment(EnvType.CLIENT)
	public static enum Glint {
		NONE,
		STANDARD,
		SPECIAL;
	}

	@Environment(EnvType.CLIENT)
	public class LayerRenderState implements FabricLayerRenderState {
		private static final Vector3f[] EMPTY = new Vector3f[0];
		public static final Supplier<Vector3f[]> DEFAULT = () -> EMPTY;
		private final List<BakedQuad> quads = new ArrayList();
		boolean useLight;
		@Nullable
		Sprite particle;
		Transformation transform = Transformation.IDENTITY;
		@Nullable
		private RenderLayer renderLayer;
		private ItemRenderState.Glint glint = ItemRenderState.Glint.NONE;
		private int[] tints = new int[0];
		@Nullable
		private SpecialModelRenderer<Object> specialModelType;
		@Nullable
		private Object data;
		Supplier<Vector3f[]> vector = DEFAULT;

		public void clear() {
			this.quads.clear();
			this.renderLayer = null;
			this.glint = ItemRenderState.Glint.NONE;
			this.specialModelType = null;
			this.data = null;
			Arrays.fill(this.tints, -1);
			this.useLight = false;
			this.particle = null;
			this.transform = Transformation.IDENTITY;
			this.vector = DEFAULT;
		}

		public List<BakedQuad> getQuads() {
			return this.quads;
		}

		public void setRenderLayer(RenderLayer layer) {
			this.renderLayer = layer;
		}

		public void setUseLight(boolean useLight) {
			this.useLight = useLight;
		}

		public void setVector(Supplier<Vector3f[]> vector) {
			this.vector = vector;
		}

		public void setParticle(Sprite particle) {
			this.particle = particle;
		}

		public void setTransform(Transformation transform) {
			this.transform = transform;
		}

		public <T> void setSpecialModel(SpecialModelRenderer<T> specialModelType, @Nullable T data) {
			this.specialModelType = eraseType(specialModelType);
			this.data = data;
		}

		private static SpecialModelRenderer<Object> eraseType(SpecialModelRenderer<?> specialModelType) {
			return (SpecialModelRenderer<Object>)specialModelType;
		}

		public void setGlint(ItemRenderState.Glint glint) {
			this.glint = glint;
		}

		public int[] initTints(int maxIndex) {
			if (maxIndex > this.tints.length) {
				this.tints = new int[maxIndex];
				Arrays.fill(this.tints, -1);
			}

			return this.tints;
		}

		void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
			matrices.push();
			this.transform.apply(ItemRenderState.this.displayContext.isLeftHand(), matrices.peek());
			if (this.specialModelType != null) {
				this.specialModelType
					.render(this.data, ItemRenderState.this.displayContext, matrices, vertexConsumers, light, overlay, this.glint != ItemRenderState.Glint.NONE);
			} else if (this.renderLayer != null) {
				ItemRenderer.renderItem(
					ItemRenderState.this.displayContext, matrices, vertexConsumers, light, overlay, this.tints, this.quads, this.renderLayer, this.glint
				);
			}

			matrices.pop();
		}
	}
}
