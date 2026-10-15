package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.render.Skies;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.OptionOverride;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.world.World;

/**
 * Replaces the overworld sky with a sky box – a day sky and an optional night sky that fade
 * into each other with the time of day. Drawn by {@link dev.onix.client.render.SkyRenderer}.
 */
public class CustomSkyModule extends Module {
    public static final String SAME_AS_DAY = "Same as Day";

    public final ModeSetting daySky = add(ModeSetting.dynamic("daySky", "Day Sky", "Clear Day", Skies::names));
    public final ModeSetting nightSky = add(ModeSetting.dynamic("nightSky", "Night Sky", "Starry Night", () -> {
        String[] skies = Skies.names();
        String[] out = new String[skies.length + 1];
        out[0] = SAME_AS_DAY;
        System.arraycopy(skies, 0, out, 1, skies.length);
        return out;
    }).describe("Fades in at dusk and out at dawn."));
    public final NumberSetting brightness = add(new NumberSetting("brightness", "Brightness", 100, 20, 100, 5).unit("%"));
    public final BoolSetting darkenAtNight = add(new BoolSetting("darkenAtNight", "Darken at Night", true)
            .describe("Dims the sky at night when day and night use the same sky.").visibleIf(() -> nightSky.is(SAME_AS_DAY)));
    public final NumberSetting rotation = add(new NumberSetting("rotation", "Rotation Speed", 0.5, 0, 10, 0.25).unit("°/min")
            .describe("Slowly turns the sky. 0 keeps it still."));

    {
        group("World");
    }

    public final BoolSetting sunAndMoon = add(new BoolSetting("sunAndMoon", "Sun and Moon", true));
    public final BoolSetting matchFog = add(new BoolSetting("matchFog", "Blend Fog Into Sky", true)
            .describe("Tints the distance fog with the sky's horizon so the world edge does not stand out."));
    public final BoolSetting hideClouds = add(new BoolSetting("hideClouds", "Hide Vanilla Clouds", false));
    public final BoolSetting inEnd = add(new BoolSetting("inEnd", "Also in the End", false));

    private final OptionOverride<CloudRenderMode> clouds = new OptionOverride<>(GameOptions::getCloudRenderMode);

    public CustomSkyModule() {
        super("customsky", "Custom Sky", "Pick from many skies, with separate day and night skies.", Category.VISUAL, false);
        tags("skybox", "sky", "sunset", "stars", "night sky", "aurora", "space");
    }

    /** Whether the sky should be replaced in the world the player is in. */
    public boolean appliesHere() {
        if (!isEnabled() || mc().world == null) return false;
        var key = mc().world.getRegistryKey();
        return key == World.OVERWORLD || (key == World.END && inEnd.isOn());
    }

    public Skies.Sky day() {
        Skies.Sky sky = Skies.byName(daySky.get());
        return sky != null ? sky : Skies.all().get(0);
    }

    /** The night sky, or null when day and night share one sky. */
    public Skies.Sky night() {
        return nightSky.is(SAME_AS_DAY) ? null : Skies.byName(nightSky.get());
    }

    @Override
    public void onTick() {
        if (hideClouds.isOn()) clouds.apply(CloudRenderMode.OFF);
        else clouds.restore();
    }

    @Override
    protected void onDisable() {
        clouds.restore();
    }

    @Override
    public void onShutdown() {
        clouds.restore();
    }
}
