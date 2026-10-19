package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public abstract class AbstractScrollArea extends AbstractWidget {
	public static final int SCROLLBAR_WIDTH = 6;
	private double scrollAmount;
	private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller");
	private static final ResourceLocation SCROLLER_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller_background");
	private boolean scrolling;

	public AbstractScrollArea(int i, int j, int k, int l, Component component) {
		super(i, j, k, l, component);
	}

	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		if (!this.visible) {
			return false;
		}

		this.setScrollAmount(this.scrollAmount() - g * this.scrollRate());
		return true;
	}

	@Override
	public boolean mouseDragged(double d, double e, int i, double f, double g) {
		if (this.scrolling) {
			if (e < this.getY()) {
				this.setScrollAmount(0.0);
			} else if (e > this.getBottom()) {
				this.setScrollAmount(this.maxScrollAmount());
			} else {
				double h = Math.max(1, this.maxScrollAmount());
				int j = this.scrollerHeight();
				double k = Math.max(1.0, h / (this.height - j));
				this.setScrollAmount(this.scrollAmount() + g * k);
			}

			return true;
		} else {
			return super.mouseDragged(d, e, i, f, g);
		}
	}

	@Override
	public void onRelease(double d, double e) {
		this.scrolling = false;
	}

	public double scrollAmount() {
		return this.scrollAmount;
	}

	public void setScrollAmount(double d) {
		this.scrollAmount = Mth.clamp(d, 0.0, this.maxScrollAmount());
	}

	public boolean updateScrolling(double d, double e, int i) {
		this.scrolling = this.scrollbarVisible()
			&& this.isValidClickButton(i)
			&& d >= this.scrollBarX()
			&& d <= this.scrollBarX() + 6
			&& e >= this.getY()
			&& e < this.getBottom();
		return this.scrolling;
	}

	public void refreshScrollAmount() {
		this.setScrollAmount(this.scrollAmount);
	}

	public int maxScrollAmount() {
		return Math.max(0, this.contentHeight() - this.height);
	}

	protected boolean scrollbarVisible() {
		return this.maxScrollAmount() > 0;
	}

	protected int scrollerHeight() {
		return Mth.clamp((int)((float)(this.height * this.height) / this.contentHeight()), 32, this.height - 8);
	}

	protected int scrollBarX() {
		return this.getRight() - 6;
	}

	protected int scrollBarY() {
		return Math.max(this.getY(), (int)this.scrollAmount * (this.height - this.scrollerHeight()) / this.maxScrollAmount() + this.getY());
	}

	protected void renderScrollbar(GuiGraphics guiGraphics) {
		if (this.scrollbarVisible()) {
			int i = this.scrollBarX();
			int j = this.scrollerHeight();
			int k = this.scrollBarY();
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND_SPRITE, i, this.getY(), 6, this.getHeight());
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE, i, k, 6, j);
		}
	}

	protected abstract int contentHeight();

	protected abstract double scrollRate();
}
