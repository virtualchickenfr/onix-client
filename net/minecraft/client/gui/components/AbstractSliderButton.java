package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public abstract class AbstractSliderButton extends AbstractWidget {
	private static final ResourceLocation SLIDER_SPRITE = ResourceLocation.withDefaultNamespace("widget/slider");
	private static final ResourceLocation HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("widget/slider_highlighted");
	private static final ResourceLocation SLIDER_HANDLE_SPRITE = ResourceLocation.withDefaultNamespace("widget/slider_handle");
	private static final ResourceLocation SLIDER_HANDLE_HIGHLIGHTED_SPRITE = ResourceLocation.withDefaultNamespace("widget/slider_handle_highlighted");
	protected static final int TEXT_MARGIN = 2;
	public static final int DEFAULT_HEIGHT = 20;
	private static final int HANDLE_WIDTH = 8;
	private static final int HANDLE_HALF_WIDTH = 4;
	protected double value;
	private boolean canChangeValue;

	public AbstractSliderButton(int i, int j, int k, int l, Component component, double d) {
		super(i, j, k, l, component);
		this.value = d;
	}

	private ResourceLocation getSprite() {
		return this.isActive() && this.isFocused() && !this.canChangeValue ? HIGHLIGHTED_SPRITE : SLIDER_SPRITE;
	}

	private ResourceLocation getHandleSprite() {
		return !this.isActive() || !this.isHovered && !this.canChangeValue ? SLIDER_HANDLE_SPRITE : SLIDER_HANDLE_HIGHLIGHTED_SPRITE;
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		return Component.translatable("gui.narrate.slider", this.getMessage());
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		narrationElementOutput.add(NarratedElementType.TITLE, this.createNarrationMessage());
		if (this.active) {
			if (this.isFocused()) {
				narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.focused"));
			} else {
				narrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.hovered"));
			}
		}
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		Minecraft minecraft = Minecraft.getInstance();
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.getSprite(), this.getX(), this.getY(), this.getWidth(), this.getHeight(), ARGB.white(this.alpha));
		guiGraphics.blitSprite(
			RenderPipelines.GUI_TEXTURED,
			this.getHandleSprite(),
			this.getX() + (int)(this.value * (this.width - 8)),
			this.getY(),
			8,
			this.getHeight(),
			ARGB.white(this.alpha)
		);
		int k = ARGB.color(this.alpha, this.active ? -1 : -6250336);
		this.renderScrollingString(guiGraphics, minecraft.font, 2, k);
	}

	@Override
	public void onClick(double d, double e) {
		this.setValueFromMouse(d);
	}

	@Override
	public void setFocused(boolean bl) {
		super.setFocused(bl);
		if (!bl) {
			this.canChangeValue = false;
		} else {
			InputType inputType = Minecraft.getInstance().getLastInputType();
			if (inputType == InputType.MOUSE || inputType == InputType.KEYBOARD_TAB) {
				this.canChangeValue = true;
			}
		}
	}

	@Override
	public boolean keyPressed(int i, int j, int k) {
		if (CommonInputs.selected(i)) {
			this.canChangeValue = !this.canChangeValue;
			return true;
		}

		if (this.canChangeValue) {
			boolean bl = i == 263;
			if (bl || i == 262) {
				float f = bl ? -1.0F : 1.0F;
				this.setValue(this.value + f / (this.width - 8));
				return true;
			}
		}

		return false;
	}

	private void setValueFromMouse(double d) {
		this.setValue((d - (this.getX() + 4)) / (this.width - 8));
	}

	private void setValue(double d) {
		double e = this.value;
		this.value = Mth.clamp(d, 0.0, 1.0);
		if (e != this.value) {
			this.applyValue();
		}

		this.updateMessage();
	}

	@Override
	protected void onDrag(double d, double e, double f, double g) {
		this.setValueFromMouse(d);
		super.onDrag(d, e, f, g);
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
	}

	@Override
	public void onRelease(double d, double e) {
		super.playDownSound(Minecraft.getInstance().getSoundManager());
	}

	protected abstract void updateMessage();

	protected abstract void applyValue();
}
