package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.TextSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Your own text with placeholders. Use | for a new line. */
public class CustomTextModule extends TextHudModule {
    private final TextSetting text = add(new TextSetting("text", "Text", "Hello, {player}!", 200)
            .describe("Placeholders: {player} {fps} {ping} {x} {y} {z} {time} {server}. Use | for a new line."));

    public CustomTextModule() {
        super("customtext", "Custom Text", "Your own text with live placeholders.", Category.HUD, false, Anchor.BOTTOM_LEFT, 4, -94);
        tags("label", "placeholder", "text");
        labelStyle.withDefault("Value Only");
    }

    private String fill(String s) {
        var p = mc().player;
        int ping = 0;
        if (p != null && mc().getConnection() != null) {
            PlayerInfo e = mc().getConnection().getPlayerInfo(p.getUUID());
            if (e != null) ping = e.getLatency();
        }
        return s.replace("{player}", mc().getUser().getName())
                .replace("{fps}", String.valueOf(mc().getFps()))
                .replace("{ping}", String.valueOf(ping))
                .replace("{x}", p == null ? "0" : String.valueOf(p.getBlockX()))
                .replace("{y}", p == null ? "0" : String.valueOf(p.getBlockY()))
                .replace("{z}", p == null ? "0" : String.valueOf(p.getBlockZ()))
                .replace("{time}", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
                .replace("{server}", mc().getCurrentServer() == null ? "Singleplayer" : mc().getCurrentServer().ip);
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        for (String line : text.get().split("\\|")) out.add(new Line("", fill(line.trim())));
    }
}
