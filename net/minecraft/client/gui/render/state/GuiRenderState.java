package net.minecraft.client.gui.render.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GuiRenderState {
	private static final int DEBUG_RECTANGLE_COLOR = 2000962815;
	private final List<GuiRenderState.Node> strata = new ArrayList<>();
	private int firstStratumAfterBlur = Integer.MAX_VALUE;
	private GuiRenderState.Node current;
	private final Set<Object> itemModelIdentities = new HashSet<>();
	@Nullable
	private ScreenRectangle lastElementBounds;

	public GuiRenderState() {
		this.nextStratum();
	}

	public void nextStratum() {
		this.current = new GuiRenderState.Node(null);
		this.strata.add(this.current);
	}

	public void blurBeforeThisStratum() {
		if (this.firstStratumAfterBlur != Integer.MAX_VALUE) {
			throw new IllegalStateException("Can only blur once per frame");
		}

		this.firstStratumAfterBlur = this.strata.size() - 1;
	}

	public void up() {
		if (this.current.up == null) {
			this.current.up = new GuiRenderState.Node(this.current);
		}

		this.current = this.current.up;
	}

	public void down() {
		if (this.current.down == null) {
			this.current.down = new GuiRenderState.Node(this.current);
		}

		this.current = this.current.down;
	}

	public void submitItem(GuiItemRenderState guiItemRenderState) {
		if (this.findAppropriateNode(guiItemRenderState)) {
			this.itemModelIdentities.add(guiItemRenderState.itemStackRenderState().getModelIdentity());
			this.current.submitItem(guiItemRenderState);
			this.sumbitDebugRectangleIfEnabled(guiItemRenderState.bounds());
		}
	}

	public void submitText(GuiTextRenderState guiTextRenderState) {
		if (this.findAppropriateNode(guiTextRenderState)) {
			this.current.submitText(guiTextRenderState);
			this.sumbitDebugRectangleIfEnabled(guiTextRenderState.bounds());
		}
	}

	public void submitPicturesInPictureState(PictureInPictureRenderState pictureInPictureRenderState) {
		if (this.findAppropriateNode(pictureInPictureRenderState)) {
			this.current.submitPicturesInPictureState(pictureInPictureRenderState);
			this.sumbitDebugRectangleIfEnabled(pictureInPictureRenderState.bounds());
		}
	}

	public void submitGuiElement(GuiElementRenderState guiElementRenderState) {
		if (this.findAppropriateNode(guiElementRenderState)) {
			this.current.submitGuiElement(guiElementRenderState);
			this.sumbitDebugRectangleIfEnabled(guiElementRenderState.bounds());
		}
	}

	private void sumbitDebugRectangleIfEnabled(@Nullable ScreenRectangle screenRectangle) {
	}

	private boolean findAppropriateNode(ScreenArea screenArea) {
		ScreenRectangle screenRectangle = screenArea.bounds();
		if (screenRectangle == null) {
			return false;
		}

		if (this.lastElementBounds != null && this.lastElementBounds.encompasses(screenRectangle)) {
			this.up();
		} else {
			this.navigateToAboveHighestElementWithIntersectingBounds(screenRectangle);
		}

		this.lastElementBounds = screenRectangle;
		return true;
	}

	private void navigateToAboveHighestElementWithIntersectingBounds(ScreenRectangle screenRectangle) {
		GuiRenderState.Node node = this.strata.getLast();

		while (node.up != null) {
			node = node.up;
		}

		boolean bl = false;

		while (!bl) {
			bl = this.hasIntersection(screenRectangle, node.elementStates)
				|| this.hasIntersection(screenRectangle, node.itemStates)
				|| this.hasIntersection(screenRectangle, node.textStates)
				|| this.hasIntersection(screenRectangle, node.picturesInPictureStates);
			if (node.parent == null) {
				break;
			}

			if (!bl) {
				node = node.parent;
			}
		}

		this.current = node;
		if (bl) {
			this.up();
		}
	}

	private boolean hasIntersection(ScreenRectangle screenRectangle, @Nullable List<? extends ScreenArea> list) {
		if (list != null) {
			for (ScreenArea screenArea : list) {
				ScreenRectangle screenRectangle2 = screenArea.bounds();
				if (screenRectangle2 != null && screenRectangle2.intersects(screenRectangle)) {
					return true;
				}
			}
		}

		return false;
	}

	public void submitBlitToCurrentLayer(BlitRenderState blitRenderState) {
		this.current.submitGuiElement(blitRenderState);
	}

	public void submitGlyphToCurrentLayer(GuiElementRenderState guiElementRenderState) {
		this.current.submitGlyph(guiElementRenderState);
	}

	public Set<Object> getItemModelIdentities() {
		return this.itemModelIdentities;
	}

	public void forEachElement(GuiRenderState.LayeredElementConsumer layeredElementConsumer, GuiRenderState.TraverseRange traverseRange) {
		MutableInt mutableInt = new MutableInt(0);
		this.traverse(node -> {
			if (node.elementStates != null || node.glyphStates != null) {
				int i = mutableInt.incrementAndGet();
				if (node.elementStates != null) {
					for (GuiElementRenderState guiElementRenderState : node.elementStates) {
						layeredElementConsumer.accept(guiElementRenderState, i);
					}
				}

				if (node.glyphStates != null) {
					for (GuiElementRenderState guiElementRenderState : node.glyphStates) {
						layeredElementConsumer.accept(guiElementRenderState, i);
					}
				}
			}
		}, traverseRange);
	}

	public void forEachItem(Consumer<GuiItemRenderState> consumer) {
		GuiRenderState.Node node = this.current;
		this.traverse(nodex -> {
			if (nodex.itemStates != null) {
				this.current = nodex;

				for (GuiItemRenderState guiItemRenderState : nodex.itemStates) {
					consumer.accept(guiItemRenderState);
				}
			}
		}, GuiRenderState.TraverseRange.ALL);
		this.current = node;
	}

	public void forEachText(Consumer<GuiTextRenderState> consumer) {
		GuiRenderState.Node node = this.current;
		this.traverse(nodex -> {
			if (nodex.textStates != null) {
				for (GuiTextRenderState guiTextRenderState : nodex.textStates) {
					this.current = nodex;
					consumer.accept(guiTextRenderState);
				}
			}
		}, GuiRenderState.TraverseRange.ALL);
		this.current = node;
	}

	public void forEachPictureInPicture(Consumer<PictureInPictureRenderState> consumer) {
		GuiRenderState.Node node = this.current;
		this.traverse(nodex -> {
			if (nodex.picturesInPictureStates != null) {
				this.current = nodex;

				for (PictureInPictureRenderState pictureInPictureRenderState : nodex.picturesInPictureStates) {
					consumer.accept(pictureInPictureRenderState);
				}
			}
		}, GuiRenderState.TraverseRange.ALL);
		this.current = node;
	}

	public void sortElements(Comparator<GuiElementRenderState> comparator) {
		this.traverse(node -> {
			if (node.elementStates != null) {
				node.elementStates.sort(comparator);
			}
		}, GuiRenderState.TraverseRange.ALL);
	}

	private void traverse(Consumer<GuiRenderState.Node> consumer, GuiRenderState.TraverseRange traverseRange) {
		int i = 0;
		int j = this.strata.size();
		if (traverseRange == GuiRenderState.TraverseRange.BEFORE_BLUR) {
			j = Math.min(this.firstStratumAfterBlur, this.strata.size());
		} else if (traverseRange == GuiRenderState.TraverseRange.AFTER_BLUR) {
			i = this.firstStratumAfterBlur;
		}

		for (int k = i; k < j; k++) {
			GuiRenderState.Node node = this.strata.get(k);
			this.traverse(node, consumer);
		}
	}

	private void traverse(GuiRenderState.Node node, Consumer<GuiRenderState.Node> consumer) {
		if (node.down != null) {
			this.traverse(node.down, consumer);
		}

		consumer.accept(node);
		if (node.up != null) {
			this.traverse(node.up, consumer);
		}
	}

	public void reset() {
		this.itemModelIdentities.clear();
		this.strata.clear();
		this.firstStratumAfterBlur = Integer.MAX_VALUE;
		this.nextStratum();
	}

	@Environment(EnvType.CLIENT)
	public interface LayeredElementConsumer {
		void accept(GuiElementRenderState guiElementRenderState, int i);
	}

	@Environment(EnvType.CLIENT)
	static class Node {
		@Nullable
		public final GuiRenderState.Node parent;
		@Nullable
		public GuiRenderState.Node up;
		@Nullable
		public GuiRenderState.Node down;
		@Nullable
		public List<GuiElementRenderState> elementStates;
		@Nullable
		public List<GuiElementRenderState> glyphStates;
		@Nullable
		public List<GuiItemRenderState> itemStates;
		@Nullable
		public List<GuiTextRenderState> textStates;
		@Nullable
		public List<PictureInPictureRenderState> picturesInPictureStates;

		Node(@Nullable GuiRenderState.Node node) {
			this.parent = node;
		}

		public void submitItem(GuiItemRenderState guiItemRenderState) {
			if (this.itemStates == null) {
				this.itemStates = new ArrayList<>();
			}

			this.itemStates.add(guiItemRenderState);
		}

		public void submitText(GuiTextRenderState guiTextRenderState) {
			if (this.textStates == null) {
				this.textStates = new ArrayList<>();
			}

			this.textStates.add(guiTextRenderState);
		}

		public void submitPicturesInPictureState(PictureInPictureRenderState pictureInPictureRenderState) {
			if (this.picturesInPictureStates == null) {
				this.picturesInPictureStates = new ArrayList<>();
			}

			this.picturesInPictureStates.add(pictureInPictureRenderState);
		}

		public void submitGuiElement(GuiElementRenderState guiElementRenderState) {
			if (this.elementStates == null) {
				this.elementStates = new ArrayList<>();
			}

			this.elementStates.add(guiElementRenderState);
		}

		public void submitGlyph(GuiElementRenderState guiElementRenderState) {
			if (this.glyphStates == null) {
				this.glyphStates = new ArrayList<>();
			}

			this.glyphStates.add(guiElementRenderState);
		}
	}

	@Environment(EnvType.CLIENT)
	public enum TraverseRange {
		ALL,
		BEFORE_BLUR,
		AFTER_BLUR;
	}
}
