package dev.onix.client;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class OnixClient implements ClientModInitializer {
    public static final String MOD_ID = "onix";
    public static final String NAME = "Onix Client";
    public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("onix")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    /**
     * The Onix server this build uses when the player has not set one (Settings → Onix Server):
     * badges, coins, the cosmetics shop and gifts. Put the address of your server (see /server) here.
     */
    public static final String DEFAULT_SERVER = "https://mconixclient.virtualchickenfr.workers.dev";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    public static OnixConfig CONFIG;
    public static ModuleManager MODULES;
    public static ProfileManager PROFILES;

    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    public static KeyMapping MENU_KEY;
    public static KeyMapping CWT_KEY;

    @Override
    public void onInitializeClient() {
        CONFIG = OnixConfig.load();
        MODULES = new ModuleManager();
        MODULES.registerDefaults();
        PROFILES = new ProfileManager(MODULES, CONFIG);
        PROFILES.loadActive();
        Cosmetics.load();
        Theme.refresh();

        MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.onix.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KEY_CATEGORY));
        CWT_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.onix.cwt", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (MENU_KEY.consumeClick()) {
                if (client.screen == null) client.setScreen(new OnixMenuScreen(null));
            }
            while (CWT_KEY.consumeClick()) CwtMode.toggle();
            LauncherInbox.tick();
            OnixBadgeModule badge = MODULES.get(OnixBadgeModule.class);
            boolean badgeOn = badge != null && badge.isEnabled();
            // others need to find you for your badge and for your name style and tag
            boolean looksOn = CONFIG.nameTagLooks.isOn();
            boolean share = badge != null && badge.sharing() && (badgeOn || looksOn);
            OnixPresence.tick(serverUrl(), share, badgeOn || looksOn, badge == null ? 15 : badge.lookupSeconds());
            if (CONFIG.socialOn.isOn() && !OnixPresence.server().isEmpty()) {
                dev.onix.client.social.Social.tick(client.screen instanceof OnixMenuScreen);
            }
            dev.onix.client.social.Social.tickShot();
            TpsTracker.tick();
            CombatTracker.tick(client);
            SessionTracker.tick(client);
            MODULES.tick();
            dev.onix.client.util.DevTest.tick(client);
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            FrameStats.onFrame();
            MODULES.renderHud(context);
            if (Minecraft.getInstance().screen == null) Notifications.render(context);
        });

        // Notifications are also drawn on top of menus.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
                ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> Notifications.render(context)));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) CombatTracker.onAttack(entity);
            return InteractionResult.PASS;
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            SessionTracker.onJoin();
            // a profile switch already checks the new profile against the server rules
            if (!switchProfileForServer(client)) ServerRules.onJoin(client);
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ServerRules::onLeave));

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
                @SuppressWarnings("unchecked")
                var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<net.minecraft.client.renderer.entity.state.AvatarRenderState, net.minecraft.client.model.player.PlayerModel>) (Object) avatarRenderer;
                helper.register(new CosmeticsFeatureRenderer(parent));
                helper.register(new dev.onix.client.cosmetics.CapeRenderer(parent));
            }
        });

        // 3D cosmetic icons in menus are rendered into small textures (see CosmeticIcons)
        net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry.register(
                context -> new dev.onix.client.cosmetics.CosmeticIcons.Renderer(context.vertexConsumers()));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MODULES.shutdown();
            save();
        });

        LOGGER.info("{} {} ready – {} modules, profile '{}'", NAME, VERSION, MODULES.all().size(), CONFIG.activeProfile);
    }

    /** Loads the profile linked to the server you just joined. */
    private static boolean switchProfileForServer(Minecraft client) {
        if (!CONFIG.autoSwitchProfiles.isOn() || client.getCurrentServer() == null) return false;
        String profile = CONFIG.serverProfiles.get(client.getCurrentServer().ip.toLowerCase(Locale.ROOT));
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
