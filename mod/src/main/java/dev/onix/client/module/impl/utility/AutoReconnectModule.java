package dev.onix.client.module.impl.utility;

import dev.onix.client.OnixClient;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Adds a reconnect button to the disconnect screen and can reconnect on its own. */
public class AutoReconnectModule extends Module {
    private final BoolSetting automatic = add(new BoolSetting("auto", "Reconnect Automatically", true));
    private final NumberSetting delay = add(new NumberSetting("delay", "Delay", 5, 1, 60, 1).unit("s").visibleIf(automatic::isOn));
    private final NumberSetting attempts = add(new NumberSetting("attempts", "Max Attempts", 5, 1, 50, 1).visibleIf(automatic::isOn));
    private final BoolSetting backoff = add(new BoolSetting("backoff", "Wait Longer Each Try", true)
            .describe("Doubles the delay after every failed attempt, up to two minutes.").visibleIf(automatic::isOn));
    private final BoolSetting button = add(new BoolSetting("button", "Show Reconnect Button", true));

    private ServerInfo lastServer;
    private int tries;

    public AutoReconnectModule() {
        super("autoreconnect", "Auto Reconnect", "Reconnect button and automatic reconnect after a kick.", Category.UTILITY, false);
        restricted("Some servers do not want clients to reconnect automatically after a kick.");
        tags("rejoin", "kicked", "disconnect");
    }

    @Override
    public void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerInfo info = client.getCurrentServerEntry();
            if (info != null) lastServer = info;
            tries = 0;
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!isEnabled() || !(screen instanceof DisconnectedScreen) || lastServer == null) return;
            long openedAt = System.currentTimeMillis();
            ButtonWidget widget = ButtonWidget.builder(Text.literal("Reconnect"), b -> reconnect(client))
                    .dimensions(width / 2 - 100, height - 30, 200, 20).build();
            if (button.isOn()) Screens.getButtons(screen).add(widget);
            ScreenEvents.afterTick(screen).register(s -> {
                if (!automatic.isOn() || tries >= attempts.getInt()) {
                    widget.setMessage(Text.literal("Reconnect"));
                    return;
                }
                double seconds = delay.get() * (backoff.isOn() ? Math.pow(2, tries) : 1);
                long wait = (long) (Math.min(120, seconds) * 1000);
                long left = wait - (System.currentTimeMillis() - openedAt);
                if (left <= 0) {
                    tries++;
                    reconnect(client);
                } else {
                    widget.setMessage(Text.literal("Reconnect (" + ((left + 999) / 1000) + ")"));
                }
            });
        });
    }

    private void reconnect(MinecraftClient client) {
        if (lastServer == null) return;
        ServerAddress address = ServerAddress.parse(lastServer.address);
        Screen parent = new MultiplayerScreen(new TitleScreen());
        try {
            // Find ConnectScreen.connect by its parameter types, so this works across small signature changes.
            for (Method m : ConnectScreen.class.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers())) continue;
                Class<?>[] types = m.getParameterTypes();
                boolean hasAddress = false;
                boolean hasInfo = false;
                for (Class<?> t : types) {
                    if (t == ServerAddress.class) hasAddress = true;
                    if (t == ServerInfo.class) hasInfo = true;
                }
                if (!hasAddress || !hasInfo) continue;
                Object[] args = new Object[types.length];
                for (int i = 0; i < types.length; i++) {
                    Class<?> t = types[i];
                    if (t == Screen.class) args[i] = parent;
                    else if (t == MinecraftClient.class) args[i] = client;
                    else if (t == ServerAddress.class) args[i] = address;
                    else if (t == ServerInfo.class) args[i] = lastServer;
                    else if (t == boolean.class) args[i] = false;
                    else args[i] = null;
                }
                m.invoke(null, args);
                return;
            }
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Reconnect failed", e);
        }
        client.setScreen(parent);
    }
}
