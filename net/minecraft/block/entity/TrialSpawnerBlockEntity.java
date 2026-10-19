package net.minecraft.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrialSpawnerBlock;
import net.minecraft.block.enums.TrialSpawnerState;
import net.minecraft.block.spawner.EntityDetector;
import net.minecraft.block.spawner.TrialSpawnerLogic;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class TrialSpawnerBlockEntity extends BlockEntity implements Spawner, TrialSpawnerLogic.TrialSpawner {
	private TrialSpawnerLogic logic = this.createDefaultLogic();

	public TrialSpawnerBlockEntity(BlockPos pos, BlockState state) {
		super(BlockEntityType.TRIAL_SPAWNER, pos, state);
	}

	private TrialSpawnerLogic createDefaultLogic() {
		EntityDetector entityDetector = EntityDetector.SURVIVAL_PLAYERS;
		EntityDetector.Selector selector = EntityDetector.Selector.IN_WORLD;
		return new TrialSpawnerLogic(this, entityDetector, selector);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		this.logic = (TrialSpawnerLogic)nbt.decode(this.logic.codec(), registries.getOps(NbtOps.INSTANCE)).orElseGet(this::createDefaultLogic);
		if (this.world != null) {
			this.updateListeners();
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.copyFromCodec(this.logic.codec(), registries.getOps(NbtOps.INSTANCE), this.logic);
	}

	public BlockEntityUpdateS2CPacket toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return this.logic.getData().getSpawnDataNbt(this.getCachedState().get(TrialSpawnerBlock.TRIAL_SPAWNER_STATE));
	}

	@Override
	public void setEntityType(EntityType<?> type, Random random) {
		if (this.world == null) {
			Util.logErrorOrPause("Expected non-null level");
		} else {
			this.logic.setEntityType(type, this.world);
			this.markDirty();
		}
	}

	public TrialSpawnerLogic getSpawner() {
		return this.logic;
	}

	@Override
	public TrialSpawnerState getSpawnerState() {
		return !this.getCachedState().contains(Properties.TRIAL_SPAWNER_STATE)
			? TrialSpawnerState.INACTIVE
			: this.getCachedState().get(Properties.TRIAL_SPAWNER_STATE);
	}

	@Override
	public void setSpawnerState(World world, TrialSpawnerState spawnerState) {
		this.markDirty();
		world.setBlockState(this.pos, this.getCachedState().with(Properties.TRIAL_SPAWNER_STATE, spawnerState));
	}

	@Override
	public void updateListeners() {
		this.markDirty();
		if (this.world != null) {
			this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), Block.NOTIFY_ALL);
		}
	}
}
