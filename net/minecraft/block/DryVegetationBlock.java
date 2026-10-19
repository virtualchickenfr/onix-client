package net.minecraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class DryVegetationBlock extends PlantBlock {
	public static final MapCodec<DryVegetationBlock> CODEC = createCodec(DryVegetationBlock::new);
	private static final VoxelShape SHAPE = Block.createColumnShape(12.0, 0.0, 13.0);
	private static final int IDLE_SOUND_CHANCE = 150;
	private static final int IDLE_SOUND_BADLANDS_CHANCE_PENALTY = 5;

	@Override
	public MapCodec<? extends DryVegetationBlock> getCodec() {
		return CODEC;
	}

	public DryVegetationBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
		return floor.isIn(BlockTags.DRY_VEGETATION_MAY_PLACE_ON);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(150) == 0) {
			BlockState blockState = world.getBlockState(pos.down());
			if ((blockState.isOf(Blocks.RED_SAND) || blockState.isIn(BlockTags.TERRACOTTA)) && random.nextInt(5) != 0) {
				return;
			}

			BlockState blockState2 = world.getBlockState(pos.down(2));
			if (blockState.isIn(BlockTags.PLAYS_AMBIENT_DESERT_BLOCK_SOUNDS) && blockState2.isIn(BlockTags.PLAYS_AMBIENT_DESERT_BLOCK_SOUNDS)) {
				world.playSoundClient(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_DEADBUSH_IDLE, SoundCategory.AMBIENT, 1.0F, 1.0F, false);
			}
		}
	}
}
