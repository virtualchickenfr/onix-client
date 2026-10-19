package net.minecraft.world;

import java.util.Objects;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

public abstract class PersistentState {
	private boolean dirty;

	public void markDirty() {
		this.setDirty(true);
	}

	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	public boolean isDirty() {
		return this.dirty;
	}

	public record Context(@Nullable ServerWorld world, long worldSeed) {
		public Context(ServerWorld world) {
			this(world, world.getSeed());
		}

		public ServerWorld getWorldOrThrow() {
			return (ServerWorld)Objects.requireNonNull(this.world);
		}
	}
}
