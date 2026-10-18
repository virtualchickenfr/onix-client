package dev.onix.client.gui;

import dev.onix.client.OnixClient;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Small toasts in a screen corner (Settings → Notifications → Position), e.g. "Profile switched". */
public final class Notifications {
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private record Entry(String title, String text, long start, Anim anim, boolean warning) {}

    private Notifications() {}

    private static long duration() {
        return OnixClient.CONFIG == null ? 2800 : (long) (OnixClient.CONFIG.notificationTime.get() * 1000);
    }

    public static void show(String title, String text) {
        add(title, text, false);
    }

    /** Same as {@link #show}, marked orange – for things worth a second look (e.g. server rules). */
    public static void warn(String title, String text) {
        add(title, text, true);
    }

    private static void add(String title, String text, boolean warning) {
        if (OnixClient.CONFIG != null && !OnixClient.CONFIG.notifications.isOn()) return;
        synchronized (ENTRIES) {
            ENTRIES.add(new Entry(title, text, System.currentTimeMillis(), new Anim(0), warning));
            while (ENTRIES.size() > 4) ENTRIES.remove(0);
        }
        if (OnixClient.CONFIG == null || OnixClient.CONFIG.notificationSound.isOn()) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f, 0.4f));
        }
    }

    public static void render(GuiGraphics ctx) {
        if (ENTRIES.isEmpty()) return;
        Theme.refresh();
        int screenW = ctx.guiWidth();
        int screenH = ctx.guiHeight();
        String pos = OnixClient.CONFIG == null ? "Top Right" : OnixClient.CONFIG.notificationPosition.get();
        boolean right = pos.endsWith("Right");
        boolean bottom = pos.startsWith("Bottom");
        int h = 28;
        int y = bottom ? screenH - 8 - h : 8;
        long now = System.currentTimeMillis();
        synchronized (ENTRIES) {
            Iterator<Entry> it = ENTRIES.iterator();
            while (it.hasNext()) {
                Entry e = it.next();
                long age = now - e.start();
                if (age > duration() + 300) {
                    it.remove();
                    continue;
                }
                float t = Anim.easeOut(e.anim().to(age > duration() ? 0f : 1f, 12f));
                int w = Math.max(Theme.boldWidth(e.title()), Theme.smallWidth(e.text())) + 30;
                int slide = Math.round((w + 10) * (1 - t));
                int x = right ? screenW - 10 - w + slide : 10 - slide;
                int mark = e.warning() ? Theme.ORANGE : Theme.accent();
                Theme.shadow(ctx, x, y, x + w, y + h, 7);
                Theme.card(ctx, x, y, x + w, y + h, 7, Theme.SURFACE, e.warning() ? Theme.withAlpha(Theme.ORANGE, 150) : Theme.BORDER_STRONG);
                Theme.round(ctx, x + 9, y + 10, x + 14, y + 15, 2, mark);
                Theme.textBold(ctx, e.title(), x + 20, y + 6, Theme.TEXT);
                Theme.small(ctx, e.text(), x + 20, y + 17, Theme.TEXT_2);
                // time left
                float left = 1f - Math.min(1f, age / (float) duration());
                ctx.fill(x + 8, y + h - 2, x + 8 + Math.round((w - 16) * left), y + h - 1, Theme.withAlpha(mark, 140));
                y += Math.round((h + 4) * t) * (bottom ? -1 : 1);
            }
        }
    }
}
