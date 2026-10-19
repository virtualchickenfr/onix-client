package net.minecraft.client.render.model.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.model.BakedGeometry;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ErrorCollectingSpriteGetter;
import net.minecraft.client.render.model.Geometry;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.ModelTextures;
import net.minecraft.client.render.model.SimpleModel;
import net.minecraft.client.render.model.UnbakedGeometry;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AxisRotation;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class GeneratedItemModel implements UnbakedModel {
	public static final Identifier GENERATED = Identifier.ofVanilla("builtin/generated");
	public static final List<String> LAYERS = List.of("layer0", "layer1", "layer2", "layer3", "layer4");
	private static final float field_32806 = 7.5F;
	private static final float field_32807 = 8.5F;
	private static final ModelTextures.Textures TEXTURES = new ModelTextures.Textures.Builder().addTextureReference("particle", "layer0").build();
	private static final ModelElementFace.UV FACING_SOUTH_UV = new ModelElementFace.UV(0.0F, 0.0F, 16.0F, 16.0F);
	private static final ModelElementFace.UV FACING_NORTH_UV = new ModelElementFace.UV(16.0F, 0.0F, 0.0F, 16.0F);

	@Override
	public ModelTextures.Textures textures() {
		return TEXTURES;
	}

	@Override
	public Geometry geometry() {
		return GeneratedItemModel::bakeGeometry;
	}

	@Nullable
	@Override
	public UnbakedModel.GuiLight guiLight() {
		return UnbakedModel.GuiLight.ITEM;
	}

	private static BakedGeometry bakeGeometry(ModelTextures textures, Baker baker, ModelBakeSettings settings, SimpleModel model) {
		return bakeGeometry(textures, baker.getSpriteGetter(), settings, model);
	}

	private static BakedGeometry bakeGeometry(
		ModelTextures textures, ErrorCollectingSpriteGetter errorCollectingSpriteGetter, ModelBakeSettings settings, SimpleModel model
	) {
		List<ModelElement> list = new ArrayList();

		for (int i = 0; i < LAYERS.size(); i++) {
			String string = (String)LAYERS.get(i);
			SpriteIdentifier spriteIdentifier = textures.get(string);
			if (spriteIdentifier == null) {
				break;
			}

			SpriteContents spriteContents = errorCollectingSpriteGetter.get(spriteIdentifier, model).getContents();
			list.addAll(addLayerElements(i, string, spriteContents));
		}

		return UnbakedGeometry.bakeGeometry(list, textures, errorCollectingSpriteGetter, settings, model);
	}

	private static List<ModelElement> addLayerElements(int tintIndex, String name, SpriteContents spriteContents) {
		Map<Direction, ModelElementFace> map = Map.of(
			Direction.SOUTH,
			new ModelElementFace(null, tintIndex, name, FACING_SOUTH_UV, AxisRotation.R0),
			Direction.NORTH,
			new ModelElementFace(null, tintIndex, name, FACING_NORTH_UV, AxisRotation.R0)
		);
		List<ModelElement> list = new ArrayList();
		list.add(new ModelElement(new Vector3f(0.0F, 0.0F, 7.5F), new Vector3f(16.0F, 16.0F, 8.5F), map));
		list.addAll(addSubComponents(spriteContents, name, tintIndex));
		return list;
	}

	private static List<ModelElement> addSubComponents(SpriteContents spriteContents, String string, int i) {
		float f = spriteContents.getWidth();
		float g = spriteContents.getHeight();
		List<ModelElement> list = new ArrayList();

		for (GeneratedItemModel.Frame frame : getFrames(spriteContents)) {
			float h = 0.0F;
			float j = 0.0F;
			float k = 0.0F;
			float l = 0.0F;
			float m = 0.0F;
			float n = 0.0F;
			float o = 0.0F;
			float p = 0.0F;
			float q = 16.0F / f;
			float r = 16.0F / g;
			float s = frame.getMin();
			float t = frame.getMax();
			float u = frame.getLevel();
			GeneratedItemModel.Side side = frame.getSide();
			switch (side) {
				case UP:
					m = s;
					h = s;
					k = n = t + 1.0F;
					o = u;
					j = u;
					l = u;
					p = u + 1.0F;
					break;
				case DOWN:
					o = u;
					p = u + 1.0F;
					m = s;
					h = s;
					k = n = t + 1.0F;
					j = u + 1.0F;
					l = u + 1.0F;
					break;
				case LEFT:
					m = u;
					h = u;
					k = u;
					n = u + 1.0F;
					p = s;
					j = s;
					l = o = t + 1.0F;
					break;
				case RIGHT:
					m = u;
					n = u + 1.0F;
					h = u + 1.0F;
					k = u + 1.0F;
					p = s;
					j = s;
					l = o = t + 1.0F;
			}

			h *= q;
			k *= q;
			j *= r;
			l *= r;
			j = 16.0F - j;
			l = 16.0F - l;
			m *= q;
			n *= q;
			o *= r;
			p *= r;
			Map<Direction, ModelElementFace> map = Map.of(
				side.getDirection(), new ModelElementFace(null, i, string, new ModelElementFace.UV(m, o, n, p), AxisRotation.R0)
			);
			switch (side) {
				case UP:
					list.add(new ModelElement(new Vector3f(h, j, 7.5F), new Vector3f(k, j, 8.5F), map));
					break;
				case DOWN:
					list.add(new ModelElement(new Vector3f(h, l, 7.5F), new Vector3f(k, l, 8.5F), map));
					break;
				case LEFT:
					list.add(new ModelElement(new Vector3f(h, j, 7.5F), new Vector3f(h, l, 8.5F), map));
					break;
				case RIGHT:
					list.add(new ModelElement(new Vector3f(k, j, 7.5F), new Vector3f(k, l, 8.5F), map));
			}
		}

		return list;
	}

	private static List<GeneratedItemModel.Frame> getFrames(SpriteContents spriteContents) {
		int i = spriteContents.getWidth();
		int j = spriteContents.getHeight();
		List<GeneratedItemModel.Frame> list = new ArrayList();
		spriteContents.getDistinctFrameCount().forEach(k -> {
			for (int l = 0; l < j; l++) {
				for (int m = 0; m < i; m++) {
					boolean bl = !isPixelTransparent(spriteContents, k, m, l, i, j);
					buildCube(GeneratedItemModel.Side.UP, list, spriteContents, k, m, l, i, j, bl);
					buildCube(GeneratedItemModel.Side.DOWN, list, spriteContents, k, m, l, i, j, bl);
					buildCube(GeneratedItemModel.Side.LEFT, list, spriteContents, k, m, l, i, j, bl);
					buildCube(GeneratedItemModel.Side.RIGHT, list, spriteContents, k, m, l, i, j, bl);
				}
			}
		});
		return list;
	}

	private static void buildCube(
		GeneratedItemModel.Side side, List<GeneratedItemModel.Frame> list, SpriteContents spriteContents, int i, int j, int k, int l, int m, boolean bl
	) {
		boolean bl2 = isPixelTransparent(spriteContents, i, j + side.getOffsetX(), k + side.getOffsetY(), l, m) && bl;
		if (bl2) {
			buildCube(list, side, j, k);
		}
	}

	private static void buildCube(List<GeneratedItemModel.Frame> list, GeneratedItemModel.Side side, int i, int j) {
		GeneratedItemModel.Frame frame = null;

		for (GeneratedItemModel.Frame frame2 : list) {
			if (frame2.getSide() == side) {
				int k = side.isVertical() ? j : i;
				if (frame2.getLevel() == k) {
					frame = frame2;
					break;
				}
			}
		}

		int l = side.isVertical() ? j : i;
		int m = side.isVertical() ? i : j;
		if (frame == null) {
			list.add(new GeneratedItemModel.Frame(side, m, l));
		} else {
			frame.expand(m);
		}
	}

	private static boolean isPixelTransparent(SpriteContents spriteContents, int i, int j, int k, int l, int m) {
		return j >= 0 && k >= 0 && j < l && k < m ? spriteContents.isPixelTransparent(i, j, k) : true;
	}

	@Environment(EnvType.CLIENT)
	static class Frame {
		private final GeneratedItemModel.Side side;
		private int min;
		private int max;
		private final int level;

		public Frame(GeneratedItemModel.Side side, int width, int depth) {
			this.side = side;
			this.min = width;
			this.max = width;
			this.level = depth;
		}

		public void expand(int newValue) {
			if (newValue < this.min) {
				this.min = newValue;
			} else if (newValue > this.max) {
				this.max = newValue;
			}
		}

		public GeneratedItemModel.Side getSide() {
			return this.side;
		}

		public int getMin() {
			return this.min;
		}

		public int getMax() {
			return this.max;
		}

		public int getLevel() {
			return this.level;
		}
	}

	@Environment(EnvType.CLIENT)
	static enum Side {
		UP(Direction.UP, 0, -1),
		DOWN(Direction.DOWN, 0, 1),
		LEFT(Direction.EAST, -1, 0),
		RIGHT(Direction.WEST, 1, 0);

		private final Direction direction;
		private final int offsetX;
		private final int offsetY;

		private Side(final Direction direction, final int offsetX, final int offsetY) {
			this.direction = direction;
			this.offsetX = offsetX;
			this.offsetY = offsetY;
		}

		public Direction getDirection() {
			return this.direction;
		}

		public int getOffsetX() {
			return this.offsetX;
		}

		public int getOffsetY() {
			return this.offsetY;
		}

		boolean isVertical() {
			return this == DOWN || this == UP;
		}
	}
}
