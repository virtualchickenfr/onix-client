package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.annotation.DeobfuscateClass;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

@Environment(EnvType.CLIENT)
@DeobfuscateClass
public class GlConst {
	public static final int GL_READ_FRAMEBUFFER = 36008;
	public static final int GL_DRAW_FRAMEBUFFER = 36009;
	public static final int GL_TRUE = 1;
	public static final int GL_FALSE = 0;
	public static final int GL_NONE = 0;
	public static final int GL_LINES = 1;
	public static final int GL_LINE_STRIP = 3;
	public static final int GL_TRIANGLE_STRIP = 5;
	public static final int GL_TRIANGLE_FAN = 6;
	public static final int GL_TRIANGLES = 4;
	public static final int GL_WRITE_ONLY = 35001;
	public static final int GL_READ_ONLY = 35000;
	public static final int GL_MAP_READ_BIT = 1;
	public static final int GL_EQUAL = 514;
	public static final int GL_LEQUAL = 515;
	public static final int GL_LESS = 513;
	public static final int GL_GREATER = 516;
	public static final int GL_GEQUAL = 518;
	public static final int GL_ALWAYS = 519;
	public static final int GL_TEXTURE_MAG_FILTER = 10240;
	public static final int GL_TEXTURE_MIN_FILTER = 10241;
	public static final int GL_TEXTURE_WRAP_S = 10242;
	public static final int GL_TEXTURE_WRAP_T = 10243;
	public static final int GL_NEAREST = 9728;
	public static final int GL_LINEAR = 9729;
	public static final int GL_NEAREST_MIPMAP_LINEAR = 9986;
	public static final int GL_LINEAR_MIPMAP_LINEAR = 9987;
	public static final int GL_CLAMP_TO_EDGE = 33071;
	public static final int GL_REPEAT = 10497;
	public static final int GL_FRONT = 1028;
	public static final int GL_FRONT_AND_BACK = 1032;
	public static final int GL_LINE = 6913;
	public static final int GL_FILL = 6914;
	public static final int GL_BYTE = 5120;
	public static final int GL_UNSIGNED_BYTE = 5121;
	public static final int GL_SHORT = 5122;
	public static final int GL_UNSIGNED_SHORT = 5123;
	public static final int GL_INT = 5124;
	public static final int GL_UNSIGNED_INT = 5125;
	public static final int GL_FLOAT = 5126;
	public static final int GL_ZERO = 0;
	public static final int GL_ONE = 1;
	public static final int GL_SRC_COLOR = 768;
	public static final int GL_ONE_MINUS_SRC_COLOR = 769;
	public static final int GL_SRC_ALPHA = 770;
	public static final int GL_ONE_MINUS_SRC_ALPHA = 771;
	public static final int GL_DST_ALPHA = 772;
	public static final int GL_ONE_MINUS_DST_ALPHA = 773;
	public static final int GL_DST_COLOR = 774;
	public static final int GL_ONE_MINUS_DST_COLOR = 775;
	public static final int GL_REPLACE = 7681;
	public static final int GL_DEPTH_BUFFER_BIT = 256;
	public static final int GL_COLOR_BUFFER_BIT = 16384;
	public static final int GL_RGBA8 = 32856;
	public static final int GL_PROXY_TEXTURE_2D = 32868;
	public static final int GL_RGBA = 6408;
	public static final int GL_TEXTURE_WIDTH = 4096;
	public static final int GL_BGR = 32992;
	public static final int GL_FUNC_ADD = 32774;
	public static final int GL_MIN = 32775;
	public static final int GL_MAX = 32776;
	public static final int GL_FUNC_SUBTRACT = 32778;
	public static final int GL_FUNC_REVERSE_SUBTRACT = 32779;
	public static final int GL_DEPTH_COMPONENT24 = 33190;
	public static final int GL_STATIC_DRAW = 35044;
	public static final int GL_DYNAMIC_DRAW = 35048;
	public static final int GL_STREAM_DRAW = 35040;
	public static final int GL_STATIC_READ = 35045;
	public static final int GL_DYNAMIC_READ = 35049;
	public static final int GL_STREAM_READ = 35041;
	public static final int GL_STATIC_COPY = 35046;
	public static final int GL_DYNAMIC_COPY = 35050;
	public static final int GL_STREAM_COPY = 35042;
	public static final int GL_SYNC_GPU_COMMANDS_COMPLETE = 37143;
	public static final int GL_TIMEOUT_EXPIRED = 37147;
	public static final int GL_WAIT_FAILED = 37149;
	public static final int GL_UNPACK_SWAP_BYTES = 3312;
	public static final int GL_UNPACK_LSB_FIRST = 3313;
	public static final int GL_UNPACK_ROW_LENGTH = 3314;
	public static final int GL_UNPACK_SKIP_ROWS = 3315;
	public static final int GL_UNPACK_SKIP_PIXELS = 3316;
	public static final int GL_UNPACK_ALIGNMENT = 3317;
	public static final int GL_PACK_ALIGNMENT = 3333;
	public static final int GL_PACK_ROW_LENGTH = 3330;
	public static final int GL_MAX_TEXTURE_SIZE = 3379;
	public static final int GL_TEXTURE_2D = 3553;
	public static final int GL_DEPTH_COMPONENT = 6402;
	public static final int GL_DEPTH_COMPONENT32 = 33191;
	public static final int GL_FRAMEBUFFER = 36160;
	public static final int GL_RENDERBUFFER = 36161;
	public static final int GL_COLOR_ATTACHMENT0 = 36064;
	public static final int GL_DEPTH_ATTACHMENT = 36096;
	public static final int GL_FRAMEBUFFER_COMPLETE = 36053;
	public static final int GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT = 36054;
	public static final int GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT = 36055;
	public static final int GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER = 36059;
	public static final int GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER = 36060;
	public static final int GL_FRAMEBUFFER_UNSUPPORTED = 36061;
	public static final int GL_LINK_STATUS = 35714;
	public static final int GL_COMPILE_STATUS = 35713;
	public static final int GL_VERTEX_SHADER = 35633;
	public static final int GL_FRAGMENT_SHADER = 35632;
	public static final int GL_TEXTURE0 = 33984;
	public static final int GL_TEXTURE1 = 33985;
	public static final int GL_TEXTURE2 = 33986;
	public static final int GL_DEPTH_TEXTURE_MODE = 34891;
	public static final int GL_TEXTURE_COMPARE_MODE = 34892;
	public static final int GL_ARRAY_BUFFER = 34962;
	public static final int GL_ELEMENT_ARRAY_BUFFER = 34963;
	public static final int GL_PIXEL_PACK_BUFFER = 35051;
	public static final int GL_COPY_READ_BUFFER = 36662;
	public static final int GL_COPY_WRITE_BUFFER = 36663;
	public static final int GL_PIXEL_UNPACK_BUFFER = 35052;
	public static final int GL_UNIFORM_BUFFER = 35345;
	public static final int GL_ALPHA_BIAS = 3357;
	public static final int GL_RGB = 6407;
	public static final int GL_RG = 33319;
	public static final int GL_R8 = 33321;
	public static final int GL_RED = 6403;
	public static final int GL_OUT_OF_MEMORY = 1285;

	public static int toGl(DepthTestFunction function) {
		return switch (function) {
			case NO_DEPTH_TEST -> GL_ALWAYS;
			case EQUAL_DEPTH_TEST -> GL_EQUAL;
			case LESS_DEPTH_TEST -> GL_LESS;
			case GREATER_DEPTH_TEST -> GL_GREATER;
			default -> GL_LEQUAL;
		};
	}

	public static int toGl(PolygonMode polygonMode) {
		return switch (polygonMode) {
			case WIREFRAME -> GL_LINE;
			default -> GL_FILL;
		};
	}

	public static int toGl(DestFactor factor) {
		return switch (factor) {
			case CONSTANT_ALPHA -> GL14.GL_CONSTANT_ALPHA;
			case CONSTANT_COLOR -> GL14.GL_CONSTANT_COLOR;
			case DST_ALPHA -> GL_DST_ALPHA;
			case DST_COLOR -> GL_DST_COLOR;
			case ONE -> GL_ONE;
			case ONE_MINUS_CONSTANT_ALPHA -> GL14.GL_ONE_MINUS_CONSTANT_ALPHA;
			case ONE_MINUS_CONSTANT_COLOR -> GL14.GL_ONE_MINUS_CONSTANT_COLOR;
			case ONE_MINUS_DST_ALPHA -> GL_ONE_MINUS_DST_ALPHA;
			case ONE_MINUS_DST_COLOR -> GL_ONE_MINUS_DST_COLOR;
			case ONE_MINUS_SRC_ALPHA -> GL_ONE_MINUS_SRC_ALPHA;
			case ONE_MINUS_SRC_COLOR -> GL_ONE_MINUS_SRC_COLOR;
			case SRC_ALPHA -> GL_SRC_ALPHA;
			case SRC_COLOR -> GL_SRC_COLOR;
			case ZERO -> GL_ZERO;
		};
	}

	public static int toGl(SourceFactor factor) {
		return switch (factor) {
			case CONSTANT_ALPHA -> GL14.GL_CONSTANT_ALPHA;
			case CONSTANT_COLOR -> GL14.GL_CONSTANT_COLOR;
			case DST_ALPHA -> GL_DST_ALPHA;
			case DST_COLOR -> GL_DST_COLOR;
			case ONE -> GL_ONE;
			case ONE_MINUS_CONSTANT_ALPHA -> GL14.GL_ONE_MINUS_CONSTANT_ALPHA;
			case ONE_MINUS_CONSTANT_COLOR -> GL14.GL_ONE_MINUS_CONSTANT_COLOR;
			case ONE_MINUS_DST_ALPHA -> GL_ONE_MINUS_DST_ALPHA;
			case ONE_MINUS_DST_COLOR -> GL_ONE_MINUS_DST_COLOR;
			case ONE_MINUS_SRC_ALPHA -> GL_ONE_MINUS_SRC_ALPHA;
			case ONE_MINUS_SRC_COLOR -> GL_ONE_MINUS_SRC_COLOR;
			case SRC_ALPHA -> GL_SRC_ALPHA;
			case SRC_ALPHA_SATURATE -> GL11.GL_SRC_ALPHA_SATURATE;
			case SRC_COLOR -> GL_SRC_COLOR;
			case ZERO -> GL_ZERO;
		};
	}

	public static int toGl(BufferType type) {
		return switch (type) {
			case VERTICES -> GL_ARRAY_BUFFER;
			case INDICES -> GL_ELEMENT_ARRAY_BUFFER;
			case PIXEL_PACK -> GL_PIXEL_PACK_BUFFER;
			case COPY_READ -> GL_COPY_READ_BUFFER;
			case COPY_WRITE -> GL_COPY_WRITE_BUFFER;
			case PIXEL_UNPACK -> GL_PIXEL_UNPACK_BUFFER;
			case UNIFORM -> GL_UNIFORM_BUFFER;
		};
	}

	public static int toGl(VertexFormat.DrawMode drawMode) {
		return switch (drawMode) {
			case LINES -> GL_TRIANGLES;
			case LINE_STRIP -> GL_TRIANGLE_STRIP;
			case DEBUG_LINES -> GL_LINES;
			case DEBUG_LINE_STRIP -> GL_LINE_STRIP;
			case TRIANGLES -> GL_TRIANGLES;
			case TRIANGLE_STRIP -> GL_TRIANGLE_STRIP;
			case TRIANGLE_FAN -> GL_TRIANGLE_FAN;
			case QUADS -> GL_TRIANGLES;
		};
	}

	public static int toGl(VertexFormat.IndexType type) {
		return switch (type) {
			case SHORT -> GL_UNSIGNED_SHORT;
			case INT -> GL_UNSIGNED_INT;
		};
	}

	public static int toGl(NativeImage.Format format) {
		return switch (format) {
			case RGBA -> GL_RGBA;
			case RGB -> GL_RGB;
			case LUMINANCE_ALPHA -> GL_RG;
			case LUMINANCE -> GL_RED;
		};
	}

	public static int toGl(BufferUsage usage) {
		return switch (usage) {
			case DYNAMIC_WRITE -> GL_DYNAMIC_DRAW;
			case STATIC_WRITE -> GL_STATIC_DRAW;
			case STREAM_WRITE -> GL_STREAM_DRAW;
			case STATIC_READ -> GL_STATIC_READ;
			case DYNAMIC_READ -> GL_DYNAMIC_READ;
			case STREAM_READ -> GL_STREAM_READ;
			case DYNAMIC_COPY -> GL_DYNAMIC_COPY;
			case STATIC_COPY -> GL_STATIC_COPY;
			case STREAM_COPY -> GL_STREAM_COPY;
		};
	}

	public static int toGl(AddressMode addressMode) {
		return switch (addressMode) {
			case REPEAT -> GL_REPEAT;
			case CLAMP_TO_EDGE -> GL_CLAMP_TO_EDGE;
		};
	}

	public static int toGl(VertexFormatElement.Type type) {
		return switch (type) {
			case FLOAT -> GL_FLOAT;
			case UBYTE -> GL_UNSIGNED_BYTE;
			case BYTE -> GL_BYTE;
			case USHORT -> GL_UNSIGNED_SHORT;
			case SHORT -> GL_SHORT;
			case UINT -> GL_UNSIGNED_INT;
			case INT -> GL_INT;
		};
	}

	public static int toGlInternalId(TextureFormat format) {
		return switch (format) {
			case RGBA8 -> GL_RGBA8;
			case RED8 -> GL_R8;
			case DEPTH32 -> GL_DEPTH_COMPONENT32;
		};
	}

	public static int toGlExternalId(TextureFormat format) {
		return switch (format) {
			case RGBA8 -> GL_RGBA;
			case RED8 -> GL_RED;
			case DEPTH32 -> GL_DEPTH_COMPONENT;
		};
	}

	public static int toGlType(TextureFormat format) {
		return switch (format) {
			case RGBA8 -> GL_UNSIGNED_BYTE;
			case RED8 -> GL_UNSIGNED_BYTE;
			case DEPTH32 -> GL_FLOAT;
		};
	}

	public static int toGl(ShaderType type) {
		return switch (type) {
			case VERTEX -> GL_VERTEX_SHADER;
			case FRAGMENT -> GL_FRAGMENT_SHADER;
		};
	}
}
