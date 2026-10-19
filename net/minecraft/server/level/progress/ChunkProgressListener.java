package net.minecraft.server.level.progress;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

public interface ChunkProgressListener {
	void updateSpawnPos(ChunkPos chunkPos);

	void onStatusChange(ChunkPos chunkPos, @Nullable ChunkStatus chunkStatus);

	void start();

	void stop();

	static int calculateDiameter(int i) {
		return 2 * i + 1;
	}
}
