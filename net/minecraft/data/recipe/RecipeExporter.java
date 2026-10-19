package net.minecraft.data.recipe;

import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.RegistryKey;
import org.jetbrains.annotations.Nullable;

public interface RecipeExporter {
	void accept(RegistryKey<Recipe<?>> key, Recipe<?> recipe, @Nullable AdvancementEntry advancement);

	Advancement.Builder getAdvancementBuilder();

	void addRootAdvancement();
}
