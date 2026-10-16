package dev.onix.client.config;

import com.google.gson.JsonObject;
import dev.onix.client.OnixClient;
import dev.onix.client.cosmetics.Cosmetics;
import dev.onix.client.gui.Notifications;
import dev.onix.client.gui.Theme;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Receives profiles that the Onix Launcher sends to a running game ("Send Profile" on the
 * Install page). The launcher copies the profile files into config/onix and then writes
 * config/onix/inbox.json; this class notices the file and loads the profile live.
 */
public final class LauncherInbox {
    private static final int CHECK_EVERY_TICKS = 20;
    private static int ticks;

    private LauncherInbox() {}

    private static Path file() {
        return ConfigFiles.dir().resolve("inbox.json");
    }

    /** Called every client tick; only touches the disk once per second. */
    public static void tick() {
        if (++ticks < CHECK_EVERY_TICKS) return;
        ticks = 0;
        Path inbox = file();
        if (!Files.exists(inbox)) return;
        JsonObject json = ConfigFiles.read(inbox);
        try {
            Files.deleteIfExists(inbox);
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Could not remove {}", inbox, e);
            return;
        }
        if (json == null || !json.has("action") || !"load-profile".equals(json.get("action").getAsString())) return;
        String profile = json.has("profile") ? json.get("profile").getAsString() : ProfileManager.DEFAULT;
        String from = json.has("from") ? json.get("from").getAsString() : "the launcher";
        try {
            Cosmetics.load();
            if (!OnixClient.PROFILES.receive(profile)) {
                Notifications.warn("Profile not found", "\"" + profile + "\" was not sent along");
                return;
            }
            Theme.refresh();
            Notifications.show("Profile received", "\"" + profile + "\" from " + from);
            OnixClient.LOGGER.info("Loaded profile '{}' sent by {}", profile, from);
        } catch (Exception e) {
            OnixClient.LOGGER.error("Could not load the profile sent by {}", from, e);
            Notifications.show("Profile not loaded", "See the log for details");
        }
    }
}
