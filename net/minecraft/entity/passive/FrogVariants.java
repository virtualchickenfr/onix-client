package net.minecraft.entity.passive;

import java.util.Optional;
import net.minecraft.entity.VariantSelectorProvider;
import net.minecraft.entity.spawn.BiomeSpawnCondition;
import net.minecraft.entity.spawn.SpawnConditionSelectors;
import net.minecraft.entity.spawn.SpawnContext;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;

public interface FrogVariants {
	RegistryKey<FrogVariant> TEMPERATE = of(AnimalTemperature.TEMPERATE);
	RegistryKey<FrogVariant> WARM = of(AnimalTemperature.WARM);
	RegistryKey<FrogVariant> COLD = of(AnimalTemperature.COLD);

	private static RegistryKey<FrogVariant> of(Identifier id) {
		return RegistryKey.of(RegistryKeys.FROG_VARIANT, id);
	}

	static void bootstrap(Registerable<FrogVariant> registry) {
		register(registry, TEMPERATE, "entity/frog/temperate_frog", SpawnConditionSelectors.createFallback(0));
		register(registry, WARM, "entity/frog/warm_frog", BiomeTags.SPAWNS_WARM_VARIANT_FROGS);
		register(registry, COLD, "entity/frog/cold_frog", BiomeTags.SPAWNS_COLD_VARIANT_FROGS);
	}

	private static void register(Registerable<FrogVariant> registry, RegistryKey<FrogVariant> key, String assetId, TagKey<Biome> requiredBiomes) {
		RegistryEntryList<Biome> registryEntryList = registry.getRegistryLookup(RegistryKeys.BIOME).getOrThrow(requiredBiomes);
		register(registry, key, assetId, SpawnConditionSelectors.createSingle(new BiomeSpawnCondition(registryEntryList), 1));
	}

	private static void register(Registerable<FrogVariant> registry, RegistryKey<FrogVariant> key, String assetId, SpawnConditionSelectors spawnConditions) {
		registry.register(key, new FrogVariant(new AssetInfo(Identifier.ofVanilla(assetId)), spawnConditions));
	}

	static Optional<RegistryEntry.Reference<FrogVariant>> select(Random random, DynamicRegistryManager registries, SpawnContext context) {
		return VariantSelectorProvider.select(registries.getOrThrow(RegistryKeys.FROG_VARIANT).streamEntries(), RegistryEntry::value, random, context);
	}
}
