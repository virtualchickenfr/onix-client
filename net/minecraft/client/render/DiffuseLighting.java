package net.minecraft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class DiffuseLighting {
	private static final Vector3f DEFAULT_DIFFUSION_LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
	private static final Vector3f DEFAULT_DIFFUSION_LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
	private static final Vector3f WORLD_DIFFUSION_LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
	private static final Vector3f WORLD_DIFFUSION_LIGHT_1 = new Vector3f(-0.2F, -1.0F, 0.7F).normalize();
	private static final Vector3f INVENTORY_DIFFUSION_LIGHT_0 = new Vector3f(0.2F, -1.0F, 1.0F).normalize();
	private static final Vector3f INVENTORY_DIFFUSION_LIGHT_1 = new Vector3f(-0.2F, -1.0F, 0.0F).normalize();

	public static void enableForLevel() {
		RenderSystem.setupLevelDiffuseLighting(WORLD_DIFFUSION_LIGHT_0, WORLD_DIFFUSION_LIGHT_1);
	}

	public static void disableForLevel() {
		RenderSystem.setupLevelDiffuseLighting(DEFAULT_DIFFUSION_LIGHT_0, DEFAULT_DIFFUSION_LIGHT_1);
	}

	public static void disableGuiDepthLighting() {
		RenderSystem.setupGuiFlatDiffuseLighting(DEFAULT_DIFFUSION_LIGHT_0, DEFAULT_DIFFUSION_LIGHT_1);
	}

	public static void enableGuiDepthLighting() {
		RenderSystem.setupGui3DDiffuseLighting(DEFAULT_DIFFUSION_LIGHT_0, DEFAULT_DIFFUSION_LIGHT_1);
	}

	public static void enableGuiShaderLighting() {
		RenderSystem.setShaderLights(INVENTORY_DIFFUSION_LIGHT_0, INVENTORY_DIFFUSION_LIGHT_1);
	}

	public static void rotateGuiShaderLighting(Quaternionf rotation) {
		RenderSystem.setShaderLights(rotation.transform(INVENTORY_DIFFUSION_LIGHT_0, new Vector3f()), rotation.transform(INVENTORY_DIFFUSION_LIGHT_1, new Vector3f()));
	}
}
