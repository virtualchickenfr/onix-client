package dev.onix.client.module;

import dev.onix.client.OnixClient;
import dev.onix.client.gui.HudEditorScreen;
import dev.onix.client.gui.Notifications;
import dev.onix.client.gui.Theme;
import dev.onix.client.module.impl.camera.*;
import dev.onix.client.module.impl.chat.*;
import dev.onix.client.module.impl.combat.*;
import dev.onix.client.module.impl.hud.*;
import dev.onix.client.module.impl.performance.*;
import dev.onix.client.module.impl.utility.*;
import dev.onix.client.module.impl.visual.*;
import dev.onix.client.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public void registerDefaults() {
        // HUD
        register(new FpsModule());
        register(new FrameGraphModule());
        register(new CpsModule());
        register(new PingModule());
        register(new TpsModule());
        register(new CoordinatesModule());
        register(new DirectionModule());
        register(new BiomeModule());
        register(new ChunkInfoModule());
        register(new LightLevelModule());
        register(new SpeedModule());
        register(new FallDistanceModule());
        register(new ClockModule());
        register(new DateModule());
        register(new GameTimeModule());
        register(new DayCounterModule());
        register(new MoonPhaseModule());
        register(new WeatherDisplayModule());
        register(new MemoryModule());
        register(new ServerAddressModule());
        register(new PlayerCountModule());
        register(new SessionTimeModule());
        register(new StopwatchModule());
        register(new CountdownModule());
        register(new KeystrokesModule());
        register(new ArmorStatusModule());
        register(new PotionEffectsModule());
        register(new HeldItemModule());
        register(new CompassModule());
        register(new InventoryViewerModule());
        register(new HungerModule());
        register(new HealthModule());
        register(new ExperienceModule());
        register(new AirModule());
        register(new PackDisplayModule());
        register(new WatermarkModule());
        register(new ModuleListModule());
        register(new DeathCoordinatesModule());
        register(new WaypointsModule());
        register(new ElytraInfoModule());
        register(new MountInfoModule());
        register(new DurabilityWarningModule());
        register(new PickupNotifierModule());
        register(new BlockInfoModule());
        register(new CustomTextModule());
        // Combat
        register(new ToggleSprintModule());
        register(new ToggleSneakModule());
        register(new HitEffectsModule());
        register(new KillEffectsModule());
        register(new HitSoundModule());
        register(new CrosshairModule());
        register(new NoHurtCamModule());
        register(new LowHealthWarningModule());
        register(new DamageFlashModule());
        register(new TotemWarningModule());
        register(new ReachDisplayModule());
        register(new ComboCounterModule());
        register(new TargetHudModule());
        register(new SessionStatsModule());
        register(new CooldownIndicatorModule());
        register(new ItemCooldownsModule());
        register(new ArrowCounterModule());
        register(new TotemCounterModule());
        register(new PearlCounterModule());
        register(new GappleCounterModule());
        register(new PotionCounterModule());
        register(new BlockCounterModule());
        register(new ResourceCounterModule());
        register(new CustomItemCounterModule());
        // Visual
        register(new AtmosphereModule());
        register(new FullbrightModule());
        register(new WeatherChangerModule());
        register(new TimeChangerModule());
        register(new TrailModule());
        register(new dev.onix.client.module.impl.visual.JumpCirclesModule());
        register(new dev.onix.client.module.impl.visual.HandAuraModule());
        register(new dev.onix.client.module.impl.visual.SwingAnimationModule());
        register(new dev.onix.client.module.impl.visual.WeaponInspectModule());
        register(new dev.onix.client.module.impl.visual.ColorGradingModule());
        register(new dev.onix.client.module.impl.visual.CustomGlintModule());
        register(new dev.onix.client.module.impl.visual.TotemSpiritModule());
        register(new NametagsModule());
        register(new OnixBadgeModule());
        register(new CustomSkyModule());
        register(new LowFireModule());
        register(new BlockOutlineModule());
        register(new ChunkBordersModule());
        register(new HitboxesModule());
        register(new HandViewModelModule());
        register(new OverlaysModule());
        register(new ScoreboardModule());
        register(new BossBarModule());
        register(new TabListModule());
        // Camera
        register(new ZoomModule());
        register(new FreelookModule());
        register(new PerspectiveModule());
        register(new CameraTweaksModule());
        register(new FovEffectsModule());
        register(new SensitivitySwitchModule());
        // Chat
        register(new ChatCleanerModule());
        register(new ChatTimestampsModule());
        register(new ChatMentionsModule());
        register(new ChatHistoryModule());
        register(new ChatFilterModule());
        register(new ChatMacrosModule());
        register(new AutoGgModule());
        // Utility
        register(new AutoToolModule());
        register(new PanicModule());
        register(new AutoRespawnModule());
        register(new AutoReconnectModule());
        register(new CopyCoordinatesModule());
        register(new ItemTooltipsModule());
        register(new AutoScreenshotModule());
        register(new TabOutAlertsModule());
        register(new FriendsModule());
        register(new StreamerModeModule());
        register(new ExtraKeybindsModule());
        register(new KeybindProfilesModule());
        // Performance
        register(new UnfocusedFpsModule());
        register(new BackgroundMuteModule());
        register(new PerformancePresetModule());
        register(new EntityDistanceModule());
        register(new RenderDistanceToggleModule());

        // from Carbon Client
        register(new dev.onix.client.module.impl.hud.MousestrokesModule());
        register(new dev.onix.client.module.impl.visual.DarkModeModule());
        register(new dev.onix.client.module.impl.visual.TitleTweakerModule());
        register(new dev.onix.client.module.impl.camera.AutoPerspectiveModule());
        register(new dev.onix.client.module.impl.utility.ShulkerPreviewModule());
        register(new dev.onix.client.module.impl.utility.DropPreventionModule());
        register(new dev.onix.client.module.impl.utility.ToastControlModule());
        register(new dev.onix.client.module.impl.performance.ParticleLimiterModule());

        for (Module m : modules) {
            try {
                m.init();
            } catch (Exception e) {
                OnixClient.LOGGER.error("Could not initialise module {}", m.getId(), e);
            }
        }
    }

    public void register(Module module) {
        modules.add(module);
    }

    public List<Module> all() {
        return Collections.unmodifiableList(modules);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> type) {
        for (Module m : modules) if (type.isInstance(m)) return (T) m;
        return null;
    }

    /** True if the module exists and is enabled. */
    public boolean isOn(Class<? extends Module> type) {
        Module m = get(type);
        return m != null && m.isEnabled();
    }

    public List<HudModule> hudModules() {
        List<HudModule> list = new ArrayList<>();
        for (Module m : modules) if (m instanceof HudModule hud) list.add(hud);
        return list;
    }

    // ---------------------------------------------------------------- search

    /** Result of a search: the module and, if it matched through a setting, that setting's name. */
    public record Match(Module module, String viaSetting) {}

    public List<Match> search(Category category, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Match> result = new ArrayList<>();
        for (Module m : modules) {
            if (category != null && m.getCategory() != category) continue;
            if (q.isEmpty() || contains(m.getName(), q) || contains(m.getDescription(), q)
                    || m.getTags().stream().anyMatch(t -> contains(t, q)) || contains(m.getCategory().getDisplayName(), q)) {
                result.add(new Match(m, null));
                continue;
            }
            for (Setting<?, ?> s : m.getSettings()) {
                if (!s.isHidden() && (contains(s.getName(), q) || contains(s.getDescription(), q))) {
                    result.add(new Match(m, s.getName()));
                    break;
                }
            }
        }
        result.sort(Comparator.comparing((Match r) -> r.viaSetting() != null)
                .thenComparing(r -> !r.module().getName().toLowerCase(Locale.ROOT).startsWith(q))
                .thenComparing(r -> r.module().getName().toLowerCase(Locale.ROOT)));
        return result;
    }

    private static boolean contains(String text, String q) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(q);
    }

    public int count(Category category) {
        int n = 0;
        for (Module m : modules) if (category == null || m.getCategory() == category) n++;
        return n;
    }

    // ---------------------------------------------------------------- loop

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null) return;
        for (Module m : modules) {
            if (m.toggleKey.poll()) {
                m.toggle();
                if (m.toggleNotify.isOn()) Notifications.show(m.getName(), m.isEnabled() ? "Enabled" : "Disabled");
                OnixClient.save();
            }
            if (!m.isEnabled()) continue;
            try {
                m.onTick();
            } catch (Exception e) {
                OnixClient.LOGGER.error("Error in module {}", m.getId(), e);
            }
        }
    }

    public void renderHud(GuiGraphics ctx) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        Theme.refresh();
        for (Module m : modules) {
            if (m instanceof OverlayRenderer overlay && m.isEnabled()) {
                try {
                    overlay.renderOverlay(ctx);
                } catch (Exception e) {
                    OnixClient.LOGGER.error("Error drawing overlay of {}", m.getId(), e);
                }
            }
        }
        if (dev.onix.client.util.DebugScreen.open() && OnixClient.CONFIG.hideHudInDebug.isOn()) return;
        if (client.screen instanceof HudEditorScreen) return;
        StreamerModeModule streamer = get(StreamerModeModule.class);
        for (Module m : modules) {
            if (!(m instanceof HudModule hud) || !hud.isEnabled()) continue;
            if (streamer != null && streamer.hides(hud)) continue;
            try {
                hud.renderAtPosition(ctx, false);
            } catch (Exception e) {
                OnixClient.LOGGER.error("Error drawing {}", hud.getId(), e);
            }
        }
    }

    public void shutdown() {
        for (Module m : modules) m.onShutdown();
    }
}
