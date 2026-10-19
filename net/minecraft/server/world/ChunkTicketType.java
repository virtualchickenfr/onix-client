package net.minecraft.server.world;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public record ChunkTicketType(long expiryTicks, boolean persist, ChunkTicketType.Use use) {
	public static final long NO_EXPIRATION = 0L;
	public static final ChunkTicketType START = register("start", 0L, false, ChunkTicketType.Use.LOADING_AND_SIMULATION);
	/**
	 * Used by the ender dragon to load the central end island during the boss battle.
	 */
	public static final ChunkTicketType DRAGON = register("dragon", 0L, false, ChunkTicketType.Use.LOADING_AND_SIMULATION);
	public static final ChunkTicketType PLAYER_LOADING = register("player_loading", 0L, false, ChunkTicketType.Use.LOADING);
	public static final ChunkTicketType PLAYER_SIMULATION = register("player_simulation", 0L, false, ChunkTicketType.Use.SIMULATION);
	/**
	 * Used to force load chunks.
	 */
	public static final ChunkTicketType FORCED = register("forced", 0L, true, ChunkTicketType.Use.LOADING_AND_SIMULATION);
	/**
	 * Used by a nether portal to load chunks in the other dimension.
	 */
	public static final ChunkTicketType PORTAL = register("portal", 300L, true, ChunkTicketType.Use.LOADING_AND_SIMULATION);
	public static final ChunkTicketType ENDER_PEARL = register("ender_pearl", 40L, false, ChunkTicketType.Use.LOADING_AND_SIMULATION);
	/**
	 * Represents a type of ticket that has an unknown cause for loading chunks.
	 */
	public static final ChunkTicketType UNKNOWN = register("unknown", 1L, false, ChunkTicketType.Use.LOADING);

	private static ChunkTicketType register(String id, long expiryTicks, boolean persist, ChunkTicketType.Use use) {
		return Registry.register(Registries.TICKET_TYPE, id, new ChunkTicketType(expiryTicks, persist, use));
	}

	public boolean isForLoading() {
		return this.use == ChunkTicketType.Use.LOADING || this.use == ChunkTicketType.Use.LOADING_AND_SIMULATION;
	}

	public boolean isForSimulation() {
		return this.use == ChunkTicketType.Use.SIMULATION || this.use == ChunkTicketType.Use.LOADING_AND_SIMULATION;
	}

	public boolean canExpire() {
		return this.expiryTicks != 0L;
	}

	public static enum Use {
		LOADING,
		SIMULATION,
		LOADING_AND_SIMULATION;
	}
}
