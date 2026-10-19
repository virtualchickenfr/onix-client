package net.minecraft.client.gui.font.glyphs;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class BakedGlyph {
	public static final float Z_FIGHTER = 0.001F;
	private final GlyphRenderTypes renderTypes;
	@Nullable
	private final GpuTextureView textureView;
	private final float u0;
	private final float u1;
	private final float v0;
	private final float v1;
	private final float left;
	private final float right;
	private final float up;
	private final float down;

	public BakedGlyph(
		GlyphRenderTypes glyphRenderTypes, @Nullable GpuTextureView gpuTextureView, float f, float g, float h, float i, float j, float k, float l, float m
	) {
		this.renderTypes = glyphRenderTypes;
		this.textureView = gpuTextureView;
		this.u0 = f;
		this.u1 = g;
		this.v0 = h;
		this.v1 = i;
		this.left = j;
		this.right = k;
		this.up = l;
		this.down = m;
	}

	public float left(BakedGlyph.GlyphInstance glyphInstance) {
		return glyphInstance.x
			+ this.left
			+ (glyphInstance.style.isItalic() ? Math.min(this.shearTop(), this.shearBottom()) : 0.0F)
			- extraThickness(glyphInstance.style.isBold());
	}

	public float top(BakedGlyph.GlyphInstance glyphInstance) {
		return glyphInstance.y + this.up - extraThickness(glyphInstance.style.isBold());
	}

	public float right(BakedGlyph.GlyphInstance glyphInstance) {
		return glyphInstance.x
			+ this.right
			+ (glyphInstance.hasShadow() ? glyphInstance.shadowOffset : 0.0F)
			+ (glyphInstance.style.isItalic() ? Math.max(this.shearTop(), this.shearBottom()) : 0.0F)
			+ extraThickness(glyphInstance.style.isBold());
	}

	public float bottom(BakedGlyph.GlyphInstance glyphInstance) {
		return glyphInstance.y + this.down + (glyphInstance.hasShadow() ? glyphInstance.shadowOffset : 0.0F) + extraThickness(glyphInstance.style.isBold());
	}

	public void renderChar(BakedGlyph.GlyphInstance glyphInstance, Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, boolean bl) {
		Style style = glyphInstance.style();
		boolean bl2 = style.isItalic();
		float f = glyphInstance.x();
		float g = glyphInstance.y();
		int j = glyphInstance.color();
		boolean bl3 = style.isBold();
		float h = bl ? 0.0F : 0.001F;
		float l;
		if (glyphInstance.hasShadow()) {
			int k = glyphInstance.shadowColor();
			this.render(bl2, f + glyphInstance.shadowOffset(), g + glyphInstance.shadowOffset(), 0.0F, matrix4f, vertexConsumer, k, bl3, i);
			if (bl3) {
				this.render(bl2, f + glyphInstance.boldOffset() + glyphInstance.shadowOffset(), g + glyphInstance.shadowOffset(), h, matrix4f, vertexConsumer, k, true, i);
			}

			l = bl ? 0.0F : 0.003F;
		} else {
			l = 0.0F;
		}

		this.render(bl2, f, g, l, matrix4f, vertexConsumer, j, bl3, i);
		if (bl3) {
			this.render(bl2, f + glyphInstance.boldOffset(), g, l + h, matrix4f, vertexConsumer, j, true, i);
		}
	}

	private void render(boolean bl, float f, float g, float h, Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, boolean bl2, int j) {
		float k = f + this.left;
		float l = f + this.right;
		float m = g + this.up;
		float n = g + this.down;
		float o = bl ? this.shearTop() : 0.0F;
		float p = bl ? this.shearBottom() : 0.0F;
		float q = extraThickness(bl2);
		vertexConsumer.addVertex(matrix4f, k + o - q, m - q, h).setColor(i).setUv(this.u0, this.v0).setLight(j);
		vertexConsumer.addVertex(matrix4f, k + p - q, n + q, h).setColor(i).setUv(this.u0, this.v1).setLight(j);
		vertexConsumer.addVertex(matrix4f, l + p + q, n + q, h).setColor(i).setUv(this.u1, this.v1).setLight(j);
		vertexConsumer.addVertex(matrix4f, l + o + q, m - q, h).setColor(i).setUv(this.u1, this.v0).setLight(j);
	}

	private static float extraThickness(boolean bl) {
		return bl ? 0.1F : 0.0F;
	}

	private float shearBottom() {
		return 1.0F - 0.25F * this.down;
	}

	private float shearTop() {
		return 1.0F - 0.25F * this.up;
	}

	public void renderEffect(BakedGlyph.Effect effect, Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, boolean bl) {
		float f = bl ? 0.0F : effect.depth;
		if (effect.hasShadow()) {
			this.buildEffect(effect, effect.shadowOffset(), f, effect.shadowColor(), vertexConsumer, i, matrix4f);
			f += bl ? 0.0F : 0.003F;
		}

		this.buildEffect(effect, 0.0F, f, effect.color, vertexConsumer, i, matrix4f);
	}

	private void buildEffect(BakedGlyph.Effect effect, float f, float g, int i, VertexConsumer vertexConsumer, int j, Matrix4f matrix4f) {
		vertexConsumer.addVertex(matrix4f, effect.x0 + f, effect.y1 + f, g).setColor(i).setUv(this.u0, this.v0).setLight(j);
		vertexConsumer.addVertex(matrix4f, effect.x1 + f, effect.y1 + f, g).setColor(i).setUv(this.u0, this.v1).setLight(j);
		vertexConsumer.addVertex(matrix4f, effect.x1 + f, effect.y0 + f, g).setColor(i).setUv(this.u1, this.v1).setLight(j);
		vertexConsumer.addVertex(matrix4f, effect.x0 + f, effect.y0 + f, g).setColor(i).setUv(this.u1, this.v0).setLight(j);
	}

	@Nullable
	public GpuTextureView textureView() {
		return this.textureView;
	}

	public RenderPipeline guiPipeline() {
		return this.renderTypes.guiPipeline();
	}

	public RenderType renderType(Font.DisplayMode displayMode) {
		return this.renderTypes.select(displayMode);
	}

	@Environment(EnvType.CLIENT)
	public record Effect(float x0, float y0, float x1, float y1, float depth, int color, int shadowColor, float shadowOffset) {

		public Effect(float f, float g, float h, float i, float j, int k) {
			this(f, g, h, i, j, k, 0, 0.0F);
		}

		public float left() {
			return this.x0;
		}

		public float top() {
			return this.y0;
		}

		public float right() {
			return this.x1 + (this.hasShadow() ? this.shadowOffset : 0.0F);
		}

		public float bottom() {
			return this.y1 + (this.hasShadow() ? this.shadowOffset : 0.0F);
		}

		boolean hasShadow() {
			return this.shadowColor() != 0;
		}
	}

	@Environment(EnvType.CLIENT)
	public record GlyphInstance(float x, float y, int color, int shadowColor, BakedGlyph glyph, Style style, float boldOffset, float shadowOffset) {

		public float left() {
			return this.glyph.left(this);
		}

		public float top() {
			return this.glyph.top(this);
		}

		public float right() {
			return this.glyph.right(this);
		}

		public float bottom() {
			return this.glyph.bottom(this);
		}

		boolean hasShadow() {
			return this.shadowColor() != 0;
		}
	}
}
