package dev.onix.client.util;

import dev.onix.client.OnixClient;
import dev.onix.client.gui.HudEditorScreen;
import dev.onix.client.gui.OnixMenuScreen;
import dev.onix.client.gui.OnixTitleScreen;
import dev.onix.client.module.HudModule;
import dev.onix.client.module.Module;
import gg.carbon.client.screen.CarbonMainMenu;
import gg.carbon.client.screen.ThemePicker;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;

/**
 * Automated visual smoke test, only active with -Donix.devtest=true (gradle runDevtest, also run by CI).
 * Walks through Onix's and Carbon's screens, joins a test world and saves screenshots to run/screenshots.
 */
public final class DevTest {
    private DevTest() {}

    public static final boolean ENABLED = Boolean.getBoolean("onix.devtest");

    private record Step(int delay, BooleanSupplier action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index;
    private static int wait;
    private static int waited;
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

    private static void design(String id) {
        OnixClient.CONFIG.menuDesign.set(id);
        Minecraft.getInstance().setScreen(new TitleScreen());
    }

    private static void build(Minecraft mc) {
        until(() -> mc.screen instanceof OnixTitleScreen || mc.screen instanceof CarbonMainMenu || mc.screen instanceof TitleScreen);
        then(20, () -> mc.options.pauseOnLostFocus = false);
        then(2, () -> design("Onix"));
        then(80, () -> snap(mc, "01_title_onix"));
        for (String id : List.of("Carbon", "Voxel", "Portal", "Classic")) {
            then(2, () -> design(id));
            then(90, () -> snap(mc, "02_title_" + id.toLowerCase(Locale.ROOT)));
        }
        then(2, () -> OnixClient.CONFIG.lightMode.set(true));
        then(60, () -> snap(mc, "02_title_classic_light"));
        then(2, () -> OnixClient.CONFIG.lightMode.set(false));
        then(2, () -> design("Onix"));
        then(20, () -> mc.setScreen(new ThemePicker(mc.screen)));
        then(50, () -> snap(mc, "03_picker"));
        then(2, () -> mc.screen.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT, 0, 0)));
        then(40, () -> snap(mc, "03_picker_next"));
        then(2, () -> design("Onix"));
        then(20, () -> mc.setScreen(new OnixMenuScreen(mc.screen)));
        then(60, () -> snap(mc, "04_menu_hub"));
        for (OnixMenuScreen.View view : List.of(OnixMenuScreen.View.MODULES, OnixMenuScreen.View.COSMETICS, OnixMenuScreen.View.DESIGN, OnixMenuScreen.View.SETTINGS)) {
            then(2, () -> mc.setScreen(new OnixMenuScreen(null, view)));
            then(50, () -> snap(mc, "05_menu_" + view.name().toLowerCase(Locale.ROOT)));
        }
        then(2, () -> mc.setScreen(OnixMenuScreen.forModule(null, OnixClient.MODULES.get(dev.onix.client.module.impl.utility.ShulkerPreviewModule.class))));
        then(40, () -> snap(mc, "06_module_carbon_shulker"));
        then(2, () -> mc.setScreen(new TitleScreen()));
        then(20, () -> {
            if (java.nio.file.Files.isDirectory(mc.gameDirectory.toPath().resolve("saves").resolve("New World"))) {
                mc.createWorldOpenFlows().openWorld("New World", () -> {});
            } else {
                CreateWorldScreen.testWorld(mc, () -> {});
            }
        });
        until(() -> {
            if (mc.player != null && mc.level != null && mc.screen == null) return true;
            acceptDialogs(mc);
            if (++waited % 100 == 0) {
                OnixClient.LOGGER.info("[devtest] waiting for world, screen = {}", mc.screen == null ? "none" : mc.screen.getClass().getName());
            }
            return false;
        });
        then(0, () -> {
            for (Module m : OnixClient.MODULES.all()) {
                String id = m.getId();
                if (m instanceof HudModule && List.of("fps", "coords", "keystrokes", "mousestrokes", "cps", "armor").contains(id)) m.setEnabled(true);
            }
        });
        then(200, () -> snap(mc, "07_hud"));
        then(2, () -> mc.setScreen(new HudEditorScreen(null)));
        then(40, () -> snap(mc, "08_hud_editor"));
        then(2, () -> mc.setScreen(new OnixMenuScreen(null)));
        then(40, () -> snap(mc, "09_menu_ingame"));
        then(2, () -> {
            OnixClient.MODULES.get(dev.onix.client.module.impl.visual.DarkModeModule.class).setEnabled(true);
            OnixClient.MODULES.get(dev.onix.client.module.impl.utility.ShulkerPreviewModule.class).setEnabled(true);
            var server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                sp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                List<net.minecraft.world.item.ItemStack> stuff = new ArrayList<>();
                stuff.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 64));
                stuff.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 12));
                stuff.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 16));
                stuff.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING, 1));
                net.minecraft.world.item.ItemStack box = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHULKER_BOX);
                box.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(stuff));
                sp.getInventory().setItem(9, box);
                sp.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            });
        });
        then(20, () -> {
            var screen = new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player);
            mc.setScreen(screen);
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, g, mx, my, d) -> {
                var box = mc.player.getInventory().getItem(9);
                if (!box.isEmpty()) OnixClient.MODULES.get(dev.onix.client.module.impl.utility.ShulkerPreviewModule.class)
                        .render(g, box, s.width / 2 + 60, s.height / 2 - 70, s.width, s.height);
            });
        });
        then(30, () -> snap(mc, "10_inventory_dark_shulker"));
        then(2, () -> mc.setScreen(null));
        then(2, () -> {
            OnixClient.MODULES.get(dev.onix.client.module.impl.visual.CustomSkyModule.class).setEnabled(true);
            mc.player.setXRot(-30);
        });
        then(60, () -> snap(mc, "11_custom_sky"));
        then(20, () -> {
            OnixClient.LOGGER.info("[devtest] finished");
            mc.stop();
        });
    }

    public static void tick(Minecraft mc) {
        if (!ENABLED) return;
        if (STEPS.isEmpty()) build(mc);
        if (index >= STEPS.size()) return;
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = STEPS.get(index);
        boolean done;
        try {
            done = step.action().getAsBoolean();
        } catch (Throwable t) {
            OnixClient.LOGGER.error("[devtest] step {} failed", index, t);
            done = true;
        }
        if (done) {
            index++;
            if (index < STEPS.size()) wait = STEPS.get(index).delay();
        }
    }

    /** Clicks through "experimental settings" / backup confirmations for the throwaway test world. */
    private static void acceptDialogs(Minecraft mc) {
        var screen = mc.screen;
        if (screen == lastAccepted) return;
        if (!(screen instanceof net.minecraft.client.gui.screens.ConfirmScreen)
                && !(screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen)
                && !(screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen)) return;
        for (var w : net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen)) {
            if (!(w instanceof net.minecraft.client.gui.components.AbstractButton b)) continue;
            String label = b.getMessage().getString().toLowerCase(Locale.ROOT);
            boolean accept = label.equals("create new world") || label.equals("load") || label.contains("proceed") || label.equals("yes") || label.contains("know what")
                    || (label.contains("load") && !label.contains("back"));
            if (accept) {
                OnixClient.LOGGER.info("[devtest] accepting dialog button '{}'", label);
                lastAccepted = screen;
                b.onPress(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 28, 0));
                return;
            }
        }
    }

    private static void snap(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1,
                msg -> OnixClient.LOGGER.info("[devtest] {}", msg.getString()));
    }
}
