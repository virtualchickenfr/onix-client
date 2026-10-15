package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import net.minecraft.client.network.ServerInfo;

import java.util.List;

public class ServerAddressModule extends TextHudModule {
    private final BoolSetting showPort = add(new BoolSetting("port", "Show Port", false));
    private final BoolSetting useName = add(new BoolSetting("useName", "Use Server List Name", false)
            .describe("Show the name from your server list instead of the address."));
    private final BoolSetting hideSingleplayer = add(new BoolSetting("hideSp", "Hide in Singleplayer", true));

    public ServerAddressModule() {
        super("server", "Server Address", "The server you are playing on.", Category.HUD, false, Anchor.TOP_RIGHT, -4, 130);
        tags("ip", "server ip");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (mc().isInSingleplayer()) {
            if (!hideSingleplayer.isOn() || preview) out.add(new Line("Server", "Singleplayer"));
            return;
        }
        ServerInfo info = mc().getCurrentServerEntry();
        if (info == null) {
            if (preview) out.add(new Line("Server", "play.example.net"));
            return;
        }
        String address = useName.isOn() ? info.name : info.address;
        if (!showPort.isOn() && !useName.isOn() && address.contains(":")) address = address.substring(0, address.lastIndexOf(':'));
        out.add(new Line("Server", address));
    }
}
