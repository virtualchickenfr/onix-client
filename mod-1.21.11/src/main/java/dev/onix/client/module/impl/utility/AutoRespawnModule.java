package dev.onix.client.module.impl.utility;

import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.TextSetting;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;

/** Respawns automatically after death, optionally reporting where you died. */
public class AutoRespawnModule extends Module {
    private final NumberSetting delay = add(new NumberSetting("delay", "Delay", 0.5, 0, 5, 0.25).unit("s"));
    private final BoolSetting onlyMultiplayer = add(new BoolSetting("onlyMultiplayer", "Only on Servers", false));
    private final BoolSetting showCoords = add(new BoolSetting("showCoords", "Show Death Position", true)
            .describe("A notification with the coordinates where you died."));

    {
        group("After Respawning");
    }

    private final TextSetting message = add(new TextSetting("message", "Send Message", "", 256).placeholder("e.g. /kit or leave empty")
            .describe("Sent once after you respawn. Leave empty to send nothing."));
    private final NumberSetting messageDelay = add(new NumberSetting("messageDelay", "Message Delay", 1, 0, 10, 0.5).unit("s")
            .visibleIf(() -> !message.get().isBlank()));

    private long deadSince = -1;
    private long sendAt = -1;

    public AutoRespawnModule() {
        super("autorespawn", "Auto Respawn", "Skip the death screen.", Category.UTILITY, false);
        restricted("Skipping the death screen automatically counts as automation on some servers.");
        tags("respawn", "death screen");
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        if (sendAt > 0 && now >= sendAt && mc().player != null && mc().player.isAlive() && mc().getConnection() != null) {
            String msg = message.get().trim();
            if (msg.startsWith("/")) mc().getConnection().sendCommand(msg.substring(1));
            else if (!msg.isEmpty()) mc().getConnection().sendChat(msg);
            sendAt = -1;
        }
        if (!(mc().screen instanceof DeathScreen) || mc().player == null) {
            deadSince = -1;
            return;
        }
        if (onlyMultiplayer.isOn() && mc().isLocalServer()) return;
        if (deadSince < 0) deadSince = now;
        if (now - deadSince < delay.get() * 1000) return;

        if (showCoords.isOn()) {
            BlockPos p = mc().player.blockPosition();
            Notifications.show("You died", p.getX() + " " + p.getY() + " " + p.getZ());
        }
        mc().player.respawn();
        mc().setScreen(null);
        deadSince = -1;
        if (!message.get().isBlank()) sendAt = now + (long) (messageDelay.get() * 1000);
    }
}
