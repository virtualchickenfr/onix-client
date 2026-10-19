package net.minecraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.sound.AmbientDesertBlockSounds;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class TerracottaBlock extends Block {
	public static final MapCodec<TerracottaBlock> CODEC = createCodec(TerracottaBlock::new);

	@Override
	public MapCodec<TerracottaBlock> getCodec() {
		return CODEC;
	}

	public TerracottaBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		AmbientDesertBlockSounds.tryPlayAmbientSounds(state, world, pos, random);
	}
}
