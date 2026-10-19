package dev.onix.client.util;

import dev.onix.client.OnixClient;
import dev.onix.client.gui.HudEditorScreen;
import dev.onix.client.gui.OnixMenuScreen;
import dev.onix.client.module.HudModule;
import dev.onix.client.module.Module;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.util.ScreenshotRecorder;

/**
 * Automated visual smoke test, only active with -Donix.devtest=true (gradle runDevtest, also run by CI).
 * Walks through Onix's screens, creates a world and saves screenshots to run/screenshots.
 */
public final class DevTest {
    private DevTest() {}

    public static final boolean ENABLED = Boolean.getBoolean("onix.devtest");

    private record Step(int delay, BooleanSupplier action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index;
    private static int wait;
    private static int waited;
    private static int ticks;
    private static Object lastAccepted;

    private static void then(int delay, Runnable r) {
        STEPS.add(new Step(delay, () -> {
            r.run();
            return true;
        }));
    }

    private static void until(BooleanSupplier condition) {
        STEPS.add(new Step(0, condition));
    }

    private static void build(MinecraftClient mc) {
        until(() -> mc.getOverlay() == null && mc.currentScreen instanceof TitleScreen
                || mc.getOverlay() == null && mc.currentScreen != null && mc.currentScreen.getClass().getSimpleName().contains("Title"));
        then(20, () -> mc.options.pauseOnLostFocus = false);
        then(60, () -> snap(mc, "01_title_onix"));
        then(2, () -> mc.setScreen(new OnixMenuScreen(mc.currentScreen)));
        then(60, () -> snap(mc, "04_menu_hub"));
        for (OnixMenuScreen.View view : List.of(OnixMenuScreen.View.MODULES, OnixMenuScreen.View.COSMETICS, OnixMenuScreen.View.DESIGN, OnixMenuScreen.View.SETTINGS)) {
            then(2, () -> mc.setScreen(new OnixMenuScreen(null, view)));
            then(50, () -> snap(mc, "05_menu_" + view.name().toLowerCase(Locale.ROOT)));
        }
        then(2, () -> mc.setScreen(new net.minecraft.client.gui.screen.option.OptionsScreen(new TitleScreen(), mc.options)));
        then(40, () -> snap(mc, "06c_vanilla_options"));
        then(2, () -> CreateWorldScreen.create(mc, new TitleScreen()));
        until(() -> {
            if (mc.player != null && mc.world != null && mc.currentScreen == null) return true;
            acceptDialogs(mc);
            if (++waited % 100 == 0) {
                OnixClient.LOGGER.info("[devtest] waiting for world, screen = {}", mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName());
            }
            return false;
        });
        then(0, () -> {
            for (Module m : OnixClient.MODULES.all()) {
                if (m instanceof HudModule && List.of("fps", "coords", "keystrokes", "cps", "armor").contains(m.getId())) m.setEnabled(true);
            }
        });
        then(200, () -> snap(mc, "07_hud"));
        then(2, () -> mc.setScreen(new HudEditorScreen(null)));
        then(40, () -> snap(mc, "08_hud_editor"));
        then(2, () -> mc.setScreen(new OnixMenuScreen(null)));
        then(40, () -> snap(mc, "09_menu_ingame"));
        then(2, () -> mc.setScreen(new net.minecraft.client.gui.screen.GameMenuScreen(true)));
        then(40, () -> snap(mc, "10b_pause"));
        then(2, () -> mc.setScreen(null));
        then(2, () -> {
            OnixClient.MODULES.get(dev.onix.client.module.impl.visual.CustomSkyModule.class).setEnabled(true);
            mc.player.setPitch(-30);
        });
        then(60, () -> snap(mc, "11_custom_sky"));
        then(20, () -> {
            OnixClient.LOGGER.info("[devtest] finished");
            mc.scheduleStop();
        });
    }

    public static void tick(MinecraftClient mc) {
        if (!ENABLED) return;
        if (STEPS.isEmpty()) build(mc);
        if (++ticks % 200 == 0) {
            OnixClient.LOGGER.info("[devtest] tick {}, step {}/{}, screen {}, overlay {}", ticks, index, STEPS.size(),
                    mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName(), mc.getOverlay() != null);
        }
        if (index >= STEPS.size()) return;
        if (wait > 0) {
            wait--;
            return;
        }
        boolean done;
        try {
            done = STEPS.get(index).action().getAsBoolean();
        } catch (Throwable t) {
            OnixClient.LOGGER.error("[devtest] step {} failed", index, t);
            done = true;
        }
        if (done) {
            OnixClient.LOGGER.info("[devtest] step {} done", index);
            index++;
            if (index < STEPS.size()) wait = STEPS.get(index).delay();
        }
    }

    /** Clicks through "create world", experimental-settings and backup confirmations for the throwaway test world. */
    private static void acceptDialogs(MinecraftClient mc) {
        var screen = mc.currentScreen;
        if (screen == null || screen == lastAccepted) return;
        for (var w : net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen)) {
            if (!(w instanceof net.minecraft.client.gui.widget.PressableWidget b)) continue;
            String label = b.getMessage().getString().toLowerCase(Locale.ROOT);
            boolean accept = label.equals("create new world") || label.contains("proceed") || label.equals("yes") || label.contains("know what")
                    || (label.contains("load") && !label.contains("back"));
            if (accept) {
                OnixClient.LOGGER.info("[devtest] accepting dialog button '{}'", label);
                lastAccepted = screen;
                b.onPress();
                return;
            }
        }
    }

    private static void snap(MinecraftClient mc, String name) {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name + ".png", mc.getFramebuffer(),
                msg -> OnixClient.LOGGER.info("[devtest] {}", msg.getString()));
    }
}
