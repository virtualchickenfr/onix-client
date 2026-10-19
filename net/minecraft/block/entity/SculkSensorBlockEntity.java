package net.minecraft.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.SculkSensorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.event.BlockPositionSource;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.PositionSource;
import net.minecraft.world.event.Vibrations;
import net.minecraft.world.event.listener.GameEventListener;
import org.jetbrains.annotations.Nullable;

public class SculkSensorBlockEntity extends BlockEntity implements GameEventListener.Holder<Vibrations.VibrationListener>, Vibrations {
	private static final int DEFAULT_LAST_VIBRATION_FREQUENCY = 0;
	private Vibrations.ListenerData listenerData;
	private final Vibrations.VibrationListener listener;
	private final Vibrations.Callback callback;
	private int lastVibrationFrequency = 0;

	protected SculkSensorBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
		super(blockEntityType, blockPos, blockState);
		this.callback = this.createCallback();
		this.listenerData = new Vibrations.ListenerData();
		this.listener = new Vibrations.VibrationListener(this);
	}

	public SculkSensorBlockEntity(BlockPos pos, BlockState state) {
		this(BlockEntityType.SCULK_SENSOR, pos, state);
	}

	public Vibrations.Callback createCallback() {
		return new SculkSensorBlockEntity.VibrationCallback(this.getPos());
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		this.lastVibrationFrequency = nbt.getInt("last_vibration_frequency", 0);
		RegistryOps<NbtElement> registryOps = registries.getOps(NbtOps.INSTANCE);
		this.listenerData = (Vibrations.ListenerData)nbt.get("listener", Vibrations.ListenerData.CODEC, registryOps).orElseGet(Vibrations.ListenerData::new);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("last_vibration_frequency", this.lastVibrationFrequency);
		RegistryOps<NbtElement> registryOps = registries.getOps(NbtOps.INSTANCE);
		nbt.put("listener", Vibrations.ListenerData.CODEC, registryOps, this.listenerData);
	}

	@Override
	public Vibrations.ListenerData getVibrationListenerData() {
		return this.listenerData;
	}

	@Override
	public Vibrations.Callback getVibrationCallback() {
		return this.callback;
	}

	public int getLastVibrationFrequency() {
		return this.lastVibrationFrequency;
	}

	public void setLastVibrationFrequency(int lastVibrationFrequency) {
		this.lastVibrationFrequency = lastVibrationFrequency;
	}

	public Vibrations.VibrationListener getEventListener() {
		return this.listener;
	}

	protected class VibrationCallback implements Vibrations.Callback {
		public static final int RANGE = 8;
		protected final BlockPos pos;
		private final PositionSource positionSource;

		public VibrationCallback(final BlockPos pos) {
			this.pos = pos;
			this.positionSource = new BlockPositionSource(pos);
		}

		@Override
		public int getRange() {
			return 8;
		}

		@Override
		public PositionSource getPositionSource() {
			return this.positionSource;
		}

		@Override
		public boolean triggersAvoidCriterion() {
			return true;
		}

		@Override
		public boolean accepts(ServerWorld world, BlockPos pos, RegistryEntry<GameEvent> event, @Nullable GameEvent.Emitter emitter) {
			if (!pos.equals(this.pos) || !event.matches(GameEvent.BLOCK_DESTROY) && !event.matches(GameEvent.BLOCK_PLACE)) {
				return Vibrations.getFrequency(event) == 0 ? false : SculkSensorBlock.isInactive(SculkSensorBlockEntity.this.getCachedState());
			} else {
				return false;
			}
		}

		@Override
		public void accept(ServerWorld world, BlockPos pos, RegistryEntry<GameEvent> event, @Nullable Entity sourceEntity, @Nullable Entity entity, float distance) {
			BlockState blockState = SculkSensorBlockEntity.this.getCachedState();
			if (SculkSensorBlock.isInactive(blockState)) {
				int i = Vibrations.getFrequency(event);
				SculkSensorBlockEntity.this.setLastVibrationFrequency(i);
				int j = Vibrations.getSignalStrength(distance, this.getRange());
				if (blockState.getBlock() instanceof SculkSensorBlock sculkSensorBlock) {
					sculkSensorBlock.setActive(sourceEntity, world, this.pos, blockState, j, i);
				}
			}
		}

		@Override
		public void onListen() {
			SculkSensorBlockEntity.this.markDirty();
		}

		@Override
		public boolean requiresTickingChunksAround() {
			return true;
		}
	}
}
