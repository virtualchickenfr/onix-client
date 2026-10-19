package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface MultiLineLabel {
	MultiLineLabel EMPTY = new MultiLineLabel() {
		@Override
		public void renderCentered(GuiGraphics guiGraphics, int i, int j) {
		}

		@Override
		public void renderCentered(GuiGraphics guiGraphics, int i, int j, int k, int l) {
		}

		@Override
		public void renderLeftAligned(GuiGraphics guiGraphics, int i, int j, int k, int l) {
		}

		@Override
		public int renderLeftAlignedNoShadow(GuiGraphics guiGraphics, int i, int j, int k, int l) {
			return j;
		}

		@Nullable
		@Override
		public Style getStyleAtCentered(int i, int j, int k, double d, double e) {
			return null;
		}

		@Nullable
		@Override
		public Style getStyleAtLeftAligned(int i, int j, int k, double d, double e) {
			return null;
		}

		@Override
		public int getLineCount() {
			return 0;
		}

		@Override
		public int getWidth() {
			return 0;
		}
	};

	static MultiLineLabel create(Font font, Component... components) {
		return create(font, Integer.MAX_VALUE, Integer.MAX_VALUE, components);
	}

	static MultiLineLabel create(Font font, int i, Component... components) {
		return create(font, i, Integer.MAX_VALUE, components);
	}

	static MultiLineLabel create(Font font, Component component, int i) {
		return create(font, i, Integer.MAX_VALUE, component);
	}

	static MultiLineLabel create(Font font, int i, int j, Component... components) {
		return components.length == 0 ? EMPTY : new MultiLineLabel() {
			@Nullable
			private List<MultiLineLabel.TextAndWidth> cachedTextAndWidth;
			@Nullable
			private Language splitWithLanguage;

			@Override
			public void renderCentered(GuiGraphics guiGraphics, int i, int j) {
				this.renderCentered(guiGraphics, i, j, 9, -1);
			}

			@Override
			public void renderCentered(GuiGraphics guiGraphics, int i, int j, int k, int l) {
				int m = j;

				for (MultiLineLabel.TextAndWidth textAndWidth : this.getSplitMessage()) {
					guiGraphics.drawString(font, textAndWidth.text, i - textAndWidth.width / 2, m, l);
					m += k;
				}
			}

			@Override
			public void renderLeftAligned(GuiGraphics guiGraphics, int i, int j, int k, int l) {
				int m = j;

				for (MultiLineLabel.TextAndWidth textAndWidth : this.getSplitMessage()) {
					guiGraphics.drawString(font, textAndWidth.text, i, m, l);
					m += k;
				}
			}

			@Override
			public int renderLeftAlignedNoShadow(GuiGraphics guiGraphics, int i, int j, int k, int l) {
				int m = j;

				for (MultiLineLabel.TextAndWidth textAndWidth : this.getSplitMessage()) {
					guiGraphics.drawString(font, textAndWidth.text, i, m, l, false);
					m += k;
				}

				return m;
			}

			@Nullable
			@Override
			public Style getStyleAtCentered(int i, int j, int k, double d, double e) {
				List<MultiLineLabel.TextAndWidth> list = this.getSplitMessage();
				int l = Mth.floor((e - j) / k);
				if (l >= 0 && l < list.size()) {
					MultiLineLabel.TextAndWidth textAndWidth = list.get(l);
					int m = i - textAndWidth.width / 2;
					if (d < m) {
						return null;
					}

					int n = Mth.floor(d - m);
					return font.getSplitter().componentStyleAtWidth(textAndWidth.text, n);
				} else {
					return null;
				}
			}

			@Nullable
			@Override
			public Style getStyleAtLeftAligned(int i, int j, int k, double d, double e) {
				if (d < i) {
					return null;
				} else {
					List<MultiLineLabel.TextAndWidth> list = this.getSplitMessage();
					int l = Mth.floor((e - j) / k);
					if (l >= 0 && l < list.size()) {
						MultiLineLabel.TextAndWidth textAndWidth = list.get(l);
						int m = Mth.floor(d - i);
						return font.getSplitter().componentStyleAtWidth(textAndWidth.text, m);
					} else {
						return null;
					}
				}
			}

			private List<MultiLineLabel.TextAndWidth> getSplitMessage() {
				Language language = Language.getInstance();
				if (this.cachedTextAndWidth != null && language == this.splitWithLanguage) {
					return this.cachedTextAndWidth;
				}

				this.splitWithLanguage = language;
				List<FormattedText> list = new ArrayList<>();

				for (Component component : components) {
					list.addAll(font.splitIgnoringLanguage(component, i));
				}

				this.cachedTextAndWidth = new ArrayList<>();
				int ix = Math.min(list.size(), j);
				List<FormattedText> list2 = list.subList(0, ix);

				for (int jx = 0; jx < list2.size(); jx++) {
					FormattedText formattedText = list2.get(jx);
					FormattedCharSequence formattedCharSequence = Language.getInstance().getVisualOrder(formattedText);
					if (jx == list2.size() - 1 && ix == j && ix != list.size()) {
						FormattedText formattedText2 = font.substrByWidth(formattedText, font.width(formattedText) - font.width(CommonComponents.ELLIPSIS));
						FormattedText formattedText3 = FormattedText.composite(formattedText2, CommonComponents.ELLIPSIS);
						this.cachedTextAndWidth.add(new MultiLineLabel.TextAndWidth(Language.getInstance().getVisualOrder(formattedText3), font.width(formattedText3)));
					} else {
						this.cachedTextAndWidth.add(new MultiLineLabel.TextAndWidth(formattedCharSequence, font.width(formattedCharSequence)));
					}
				}

				return this.cachedTextAndWidth;
			}

			@Override
			public int getLineCount() {
				return this.getSplitMessage().size();
			}

			@Override
			public int getWidth() {
				return Math.min(i, this.getSplitMessage().stream().mapToInt(MultiLineLabel.TextAndWidth::width).max().orElse(0));
			}
		};
	}

	void renderCentered(GuiGraphics guiGraphics, int i, int j);

	void renderCentered(GuiGraphics guiGraphics, int i, int j, int k, int l);

	void renderLeftAligned(GuiGraphics guiGraphics, int i, int j, int k, int l);

	int renderLeftAlignedNoShadow(GuiGraphics guiGraphics, int i, int j, int k, int l);

	@Nullable
	Style getStyleAtCentered(int i, int j, int k, double d, double e);

	@Nullable
	Style getStyleAtLeftAligned(int i, int j, int k, double d, double e);

	int getLineCount();

	int getWidth();

	@Environment(EnvType.CLIENT)
	record TextAndWidth(FormattedCharSequence text, int width) {
	}
}
