package net.minecraft.client.render.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface UnbakedModel {
	String PARTICLE_TEXTURE = "particle";

	@Nullable
	default Boolean ambientOcclusion() {
		return null;
	}

	@Nullable
	default UnbakedModel.GuiLight guiLight() {
		return null;
	}

	@Nullable
	default ModelTransformation transformations() {
		return null;
	}

	default ModelTextures.Textures textures() {
		return ModelTextures.Textures.EMPTY;
	}

	@Nullable
	default Geometry geometry() {
		return null;
	}

	@Nullable
	default Identifier parent() {
		return null;
	}

	@Environment(EnvType.CLIENT)
	public static enum GuiLight {
		/**
		 * The model will be shaded from the front, like a basic item
		 */
		ITEM("front"),
		/**
		 * The model will be shaded from the side, like a block.
		 */
		BLOCK("side");

		private final String name;

		private GuiLight(final String name) {
			this.name = name;
		}

		public static UnbakedModel.GuiLight byName(String value) {
			for (UnbakedModel.GuiLight guiLight : values()) {
				if (guiLight.name.equals(value)) {
					return guiLight;
				}
			}

			throw new IllegalArgumentException("Invalid gui light: " + value);
		}

		public boolean isSide() {
			return this == BLOCK;
		}
	}
}
