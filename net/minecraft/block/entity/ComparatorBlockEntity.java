package net.minecraft.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

public class ComparatorBlockEntity extends BlockEntity {
	private static final int DEFAULT_OUTPUT_SIGNAL = 0;
	private int outputSignal = 0;

	public ComparatorBlockEntity(BlockPos pos, BlockState state) {
		super(BlockEntityType.COMPARATOR, pos, state);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("OutputSignal", this.outputSignal);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		this.outputSignal = nbt.getInt("OutputSignal", 0);
	}

	public int getOutputSignal() {
		return this.outputSignal;
	}

	public void setOutputSignal(int outputSignal) {
		this.outputSignal = outputSignal;
	}
}
