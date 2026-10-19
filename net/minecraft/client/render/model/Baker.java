package net.minecraft.client.render.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public interface Baker {
	BakedSimpleModel getModel(Identifier id);

	ErrorCollectingSpriteGetter getSpriteGetter();

	<T> T compute(Baker.ResolvableCacheKey<T> key);

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface ResolvableCacheKey<T> {
		T compute(Baker baker);
	}
}
