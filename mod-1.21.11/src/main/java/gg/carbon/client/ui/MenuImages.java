package gg.carbon.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import gg.carbon.client.CarbonClient;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Optional main menu artwork (the "CARBON CLIENT" logo and the character render), toggled in the
 * Design page. The images are drawn at device resolution through a linear sampler so they stay
 * smooth at any GUI scale.
 */
public final class MenuImages {
	private MenuImages() {}

	public static final String LOGO = "menu_logo";
	public static final String SKIN = "menu_skin";

	private record Tex(Identifier id, int w, int h) {}

	private static final Map<String, Tex> CACHE = new HashMap<>();

	public static boolean logoEnabled() {
		return CarbonClient.modules() != null && CarbonClient.modules().client.menuLogo.on();
	}

	public static boolean skinEnabled() {
		return CarbonClient.modules() != null && CarbonClient.modules().client.menuSkin.on();
	}

	private static Tex texture(String name) {
		return CACHE.computeIfAbsent(name, n -> {
			String path = "/assets/carbon/textures/gui/" + n + ".png";
			try (InputStream in = MenuImages.class.getResourceAsStream(path)) {
				if (in == null) throw new IllegalStateException("missing " + path);
				NativeImage img = NativeImage.read(in);
				Identifier id = Identifier.fromNamespaceAndPath("carbon", "dynamic/" + n);
				int w = img.getWidth(), h = img.getHeight();
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "carbon " + n, img) {
					{
						this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
					}
				});
				return new Tex(id, w, h);
			} catch (Exception e) {
				CarbonClient.LOG.error("Could not load menu image {}", n, e);
				return null;
			}
		});
	}

	/** Width (in the caller's units) of an image drawn at {@code height}. */
	public static float width(String name, float height) {
		Tex t = texture(name);
		return t == null ? 0 : height * t.w() / t.h();
	}

	/** Draws the image with its top-left corner at (x, y), scaled to {@code height}. */
	public static void draw(GuiGraphics g, String name, float x, float y, float height) {
		Tex t = texture(name);
		if (t == null) return;
		float width = height * t.w() / t.h();
		int[] p0 = R2D.point(g, x, y);
		int[] p1 = R2D.point(g, x + width, y + height);
		int dw = p1[0] - p0[0], dh = p1[1] - p0[1];
		if (dw <= 0 || dh <= 0) return;
		R2D.beginDevice(g);
		g.blit(RenderPipelines.GUI_TEXTURED, t.id(), p0[0], p0[1], 0, 0, dw, dh, t.w(), t.h(), t.w(), t.h(), R2D.tint(0xFFFFFFFF));
		R2D.endDevice(g);
	}

	/** Draws the image horizontally centred on {@code cx}. */
	public static void drawCentered(GuiGraphics g, String name, float cx, float y, float height) {
		draw(g, name, cx - width(name, height) / 2f, y, height);
	}
}
