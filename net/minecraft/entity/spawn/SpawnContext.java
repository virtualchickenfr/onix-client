package net.minecraft.entity.spawn;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.biome.Biome;

public record SpawnContext(BlockPos pos, ServerWorldAccess world, RegistryEntry<Biome> biome) {
	public static SpawnContext of(ServerWorldAccess world, BlockPos pos) {
		RegistryEntry<Biome> registryEntry = world.getBiome(pos);
		return new SpawnContext(pos, world, registryEntry);
	}
}
