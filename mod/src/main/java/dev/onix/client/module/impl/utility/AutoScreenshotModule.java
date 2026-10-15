package dev.onix.client.module.impl.utility;

import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.ChatPipeline;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

import java.util.Locale;

/** Takes a screenshot when a chat line contains one of your phrases – wins, first places, rare drops. */
public class AutoScreenshotModule extends Module implements ChatPipeline.Processor {
    private final TextSetting phrases = add(new TextSetting("phrases", "Trigger Phrases", "victory, you won, winner, 1st place", 300)
            .placeholder("victory, you won")
            .describe("Comma separated. A chat line containing any of them triggers a screenshot."));
    private final NumberSetting delay = add(new NumberSetting("delay", "Delay", 1, 0, 5, 0.5).unit("s")
            .describe("Wait a moment so the win screen or fireworks are in the shot."));
    private final NumberSetting cooldown = add(new NumberSetting("cooldown", "Cooldown", 20, 5, 120, 5).unit("s")
            .describe("At most one screenshot in this time, even if the phrase repeats."));

    {
        group(GROUP_APPEARANCE);
    }

    private final BoolSetting hideHud = add(new BoolSetting("hideHud", "Hide HUD in the Shot", false)
            .describe("Like pressing F1 for the moment the screenshot is taken."));
    private final BoolSetting notify = add(new BoolSetting("notify", "Notify", true));

    private int countdown = -1;
    private int hudTicks = -1;
    private boolean hudWasHidden;
    private long lastShot;
    private String lastTrigger = "";

    public AutoScreenshotModule() {
        super("autoscreenshot", "Auto Screenshot", "Saves a screenshot when you win – or whenever chat says your words.", Category.UTILITY, false);
        tags("screenshot", "victory", "win", "f2");
    }

    @Override
    public void init() {
        ChatPipeline.register(this);
    }

    @Override
    public int priority() {
        return 200; // only looks, never changes the line
    }

    @Override
    public Text process(Text message) {
        if (!isEnabled()) return message;
        String line = message.getString().toLowerCase(Locale.ROOT);
        for (String p : phrases.get().split(",")) {
            String phrase = p.trim().toLowerCase(Locale.ROOT);
            if (phrase.isEmpty() || !line.contains(phrase)) continue;
            if (System.currentTimeMillis() - lastShot < cooldown.get() * 1000) break;
            lastShot = System.currentTimeMillis();
            lastTrigger = phrase;
            int ticks = Math.max(1, (int) Math.round(delay.get() * 20));
            mc().execute(() -> countdown = ticks);
            break;
        }
        return message;
    }

    @Override
    public void onTick() {
        if (countdown > 0 && --countdown == 0) {
            countdown = -1;
            if (hideHud.isOn()) {
                hudWasHidden = mc().options.hudHidden;
                mc().options.hudHidden = true;
                hudTicks = 2; // let one frame render without the HUD
            } else {
                shoot();
            }
        }
        if (hudTicks > 0 && --hudTicks == 0) {
            hudTicks = -1;
            shoot();
            mc().options.hudHidden = hudWasHidden;
        }
    }

    private void shoot() {
        ScreenshotRecorder.saveScreenshot(mc().runDirectory, mc().getFramebuffer(), result -> mc().execute(() -> {
            if (notify.isOn()) Notifications.show("Screenshot saved", "Triggered by \"" + lastTrigger + "\"");
            else if (mc().inGameHud != null) mc().inGameHud.getChatHud().addMessage(result);
        }));
    }

    @Override
    protected void onDisable() {
        countdown = -1;
        if (hudTicks > 0) mc().options.hudHidden = hudWasHidden;
        hudTicks = -1;
    }
}
