package net.minecraft.client.gui;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringDecomposer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class Font {
	private static final float EFFECT_DEPTH = 0.001F;
	private static final float OVER_EFFECT_DEPTH = 0.001F;
	private static final float UNDER_EFFECT_DEPTH = -0.001F;
	public static final float SHADOW_DEPTH = 0.003F;
	public static final int NO_SHADOW = 0;
	public final int lineHeight = 9;
	public final RandomSource random = RandomSource.create();
	private final Function<ResourceLocation, FontSet> fonts;
	final boolean filterFishyGlyphs;
	private final StringSplitter splitter;

	public Font(Function<ResourceLocation, FontSet> function, boolean bl) {
		this.fonts = function;
		this.filterFishyGlyphs = bl;
		this.splitter = new StringSplitter((i, style) -> this.getFontSet(style.getFont()).getGlyphInfo(i, this.filterFishyGlyphs).getAdvance(style.isBold()));
	}

	FontSet getFontSet(ResourceLocation resourceLocation) {
		return this.fonts.apply(resourceLocation);
	}

	public String bidirectionalShaping(String string) {
		try {
			Bidi bidi = new Bidi(new ArabicShaping(8).shape(string), 127);
			bidi.setReorderingMode(0);
			return bidi.writeReordered(2);
		} catch (ArabicShapingException var3) {
			return string;
		}
	}

	public void drawInBatch(
		String string, float f, float g, int i, boolean bl, Matrix4f matrix4f, MultiBufferSource multiBufferSource, Font.DisplayMode displayMode, int j, int k
	) {
		Font.PreparedText preparedText = this.prepareText(string, f, g, i, bl, j);
		preparedText.visit(Font.GlyphVisitor.forMultiBufferSource(multiBufferSource, matrix4f, displayMode, k));
	}

	public void drawInBatch(
		Component component, float f, float g, int i, boolean bl, Matrix4f matrix4f, MultiBufferSource multiBufferSource, Font.DisplayMode displayMode, int j, int k
	) {
		Font.PreparedText preparedText = this.prepareText(component.getVisualOrderText(), f, g, i, bl, j);
		preparedText.visit(Font.GlyphVisitor.forMultiBufferSource(multiBufferSource, matrix4f, displayMode, k));
	}

	public void drawInBatch(
		FormattedCharSequence formattedCharSequence,
		float f,
		float g,
		int i,
		boolean bl,
		Matrix4f matrix4f,
		MultiBufferSource multiBufferSource,
		Font.DisplayMode displayMode,
		int j,
		int k
	) {
		Font.PreparedText preparedText = this.prepareText(formattedCharSequence, f, g, i, bl, j);
		preparedText.visit(Font.GlyphVisitor.forMultiBufferSource(multiBufferSource, matrix4f, displayMode, k));
	}

	public void drawInBatch8xOutline(
		FormattedCharSequence formattedCharSequence, float f, float g, int i, int j, Matrix4f matrix4f, MultiBufferSource multiBufferSource, int k
	) {
		Font.PreparedTextBuilder preparedTextBuilder = new Font.PreparedTextBuilder(0.0F, 0.0F, j, false);

		for (int l = -1; l <= 1; l++) {
			for (int m = -1; m <= 1; m++) {
				if (l != 0 || m != 0) {
					float[] fs = new float[]{f};
					int n = l;
					int o = m;
					formattedCharSequence.accept((lx, style, mx) -> {
						boolean bl = style.isBold();
						FontSet fontSet = this.getFontSet(style.getFont());
						GlyphInfo glyphInfo = fontSet.getGlyphInfo(mx, this.filterFishyGlyphs);
						preparedTextBuilder.x = fs[0] + n * glyphInfo.getShadowOffset();
						preparedTextBuilder.y = g + o * glyphInfo.getShadowOffset();
						fs[0] += glyphInfo.getAdvance(bl);
						return preparedTextBuilder.accept(lx, style.withColor(j), mx);
					});
				}
			}
		}

		Font.GlyphVisitor glyphVisitor = Font.GlyphVisitor.forMultiBufferSource(multiBufferSource, matrix4f, Font.DisplayMode.NORMAL, k);

		for (BakedGlyph.GlyphInstance glyphInstance : preparedTextBuilder.glyphs) {
			glyphVisitor.acceptGlyph(glyphInstance);
		}

		Font.PreparedTextBuilder preparedTextBuilder2 = new Font.PreparedTextBuilder(f, g, i, false);
		formattedCharSequence.accept(preparedTextBuilder2);
		preparedTextBuilder2.visit(Font.GlyphVisitor.forMultiBufferSource(multiBufferSource, matrix4f, Font.DisplayMode.POLYGON_OFFSET, k));
	}

	public Font.PreparedText prepareText(String string, float f, float g, int i, boolean bl, int j) {
		if (this.isBidirectional()) {
			string = this.bidirectionalShaping(string);
		}

		Font.PreparedTextBuilder preparedTextBuilder = new Font.PreparedTextBuilder(f, g, i, j, bl);
		StringDecomposer.iterateFormatted(string, Style.EMPTY, preparedTextBuilder);
		return preparedTextBuilder;
	}

	public Font.PreparedText prepareText(FormattedCharSequence formattedCharSequence, float f, float g, int i, boolean bl, int j) {
		Font.PreparedTextBuilder preparedTextBuilder = new Font.PreparedTextBuilder(f, g, i, j, bl);
		formattedCharSequence.accept(preparedTextBuilder);
		return preparedTextBuilder;
	}

	public int width(String string) {
		return Mth.ceil(this.splitter.stringWidth(string));
	}

	public int width(FormattedText formattedText) {
		return Mth.ceil(this.splitter.stringWidth(formattedText));
	}

	public int width(FormattedCharSequence formattedCharSequence) {
		return Mth.ceil(this.splitter.stringWidth(formattedCharSequence));
	}

	public String plainSubstrByWidth(String string, int i, boolean bl) {
		return bl ? this.splitter.plainTailByWidth(string, i, Style.EMPTY) : this.splitter.plainHeadByWidth(string, i, Style.EMPTY);
	}

	public String plainSubstrByWidth(String string, int i) {
		return this.splitter.plainHeadByWidth(string, i, Style.EMPTY);
	}

	public FormattedText substrByWidth(FormattedText formattedText, int i) {
		return this.splitter.headByWidth(formattedText, i, Style.EMPTY);
	}

	public int wordWrapHeight(String string, int i) {
		return 9 * this.splitter.splitLines(string, i, Style.EMPTY).size();
	}

	public int wordWrapHeight(FormattedText formattedText, int i) {
		return 9 * this.splitter.splitLines(formattedText, i, Style.EMPTY).size();
	}

	public List<FormattedCharSequence> split(FormattedText formattedText, int i) {
		return Language.getInstance().getVisualOrder(this.splitter.splitLines(formattedText, i, Style.EMPTY));
	}

	public List<FormattedText> splitIgnoringLanguage(FormattedText formattedText, int i) {
		return this.splitter.splitLines(formattedText, i, Style.EMPTY);
	}

	public boolean isBidirectional() {
		return Language.getInstance().isDefaultRightToLeft();
	}

	public StringSplitter getSplitter() {
		return this.splitter;
	}

	@Environment(EnvType.CLIENT)
	public enum DisplayMode {
		NORMAL,
		SEE_THROUGH,
		POLYGON_OFFSET;
	}

	@Environment(EnvType.CLIENT)
	public interface GlyphVisitor {
		static Font.GlyphVisitor forMultiBufferSource(MultiBufferSource multiBufferSource, Matrix4f matrix4f, Font.DisplayMode displayMode, int i) {
			return new Font.GlyphVisitor() {
				@Override
				public void acceptGlyph(BakedGlyph.GlyphInstance glyphInstance) {
					BakedGlyph bakedGlyph = glyphInstance.glyph();
					VertexConsumer vertexConsumer = multiBufferSource.getBuffer(bakedGlyph.renderType(displayMode));
					bakedGlyph.renderChar(glyphInstance, matrix4f, vertexConsumer, i, false);
				}

				@Override
				public void acceptEffect(BakedGlyph bakedGlyph, BakedGlyph.Effect effect) {
					VertexConsumer vertexConsumer = multiBufferSource.getBuffer(bakedGlyph.renderType(displayMode));
					bakedGlyph.renderEffect(effect, matrix4f, vertexConsumer, i, false);
				}
			};
		}

		void acceptGlyph(BakedGlyph.GlyphInstance glyphInstance);

		void acceptEffect(BakedGlyph bakedGlyph, BakedGlyph.Effect effect);
	}

	@Environment(EnvType.CLIENT)
	public interface PreparedText {
		void visit(Font.GlyphVisitor glyphVisitor);

		@Nullable
		ScreenRectangle bounds();
	}

	@Environment(EnvType.CLIENT)
	class PreparedTextBuilder implements FormattedCharSink, Font.PreparedText {
		private final boolean drawShadow;
		private final int color;
		private final int backgroundColor;
		float x;
		float y;
		private float left = Float.MAX_VALUE;
		private float top = Float.MAX_VALUE;
		private float right = -Float.MAX_VALUE;
		private float bottom = -Float.MAX_VALUE;
		private float backgroundLeft = Float.MAX_VALUE;
		private float backgroundTop = Float.MAX_VALUE;
		private float backgroundRight = -Float.MAX_VALUE;
		private float backgroundBottom = -Float.MAX_VALUE;
		final List<BakedGlyph.GlyphInstance> glyphs = new ArrayList<>();
		@Nullable
		private List<BakedGlyph.Effect> effects;

		public PreparedTextBuilder(final float f, final float g, final int i, final boolean bl) {
			this(f, g, i, 0, bl);
		}

		public PreparedTextBuilder(final float f, final float g, final int i, final int j, final boolean bl) {
			this.x = f;
			this.y = g;
			this.drawShadow = bl;
			this.color = i;
			this.backgroundColor = j;
			this.markBackground(f, g, 0.0F);
		}

		private void markSize(float f, float g, float h, float i) {
			this.left = Math.min(this.left, f);
			this.top = Math.min(this.top, g);
			this.right = Math.max(this.right, h);
			this.bottom = Math.max(this.bottom, i);
		}

		private void markBackground(float f, float g, float h) {
			if (ARGB.alpha(this.backgroundColor) != 0) {
				this.backgroundLeft = Math.min(this.backgroundLeft, f - 1.0F);
				this.backgroundTop = Math.min(this.backgroundTop, g - 1.0F);
				this.backgroundRight = Math.max(this.backgroundRight, f + h);
				this.backgroundBottom = Math.max(this.backgroundBottom, g + 9.0F);
				this.markSize(this.backgroundLeft, this.backgroundTop, this.backgroundRight, this.backgroundBottom);
			}
		}

		private void addGlyph(BakedGlyph.GlyphInstance glyphInstance) {
			this.glyphs.add(glyphInstance);
			this.markSize(glyphInstance.left(), glyphInstance.top(), glyphInstance.right(), glyphInstance.bottom());
		}

		private void addEffect(BakedGlyph.Effect effect) {
			if (this.effects == null) {
				this.effects = new ArrayList<>();
			}

			this.effects.add(effect);
			this.markSize(effect.left(), effect.top(), effect.right(), effect.bottom());
		}

		@Override
		public boolean accept(int i, Style style, int j) {
			FontSet fontSet = Font.this.getFontSet(style.getFont());
			GlyphInfo glyphInfo = fontSet.getGlyphInfo(j, Font.this.filterFishyGlyphs);
			BakedGlyph bakedGlyph = style.isObfuscated() && j != 32 ? fontSet.getRandomGlyph(glyphInfo) : fontSet.getGlyph(j);
			boolean bl = style.isBold();
			TextColor textColor = style.getColor();
			int k = this.getTextColor(textColor);
			int l = this.getShadowColor(style, k);
			float f = glyphInfo.getAdvance(bl);
			float g = i == 0 ? this.x - 1.0F : this.x;
			float h = glyphInfo.getShadowOffset();
			if (!(bakedGlyph instanceof EmptyGlyph)) {
				float m = bl ? glyphInfo.getBoldOffset() : 0.0F;
				this.addGlyph(new BakedGlyph.GlyphInstance(this.x, this.y, k, l, bakedGlyph, style, m, h));
			}

			this.markBackground(this.x, this.y, f);
			if (style.isStrikethrough()) {
				this.addEffect(new BakedGlyph.Effect(g, this.y + 4.5F - 1.0F, this.x + f, this.y + 4.5F, 0.001F, k, l, h));
			}

			if (style.isUnderlined()) {
				this.addEffect(new BakedGlyph.Effect(g, this.y + 9.0F - 1.0F, this.x + f, this.y + 9.0F, 0.001F, k, l, h));
			}

			this.x += f;
			return true;
		}

		@Override
		public void visit(Font.GlyphVisitor glyphVisitor) {
			BakedGlyph bakedGlyph = null;
			if (ARGB.alpha(this.backgroundColor) != 0) {
				BakedGlyph.Effect effect = new BakedGlyph.Effect(
					this.backgroundLeft, this.backgroundTop, this.backgroundRight, this.backgroundBottom, -0.001F, this.backgroundColor
				);
				bakedGlyph = Font.this.getFontSet(Style.DEFAULT_FONT).whiteGlyph();
				glyphVisitor.acceptEffect(bakedGlyph, effect);
			}

			for (BakedGlyph.GlyphInstance glyphInstance : this.glyphs) {
				glyphVisitor.acceptGlyph(glyphInstance);
			}

			if (this.effects != null) {
				if (bakedGlyph == null) {
					bakedGlyph = Font.this.getFontSet(Style.DEFAULT_FONT).whiteGlyph();
				}

				for (BakedGlyph.Effect effect2 : this.effects) {
					glyphVisitor.acceptEffect(bakedGlyph, effect2);
				}
			}
		}

		private int getTextColor(@Nullable TextColor textColor) {
			if (textColor != null) {
				int i = ARGB.alpha(this.color);
				int j = textColor.getValue();
				return ARGB.color(i, j);
			} else {
				return this.color;
			}
		}

		private int getShadowColor(Style style, int i) {
			Integer integer = style.getShadowColor();
			if (integer != null) {
				float f = ARGB.alphaFloat(i);
				float g = ARGB.alphaFloat(integer);
				return f != 1.0F ? ARGB.color(ARGB.as8BitChannel(f * g), integer) : integer;
			} else {
				return this.drawShadow ? ARGB.scaleRGB(i, 0.25F) : 0;
			}
		}

		@Nullable
		@Override
		public ScreenRectangle bounds() {
			if (!(this.left >= this.right) && !(this.top >= this.bottom)) {
				int i = Mth.floor(this.left);
				int j = Mth.floor(this.top);
				int k = Mth.ceil(this.right);
				int l = Mth.ceil(this.bottom);
				return new ScreenRectangle(i, j, k - i, l - j);
			} else {
				return null;
			}
		}
	}
}
