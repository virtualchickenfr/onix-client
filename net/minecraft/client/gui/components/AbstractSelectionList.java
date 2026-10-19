package net.minecraft.client.gui.components;

import com.google.common.collect.Lists;
import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractSelectionList<E extends AbstractSelectionList.Entry<E>> extends AbstractContainerWidget {
	private static final ResourceLocation MENU_LIST_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/menu_list_background.png");
	private static final ResourceLocation INWORLD_MENU_LIST_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
	protected final Minecraft minecraft;
	protected final int itemHeight;
	private final List<E> children = new AbstractSelectionList.TrackedList();
	protected boolean centerListVertically = true;
	private boolean renderHeader;
	protected int headerHeight;
	@Nullable
	private E selected;
	@Nullable
	private E hovered;

	public AbstractSelectionList(Minecraft minecraft, int i, int j, int k, int l) {
		super(0, k, i, j, CommonComponents.EMPTY);
		this.minecraft = minecraft;
		this.itemHeight = l;
	}

	public AbstractSelectionList(Minecraft minecraft, int i, int j, int k, int l, int m) {
		this(minecraft, i, j, k, l);
		this.renderHeader = true;
		this.headerHeight = m;
	}

	@Nullable
	public E getSelected() {
		return this.selected;
	}

	public void setSelectedIndex(int i) {
		if (i == -1) {
			this.setSelected(null);
		} else if (this.getItemCount() != 0) {
			this.setSelected(this.getEntry(i));
		}
	}

	public void setSelected(@Nullable E entry) {
		this.selected = entry;
	}

	public E getFirstElement() {
		return this.children.get(0);
	}

	@Nullable
	public E getFocused() {
		return (E)super.getFocused();
	}

	@Override
	public final List<E> children() {
		return this.children;
	}

	protected void clearEntries() {
		this.children.clear();
		this.selected = null;
	}

	public void replaceEntries(Collection<E> collection) {
		this.clearEntries();
		this.children.addAll(collection);
	}

	protected E getEntry(int i) {
		return this.children().get(i);
	}

	protected int addEntry(E entry) {
		this.children.add(entry);
		return this.children.size() - 1;
	}

	protected void addEntryToTop(E entry) {
		double d = this.maxScrollAmount() - this.scrollAmount();
		this.children.add(0, entry);
		this.setScrollAmount(this.maxScrollAmount() - d);
	}

	protected boolean removeEntryFromTop(E entry) {
		double d = this.maxScrollAmount() - this.scrollAmount();
		boolean bl = this.removeEntry(entry);
		this.setScrollAmount(this.maxScrollAmount() - d);
		return bl;
	}

	protected int getItemCount() {
		return this.children().size();
	}

	protected boolean isSelectedItem(int i) {
		return Objects.equals(this.getSelected(), this.children().get(i));
	}

	@Nullable
	protected final E getEntryAtPosition(double d, double e) {
		int i = this.getRowWidth() / 2;
		int j = this.getX() + this.width / 2;
		int k = j - i;
		int l = j + i;
		int m = Mth.floor(e - this.getY()) - this.headerHeight + (int)this.scrollAmount() - 4;
		int n = m / this.itemHeight;
		return d >= k && d <= l && n >= 0 && m >= 0 && n < this.getItemCount() ? this.children().get(n) : null;
	}

	public void updateSize(int i, HeaderAndFooterLayout headerAndFooterLayout) {
		this.updateSizeAndPosition(i, headerAndFooterLayout.getContentHeight(), headerAndFooterLayout.getHeaderHeight());
	}

	public void updateSizeAndPosition(int i, int j, int k) {
		this.setSize(i, j);
		this.setPosition(0, k);
		this.refreshScrollAmount();
	}

	@Override
	protected int contentHeight() {
		return this.getItemCount() * this.itemHeight + this.headerHeight + 4;
	}

	protected void renderHeader(GuiGraphics guiGraphics, int i, int j) {
	}

	protected void renderDecorations(GuiGraphics guiGraphics, int i, int j) {
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		this.hovered = this.isMouseOver(i, j) ? this.getEntryAtPosition(i, j) : null;
		this.renderListBackground(guiGraphics);
		this.enableScissor(guiGraphics);
		if (this.renderHeader) {
			int k = this.getRowLeft();
			int l = this.getY() + 4 - (int)this.scrollAmount();
			this.renderHeader(guiGraphics, k, l);
		}

		this.renderListItems(guiGraphics, i, j, f);
		guiGraphics.disableScissor();
		this.renderListSeparators(guiGraphics);
		this.renderScrollbar(guiGraphics);
		this.renderDecorations(guiGraphics, i, j);
	}

	protected void renderListSeparators(GuiGraphics guiGraphics) {
		ResourceLocation resourceLocation = this.minecraft.level == null ? Screen.HEADER_SEPARATOR : Screen.INWORLD_HEADER_SEPARATOR;
		ResourceLocation resourceLocation2 = this.minecraft.level == null ? Screen.FOOTER_SEPARATOR : Screen.INWORLD_FOOTER_SEPARATOR;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, resourceLocation, this.getX(), this.getY() - 2, 0.0F, 0.0F, this.getWidth(), 2, 32, 2);
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, resourceLocation2, this.getX(), this.getBottom(), 0.0F, 0.0F, this.getWidth(), 2, 32, 2);
	}

	protected void renderListBackground(GuiGraphics guiGraphics) {
		ResourceLocation resourceLocation = this.minecraft.level == null ? MENU_LIST_BACKGROUND : INWORLD_MENU_LIST_BACKGROUND;
		guiGraphics.blit(
			RenderPipelines.GUI_TEXTURED,
			resourceLocation,
			this.getX(),
			this.getY(),
			this.getRight(),
			this.getBottom() + (int)this.scrollAmount(),
			this.getWidth(),
			this.getHeight(),
			32,
			32
		);
	}

	protected void enableScissor(GuiGraphics guiGraphics) {
		guiGraphics.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());
	}

	protected void centerScrollOn(E entry) {
		this.setScrollAmount(this.children().indexOf(entry) * this.itemHeight + this.itemHeight / 2 - this.height / 2);
	}

	protected void ensureVisible(E entry) {
		int i = this.getRowTop(this.children().indexOf(entry));
		int j = i - this.getY() - 4 - this.itemHeight;
		if (j < 0) {
			this.scroll(j);
		}

		int k = this.getBottom() - i - this.itemHeight - this.itemHeight;
		if (k < 0) {
			this.scroll(-k);
		}
	}

	private void scroll(int i) {
		this.setScrollAmount(this.scrollAmount() + i);
	}

	@Override
	protected double scrollRate() {
		return this.itemHeight / 2.0;
	}

	@Override
	protected int scrollBarX() {
		return this.getRowRight() + 6 + 2;
	}

	@Override
	public Optional<GuiEventListener> getChildAt(double d, double e) {
		return Optional.ofNullable(this.getEntryAtPosition(d, e));
	}

	@Override
	public void setFocused(@Nullable GuiEventListener guiEventListener) {
		E entry = this.getFocused();
		if (entry != guiEventListener && entry instanceof ContainerEventHandler containerEventHandler) {
			containerEventHandler.setFocused(null);
		}

		super.setFocused(guiEventListener);
		int i = this.children.indexOf(guiEventListener);
		if (i >= 0) {
			E entry2 = this.children.get(i);
			this.setSelected(entry2);
			if (this.minecraft.getLastInputType().isKeyboard()) {
				this.ensureVisible(entry2);
			}
		}
	}

	@Nullable
	protected E nextEntry(ScreenDirection screenDirection) {
		return this.nextEntry(screenDirection, entry -> true);
	}

	@Nullable
	protected E nextEntry(ScreenDirection screenDirection, Predicate<E> predicate) {
		return this.nextEntry(screenDirection, predicate, this.getSelected());
	}

	@Nullable
	protected E nextEntry(ScreenDirection screenDirection, Predicate<E> predicate, @Nullable E entry) {
		int i = switch (screenDirection) {
			case RIGHT, LEFT -> 0;
			case UP -> -1;
			case DOWN -> 1;
		};
		if (!this.children().isEmpty() && i != 0) {
			int j;
			if (entry == null) {
				j = i > 0 ? 0 : this.children().size() - 1;
			} else {
				j = this.children().indexOf(entry) + i;
			}

			for (int k = j; k >= 0 && k < this.children.size(); k += i) {
				E entry2 = this.children().get(k);
				if (predicate.test(entry2)) {
					return entry2;
				}
			}
		}

		return null;
	}

	protected void renderListItems(GuiGraphics guiGraphics, int i, int j, float f) {
		int k = this.getRowLeft();
		int l = this.getRowWidth();
		int m = this.itemHeight - 4;
		int n = this.getItemCount();

		for (int o = 0; o < n; o++) {
			int p = this.getRowTop(o);
			int q = this.getRowBottom(o);
			if (q >= this.getY() && p <= this.getBottom()) {
				this.renderItem(guiGraphics, i, j, f, o, k, p, l, m);
			}
		}
	}

	protected void renderItem(GuiGraphics guiGraphics, int i, int j, float f, int k, int l, int m, int n, int o) {
		E entry = this.getEntry(k);
		entry.renderBack(guiGraphics, k, m, l, n, o, i, j, Objects.equals(this.hovered, entry), f);
		if (this.isSelectedItem(k)) {
			int p = this.isFocused() ? -1 : -8355712;
			this.renderSelection(guiGraphics, m, n, o, p, -16777216);
		}

		entry.render(guiGraphics, k, m, l, n, o, i, j, Objects.equals(this.hovered, entry), f);
	}

	protected void renderSelection(GuiGraphics guiGraphics, int i, int j, int k, int l, int m) {
		int n = this.getX() + (this.width - j) / 2;
		int o = this.getX() + (this.width + j) / 2;
		guiGraphics.fill(n, i - 2, o, i + k + 2, l);
		guiGraphics.fill(n + 1, i - 1, o - 1, i + k + 1, m);
	}

	public int getRowLeft() {
		return this.getX() + this.width / 2 - this.getRowWidth() / 2 + 2;
	}

	public int getRowRight() {
		return this.getRowLeft() + this.getRowWidth();
	}

	public int getRowTop(int i) {
		return this.getY() + 4 - (int)this.scrollAmount() + i * this.itemHeight + this.headerHeight;
	}

	public int getRowBottom(int i) {
		return this.getRowTop(i) + this.itemHeight;
	}

	public int getRowWidth() {
		return 220;
	}

	@Override
	public NarratableEntry.NarrationPriority narrationPriority() {
		if (this.isFocused()) {
			return NarratableEntry.NarrationPriority.FOCUSED;
		} else {
			return this.hovered != null ? NarratableEntry.NarrationPriority.HOVERED : NarratableEntry.NarrationPriority.NONE;
		}
	}

	@Nullable
	protected E remove(int i) {
		E entry = this.children.get(i);
		return this.removeEntry(this.children.get(i)) ? entry : null;
	}

	protected boolean removeEntry(E entry) {
		boolean bl = this.children.remove(entry);
		if (bl && entry == this.getSelected()) {
			this.setSelected(null);
		}

		return bl;
	}

	@Nullable
	protected E getHovered() {
		return this.hovered;
	}

	void bindEntryToSelf(AbstractSelectionList.Entry<E> entry) {
		entry.list = this;
	}

	protected void narrateListElementPosition(NarrationElementOutput narrationElementOutput, E entry) {
		List<E> list = this.children();
		if (list.size() > 1) {
			int i = list.indexOf(entry);
			if (i != -1) {
				narrationElementOutput.add(NarratedElementType.POSITION, Component.translatable("narrator.position.list", i + 1, list.size()));
			}
		}
	}

	@Environment(EnvType.CLIENT)
	protected abstract static class Entry<E extends AbstractSelectionList.Entry<E>> implements GuiEventListener {
		@Deprecated
		AbstractSelectionList<E> list;

		@Override
		public void setFocused(boolean bl) {
		}

		@Override
		public boolean isFocused() {
			return this.list.getFocused() == this;
		}

		public abstract void render(GuiGraphics guiGraphics, int i, int j, int k, int l, int m, int n, int o, boolean bl, float f);

		public void renderBack(GuiGraphics guiGraphics, int i, int j, int k, int l, int m, int n, int o, boolean bl, float f) {
		}

		@Override
		public boolean isMouseOver(double d, double e) {
			return Objects.equals(this.list.getEntryAtPosition(d, e), this);
		}
	}

	@Environment(EnvType.CLIENT)
	class TrackedList extends AbstractList<E> {
		private final List<E> delegate = Lists.newArrayList();

		public E get(int i) {
			return this.delegate.get(i);
		}

		@Override
		public int size() {
			return this.delegate.size();
		}

		public E set(int i, E entry) {
			E entry2 = this.delegate.set(i, entry);
			AbstractSelectionList.this.bindEntryToSelf(entry);
			return entry2;
		}

		public void add(int i, E entry) {
			this.delegate.add(i, entry);
			AbstractSelectionList.this.bindEntryToSelf(entry);
		}

		public E remove(int i) {
			return this.delegate.remove(i);
		}
	}
}
