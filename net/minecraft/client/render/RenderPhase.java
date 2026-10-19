package net.minecraft.client.render;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

@Environment(EnvType.CLIENT)
public abstract class RenderPhase {
	public static final double field_42230 = 8.0;
	protected final String name;
	private final Runnable beginAction;
	private final Runnable endAction;
	public static final RenderPhase.Texture MIPMAP_BLOCK_ATLAS_TEXTURE = new RenderPhase.Texture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, TriState.FALSE, true);
	public static final RenderPhase.Texture BLOCK_ATLAS_TEXTURE = new RenderPhase.Texture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, TriState.FALSE, false);
	public static final RenderPhase.TextureBase NO_TEXTURE = new RenderPhase.TextureBase();
	public static final RenderPhase.Texturing DEFAULT_TEXTURING = new RenderPhase.Texturing("default_texturing", () -> {}, () -> {});
	public static final RenderPhase.Texturing GLINT_TEXTURING = new RenderPhase.Texturing(
		"glint_texturing", () -> setupGlintTexturing(8.0F), RenderSystem::resetTextureMatrix
	);
	public static final RenderPhase.Texturing ENTITY_GLINT_TEXTURING = new RenderPhase.Texturing(
		"entity_glint_texturing", () -> setupGlintTexturing(0.5F), RenderSystem::resetTextureMatrix
	);
	public static final RenderPhase.Texturing ARMOR_ENTITY_GLINT_TEXTURING = new RenderPhase.Texturing(
		"armor_entity_glint_texturing", () -> setupGlintTexturing(0.16F), RenderSystem::resetTextureMatrix
	);
	public static final RenderPhase.Lightmap ENABLE_LIGHTMAP = new RenderPhase.Lightmap(true);
	public static final RenderPhase.Lightmap DISABLE_LIGHTMAP = new RenderPhase.Lightmap(false);
	public static final RenderPhase.Overlay ENABLE_OVERLAY_COLOR = new RenderPhase.Overlay(true);
	public static final RenderPhase.Overlay DISABLE_OVERLAY_COLOR = new RenderPhase.Overlay(false);
	public static final RenderPhase.Layering NO_LAYERING = new RenderPhase.Layering("no_layering", () -> {}, () -> {});
	public static final RenderPhase.Layering VIEW_OFFSET_Z_LAYERING = new RenderPhase.Layering("view_offset_z_layering", () -> {
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		RenderSystem.getProjectionType().apply(matrix4fStack, 1.0F);
	}, () -> {
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.popMatrix();
	});
	public static final RenderPhase.Layering VIEW_OFFSET_Z_LAYERING_FORWARD = new RenderPhase.Layering("view_offset_z_layering_forward", () -> {
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		RenderSystem.getProjectionType().apply(matrix4fStack, -1.0F);
	}, () -> {
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.popMatrix();
	});
	public static final RenderPhase.Target MAIN_TARGET = new RenderPhase.Target("main_target", () -> MinecraftClient.getInstance().getFramebuffer());
	public static final RenderPhase.Target OUTLINE_TARGET = new RenderPhase.Target("outline_target", () -> {
		Framebuffer framebuffer = MinecraftClient.getInstance().worldRenderer.getEntityOutlinesFramebuffer();
		return framebuffer != null ? framebuffer : MinecraftClient.getInstance().getFramebuffer();
	});
	public static final RenderPhase.Target TRANSLUCENT_TARGET = new RenderPhase.Target("translucent_target", () -> {
		Framebuffer framebuffer = MinecraftClient.getInstance().worldRenderer.getTranslucentFramebuffer();
		return framebuffer != null ? framebuffer : MinecraftClient.getInstance().getFramebuffer();
	});
	public static final RenderPhase.Target PARTICLES_TARGET = new RenderPhase.Target("particles_target", () -> {
		Framebuffer framebuffer = MinecraftClient.getInstance().worldRenderer.getParticlesFramebuffer();
		return framebuffer != null ? framebuffer : MinecraftClient.getInstance().getFramebuffer();
	});
	public static final RenderPhase.Target WEATHER_TARGET = new RenderPhase.Target("weather_target", () -> {
		Framebuffer framebuffer = MinecraftClient.getInstance().worldRenderer.getWeatherFramebuffer();
		return framebuffer != null ? framebuffer : MinecraftClient.getInstance().getFramebuffer();
	});
	public static final RenderPhase.Target ITEM_ENTITY_TARGET = new RenderPhase.Target("item_entity_target", () -> {
		Framebuffer framebuffer = MinecraftClient.getInstance().worldRenderer.getEntityFramebuffer();
		return framebuffer != null ? framebuffer : MinecraftClient.getInstance().getFramebuffer();
	});
	public static final RenderPhase.LineWidth FULL_LINE_WIDTH = new RenderPhase.LineWidth(OptionalDouble.of(1.0));

	public RenderPhase(String name, Runnable beginAction, Runnable endAction) {
		this.name = name;
		this.beginAction = beginAction;
		this.endAction = endAction;
	}

	public void startDrawing() {
		this.beginAction.run();
	}

	public void endDrawing() {
		this.endAction.run();
	}

	public String toString() {
		return this.name;
	}

	public String getName() {
		return this.name;
	}

	private static void setupGlintTexturing(float scale) {
		long l = (long)(Util.getMeasuringTimeMs() * MinecraftClient.getInstance().options.getGlintSpeed().getValue() * 8.0);
		float f = (float)(l % 110000L) / 110000.0F;
		float g = (float)(l % 30000L) / 30000.0F;
		Matrix4f matrix4f = new Matrix4f().translation(-f, g, 0.0F);
		matrix4f.rotateZ((float) (Math.PI / 18)).scale(scale);
		RenderSystem.setTextureMatrix(matrix4f);
	}

	@Environment(EnvType.CLIENT)
	public static class Layering extends RenderPhase {
		public Layering(String string, Runnable runnable, Runnable runnable2) {
			super(string, runnable, runnable2);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Lightmap extends RenderPhase.Toggleable {
		public Lightmap(boolean lightmap) {
			super("lightmap", () -> {
				if (lightmap) {
					MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().enable();
				}
			}, () -> {
				if (lightmap) {
					MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().disable();
				}
			}, lightmap);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class LineWidth extends RenderPhase {
		private final OptionalDouble width;

		public LineWidth(OptionalDouble width) {
			super("line_width", () -> {
				if (!Objects.equals(width, OptionalDouble.of(1.0))) {
					if (width.isPresent()) {
						RenderSystem.lineWidth((float)width.getAsDouble());
					} else {
						RenderSystem.lineWidth(Math.max(2.5F, MinecraftClient.getInstance().getWindow().getFramebufferWidth() / 1920.0F * 2.5F));
					}
				}
			}, () -> {
				if (!Objects.equals(width, OptionalDouble.of(1.0))) {
					RenderSystem.lineWidth(1.0F);
				}
			});
			this.width = width;
		}

		@Override
		public String toString() {
			return this.name + "[" + (this.width.isPresent() ? this.width.getAsDouble() : "window_scale") + "]";
		}
	}

	@Environment(EnvType.CLIENT)
	public static final class OffsetTexturing extends RenderPhase.Texturing {
		public OffsetTexturing(float x, float y) {
			super("offset_texturing", () -> RenderSystem.setTextureMatrix(new Matrix4f().translation(x, y, 0.0F)), () -> RenderSystem.resetTextureMatrix());
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Overlay extends RenderPhase.Toggleable {
		public Overlay(boolean overlayColor) {
			super("overlay", () -> {
				if (overlayColor) {
					MinecraftClient.getInstance().gameRenderer.getOverlayTexture().setupOverlayColor();
				}
			}, () -> {
				if (overlayColor) {
					MinecraftClient.getInstance().gameRenderer.getOverlayTexture().teardownOverlayColor();
				}
			}, overlayColor);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Target extends RenderPhase {
		private final Supplier<Framebuffer> framebuffer;

		public Target(String name, Supplier<Framebuffer> framebuffer) {
			super(name, () -> {}, () -> {});
			this.framebuffer = framebuffer;
		}

		public Framebuffer get() {
			return (Framebuffer)this.framebuffer.get();
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Texture extends RenderPhase.TextureBase {
		private final Optional<Identifier> id;
		private final TriState blur;
		private final boolean mipmap;

		public Texture(Identifier id, TriState bilinear, boolean mipmap) {
			super(() -> {
				TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
				AbstractTexture abstractTexture = textureManager.getTexture(id);
				abstractTexture.setFilter(bilinear, mipmap);
				RenderSystem.setShaderTexture(0, abstractTexture.getGlTexture());
			}, () -> {});
			this.id = Optional.of(id);
			this.blur = bilinear;
			this.mipmap = mipmap;
		}

		@Override
		public String toString() {
			return this.name + "[" + this.id + "(blur=" + this.blur + ", mipmap=" + this.mipmap + ")]";
		}

		@Override
		protected Optional<Identifier> getId() {
			return this.id;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class TextureBase extends RenderPhase {
		public TextureBase(Runnable apply, Runnable unapply) {
			super("texture", apply, unapply);
		}

		TextureBase() {
			super("texture", () -> {}, () -> {});
		}

		protected Optional<Identifier> getId() {
			return Optional.empty();
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Textures extends RenderPhase.TextureBase {
		private final Optional<Identifier> id;

		Textures(List<RenderPhase.Textures.TextureEntry> textures) {
			super(() -> {
				for (int i = 0; i < textures.size(); i++) {
					RenderPhase.Textures.TextureEntry textureEntry = (RenderPhase.Textures.TextureEntry)textures.get(i);
					TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
					AbstractTexture abstractTexture = textureManager.getTexture(textureEntry.id);
					abstractTexture.setFilter(textureEntry.blur, textureEntry.mipmap);
					RenderSystem.setShaderTexture(i, abstractTexture.getGlTexture());
				}
			}, () -> {});
			this.id = textures.isEmpty() ? Optional.empty() : Optional.of(((RenderPhase.Textures.TextureEntry)textures.getFirst()).id);
		}

		@Override
		protected Optional<Identifier> getId() {
			return this.id;
		}

		public static RenderPhase.Textures.Builder create() {
			return new RenderPhase.Textures.Builder();
		}

		@Environment(EnvType.CLIENT)
		public static final class Builder {
			private final ImmutableList.Builder<RenderPhase.Textures.TextureEntry> textures = new ImmutableList.Builder<>();

			public RenderPhase.Textures.Builder add(Identifier id, boolean blur, boolean mipmap) {
				this.textures.add(new RenderPhase.Textures.TextureEntry(id, blur, mipmap));
				return this;
			}

			public RenderPhase.Textures build() {
				return new RenderPhase.Textures(this.textures.build());
			}
		}

		@Environment(EnvType.CLIENT)
		record TextureEntry(Identifier id, boolean blur, boolean mipmap) {
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Texturing extends RenderPhase {
		public Texturing(String string, Runnable runnable, Runnable runnable2) {
			super(string, runnable, runnable2);
		}
	}

	@Environment(EnvType.CLIENT)
	static class Toggleable extends RenderPhase {
		private final boolean enabled;

		public Toggleable(String name, Runnable apply, Runnable unapply, boolean enabled) {
			super(name, apply, unapply);
			this.enabled = enabled;
		}

		@Override
		public String toString() {
			return this.name + "[" + this.enabled + "]";
		}
	}
}
