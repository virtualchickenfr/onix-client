package dev.onix.client.module.impl.performance;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/** Applies FPS-friendly video settings and restores yours when turned off. */
public class PerformancePresetModule extends Module {
    private final BoolSetting clouds = add(new BoolSetting("clouds", "Disable Clouds", true));
    private final BoolSetting particles = add(new BoolSetting("particles", "Minimal Particles", true));
    private final BoolSetting shadows = add(new BoolSetting("shadows", "Disable Entity Shadows", true));
    private final BoolSetting biomeBlend = add(new BoolSetting("biomeBlend", "No Biome Blend", true));
    private final BoolSetting fastGraphics = add(new BoolSetting("fast", "Fast Graphics", false));

    private CloudStatus prevClouds;
    private ParticleStatus prevParticles;
    private Boolean prevShadows;
    private Integer prevBlend;
    private GraphicsPreset prevGraphics;

    public PerformancePresetModule() {
        super("performance", "Performance Preset", "One switch for FPS-friendly video settings.", Category.PERFORMANCE, false);
        tags("fps boost", "low end", "potato");
    }

    @Override
    public void onTick() {
        Options o = mc().options;
        if (clouds.isOn()) {
            if (prevClouds == null) prevClouds = o.cloudStatus().get();
            if (o.cloudStatus().get() != CloudStatus.OFF) o.cloudStatus().set(CloudStatus.OFF);
        } else if (prevClouds != null) {
            o.cloudStatus().set(prevClouds);
            prevClouds = null;
        }
        if (particles.isOn()) {
            if (prevParticles == null) prevParticles = o.particles().get();
            if (o.particles().get() != ParticleStatus.MINIMAL) o.particles().set(ParticleStatus.MINIMAL);
        } else if (prevParticles != null) {
            o.particles().set(prevParticles);
            prevParticles = null;
        }
        if (shadows.isOn()) {
            if (prevShadows == null) prevShadows = o.entityShadows().get();
            if (o.entityShadows().get()) o.entityShadows().set(false);
        } else if (prevShadows != null) {
            o.entityShadows().set(prevShadows);
            prevShadows = null;
        }
        if (biomeBlend.isOn()) {
            if (prevBlend == null) prevBlend = o.biomeBlendRadius().get();
            if (o.biomeBlendRadius().get() != 0) o.biomeBlendRadius().set(0);
        } else if (prevBlend != null) {
            o.biomeBlendRadius().set(prevBlend);
            prevBlend = null;
        }
        if (fastGraphics.isOn()) {
            if (prevGraphics == null) prevGraphics = o.graphicsPreset().get();
            if (o.graphicsPreset().get() != GraphicsPreset.FAST) o.graphicsPreset().set(GraphicsPreset.FAST);
        } else if (prevGraphics != null) {
            o.graphicsPreset().set(prevGraphics);
            prevGraphics = null;
        }
    }

    @Override
    protected void onDisable() {
        Options o = mc().options;
        if (o == null) return;
        if (prevClouds != null) o.cloudStatus().set(prevClouds);
        if (prevParticles != null) o.particles().set(prevParticles);
        if (prevShadows != null) o.entityShadows().set(prevShadows);
        if (prevBlend != null) o.biomeBlendRadius().set(prevBlend);
        if (prevGraphics != null) o.graphicsPreset().set(prevGraphics);
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
