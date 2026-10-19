package net.minecraft.client.gui.components;

import java.util.OptionalInt;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.SingleKeyCache;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class MultiLineTextWidget extends AbstractStringWidget {
	private OptionalInt maxWidth = OptionalInt.empty();
	private OptionalInt maxRows = OptionalInt.empty();
	private final SingleKeyCache<MultiLineTextWidget.CacheKey, MultiLineLabel> cache;
	private boolean centered = false;
	private boolean allowHoverComponents = false;
	@Nullable
	private Consumer<Style> componentClickHandler = null;

	public MultiLineTextWidget(Component component, Font font) {
		this(0, 0, component, font);
	}

	public MultiLineTextWidget(int i, int j, Component component, Font font) {
		super(i, j, 0, 0, component, font);
		this.cache = Util.singleKeyCache(
			cacheKey -> cacheKey.maxRows.isPresent()
				? MultiLineLabel.create(font, cacheKey.maxWidth, cacheKey.maxRows.getAsInt(), cacheKey.message)
				: MultiLineLabel.create(font, cacheKey.message, cacheKey.maxWidth)
		);
		this.active = false;
	}

	public MultiLineTextWidget setColor(int i) {
		super.setColor(i);
		return this;
	}

	public MultiLineTextWidget setMaxWidth(int i) {
		this.maxWidth = OptionalInt.of(i);
		return this;
	}

	public MultiLineTextWidget setMaxRows(int i) {
		this.maxRows = OptionalInt.of(i);
		return this;
	}

	public MultiLineTextWidget setCentered(boolean bl) {
		this.centered = bl;
		return this;
	}

	public MultiLineTextWidget configureStyleHandling(boolean bl, @Nullable Consumer<Style> consumer) {
		this.allowHoverComponents = bl;
		this.componentClickHandler = consumer;
		return this;
	}

	@Override
	public int getWidth() {
		return this.cache.getValue(this.getFreshCacheKey()).getWidth();
	}

	@Override
	public int getHeight() {
		return this.cache.getValue(this.getFreshCacheKey()).getLineCount() * 9;
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		MultiLineLabel multiLineLabel = this.cache.getValue(this.getFreshCacheKey());
		int k = this.getX();
		int l = this.getY();
		int m = 9;
		int n = this.getColor();
		if (this.centered) {
			multiLineLabel.renderCentered(guiGraphics, k + this.getWidth() / 2, l, m, n);
		} else {
			multiLineLabel.renderLeftAligned(guiGraphics, k, l, m, n);
		}

		if (this.allowHoverComponents) {
			Style style = this.getComponentStyleAt(i, j);
			if (this.isHovered()) {
				guiGraphics.renderComponentHoverEffect(this.getFont(), style, i, j);
			}
		}
	}

	@Nullable
	private Style getComponentStyleAt(double d, double e) {
		MultiLineLabel multiLineLabel = this.cache.getValue(this.getFreshCacheKey());
		int i = this.getX();
		int j = this.getY();
		int k = 9;
		return this.centered ? multiLineLabel.getStyleAtCentered(i + this.getWidth() / 2, j, k, d, e) : multiLineLabel.getStyleAtLeftAligned(i, j, k, d, e);
	}

	@Override
	public void onClick(double d, double e) {
		if (this.componentClickHandler != null) {
			Style style = this.getComponentStyleAt(d, e);
			if (style != null) {
				this.componentClickHandler.accept(style);
				return;
			}
		}

		super.onClick(d, e);
	}

	private MultiLineTextWidget.CacheKey getFreshCacheKey() {
		return new MultiLineTextWidget.CacheKey(this.getMessage(), this.maxWidth.orElse(Integer.MAX_VALUE), this.maxRows);
	}

	@Environment(EnvType.CLIENT)
	record CacheKey(Component message, int maxWidth, OptionalInt maxRows) {
	}
}
