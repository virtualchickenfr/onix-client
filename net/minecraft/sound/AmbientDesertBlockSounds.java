package net.minecraft.sound;

import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

public class AmbientDesertBlockSounds {
	private static final int IDLE_SOUND_CHANCE = 1600;
	private static final int WIND_SOUND_CHANCE = 10000;
	private static final int REQUIRED_CHECK_DIRECTIONS = 3;
	private static final int DISTANCE_TO_CHECK = 8;

	public static void tryPlayAmbientSounds(BlockState state, World world, BlockPos pos, Random random) {
		if (state.isIn(BlockTags.PLAYS_AMBIENT_DESERT_BLOCK_SOUNDS) && world.isSkyVisible(pos.up())) {
			if (random.nextInt(1600) == 0 && shouldPlayAmbientSoundAt(world, pos)) {
				world.playSoundClient(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_SAND_IDLE, SoundCategory.AMBIENT, 1.0F, 1.0F, false);
			}

			if (random.nextInt(10000) == 0 && shouldPlayWindSoundIn(world.getBiome(pos)) && shouldPlayAmbientSoundAt(world, pos)) {
				world.playSoundClient(SoundEvents.BLOCK_SAND_WIND, SoundCategory.AMBIENT, 1.0F, 1.0F);
			}
		}
	}

	private static boolean shouldPlayWindSoundIn(RegistryEntry<Biome> biome) {
		return biome.matchesKey(BiomeKeys.DESERT) || biome.isIn(BiomeTags.IS_BADLANDS);
	}

	private static boolean shouldPlayAmbientSoundAt(World world, BlockPos pos) {
		int i = 0;

		for (Direction direction : Direction.Type.HORIZONTAL) {
			BlockPos blockPos = pos.offset(direction, 8);
			BlockState blockState = world.getBlockState(blockPos.withY(world.getTopY(Heightmap.Type.WORLD_SURFACE, blockPos) - 1));
			if (blockState.isIn(BlockTags.PLAYS_AMBIENT_DESERT_BLOCK_SOUNDS)) {
				if (++i >= 3) {
					return true;
				}
			}
		}

		return false;
	}
}
