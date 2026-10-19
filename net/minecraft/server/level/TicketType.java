package net.minecraft.server.level;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public record TicketType(long timeout, boolean persist, TicketType.TicketUse use) {
	public static final long NO_TIMEOUT = 0L;
	public static final TicketType START = register("start", 0L, false, TicketType.TicketUse.LOADING_AND_SIMULATION);
	public static final TicketType DRAGON = register("dragon", 0L, false, TicketType.TicketUse.LOADING_AND_SIMULATION);
	public static final TicketType PLAYER_LOADING = register("player_loading", 0L, false, TicketType.TicketUse.LOADING);
	public static final TicketType PLAYER_SIMULATION = register("player_simulation", 0L, false, TicketType.TicketUse.SIMULATION);
	public static final TicketType FORCED = register("forced", 0L, true, TicketType.TicketUse.LOADING_AND_SIMULATION);
	public static final TicketType PORTAL = register("portal", 300L, true, TicketType.TicketUse.LOADING_AND_SIMULATION);
	public static final TicketType ENDER_PEARL = register("ender_pearl", 40L, false, TicketType.TicketUse.LOADING_AND_SIMULATION);
	public static final TicketType UNKNOWN = register("unknown", 1L, false, TicketType.TicketUse.LOADING);

	private static TicketType register(String string, long l, boolean bl, TicketType.TicketUse ticketUse) {
		return Registry.register(BuiltInRegistries.TICKET_TYPE, string, new TicketType(l, bl, ticketUse));
	}

	public boolean doesLoad() {
		return this.use == TicketType.TicketUse.LOADING || this.use == TicketType.TicketUse.LOADING_AND_SIMULATION;
	}

	public boolean doesSimulate() {
		return this.use == TicketType.TicketUse.SIMULATION || this.use == TicketType.TicketUse.LOADING_AND_SIMULATION;
	}

	public boolean hasTimeout() {
		return this.timeout != 0L;
	}

	public enum TicketUse {
		LOADING,
		SIMULATION,
		LOADING_AND_SIMULATION;
	}
}
