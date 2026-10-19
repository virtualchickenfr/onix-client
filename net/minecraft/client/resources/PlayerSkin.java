package net.minecraft.client.resources;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record PlayerSkin(
	ResourceLocation texture,
	@Nullable String textureUrl,
	@Nullable ResourceLocation capeTexture,
	@Nullable ResourceLocation elytraTexture,
	PlayerSkin.Model model,
	boolean secure
) {
	@Environment(EnvType.CLIENT)
	public enum Model {
		SLIM("slim"),
		WIDE("default");

		private final String id;

		Model(final String string2) {
			this.id = string2;
		}

		public static PlayerSkin.Model byName(@Nullable String string) {
			if (string == null) {
				return WIDE;
			}

			return switch (string) {
				case "slim" -> SLIM;
				default -> WIDE;
			};
		}

		public String id() {
			return this.id;
		}
	}
}
