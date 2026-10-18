package dev.onix.client.module.impl.utility;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.ChatPipeline;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Gets your attention while Minecraft is in the background: the taskbar icon flashes and a
 * sound plays when a game starts, someone mentions you or you take damage.
 */
public class TabOutAlertsModule extends Module implements ChatPipeline.Processor {
    private final TextSetting phrases = add(new TextSetting("phrases", "Chat Phrases", "game starts in, has started, teleporting", 300)
            .placeholder("game starts in, your turn")
            .describe("Comma separated. Alerts you when a chat line contains one of them."));
    private final BoolSetting mentions = add(new BoolSetting("mentions", "When Someone Says Your Name", true));
    private final BoolSetting damage = add(new BoolSetting("damage", "When You Take Damage", true));
    private final BoolSetting death = add(new BoolSetting("death", "When You Die", true));

    {
        group("Alert");
    }

    private final BoolSetting flash = add(new BoolSetting("flash", "Flash Taskbar", true));
    private final BoolSetting sound = add(new BoolSetting("sound", "Sound", true));
    private final NumberSetting volume = add(new NumberSetting("volume", "Volume", 80, 0, 100, 5).unit("%").visibleIf(sound::isOn));
    private final NumberSetting cooldown = add(new NumberSetting("cooldown", "Cooldown", 5, 1, 60, 1).unit("s")
            .describe("Quiet time after an alert, so a fight does not ring constantly."));

    private long lastAlert;
    private int lastHurtTime;
    private boolean wasDead;

    public TabOutAlertsModule() {
        super("tabout", "Tab-out Alerts", "Flashes the taskbar and plays a sound when something happens while you are tabbed out.",
                Category.UTILITY, false);
        tags("afk", "queue", "background", "notify", "alt tab");
    }

    @Override
    public void init() {
        ChatPipeline.register(this);
    }

    @Override
    public int priority() {
        return 210;
    }

    @Override
    public Component process(Component message) {
        if (!isEnabled()) return message;
        String line = message.getString().toLowerCase(Locale.ROOT);
        boolean hit = false;
        if (mentions.isOn()) {
            String name = mc().getUser().getName().toLowerCase(Locale.ROOT);
            hit = line.contains(name) && !line.startsWith("<" + name + ">");
        }
        for (String p : phrases.get().split(",")) {
            String phrase = p.trim().toLowerCase(Locale.ROOT);
            if (!phrase.isEmpty() && line.contains(phrase)) hit = true;
        }
        if (hit) mc().execute(this::alert);
        return message;
    }

    @Override
    public void onTick() {
        if (mc().player == null) return;
        int hurt = mc().player.hurtTime;
        if (damage.isOn() && hurt > lastHurtTime) alert();
        lastHurtTime = hurt;
        boolean dead = mc().player.isDeadOrDying();
        if (death.isOn() && dead && !wasDead) alert();
        wasDead = dead;
    }

    private void alert() {
        if (mc().isWindowActive()) return;
        long now = System.currentTimeMillis();
        if (now - lastAlert < cooldown.get() * 1000) return;
        lastAlert = now;
        if (flash.isOn()) GLFW.glfwRequestWindowAttention(mc().getWindow().handle());
        if (sound.isOn() && volume.get() > 0) {
            mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f,
                    volume.getFloat() / 100f));
        }
    }
}
