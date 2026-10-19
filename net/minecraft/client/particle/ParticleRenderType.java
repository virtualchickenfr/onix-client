package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record ParticleRenderType(String name, @Nullable RenderType renderType) {
	public static final ParticleRenderType TERRAIN_SHEET = new ParticleRenderType("TERRAIN_SHEET", RenderType.translucentParticle(TextureAtlas.LOCATION_BLOCKS));
	public static final ParticleRenderType PARTICLE_SHEET_OPAQUE = new ParticleRenderType(
		"PARTICLE_SHEET_OPAQUE", RenderType.opaqueParticle(TextureAtlas.LOCATION_PARTICLES)
	);
	public static final ParticleRenderType PARTICLE_SHEET_TRANSLUCENT = new ParticleRenderType(
		"PARTICLE_SHEET_TRANSLUCENT", RenderType.translucentParticle(TextureAtlas.LOCATION_PARTICLES)
	);
	public static final ParticleRenderType CUSTOM = new ParticleRenderType("CUSTOM", null);
	public static final ParticleRenderType NO_RENDER = new ParticleRenderType("NO_RENDER", null);
}
