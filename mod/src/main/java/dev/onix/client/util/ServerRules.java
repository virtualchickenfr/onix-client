package dev.onix.client.util;

import dev.onix.client.OnixClient;
import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles modules marked with {@link Module#restricted} when you join a server: a reminder, or
 * switching them off for the session (they come back on when you leave).
 */
public final class ServerRules {
    /** Modules this class switched off on join, to restore on disconnect. */
    private static final List<Module> DISABLED = new ArrayList<>();

    private ServerRules() {}

    /** Enabled modules that some servers do not allow. */
    public static List<Module> enabledRestricted() {
        List<Module> list = new ArrayList<>();
        for (Module m : OnixClient.MODULES.all()) if (m.isEnabled() && m.isRestricted()) list.add(m);
        return list;
    }

    public static void onJoin(MinecraftClient client) {
        ServerInfo server = client.getCurrentServerEntry();
        if (server == null || client.isInSingleplayer()) return; // singleplayer and LAN worlds are yours
        String mode = OnixClient.CONFIG.rulesOnJoin.get();
        if (mode.equals("Do Nothing") || isSkipped(server.address)) return;
        List<Module> active = enabledRestricted();
        if (active.isEmpty()) return;

        if (mode.equals("Turn Off")) {
            for (Module m : active) {
                m.setEnabled(false);
                DISABLED.add(m);
            }
            Notifications.warn("Server rules", active.size() + (active.size() == 1 ? " module" : " modules")
                    + " paused here: " + names(active));
        } else {
            Notifications.warn("Check the server rules", names(active) + " may not be allowed here");
        }
    }

    public static void onLeave() {
        if (DISABLED.isEmpty()) return;
        for (Module m : DISABLED) m.setEnabled(true);
        DISABLED.clear();
    }

    /**
     * A different profile was loaded. Paused modules belonged to the old one, so forget them and
     * check the new profile against the server you are on (if any).
     */
    public static void profileChanged() {
        DISABLED.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.world != null) onJoin(client);
    }

    /** Whether a module is currently paused because of the server you are on. */
    public static boolean isPaused(Module m) {
        return DISABLED.contains(m);
    }

    private static boolean isSkipped(String address) {
        String host = address.toLowerCase(Locale.ROOT).trim();
        for (String entry : OnixClient.CONFIG.rulesSkip.get().split(",")) {
            String e = entry.toLowerCase(Locale.ROOT).trim();
            if (e.isEmpty()) continue;
            if (host.equals(e) || host.startsWith(e + ":") || host.endsWith("." + e)) return true;
        }
        return false;
    }

    private static String names(List<Module> modules) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < modules.size() && i < 3; i++) {
            if (i > 0) sb.append(", ");
            sb.append(modules.get(i).getName());
        }
        if (modules.size() > 3) sb.append(" +").append(modules.size() - 3);
        return sb.toString();
    }
}
