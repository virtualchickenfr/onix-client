package dev.onix.client.module.impl.performance;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;

/** Applies FPS-friendly video settings and restores yours when turned off. */
public class PerformancePresetModule extends Module {
    private final BoolSetting clouds = add(new BoolSetting("clouds", "Disable Clouds", true));
    private final BoolSetting particles = add(new BoolSetting("particles", "Minimal Particles", true));
    private final BoolSetting shadows = add(new BoolSetting("shadows", "Disable Entity Shadows", true));
    private final BoolSetting biomeBlend = add(new BoolSetting("biomeBlend", "No Biome Blend", true));
    private final BoolSetting fastGraphics = add(new BoolSetting("fast", "Fast Graphics", false));

    private CloudRenderMode prevClouds;
    private ParticlesMode prevParticles;
    private Boolean prevShadows;
    private Integer prevBlend;
    private GraphicsMode prevGraphics;

    public PerformancePresetModule() {
        super("performance", "Performance Preset", "One switch for FPS-friendly video settings.", Category.PERFORMANCE, false);
        tags("fps boost", "low end", "potato");
    }

    @Override
    public void onTick() {
        GameOptions o = mc().options;
        if (clouds.isOn()) {
            if (prevClouds == null) prevClouds = o.getCloudRenderMode().getValue();
            if (o.getCloudRenderMode().getValue() != CloudRenderMode.OFF) o.getCloudRenderMode().setValue(CloudRenderMode.OFF);
        } else if (prevClouds != null) {
            o.getCloudRenderMode().setValue(prevClouds);
            prevClouds = null;
        }
        if (particles.isOn()) {
            if (prevParticles == null) prevParticles = o.getParticles().getValue();
            if (o.getParticles().getValue() != ParticlesMode.MINIMAL) o.getParticles().setValue(ParticlesMode.MINIMAL);
        } else if (prevParticles != null) {
            o.getParticles().setValue(prevParticles);
            prevParticles = null;
        }
        if (shadows.isOn()) {
            if (prevShadows == null) prevShadows = o.getEntityShadows().getValue();
            if (o.getEntityShadows().getValue()) o.getEntityShadows().setValue(false);
        } else if (prevShadows != null) {
            o.getEntityShadows().setValue(prevShadows);
            prevShadows = null;
        }
        if (biomeBlend.isOn()) {
            if (prevBlend == null) prevBlend = o.getBiomeBlendRadius().getValue();
            if (o.getBiomeBlendRadius().getValue() != 0) o.getBiomeBlendRadius().setValue(0);
        } else if (prevBlend != null) {
            o.getBiomeBlendRadius().setValue(prevBlend);
            prevBlend = null;
        }
        if (fastGraphics.isOn()) {
            if (prevGraphics == null) prevGraphics = o.getGraphicsMode().getValue();
            if (o.getGraphicsMode().getValue() != GraphicsMode.FAST) o.getGraphicsMode().setValue(GraphicsMode.FAST);
        } else if (prevGraphics != null) {
            o.getGraphicsMode().setValue(prevGraphics);
            prevGraphics = null;
        }
    }

    @Override
    protected void onDisable() {
        GameOptions o = mc().options;
        if (o == null) return;
        if (prevClouds != null) o.getCloudRenderMode().setValue(prevClouds);
        if (prevParticles != null) o.getParticles().setValue(prevParticles);
        if (prevShadows != null) o.getEntityShadows().setValue(prevShadows);
        if (prevBlend != null) o.getBiomeBlendRadius().setValue(prevBlend);
        if (prevGraphics != null) o.getGraphicsMode().setValue(prevGraphics);
        prevClouds = null;
        prevParticles = null;
        prevShadows = null;
        prevBlend = null;
        prevGraphics = null;
    }

    @Override
    public void onShutdown() {
        onDisable();
    }
}
