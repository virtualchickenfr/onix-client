package net.minecraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.enums.RailShape;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.block.WireOrientation;
import net.minecraft.world.tick.ScheduledTickView;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractRailBlock extends Block implements Waterloggable {
	public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
	private static final VoxelShape STRAIGHT_SHAPE = Block.createColumnShape(16.0, 0.0, 2.0);
	private static final VoxelShape ASCENDING_SHAPE = Block.createColumnShape(16.0, 0.0, 8.0);
	private final boolean forbidCurves;

	public static boolean isRail(World world, BlockPos pos) {
		return isRail(world.getBlockState(pos));
	}

	public static boolean isRail(BlockState state) {
		return state.isIn(BlockTags.RAILS) && state.getBlock() instanceof AbstractRailBlock;
	}

	protected AbstractRailBlock(boolean forbidCurves, AbstractBlock.Settings settings) {
		super(settings);
		this.forbidCurves = forbidCurves;
	}

	@Override
	protected abstract MapCodec<? extends AbstractRailBlock> getCodec();

	public boolean cannotMakeCurves() {
		return this.forbidCurves;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return ((RailShape)state.get(this.getShapeProperty())).isAscending() ? ASCENDING_SHAPE : STRAIGHT_SHAPE;
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return hasTopRim(world, pos.down());
	}

	@Override
	protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		if (!oldState.isOf(state.getBlock())) {
			this.updateCurves(state, world, pos, notify);
		}
	}

	protected BlockState updateCurves(BlockState state, World world, BlockPos pos, boolean notify) {
		state = this.updateBlockState(world, pos, state, true);
		if (this.forbidCurves) {
			world.updateNeighbor(state, pos, this, null, notify);
		}

		return state;
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
		if (!world.isClient && world.getBlockState(pos).isOf(this)) {
			RailShape railShape = state.get(this.getShapeProperty());
			if (shouldDropRail(pos, world, railShape)) {
				dropStacks(state, world, pos);
				world.removeBlock(pos, notify);
			} else {
				this.updateBlockState(state, world, pos, sourceBlock);
			}
		}
	}

	/**
	 * Checks if this rail should be dropped.
	 * 
	 * <p>This method will return true if:
	 * <ul><li>The rail block is ascending.</li>
	 * <li>The block in the direction of ascent does not have a top rim.</li></ul>
	 */
	private static boolean shouldDropRail(BlockPos pos, World world, RailShape shape) {
		if (!hasTopRim(world, pos.down())) {
			return true;
		} else {
			switch (shape) {
				case ASCENDING_EAST:
					return !hasTopRim(world, pos.east());
				case ASCENDING_WEST:
					return !hasTopRim(world, pos.west());
				case ASCENDING_NORTH:
					return !hasTopRim(world, pos.north());
				case ASCENDING_SOUTH:
					return !hasTopRim(world, pos.south());
				default:
					return false;
			}
		}
	}

	protected void updateBlockState(BlockState state, World world, BlockPos pos, Block neighbor) {
	}

	protected BlockState updateBlockState(World world, BlockPos pos, BlockState state, boolean forceUpdate) {
		if (world.isClient) {
			return state;
		} else {
			RailShape railShape = state.get(this.getShapeProperty());
			return new RailPlacementHelper(world, pos, state).updateBlockState(world.isReceivingRedstonePower(pos), forceUpdate, railShape).getBlockState();
		}
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		if (!moved) {
			if (((RailShape)state.get(this.getShapeProperty())).isAscending()) {
				world.updateNeighbors(pos.up(), this);
			}

			if (this.forbidCurves) {
				world.updateNeighbors(pos, this);
				world.updateNeighbors(pos.down(), this);
			}
		}
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		FluidState fluidState = ctx.getWorld().getFluidState(ctx.getBlockPos());
		boolean bl = fluidState.getFluid() == Fluids.WATER;
		BlockState blockState = super.getDefaultState();
		Direction direction = ctx.getHorizontalPlayerFacing();
		boolean bl2 = direction == Direction.EAST || direction == Direction.WEST;
		return blockState.with(this.getShapeProperty(), bl2 ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH).with(WATERLOGGED, bl);
	}

	public abstract Property<RailShape> getShapeProperty();

	@Override
	protected BlockState getStateForNeighborUpdate(
		BlockState state,
		WorldView world,
		ScheduledTickView tickView,
		BlockPos pos,
		Direction direction,
		BlockPos neighborPos,
		BlockState neighborState,
		Random random
	) {
		if ((Boolean)state.get(WATERLOGGED)) {
			tickView.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
		}

		return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
	}
}
