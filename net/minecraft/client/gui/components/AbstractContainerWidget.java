package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractContainerWidget extends AbstractScrollArea implements ContainerEventHandler {
	@Nullable
	private GuiEventListener focused;
	private boolean isDragging;

	public AbstractContainerWidget(int i, int j, int k, int l, Component component) {
		super(i, j, k, l, component);
	}

	@Override
	public final boolean isDragging() {
		return this.isDragging;
	}

	@Override
	public final void setDragging(boolean bl) {
		this.isDragging = bl;
	}

	@Nullable
	@Override
	public GuiEventListener getFocused() {
		return this.focused;
	}

	@Override
	public void setFocused(@Nullable GuiEventListener guiEventListener) {
		if (this.focused != null) {
			this.focused.setFocused(false);
		}

		if (guiEventListener != null) {
			guiEventListener.setFocused(true);
		}

		this.focused = guiEventListener;
	}

	@Nullable
	@Override
	public ComponentPath nextFocusPath(FocusNavigationEvent focusNavigationEvent) {
		return ContainerEventHandler.super.nextFocusPath(focusNavigationEvent);
	}

	@Override
	public boolean mouseClicked(double d, double e, int i) {
		boolean bl = this.updateScrolling(d, e, i);
		return ContainerEventHandler.super.mouseClicked(d, e, i) || bl;
	}

	@Override
	public boolean mouseReleased(double d, double e, int i) {
		super.mouseReleased(d, e, i);
		return ContainerEventHandler.super.mouseReleased(d, e, i);
	}

	@Override
	public boolean mouseDragged(double d, double e, int i, double f, double g) {
		super.mouseDragged(d, e, i, f, g);
		return ContainerEventHandler.super.mouseDragged(d, e, i, f, g);
	}

	@Override
	public boolean isFocused() {
		return ContainerEventHandler.super.isFocused();
	}

	@Override
	public void setFocused(boolean bl) {
		ContainerEventHandler.super.setFocused(bl);
	}
}
