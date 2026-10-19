package net.minecraft.client.render;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.util.Util;

/**
 * Defines settings that should be used when rendering something.
 * 
 * <p>This includes {@linkplain VertexFormat vertex format}, {@linkplain
 * VertexFormat.DrawMode draw mode}, {@linkplain
 * net.minecraft.client.gl.ShaderProgram shader program}, texture,
 * some uniform variables values (such as {@code LineWidth} when using the
 * {@link #getLines rendertype_lines} shader
 * program), and some GL state values (such as whether to enable depth
 * testing).
 * 
 * <p>Before drawing something, a render layer setups these states. After
 * drawing something, a render layer resets those states to default.
 */
@Environment(EnvType.CLIENT)
public abstract class RenderLayer extends RenderPhase {
	private static final int field_32777 = 1048576;
	public static final int SOLID_BUFFER_SIZE = 4194304;
	public static final int CUTOUT_BUFFER_SIZE = 786432;
	public static final int DEFAULT_BUFFER_SIZE = 1536;
	private static final RenderLayer SOLID = of(
		"solid",
		4194304,
		true,
		false,
		RenderPipelines.SOLID,
		RenderLayer.MultiPhaseParameters.builder().lightmap(ENABLE_LIGHTMAP).texture(MIPMAP_BLOCK_ATLAS_TEXTURE).build(true)
	);
	private static final RenderLayer CUTOUT_MIPPED = of(
		"cutout_mipped",
		4194304,
		true,
		false,
		RenderPipelines.CUTOUT_MIPPED,
		RenderLayer.MultiPhaseParameters.builder().lightmap(ENABLE_LIGHTMAP).texture(MIPMAP_BLOCK_ATLAS_TEXTURE).build(true)
	);
	private static final RenderLayer CUTOUT = of(
		"cutout",
		786432,
		true,
		false,
		RenderPipelines.CUTOUT,
		RenderLayer.MultiPhaseParameters.builder().lightmap(ENABLE_LIGHTMAP).texture(BLOCK_ATLAS_TEXTURE).build(true)
	);
	private static final RenderLayer TRANSLUCENT = of(
		"translucent",
		786432,
		true,
		true,
		RenderPipelines.TRANSLUCENT,
		RenderLayer.MultiPhaseParameters.builder().lightmap(ENABLE_LIGHTMAP).texture(MIPMAP_BLOCK_ATLAS_TEXTURE).target(TRANSLUCENT_TARGET).build(true)
	);
	private static final RenderLayer TRANSLUCENT_MOVING_BLOCK = of(
		"translucent_moving_block",
		786432,
		false,
		true,
		RenderPipelines.RENDERTYPE_TRANSLUCENT_MOVING_BLOCK,
		RenderLayer.MultiPhaseParameters.builder().lightmap(ENABLE_LIGHTMAP).texture(MIPMAP_BLOCK_ATLAS_TEXTURE).target(ITEM_ENTITY_TARGET).build(true)
	);
	private static final Function<Identifier, RenderLayer> ARMOR_CUTOUT_NO_CULL = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.layering(VIEW_OFFSET_Z_LAYERING)
				.build(true);
			return of("armor_cutout_no_cull", 1536, true, false, RenderPipelines.ARMOR_CUTOUT_NO_CULL, multiPhaseParameters);
		})
	);
	private static final Function<Identifier, RenderLayer> ARMOR_TRANSLUCENT = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.layering(VIEW_OFFSET_Z_LAYERING)
				.build(true);
			return of("armor_translucent", 1536, true, true, RenderPipelines.ARMOR_TRANSLUCENT, multiPhaseParameters);
		})
	);
	private static final Function<Identifier, RenderLayer> ENTITY_SOLID = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.build(true);
			return of("entity_solid", 1536, true, false, RenderPipelines.ENTITY_SOLID, multiPhaseParameters);
		})
	);
	private static final Function<Identifier, RenderLayer> ENTITY_SOLID_Z_OFFSET_FORWARD = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.layering(VIEW_OFFSET_Z_LAYERING_FORWARD)
				.build(true);
			return of("entity_solid_z_offset_forward", 1536, true, false, RenderPipelines.ENTITY_SOLID_OFFSET_FORWARD, multiPhaseParameters);
		})
	);
	private static final Function<Identifier, RenderLayer> ENTITY_CUTOUT = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.build(true);
			return of("entity_cutout", 1536, true, false, RenderPipelines.ENTITY_CUTOUT, multiPhaseParameters);
		})
	);
	private static final BiFunction<Identifier, Boolean, RenderLayer> ENTITY_CUTOUT_NO_CULL = Util.memoize(
		(BiFunction<Identifier, Boolean, RenderLayer>)((texture, affectsOutline) -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.build(affectsOutline);
			return of("entity_cutout_no_cull", 1536, true, false, RenderPipelines.ENTITY_CUTOUT_NO_CULL, multiPhaseParameters);
		})
	);
	private static final BiFunction<Identifier, Boolean, RenderLayer> ENTITY_CUTOUT_NO_CULL_Z_OFFSET = Util.memoize(
		(BiFunction<Identifier, Boolean, RenderLayer>)((texture, affectsOutline) -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.layering(VIEW_OFFSET_Z_LAYERING)
				.build(affectsOutline);
			return of("entity_cutout_no_cull_z_offset", 1536, true, false, RenderPipelines.ENTITY_CUTOUT_NO_CULL_Z_OFFSET, multiPhaseParameters);
		})
	);
	private static final Function<Identifier, RenderLayer> ITEM_ENTITY_TRANSLUCENT_CULL = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.target(ITEM_ENTITY_TARGET)
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.build(true);
			return of("item_entity_translucent_cull", 1536, true, true, RenderPipelines.RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL, multiPhaseParameters);
		})
	);
	private static final BiFunction<Identifier, Boolean, RenderLayer> ENTITY_TRANSLUCENT = Util.memoize(
		(BiFunction<Identifier, Boolean, RenderLayer>)((texture, affectsOutline) -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.build(affectsOutline);
			return of("entity_translucent", 1536, true, true, RenderPipelines.ENTITY_TRANSLUCENT, multiPhaseParameters);
		})
	);
	private static final BiFunction<Identifier, Boolean, RenderLayer> ENTITY_TRANSLUCENT_EMISSIVE = Util.memoize(
		(BiFunction<Identifier, Boolean, RenderLayer>)((texture, affectsOutline) -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.overlay(ENABLE_OVERLAY_COLOR)
				.build(affectsOutline);
			return of("entity_translucent_emissive", 1536, true, true, RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE, multiPhaseParameters);
		})
	);
	private static final Function<Identifier, RenderLayer> ENTITY_SMOOTH_CUTOUT = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.build(true);
			return of("entity_smooth_cutout", 1536, RenderPipelines.ENTITY_SMOOTH_CUTOUT, multiPhaseParameters);
		})
	);
	private static final BiFunction<Identifier, Boolean, RenderLayer> BEACON_BEAM = Util.memoize(
		(BiFunction<Identifier, Boolean, RenderLayer>)((texture, affectsOutline) -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.build(false);
			return of(
				"beacon_beam", 1536, false, true, affectsOutline ? RenderPipelines.BEACON_BEAM_TRANSLUCENT : RenderPipelines.BEACON_BEAM_OPAQUE, multiPhaseParameters
			);
		})
	);
	private static final Function<Identifier, RenderLayer> ENTITY_DECAL = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.build(false);
			return of("entity_decal", 1536, RenderPipelines.RENDERTYPE_ENTITY_DECAL, multiPhaseParameters);
		})
	);
	private static final Function<Identifier, RenderLayer> ENTITY_NO_OUTLINE = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.build(false);
			return of("entity_no_outline", 1536, false, true, RenderPipelines.ENTITY_NO_OUTLINE, multiPhaseParameters);
		})
	);
	private static final Function<Identifier, RenderLayer> ENTITY_SHADOW = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.layering(VIEW_OFFSET_Z_LAYERING)
				.build(false);
			return of("entity_shadow", 1536, false, false, RenderPipelines.RENDERTYPE_ENTITY_SHADOW, multiPhaseParameters);
		})
	);
	private static final Function<Identifier, RenderLayer> ENTITY_ALPHA = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> {
			RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.build(true);
			return of("entity_alpha", 1536, RenderPipelines.RENDERTYPE_ENTITY_ALPHA, multiPhaseParameters);
		})
	);
	private static final Function<Identifier, RenderLayer> EYES = Util.memoize((Function<Identifier, RenderLayer>)(texture -> {
		RenderPhase.Texture texture2 = new RenderPhase.Texture(texture, TriState.FALSE, false);
		return of("eyes", 1536, false, true, RenderPipelines.ENTITY_EYES, RenderLayer.MultiPhaseParameters.builder().texture(texture2).build(false));
	}));
	private static final RenderLayer LEASH = of(
		"leash", 1536, RenderPipelines.RENDERTYPE_LEASH, RenderLayer.MultiPhaseParameters.builder().texture(NO_TEXTURE).lightmap(ENABLE_LIGHTMAP).build(false)
	);
	private static final RenderLayer WATER_MASK = of(
		"water_mask", 1536, RenderPipelines.RENDERTYPE_WATER_MASK, RenderLayer.MultiPhaseParameters.builder().texture(NO_TEXTURE).build(false)
	);
	private static final RenderLayer ARMOR_ENTITY_GLINT = of(
		"armor_entity_glint",
		1536,
		RenderPipelines.GLINT,
		RenderLayer.MultiPhaseParameters.builder()
			.texture(new RenderPhase.Texture(ItemRenderer.ENTITY_ENCHANTMENT_GLINT, TriState.DEFAULT, false))
			.texturing(ARMOR_ENTITY_GLINT_TEXTURING)
			.layering(VIEW_OFFSET_Z_LAYERING)
			.build(false)
	);
	private static final RenderLayer GLINT_TRANSLUCENT = of(
		"glint_translucent",
		1536,
		RenderPipelines.GLINT,
		RenderLayer.MultiPhaseParameters.builder()
			.texture(new RenderPhase.Texture(ItemRenderer.ITEM_ENCHANTMENT_GLINT, TriState.DEFAULT, false))
			.texturing(GLINT_TEXTURING)
			.target(ITEM_ENTITY_TARGET)
			.build(false)
	);
	private static final RenderLayer GLINT = of(
		"glint",
		1536,
		RenderPipelines.GLINT,
		RenderLayer.MultiPhaseParameters.builder()
			.texture(new RenderPhase.Texture(ItemRenderer.ITEM_ENCHANTMENT_GLINT, TriState.DEFAULT, false))
			.texturing(GLINT_TEXTURING)
			.build(false)
	);
	private static final RenderLayer ENTITY_GLINT = of(
		"entity_glint",
		1536,
		RenderPipelines.GLINT,
		RenderLayer.MultiPhaseParameters.builder()
			.texture(new RenderPhase.Texture(ItemRenderer.ITEM_ENCHANTMENT_GLINT, TriState.DEFAULT, false))
			.texturing(ENTITY_GLINT_TEXTURING)
			.build(false)
	);
	private static final Function<Identifier, RenderLayer> CRUMBLING = Util.memoize((Function<Identifier, RenderLayer>)(texture -> {
		RenderPhase.Texture texture2 = new RenderPhase.Texture(texture, TriState.FALSE, false);
		return of("crumbling", 1536, false, true, RenderPipelines.RENDERTYPE_CRUMBLING, RenderLayer.MultiPhaseParameters.builder().texture(texture2).build(false));
	}));
	private static final Function<Identifier, RenderLayer> TEXT = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"text",
			786432,
			false,
			false,
			RenderPipelines.RENDERTYPE_TEXT,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).lightmap(ENABLE_LIGHTMAP).build(false)
		))
	);
	private static final RenderLayer TEXT_BACKGROUND = of(
		"text_background",
		1536,
		false,
		true,
		RenderPipelines.RENDERTYPE_TEXT_BG,
		RenderLayer.MultiPhaseParameters.builder().texture(NO_TEXTURE).lightmap(ENABLE_LIGHTMAP).build(false)
	);
	private static final Function<Identifier, RenderLayer> TEXT_INTENSITY = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"text_intensity",
			786432,
			false,
			false,
			RenderPipelines.RENDERTYPE_TEXT_INTENSITY,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).lightmap(ENABLE_LIGHTMAP).build(false)
		))
	);
	private static final Function<Identifier, RenderLayer> TEXT_POLYGON_OFFSET = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"text_polygon_offset",
			1536,
			false,
			true,
			RenderPipelines.RENDERTYPE_TEXT_POLYGON_OFFSET,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).lightmap(ENABLE_LIGHTMAP).build(false)
		))
	);
	private static final Function<Identifier, RenderLayer> TEXT_INTENSITY_POLYGON_OFFSET = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"text_intensity_polygon_offset",
			1536,
			false,
			true,
			RenderPipelines.RENDERTYPE_TEXT_INTENSITY,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).lightmap(ENABLE_LIGHTMAP).build(false)
		))
	);
	private static final Function<Identifier, RenderLayer> TEXT_SEE_THROUGH = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"text_see_through",
			1536,
			false,
			false,
			RenderPipelines.RENDERTYPE_TEXT_SEETHROUGH,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).lightmap(ENABLE_LIGHTMAP).build(false)
		))
	);
	private static final RenderLayer TEXT_BACKGROUND_SEE_THROUGH = of(
		"text_background_see_through",
		1536,
		false,
		true,
		RenderPipelines.RENDERTYPE_TEXT_BG_SEETHROUGH,
		RenderLayer.MultiPhaseParameters.builder().texture(NO_TEXTURE).lightmap(ENABLE_LIGHTMAP).build(false)
	);
	private static final Function<Identifier, RenderLayer> TEXT_INTENSITY_SEE_THROUGH = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"text_intensity_see_through",
			1536,
			false,
			true,
			RenderPipelines.RENDERTYPE_TEXT_INTENSITY_SEETHROUGH,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).lightmap(ENABLE_LIGHTMAP).build(false)
		))
	);
	private static final RenderLayer LIGHTNING = of(
		"lightning", 1536, false, true, RenderPipelines.RENDERTYPE_LIGHTNING, RenderLayer.MultiPhaseParameters.builder().target(WEATHER_TARGET).build(false)
	);
	private static final RenderLayer DRAGON_RAYS = of(
		"dragon_rays", 1536, false, false, RenderPipelines.RENDERTYPE_LIGHTNING_DRAGON_RAYS, RenderLayer.MultiPhaseParameters.builder().build(false)
	);
	private static final RenderLayer DRAGON_RAYS_DEPTH = of(
		"dragon_rays_depth", 1536, false, false, RenderPipelines.POSITION_DRAGON_RAYS_DEPTH, RenderLayer.MultiPhaseParameters.builder().build(false)
	);
	private static final RenderLayer TRIPWIRE = of(
		"tripwire",
		1536,
		true,
		true,
		RenderPipelines.TRIPWIRE,
		RenderLayer.MultiPhaseParameters.builder().lightmap(ENABLE_LIGHTMAP).texture(MIPMAP_BLOCK_ATLAS_TEXTURE).target(WEATHER_TARGET).build(true)
	);
	private static final RenderLayer END_PORTAL = of(
		"end_portal",
		1536,
		false,
		false,
		RenderPipelines.END_PORTAL,
		RenderLayer.MultiPhaseParameters.builder()
			.texture(
				RenderPhase.Textures.create()
					.add(EndPortalBlockEntityRenderer.SKY_TEXTURE, false, false)
					.add(EndPortalBlockEntityRenderer.PORTAL_TEXTURE, false, false)
					.build()
			)
			.build(false)
	);
	private static final RenderLayer END_GATEWAY = of(
		"end_gateway",
		1536,
		false,
		false,
		RenderPipelines.END_GATEWAY,
		RenderLayer.MultiPhaseParameters.builder()
			.texture(
				RenderPhase.Textures.create()
					.add(EndPortalBlockEntityRenderer.SKY_TEXTURE, false, false)
					.add(EndPortalBlockEntityRenderer.PORTAL_TEXTURE, false, false)
					.build()
			)
			.build(false)
	);
	public static final RenderLayer.MultiPhase LINES = of(
		"lines",
		1536,
		RenderPipelines.LINES,
		RenderLayer.MultiPhaseParameters.builder()
			.lineWidth(new RenderPhase.LineWidth(OptionalDouble.empty()))
			.layering(VIEW_OFFSET_Z_LAYERING)
			.target(ITEM_ENTITY_TARGET)
			.build(false)
	);
	public static final RenderLayer.MultiPhase SECONDARY_BLOCK_OUTLINE = of(
		"secondary_block_outline",
		1536,
		RenderPipelines.SECOND_BLOCK_OUTLINE,
		RenderLayer.MultiPhaseParameters.builder()
			.lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(7.0)))
			.layering(VIEW_OFFSET_Z_LAYERING)
			.target(ITEM_ENTITY_TARGET)
			.build(false)
	);
	public static final RenderLayer.MultiPhase LINE_STRIP = of(
		"line_strip",
		1536,
		RenderPipelines.LINE_STRIP,
		RenderLayer.MultiPhaseParameters.builder()
			.lineWidth(new RenderPhase.LineWidth(OptionalDouble.empty()))
			.layering(VIEW_OFFSET_Z_LAYERING)
			.target(ITEM_ENTITY_TARGET)
			.build(false)
	);
	private static final Function<Double, RenderLayer.MultiPhase> DEBUG_LINE_STRIP = Util.memoize(
		(Function<Double, RenderLayer.MultiPhase>)(lineWidth -> of(
			"debug_line_strip",
			1536,
			RenderPipelines.DEBUG_LINE_STRIP,
			RenderLayer.MultiPhaseParameters.builder().lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(lineWidth))).build(false)
		))
	);
	private static final Function<Double, RenderLayer.MultiPhase> DEBUG_LINE = Util.memoize(
		(Function<Double, RenderLayer.MultiPhase>)(double_ -> of(
			"debug_line",
			1536,
			RenderPipelines.LINES,
			RenderLayer.MultiPhaseParameters.builder().lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(double_))).build(false)
		))
	);
	private static final RenderLayer.MultiPhase DEBUG_FILLED_BOX = of(
		"debug_filled_box",
		1536,
		false,
		true,
		RenderPipelines.DEBUG_FILLED_BOX,
		RenderLayer.MultiPhaseParameters.builder().layering(VIEW_OFFSET_Z_LAYERING).build(false)
	);
	private static final RenderLayer.MultiPhase DEBUG_QUADS = of(
		"debug_quads", 1536, false, true, RenderPipelines.DEBUG_QUADS, RenderLayer.MultiPhaseParameters.builder().build(false)
	);
	private static final RenderLayer.MultiPhase DEBUG_TRIANGLE_FAN = of(
		"debug_triangle_fan", 1536, false, true, RenderPipelines.DEBUG_TRIANGLE_FAN, RenderLayer.MultiPhaseParameters.builder().build(false)
	);
	private static final RenderLayer.MultiPhase DEBUG_STRUCTURE_QUADS = of(
		"debug_structure_quads", 1536, false, true, RenderPipelines.DEBUG_STRUCTURE_QUADS, RenderLayer.MultiPhaseParameters.builder().build(false)
	);
	private static final RenderLayer.MultiPhase DEBUG_SECTION_QUADS = of(
		"debug_section_quads",
		1536,
		false,
		true,
		RenderPipelines.DEBUG_SECTION_QUADS,
		RenderLayer.MultiPhaseParameters.builder().layering(VIEW_OFFSET_Z_LAYERING).build(false)
	);
	private static final Function<Identifier, RenderLayer> OPAQUE_PARTICLE = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"opaque_particle",
			1536,
			false,
			false,
			RenderPipelines.OPAQUE_PARTICLE,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).lightmap(ENABLE_LIGHTMAP).build(false)
		))
	);
	private static final Function<Identifier, RenderLayer> TRANSLUCENT_PARTICLE = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"translucent_particle",
			1536,
			false,
			false,
			RenderPipelines.TRANSLUCENT_PARTICLE,
			RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.target(PARTICLES_TARGET)
				.lightmap(ENABLE_LIGHTMAP)
				.build(false)
		))
	);
	private static final Function<Identifier, RenderLayer> WEATHER_ALL_MASK = createWeather(RenderPipelines.WEATHER_DEPTH);
	private static final Function<Identifier, RenderLayer> WEATHER_COLOR_MASK = createWeather(RenderPipelines.WEATHER_NO_DEPTH);
	private static final RenderLayer SUNRISE_SUNSET = of(
		"sunrise_sunset", 1536, false, false, RenderPipelines.POSITION_COLOR_SUNRISE_SUNSET, RenderLayer.MultiPhaseParameters.builder().build(false)
	);
	private static final Function<Identifier, RenderLayer> CELESTIAL = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"celestial",
			1536,
			false,
			false,
			RenderPipelines.POSITION_TEX_COLOR_CELESTIAL,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).build(false)
		))
	);
	private static final Function<Identifier, RenderLayer> BLOCK_SCREEN_EFFECT = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"block_screen_effect",
			1536,
			false,
			false,
			RenderPipelines.BLOCK_SCREEN_EFFECT,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).build(false)
		))
	);
	private static final Function<Identifier, RenderLayer> FIRE_SCREEN_EFFECT = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"fire_screen_effect",
			1536,
			false,
			false,
			RenderPipelines.FIRE_SCREEN_EFFECT,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).build(false)
		))
	);
	private static final RenderLayer.MultiPhase GUI = of("gui", 786432, RenderPipelines.GUI, RenderLayer.MultiPhaseParameters.builder().build(false));
	private static final RenderLayer.MultiPhase GUI_OVERLAY = of(
		"gui_overlay", 1536, RenderPipelines.GUI_OVERLAY, RenderLayer.MultiPhaseParameters.builder().build(false)
	);
	private static final Function<Identifier, RenderLayer> GUI_TEXTURED_OVERLAY = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"gui_textured_overlay",
			1536,
			RenderPipelines.GUI_TEXTURED_OVERLAY,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.DEFAULT, false)).build(false)
		))
	);
	private static final Function<Identifier, RenderLayer> GUI_OPAQUE_TEXTURED_BACKGROUND = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"gui_opaque_textured_background",
			786432,
			RenderPipelines.GUI_OPAQUE_TEX_BG,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).build(false)
		))
	);
	private static final RenderLayer.MultiPhase GUI_NAUSEA_OVERLAY = of(
		"gui_nausea_overlay",
		1536,
		RenderPipelines.GUI_NAUSEA_OVERLAY,
		RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(InGameHud.NAUSEA_TEXTURE, TriState.DEFAULT, false)).build(false)
	);
	private static final RenderLayer.MultiPhase GUI_TEXT_HIGHLIGHT = of(
		"gui_text_highlight", 1536, RenderPipelines.GUI_TEXT_HIGHLIGHT, RenderLayer.MultiPhaseParameters.builder().build(false)
	);
	private static final RenderLayer.MultiPhase GUI_GHOST_RECIPE_OVERLAY = of(
		"gui_ghost_recipe_overlay", 1536, RenderPipelines.GUI_GHOST_RECIPE_OVERLAY, RenderLayer.MultiPhaseParameters.builder().build(false)
	);
	private static final Function<Identifier, RenderLayer> GUI_TEXTURED = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"gui_textured",
			786432,
			RenderPipelines.GUI_TEXTURED,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).build(false)
		))
	);
	private static final Function<Identifier, RenderLayer> VIGNETTE = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"vignette",
			786432,
			RenderPipelines.VIGNETTE,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.DEFAULT, false)).build(false)
		))
	);
	private static final Function<Identifier, RenderLayer> CROSSHAIR = Util.memoize(
		(Function<Identifier, RenderLayer>)(texture -> of(
			"crosshair",
			786432,
			RenderPipelines.CROSSHAIR,
			RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(texture, TriState.FALSE, false)).build(false)
		))
	);
	private static final RenderLayer.MultiPhase MOJANG_LOGO = of(
		"mojang_logo",
		786432,
		RenderPipelines.MOJANG_LOGO,
		RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(SplashOverlay.LOGO, TriState.DEFAULT, false)).build(false)
	);
	private static final ImmutableList<RenderLayer> BLOCK_LAYERS = ImmutableList.of(getSolid(), getCutoutMipped(), getCutout(), getTranslucent(), getTripwire());
	private final int expectedBufferSize;
	private final boolean hasCrumbling;
	private final boolean translucent;

	public static RenderLayer getSolid() {
		return SOLID;
	}

	public static RenderLayer getCutoutMipped() {
		return CUTOUT_MIPPED;
	}

	public static RenderLayer getCutout() {
		return CUTOUT;
	}

	public static RenderLayer getTranslucent() {
		return TRANSLUCENT;
	}

	public static RenderLayer getTranslucentMovingBlock() {
		return TRANSLUCENT_MOVING_BLOCK;
	}

	public static RenderLayer getArmorCutoutNoCull(Identifier texture) {
		return (RenderLayer)ARMOR_CUTOUT_NO_CULL.apply(texture);
	}

	public static RenderLayer createArmorDecalCutoutNoCull(Identifier texture) {
		RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
			.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
			.lightmap(ENABLE_LIGHTMAP)
			.overlay(ENABLE_OVERLAY_COLOR)
			.layering(VIEW_OFFSET_Z_LAYERING)
			.build(true);
		return of("armor_decal_cutout_no_cull", 1536, true, false, RenderPipelines.ARMOR_DECAL_CUTOUT_NO_CULL, multiPhaseParameters);
	}

	public static RenderLayer createArmorTranslucent(Identifier texture) {
		return (RenderLayer)ARMOR_TRANSLUCENT.apply(texture);
	}

	public static RenderLayer getEntitySolid(Identifier texture) {
		return (RenderLayer)ENTITY_SOLID.apply(texture);
	}

	public static RenderLayer getEntitySolidZOffsetForward(Identifier texture) {
		return (RenderLayer)ENTITY_SOLID_Z_OFFSET_FORWARD.apply(texture);
	}

	public static RenderLayer getEntityCutout(Identifier texture) {
		return (RenderLayer)ENTITY_CUTOUT.apply(texture);
	}

	public static RenderLayer getEntityCutoutNoCull(Identifier texture, boolean affectsOutline) {
		return (RenderLayer)ENTITY_CUTOUT_NO_CULL.apply(texture, affectsOutline);
	}

	public static RenderLayer getEntityCutoutNoCull(Identifier texture) {
		return getEntityCutoutNoCull(texture, true);
	}

	public static RenderLayer getEntityCutoutNoCullZOffset(Identifier texture, boolean affectsOutline) {
		return (RenderLayer)ENTITY_CUTOUT_NO_CULL_Z_OFFSET.apply(texture, affectsOutline);
	}

	public static RenderLayer getEntityCutoutNoCullZOffset(Identifier texture) {
		return getEntityCutoutNoCullZOffset(texture, true);
	}

	public static RenderLayer getItemEntityTranslucentCull(Identifier texture) {
		return (RenderLayer)ITEM_ENTITY_TRANSLUCENT_CULL.apply(texture);
	}

	public static RenderLayer getEntityTranslucent(Identifier texture, boolean affectsOutline) {
		return (RenderLayer)ENTITY_TRANSLUCENT.apply(texture, affectsOutline);
	}

	public static RenderLayer getEntityTranslucent(Identifier texture) {
		return getEntityTranslucent(texture, true);
	}

	public static RenderLayer getEntityTranslucentEmissive(Identifier texture, boolean affectsOutline) {
		return (RenderLayer)ENTITY_TRANSLUCENT_EMISSIVE.apply(texture, affectsOutline);
	}

	public static RenderLayer getEntityTranslucentEmissive(Identifier texture) {
		return getEntityTranslucentEmissive(texture, true);
	}

	public static RenderLayer getEntitySmoothCutout(Identifier texture) {
		return (RenderLayer)ENTITY_SMOOTH_CUTOUT.apply(texture);
	}

	public static RenderLayer getBeaconBeam(Identifier texture, boolean translucent) {
		return (RenderLayer)BEACON_BEAM.apply(texture, translucent);
	}

	public static RenderLayer getEntityDecal(Identifier texture) {
		return (RenderLayer)ENTITY_DECAL.apply(texture);
	}

	public static RenderLayer getEntityNoOutline(Identifier texture) {
		return (RenderLayer)ENTITY_NO_OUTLINE.apply(texture);
	}

	public static RenderLayer getEntityShadow(Identifier texture) {
		return (RenderLayer)ENTITY_SHADOW.apply(texture);
	}

	public static RenderLayer getEntityAlpha(Identifier texture) {
		return (RenderLayer)ENTITY_ALPHA.apply(texture);
	}

	public static RenderLayer getEyes(Identifier texture) {
		return (RenderLayer)EYES.apply(texture);
	}

	public static RenderLayer getEntityTranslucentEmissiveNoOutline(Identifier texture) {
		return (RenderLayer)ENTITY_TRANSLUCENT_EMISSIVE.apply(texture, false);
	}

	public static RenderLayer getBreezeWind(Identifier texture, float x, float y) {
		return of(
			"breeze_wind",
			1536,
			false,
			true,
			RenderPipelines.BREEZE_WIND,
			RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.texturing(new RenderPhase.OffsetTexturing(x, y))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(DISABLE_OVERLAY_COLOR)
				.build(false)
		);
	}

	public static RenderLayer getEnergySwirl(Identifier texture, float x, float y) {
		return of(
			"energy_swirl",
			1536,
			false,
			true,
			RenderPipelines.ENTITY_ENERGY_SWIRL,
			RenderLayer.MultiPhaseParameters.builder()
				.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
				.texturing(new RenderPhase.OffsetTexturing(x, y))
				.lightmap(ENABLE_LIGHTMAP)
				.overlay(ENABLE_OVERLAY_COLOR)
				.build(false)
		);
	}

	public static RenderLayer getLeash() {
		return LEASH;
	}

	public static RenderLayer getWaterMask() {
		return WATER_MASK;
	}

	public static RenderLayer getOutline(Identifier texture) {
		return (RenderLayer)RenderLayer.MultiPhase.CULLING_LAYERS.apply(texture, false);
	}

	public static RenderLayer getArmorEntityGlint() {
		return ARMOR_ENTITY_GLINT;
	}

	public static RenderLayer getGlintTranslucent() {
		return GLINT_TRANSLUCENT;
	}

	public static RenderLayer getGlint() {
		return GLINT;
	}

	public static RenderLayer getEntityGlint() {
		return ENTITY_GLINT;
	}

	public static RenderLayer getBlockBreaking(Identifier texture) {
		return (RenderLayer)CRUMBLING.apply(texture);
	}

	public static RenderLayer getText(Identifier texture) {
		return (RenderLayer)TEXT.apply(texture);
	}

	public static RenderLayer getTextBackground() {
		return TEXT_BACKGROUND;
	}

	public static RenderLayer getTextIntensity(Identifier texture) {
		return (RenderLayer)TEXT_INTENSITY.apply(texture);
	}

	public static RenderLayer getTextPolygonOffset(Identifier texture) {
		return (RenderLayer)TEXT_POLYGON_OFFSET.apply(texture);
	}

	public static RenderLayer getTextIntensityPolygonOffset(Identifier texture) {
		return (RenderLayer)TEXT_INTENSITY_POLYGON_OFFSET.apply(texture);
	}

	public static RenderLayer getTextSeeThrough(Identifier texture) {
		return (RenderLayer)TEXT_SEE_THROUGH.apply(texture);
	}

	public static RenderLayer getTextBackgroundSeeThrough() {
		return TEXT_BACKGROUND_SEE_THROUGH;
	}

	public static RenderLayer getTextIntensitySeeThrough(Identifier texture) {
		return (RenderLayer)TEXT_INTENSITY_SEE_THROUGH.apply(texture);
	}

	public static RenderLayer getLightning() {
		return LIGHTNING;
	}

	public static RenderLayer getDragonRays() {
		return DRAGON_RAYS;
	}

	public static RenderLayer getDragonRaysDepth() {
		return DRAGON_RAYS_DEPTH;
	}

	public static RenderLayer getTripwire() {
		return TRIPWIRE;
	}

	public static RenderLayer getEndPortal() {
		return END_PORTAL;
	}

	public static RenderLayer getEndGateway() {
		return END_GATEWAY;
	}

	public static RenderLayer getLines() {
		return LINES;
	}

	public static RenderLayer getSecondaryBlockOutline() {
		return SECONDARY_BLOCK_OUTLINE;
	}

	public static RenderLayer getLineStrip() {
		return LINE_STRIP;
	}

	public static RenderLayer getDebugLineStrip(double lineWidth) {
		return (RenderLayer)DEBUG_LINE_STRIP.apply(lineWidth);
	}

	public static RenderLayer getDebugCrosshair(double d) {
		return (RenderLayer)DEBUG_LINE.apply(d);
	}

	public static RenderLayer getDebugFilledBox() {
		return DEBUG_FILLED_BOX;
	}

	public static RenderLayer getDebugQuads() {
		return DEBUG_QUADS;
	}

	public static RenderLayer getDebugTriangleFan() {
		return DEBUG_TRIANGLE_FAN;
	}

	public static RenderLayer getDebugStructureQuads() {
		return DEBUG_STRUCTURE_QUADS;
	}

	public static RenderLayer getDebugSectionQuads() {
		return DEBUG_SECTION_QUADS;
	}

	public static RenderLayer getOpaqueParticle(Identifier texture) {
		return (RenderLayer)OPAQUE_PARTICLE.apply(texture);
	}

	public static RenderLayer getTranslucentParticle(Identifier texture) {
		return (RenderLayer)TRANSLUCENT_PARTICLE.apply(texture);
	}

	private static Function<Identifier, RenderLayer> createWeather(RenderPipeline pipeline) {
		return Util.memoize(
			(Function<Identifier, RenderLayer>)(texture -> of(
				"weather",
				1536,
				false,
				false,
				pipeline,
				RenderLayer.MultiPhaseParameters.builder()
					.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
					.target(WEATHER_TARGET)
					.lightmap(ENABLE_LIGHTMAP)
					.build(false)
			))
		);
	}

	public static RenderLayer getWeather(Identifier texture, boolean allMask) {
		return (RenderLayer)(allMask ? WEATHER_ALL_MASK : WEATHER_COLOR_MASK).apply(texture);
	}

	public static RenderLayer getSunriseSunset() {
		return SUNRISE_SUNSET;
	}

	public static RenderLayer getCelestial(Identifier texture) {
		return (RenderLayer)CELESTIAL.apply(texture);
	}

	public static RenderLayer getBlockScreenEffect(Identifier texture) {
		return (RenderLayer)BLOCK_SCREEN_EFFECT.apply(texture);
	}

	public static RenderLayer getFireScreenEffect(Identifier texture) {
		return (RenderLayer)FIRE_SCREEN_EFFECT.apply(texture);
	}

	public static RenderLayer getGui() {
		return GUI;
	}

	public static RenderLayer getGuiOverlay() {
		return GUI_OVERLAY;
	}

	public static RenderLayer getGuiTexturedOverlay(Identifier texture) {
		return (RenderLayer)GUI_TEXTURED_OVERLAY.apply(texture);
	}

	public static RenderLayer getGuiOpaqueTexturedBackground(Identifier texture) {
		return (RenderLayer)GUI_OPAQUE_TEXTURED_BACKGROUND.apply(texture);
	}

	public static RenderLayer getGuiNauseaOverlay() {
		return GUI_NAUSEA_OVERLAY;
	}

	public static RenderLayer getGuiTextHighlight() {
		return GUI_TEXT_HIGHLIGHT;
	}

	public static RenderLayer getGuiGhostRecipeOverlay() {
		return GUI_GHOST_RECIPE_OVERLAY;
	}

	public static RenderLayer getGuiTextured(Identifier texture) {
		return (RenderLayer)GUI_TEXTURED.apply(texture);
	}

	public static RenderLayer getVignette(Identifier texture) {
		return (RenderLayer)VIGNETTE.apply(texture);
	}

	public static RenderLayer getCrosshair(Identifier texture) {
		return (RenderLayer)CROSSHAIR.apply(texture);
	}

	public static RenderLayer getMojangLogo() {
		return MOJANG_LOGO;
	}

	public RenderLayer(String name, int size, boolean hasCrumbling, boolean translucent, Runnable begin, Runnable end) {
		super(name, begin, end);
		this.expectedBufferSize = size;
		this.hasCrumbling = hasCrumbling;
		this.translucent = translucent;
	}

	public static RenderLayer.MultiPhase of(String name, int size, RenderPipeline pipeline, RenderLayer.MultiPhaseParameters params) {
		return of(name, size, false, false, pipeline, params);
	}

	public static RenderLayer.MultiPhase of(
		String name, int size, boolean hasCrumbling, boolean translucent, RenderPipeline pipeline, RenderLayer.MultiPhaseParameters params
	) {
		return new RenderLayer.MultiPhase(name, size, hasCrumbling, translucent, pipeline, params);
	}

	public abstract void draw(BuiltBuffer buffer);

	public abstract Framebuffer getTarget();

	public abstract RenderPipeline getPipeline();

	public static List<RenderLayer> getBlockLayers() {
		return BLOCK_LAYERS;
	}

	public int getExpectedBufferSize() {
		return this.expectedBufferSize;
	}

	public abstract VertexFormat getVertexFormat();

	public abstract VertexFormat.DrawMode getDrawMode();

	public Optional<RenderLayer> getAffectedOutline() {
		return Optional.empty();
	}

	public boolean isOutline() {
		return false;
	}

	public boolean hasCrumbling() {
		return this.hasCrumbling;
	}

	public boolean areVerticesNotShared() {
		return !this.getDrawMode().shareVertices;
	}

	public boolean isTranslucent() {
		return this.translucent;
	}

	@Environment(EnvType.CLIENT)
	public static final class MultiPhase extends RenderLayer {
		static final BiFunction<Identifier, Boolean, RenderLayer> CULLING_LAYERS = Util.memoize(
			(BiFunction<Identifier, Boolean, RenderLayer>)((texture, hasCulling) -> RenderLayer.of(
				"outline",
				1536,
				hasCulling ? RenderPipelines.OUTLINE_CULL : RenderPipelines.OUTLINE_NO_CULL,
				RenderLayer.MultiPhaseParameters.builder()
					.texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
					.target(OUTLINE_TARGET)
					.build(RenderLayer.OutlineMode.IS_OUTLINE)
			))
		);
		private final RenderLayer.MultiPhaseParameters phases;
		private final RenderPipeline pipeline;
		private final Optional<RenderLayer> affectedOutline;
		private final boolean outline;

		MultiPhase(String name, int size, boolean hasCrumbling, boolean translucent, RenderPipeline pipeline, RenderLayer.MultiPhaseParameters phases) {
			super(name, size, hasCrumbling, translucent, () -> phases.phases.forEach(RenderPhase::startDrawing), () -> phases.phases.forEach(RenderPhase::endDrawing));
			this.phases = phases;
			this.pipeline = pipeline;
			this.affectedOutline = phases.outlineMode == RenderLayer.OutlineMode.AFFECTS_OUTLINE
				? phases.texture.getId().map(id -> (RenderLayer)CULLING_LAYERS.apply(id, pipeline.isCull()))
				: Optional.empty();
			this.outline = phases.outlineMode == RenderLayer.OutlineMode.IS_OUTLINE;
		}

		@Override
		public Optional<RenderLayer> getAffectedOutline() {
			return this.affectedOutline;
		}

		@Override
		public boolean isOutline() {
			return this.outline;
		}

		@Override
		public RenderPipeline getPipeline() {
			return this.pipeline;
		}

		@Override
		public VertexFormat getVertexFormat() {
			return this.pipeline.getVertexFormat();
		}

		@Override
		public VertexFormat.DrawMode getDrawMode() {
			return this.pipeline.getVertexFormatMode();
		}

		@Override
		public void draw(BuiltBuffer buffer) {
			RenderPipeline renderPipeline = this.getPipeline();
			this.startDrawing();
			BuiltBuffer var3 = buffer;

			try {
				GpuBuffer gpuBuffer = renderPipeline.getVertexFormat().uploadImmediateVertexBuffer(buffer.getBuffer());
				GpuBuffer gpuBuffer2;
				VertexFormat.IndexType indexType;
				if (buffer.getSortedBuffer() == null) {
					RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(buffer.getDrawParameters().mode());
					gpuBuffer2 = shapeIndexBuffer.getIndexBuffer(buffer.getDrawParameters().indexCount());
					indexType = shapeIndexBuffer.getIndexType();
				} else {
					gpuBuffer2 = renderPipeline.getVertexFormat().uploadImmediateIndexBuffer(buffer.getSortedBuffer());
					indexType = buffer.getDrawParameters().indexType();
				}

				Framebuffer framebuffer = this.phases.target.get();

				try (RenderPass renderPass = RenderSystem.getDevice()
						.createCommandEncoder()
						.createRenderPass(
							framebuffer.getColorAttachment(), OptionalInt.empty(), framebuffer.useDepthAttachment ? framebuffer.getDepthAttachment() : null, OptionalDouble.empty()
						)) {
					renderPass.setPipeline(renderPipeline);
					renderPass.setVertexBuffer(0, gpuBuffer);
					if (RenderSystem.SCISSOR_STATE.isEnabled()) {
						renderPass.enableScissor(RenderSystem.SCISSOR_STATE);
					}

					for (int i = 0; i < 12; i++) {
						GpuTexture gpuTexture = RenderSystem.getShaderTexture(i);
						if (gpuTexture != null) {
							renderPass.bindSampler("Sampler" + i, gpuTexture);
						}
					}

					renderPass.setIndexBuffer(gpuBuffer2, indexType);
					renderPass.drawIndexed(0, buffer.getDrawParameters().indexCount());
				}
			} catch (Throwable var14) {
				if (buffer != null) {
					try {
						var3.close();
					} catch (Throwable var11) {
						var14.addSuppressed(var11);
					}
				}

				throw var14;
			}

			if (buffer != null) {
				buffer.close();
			}

			this.endDrawing();
		}

		@Override
		public Framebuffer getTarget() {
			return this.phases.target.get();
		}

		@Override
		public String toString() {
			return "RenderType[" + this.name + ":" + this.phases + "]";
		}
	}

	@Environment(EnvType.CLIENT)
	public static final class MultiPhaseParameters {
		final RenderPhase.TextureBase texture;
		final RenderPhase.Target target;
		final RenderLayer.OutlineMode outlineMode;
		final ImmutableList<RenderPhase> phases;

		MultiPhaseParameters(
			RenderPhase.TextureBase texture,
			RenderPhase.Lightmap lightMap,
			RenderPhase.Overlay overlay,
			RenderPhase.Layering layering,
			RenderPhase.Target target,
			RenderPhase.Texturing texturing,
			RenderPhase.LineWidth lineWidth,
			RenderLayer.OutlineMode outlineMode
		) {
			this.texture = texture;
			this.target = target;
			this.outlineMode = outlineMode;
			this.phases = ImmutableList.of(texture, lightMap, overlay, layering, target, texturing, lineWidth);
		}

		public String toString() {
			return "CompositeState[" + this.phases + ", outlineProperty=" + this.outlineMode + "]";
		}

		public static RenderLayer.MultiPhaseParameters.Builder builder() {
			return new RenderLayer.MultiPhaseParameters.Builder();
		}

		@Environment(EnvType.CLIENT)
		public static class Builder {
			private RenderPhase.TextureBase texture = RenderPhase.NO_TEXTURE;
			private RenderPhase.Lightmap lightmap = RenderPhase.DISABLE_LIGHTMAP;
			private RenderPhase.Overlay overlay = RenderPhase.DISABLE_OVERLAY_COLOR;
			private RenderPhase.Layering layering = RenderPhase.NO_LAYERING;
			private RenderPhase.Target target = RenderPhase.MAIN_TARGET;
			private RenderPhase.Texturing texturing = RenderPhase.DEFAULT_TEXTURING;
			private RenderPhase.LineWidth lineWidth = RenderPhase.FULL_LINE_WIDTH;

			Builder() {
			}

			public RenderLayer.MultiPhaseParameters.Builder texture(RenderPhase.TextureBase texture) {
				this.texture = texture;
				return this;
			}

			public RenderLayer.MultiPhaseParameters.Builder lightmap(RenderPhase.Lightmap lightmap) {
				this.lightmap = lightmap;
				return this;
			}

			public RenderLayer.MultiPhaseParameters.Builder overlay(RenderPhase.Overlay overlay) {
				this.overlay = overlay;
				return this;
			}

			public RenderLayer.MultiPhaseParameters.Builder layering(RenderPhase.Layering layering) {
				this.layering = layering;
				return this;
			}

			public RenderLayer.MultiPhaseParameters.Builder target(RenderPhase.Target target) {
				this.target = target;
				return this;
			}

			public RenderLayer.MultiPhaseParameters.Builder texturing(RenderPhase.Texturing texturing) {
				this.texturing = texturing;
				return this;
			}

			public RenderLayer.MultiPhaseParameters.Builder lineWidth(RenderPhase.LineWidth lineWidth) {
				this.lineWidth = lineWidth;
				return this;
			}

			public RenderLayer.MultiPhaseParameters build(boolean affectsOutline) {
				return this.build(affectsOutline ? RenderLayer.OutlineMode.AFFECTS_OUTLINE : RenderLayer.OutlineMode.NONE);
			}

			public RenderLayer.MultiPhaseParameters build(RenderLayer.OutlineMode outlineMode) {
				return new RenderLayer.MultiPhaseParameters(
					this.texture, this.lightmap, this.overlay, this.layering, this.target, this.texturing, this.lineWidth, outlineMode
				);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static enum OutlineMode {
		NONE("none"),
		IS_OUTLINE("is_outline"),
		AFFECTS_OUTLINE("affects_outline");

		private final String name;

		private OutlineMode(final String name) {
			this.name = name;
		}

		public String toString() {
			return this.name;
		}
	}
}
