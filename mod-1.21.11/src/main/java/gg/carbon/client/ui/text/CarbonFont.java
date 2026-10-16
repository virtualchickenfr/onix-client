package gg.carbon.client.ui.text;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FreeType;

/**
 * A TrueType face rasterised by FreeType at the exact physical pixel size it is shown at.
 * Glyphs are packed into per-size atlases and drawn 1:1 on whole pixels, so text is never
 * resampled — the reason it stays razor sharp at every GUI scale.
 */
public final class CarbonFont {
	private final String file;
	private final boolean mono;
	private final float baseline;
	private ByteBuffer data;
	private FT_Face face;
	private float unitsPerEm;
	private final Map<Integer, Float> advances = new HashMap<>();
	private final Map<Integer, Atlas> atlases = new HashMap<>();
	private static int atlasCounter;
	private static final int[] GAMMA = new int[256];
	static final int SUBPIXELS = 4;

	static {
		for (int i = 0; i < 256; i++) GAMMA[i] = (int) Math.round(255 * Math.pow(i / 255.0, 0.78));
	}

	/**
	 * @param file     font file inside assets/carbon/font
	 * @param mono     render 1-bit (for pixel fonts) instead of anti-aliased
	 * @param baseline baseline position as a fraction of the em size, measured from the line top
	 */
	public CarbonFont(String file, boolean mono, float baseline) {
		this.file = file;
		this.mono = mono;
		this.baseline = baseline;
	}

	public float baseline() {
		return baseline;
	}

	private FT_Face face() {
		if (face != null) return face;
		try (InputStream in = CarbonFont.class.getResourceAsStream("/assets/carbon/font/" + file)) {
			if (in == null) throw new IOException("missing font " + file);
			byte[] bytes = in.readAllBytes();
			data = MemoryUtil.memAlloc(bytes.length);
			data.put(bytes).flip();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		synchronized (FreeTypeUtil.LIBRARY_LOCK) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer ptr = stack.mallocPointer(1);
				FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), data, 0, ptr), "Initializing font " + file);
				face = FT_Face.create(ptr.get());
			}
		}
		unitsPerEm = face.units_per_EM();
		return face;
	}

	/** Resolution independent advance of a codepoint, in em units. */
	public float advance(int cp) {
		Float cached = advances.get(cp);
		if (cached != null) return cached;
		FT_Face f = face();
		float adv;
		synchronized (FreeTypeUtil.LIBRARY_LOCK) {
			int index = FreeType.FT_Get_Char_Index(f, cp);
			if (FreeType.FT_Load_Glyph(f, index, FreeType.FT_LOAD_NO_SCALE) == 0) {
				adv = f.glyph().advance().x() / unitsPerEm;
			} else {
				adv = 0.5f;
			}
		}
		advances.put(cp, adv);
		return adv;
	}

	/** Width of a string in em units. */
	public float width(String text) {
		float w = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			w += advance(cp);
			i += Character.charCount(cp);
		}
		return w;
	}

	Atlas atlas(int pixelSize) {
		return atlases.computeIfAbsent(pixelSize, Atlas::new);
	}

	/** One pixel size of this face: lazily rasterised glyphs packed into texture pages. */
	final class Atlas {
		final int px;
		final Map<Integer, Glyph> glyphs = new HashMap<>();
		final List<Page> pages = new ArrayList<>();

		Atlas(int px) {
			this.px = px;
		}

		Glyph glyph(int cp) {
			return glyph(cp, 0);
		}

		/**
		 * Glyph rasterised at a horizontal sub-pixel phase ({@code sub} quarter pixels). Placing
		 * every glyph at its true fractional pen position keeps letter spacing perfectly even at
		 * small sizes, instead of gaps that wobble by a pixel from letter to letter.
		 */
		Glyph glyph(int cp, int sub) {
			if (mono) sub = 0;
			int key = cp * SUBPIXELS + sub;
			Glyph g = glyphs.get(key);
			if (g == null) {
				g = rasterise(cp, sub);
				glyphs.put(key, g);
			}
			return g;
		}

		private Glyph rasterise(int cp, int sub) {
			FT_Face f = face();
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				FreeType.FT_Set_Pixel_Sizes(f, 0, px);
				int index = FreeType.FT_Get_Char_Index(f, cp);
				FT_GlyphSlot slot;
				if (mono) {
					int flags = FreeType.FT_LOAD_RENDER | ((FreeType.FT_RENDER_MODE_MONO & 15) << 16) | FreeType.FT_LOAD_MONOCHROME;
					if (FreeType.FT_Load_Glyph(f, index, flags) != 0) return Glyph.EMPTY;
					slot = f.glyph();
				} else {
					// light hinting only snaps vertically, so the outline can be shifted horizontally freely
					if (FreeType.FT_Load_Glyph(f, index, ((FreeType.FT_RENDER_MODE_LIGHT & 15) << 16) | FreeType.FT_LOAD_NO_BITMAP) != 0) return Glyph.EMPTY;
					slot = f.glyph();
					if (sub > 0) FreeType.FT_Outline_Translate(slot.outline(), sub * (64 / SUBPIXELS), 0);
					if (FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_LIGHT) != 0) return Glyph.EMPTY;
				}
				FT_Bitmap bmp = slot.bitmap();
				int w = bmp.width(), h = bmp.rows();
				if (w <= 0 || h <= 0) return Glyph.EMPTY;
				int pitch = bmp.pitch();
				ByteBuffer buf = bmp.buffer(Math.abs(pitch) * h);
				boolean oneBit = bmp.pixel_mode() == FreeType.FT_PIXEL_MODE_MONO;
				Page page = null;
				int[] pos = null;
				for (Page p : pages) {
					pos = p.place(w, h);
					if (pos != null) {
						page = p;
						break;
					}
				}
				if (page == null) {
					page = new Page(Math.max(256, Integer.highestOneBit(Math.max(w, h) * 8)));
					pages.add(page);
					pos = page.place(w, h);
				}
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int a;
						if (oneBit) {
							int b = buf.get(y * pitch + (x >> 3)) & 0xFF;
							a = ((b >> (7 - (x & 7))) & 1) * 255;
						} else {
							a = buf.get(y * pitch + x) & 0xFF;
							// slight gamma lift: thin stems at small sizes read crisper on dark backgrounds
							if (px <= 26 && a > 0 && a < 255) a = GAMMA[a];
						}
						page.image.setPixel(pos[0] + x, pos[1] + y, (a << 24) | 0xFFFFFF);
					}
				}
				page.dirty = true;
				return new Glyph(page, pos[0], pos[1], w, h, slot.bitmap_left(), slot.bitmap_top());
			}
		}
	}

	/** A texture page with simple shelf packing. */
	static final class Page {
		final int size;
		final NativeImage image;
		final DynamicTexture texture;
		final Identifier id;
		boolean dirty;
		private int shelfX = 1, shelfY = 1, shelfH;

		Page(int size) {
			this.size = size;
			this.image = new NativeImage(size, size, true);
			this.id = Identifier.fromNamespaceAndPath("carbon", "dynamic/text_" + (atlasCounter++));
			this.texture = new DynamicTexture(() -> "carbon text " + id, image);
			Minecraft.getInstance().getTextureManager().register(id, texture);
		}

		int[] place(int w, int h) {
			if (shelfX + w + 1 > size) {
				shelfX = 1;
				shelfY += shelfH + 1;
				shelfH = 0;
			}
			if (shelfY + h + 1 > size) return null;
			int[] pos = {shelfX, shelfY};
			shelfX += w + 1;
			shelfH = Math.max(shelfH, h);
			return pos;
		}

		void flush() {
			if (dirty) {
				texture.upload();
				dirty = false;
			}
		}
	}

	record Glyph(Page page, int u, int v, int w, int h, int left, int top) {
		static final Glyph EMPTY = new Glyph(null, 0, 0, 0, 0, 0, 0);
	}
}
