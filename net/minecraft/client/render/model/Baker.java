package net.minecraft.client.render.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface Baker {
	UnbakedModel getOrLoadModel(Identifier id);

	@Nullable
	BakedModel bake(Identifier id, ModelBakeSettings settings);
}
