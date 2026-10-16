package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.SessionTracker;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class DeathCoordinatesModule extends TextHudModule {
    private final BoolSetting distance = add(new BoolSetting("distance", "Show Distance", true));
    private final BoolSetting chatMessage = add(new BoolSetting("chat", "Post in Chat on Death", true));
    private final NumberSetting hideAfter = add(new NumberSetting("hideAfter", "Hide After", 10, 0, 120, 1).unit("min")
            .describe("0 keeps it visible until the next death."));

    private long announced;

    public DeathCoordinatesModule() {
        super("deathcoords", "Death Coordinates", "Remembers where you died so you can get your items back.", Category.HUD, false, Anchor.TOP_LEFT, 4, 184);
        tags("death", "grave", "items");
    }

    @Override
    public void onTick() {
        BlockPos pos = SessionTracker.deathPos();
        if (pos == null || SessionTracker.deathTime() == announced || mc().player == null) return;
        announced = SessionTracker.deathTime();
        if (chatMessage.isOn()) {
            mc().player.displayClientMessage(Component.literal("[Onix] ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("You died at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                            + " (" + SessionTracker.deathDimension().replace('_', ' ') + ")").withStyle(ChatFormatting.WHITE)), false);
        }
    }

    private boolean expired() {
        return hideAfter.getInt() > 0 && System.currentTimeMillis() - SessionTracker.deathTime() > hideAfter.getInt() * 60_000L;
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        BlockPos pos = SessionTracker.deathPos();
        if (pos == null || expired()) {
            if (preview) out.add(new Line("Death", "120, 64, -340"));
            return;
        }
        out.add(new Line("Death", pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        if (distance.isOn() && mc().player != null) {
            int d = (int) Math.sqrt(mc().player.blockPosition().distSqr(pos));
            out.add(new Line("Distance", d + " blocks"));
        }
    }
}
