package dev.onix.client;

import dev.onix.client.config.CwtMode;
import dev.onix.client.util.ServerRules;
import dev.onix.client.config.LauncherInbox;
import dev.onix.client.config.OnixConfig;
import dev.onix.client.config.ProfileManager;
import dev.onix.client.cosmetics.Cosmetics;
import dev.onix.client.cosmetics.CosmeticsFeatureRenderer;
import dev.onix.client.module.impl.visual.OnixBadgeModule;
import dev.onix.client.network.OnixPresence;
import dev.onix.client.gui.Notifications;
import dev.onix.client.gui.OnixMenuScreen;
import dev.onix.client.gui.Theme;
import dev.onix.client.module.ModuleManager;
import dev.onix.client.util.CombatTracker;
import dev.onix.client.util.FrameStats;
import dev.onix.client.util.SessionTracker;
import dev.onix.client.util.TpsTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class OnixClient implements ClientModInitializer {
    public static final String MOD_ID = "onix";
    public static final String NAME = "Onix Client";
    public static final String VERSION = "1.0.0";
    /**
     * The Onix server this build uses when the player has not set one (Settings → Onix Server):
     * badges, coins, the cosmetics shop and gifts. Put the address of your server (see /server) here.
     */
    public static final String DEFAULT_SERVER = "https://mconixclient.virtualchickenfr.workers.dev";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    public static OnixConfig CONFIG;
    public static ModuleManager MODULES;
    public static ProfileManager PROFILES;

    public static KeyBinding MENU_KEY;
    public static KeyBinding CWT_KEY;

    @Override
    public void onInitializeClient() {
        CONFIG = OnixConfig.load();
        MODULES = new ModuleManager();
        MODULES.registerDefaults();
        PROFILES = new ProfileManager(MODULES, CONFIG);
        PROFILES.loadActive();
        Cosmetics.load();
        Theme.refresh();

        MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.onix.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.onix"));
        CWT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.onix.cwt", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.onix"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            dev.onix.client.util.DevTest.tick(client);
            while (MENU_KEY.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new OnixMenuScreen(null));
            }
            while (CWT_KEY.wasPressed()) CwtMode.toggle();
            LauncherInbox.tick();
            OnixBadgeModule badge = MODULES.get(OnixBadgeModule.class);
            boolean badgeOn = badge != null && badge.isEnabled();
            OnixPresence.tick(serverUrl(), badgeOn && badge.sharing(), badgeOn, badge == null ? 15 : badge.lookupSeconds());
            TpsTracker.tick();
            CombatTracker.tick(client);
            SessionTracker.tick(client);
            MODULES.tick();
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            FrameStats.onFrame();
            MODULES.renderHud(context);
            if (MinecraftClient.getInstance().currentScreen == null) Notifications.render(context);
        });

        // Notifications are also drawn on top of menus.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
                ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> Notifications.render(context)));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) CombatTracker.onAttack(entity);
            return ActionResult.PASS;
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            SessionTracker.onJoin();
            // a profile switch already checks the new profile against the server rules
            if (!switchProfileForServer(client)) ServerRules.onJoin(client);
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ServerRules::onLeave));

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
                helper.register(new CosmeticsFeatureRenderer(playerRenderer));
                helper.register(new dev.onix.client.cosmetics.CapeRenderer(playerRenderer));
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MODULES.shutdown();
            save();
        });

        LOGGER.info("{} {} ready – {} modules, profile '{}'", NAME, VERSION, MODULES.all().size(), CONFIG.activeProfile);
    }

    /** Loads the profile linked to the server you just joined. */
    private static boolean switchProfileForServer(MinecraftClient client) {
        if (!CONFIG.autoSwitchProfiles.isOn() || client.getCurrentServerEntry() == null) return false;
        String profile = CONFIG.serverProfiles.get(client.getCurrentServerEntry().address.toLowerCase(Locale.ROOT));
        if (profile != null && !profile.equals(PROFILES.active()) && PROFILES.list().contains(profile)) {
            PROFILES.switchTo(profile);
            Notifications.show("Profile switched", "Loaded \"" + profile + "\" for this server");
            return true;
        }
        return false;
    }

    /** Saves global settings and the active profile. */
    /** The Onix server to talk to: Settings → Onix Server, else the old Onix Badge setting, else DEFAULT_SERVER. */
    public static String serverUrl() {
        if (CONFIG != null && !CONFIG.onixServer.get().isBlank()) return CONFIG.onixServer.get().trim();
        OnixBadgeModule badge = MODULES == null ? null : MODULES.get(OnixBadgeModule.class);
        if (badge != null && !badge.legacyServer().isBlank()) return badge.legacyServer().trim();
        return DEFAULT_SERVER;
    }

    public static void save() {
        if (CONFIG != null) CONFIG.save();
        if (PROFILES != null) PROFILES.saveActive();
    }
}
