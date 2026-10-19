package net.minecraft.client.render.model;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.model.json.ModelElementFace;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.AxisRotation;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.MatrixUtil;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public class BakedQuadFactory {
	public static final int field_32796 = 8;
	private static final float MIN_SCALE = 1.0F / (float)Math.cos((float) (Math.PI / 8)) - 1.0F;
	private static final float MAX_SCALE = 1.0F / (float)Math.cos((float) (Math.PI / 4)) - 1.0F;
	public static final int field_32797 = 4;
	private static final int field_32799 = 3;
	public static final int field_32798 = 4;

	@VisibleForTesting
	static ModelElementFace.UV setDefaultUV(Vector3fc from, Vector3fc to, Direction facing) {
		return switch (facing) {
			case DOWN -> new ModelElementFace.UV(from.x(), 16.0F - to.z(), to.x(), 16.0F - from.z());
			case UP -> new ModelElementFace.UV(from.x(), from.z(), to.x(), to.z());
			case NORTH -> new ModelElementFace.UV(16.0F - to.x(), 16.0F - to.y(), 16.0F - from.x(), 16.0F - from.y());
			case SOUTH -> new ModelElementFace.UV(from.x(), 16.0F - to.y(), to.x(), 16.0F - from.y());
			case WEST -> new ModelElementFace.UV(from.z(), 16.0F - to.y(), to.z(), 16.0F - from.y());
			case EAST -> new ModelElementFace.UV(16.0F - to.z(), 16.0F - to.y(), 16.0F - from.z(), 16.0F - from.y());
		};
	}

	public static BakedQuad bake(
		Vector3fc from,
		Vector3fc to,
		ModelElementFace facing,
		Sprite sprite,
		Direction direction,
		ModelBakeSettings settings,
		@Nullable net.minecraft.client.render.model.json.ModelRotation rotation,
		boolean shade,
		int lightEmission
	) {
		ModelElementFace.UV uV = facing.uvs();
		if (uV == null) {
			uV = setDefaultUV(from, to, direction);
		}

		uV = compactUV(sprite, uV);
		Matrix4fc matrix4fc = settings.reverse(direction);
		int[] is = packVertexData(uV, facing.rotation(), matrix4fc, sprite, direction, getPositionMatrix(from, to), settings.getRotation(), rotation);
		Direction direction2 = decodeDirection(is);
		if (rotation == null) {
			encodeDirection(is, direction2);
		}

		return new BakedQuad(is, facing.tintIndex(), direction2, sprite, shade, lightEmission);
	}

	private static ModelElementFace.UV compactUV(Sprite sprite, ModelElementFace.UV uv) {
		float f = uv.minU();
		float g = uv.minV();
		float h = uv.maxU();
		float i = uv.maxV();
		float j = sprite.getUvScaleDelta();
		float k = (f + f + h + h) / 4.0F;
		float l = (g + g + i + i) / 4.0F;
		return new ModelElementFace.UV(MathHelper.lerp(j, f, k), MathHelper.lerp(j, g, l), MathHelper.lerp(j, h, k), MathHelper.lerp(j, i, l));
	}

	private static int[] packVertexData(
		ModelElementFace.UV texture,
		AxisRotation rotation,
		Matrix4fc matrix4fc,
		Sprite sprite,
		Direction facing,
		float[] fs,
		AffineTransformation transform,
		@Nullable net.minecraft.client.render.model.json.ModelRotation modelRotation
	) {
		CubeFace cubeFace = CubeFace.getFace(facing);
		int[] is = new int[32];

		for (int i = 0; i < 4; i++) {
			packVertexData(is, i, cubeFace, texture, rotation, matrix4fc, fs, sprite, transform, modelRotation);
		}

		return is;
	}

	private static float[] getPositionMatrix(Vector3fc from, Vector3fc to) {
		float[] fs = new float[Direction.values().length];
		fs[CubeFace.DirectionIds.WEST] = from.x() / 16.0F;
		fs[CubeFace.DirectionIds.DOWN] = from.y() / 16.0F;
		fs[CubeFace.DirectionIds.NORTH] = from.z() / 16.0F;
		fs[CubeFace.DirectionIds.EAST] = to.x() / 16.0F;
		fs[CubeFace.DirectionIds.UP] = to.y() / 16.0F;
		fs[CubeFace.DirectionIds.SOUTH] = to.z() / 16.0F;
		return fs;
	}

	private static void packVertexData(
		int[] vertices,
		int cornerIndex,
		CubeFace cubeFace,
		ModelElementFace.UV texture,
		AxisRotation axisRotation,
		Matrix4fc matrix4fc,
		float[] fs,
		Sprite sprite,
		AffineTransformation affineTransformation,
		@Nullable net.minecraft.client.render.model.json.ModelRotation modelRotation
	) {
		CubeFace.Corner corner = cubeFace.getCorner(cornerIndex);
		Vector3f vector3f = new Vector3f(fs[corner.xSide], fs[corner.ySide], fs[corner.zSide]);
		rotateVertex(vector3f, modelRotation);
		transformVertex(vector3f, affineTransformation);
		float f = ModelElementFace.getUValue(texture, axisRotation, cornerIndex);
		float g = ModelElementFace.getVValue(texture, axisRotation, cornerIndex);
		float i;
		float h;
		if (MatrixUtil.isIdentity(matrix4fc)) {
			h = f;
			i = g;
		} else {
			Vector3f vector3f2 = matrix4fc.transformPosition(new Vector3f(setCenterBack(f), setCenterBack(g), 0.0F));
			h = setCenterForward(vector3f2.x);
			i = setCenterForward(vector3f2.y);
		}

		packVertexData(vertices, cornerIndex, vector3f, sprite, h, i);
	}

	private static float setCenterBack(float f) {
		return f - 0.5F;
	}

	private static float setCenterForward(float f) {
		return f + 0.5F;
	}

	private static void packVertexData(int[] vertices, int cornerIndex, Vector3f pos, Sprite sprite, float f, float g) {
		int i = cornerIndex * 8;
		vertices[i] = Float.floatToRawIntBits(pos.x());
		vertices[i + 1] = Float.floatToRawIntBits(pos.y());
		vertices[i + 2] = Float.floatToRawIntBits(pos.z());
		vertices[i + 3] = -1;
		vertices[i + 4] = Float.floatToRawIntBits(sprite.getFrameU(f));
		vertices[i + 4 + 1] = Float.floatToRawIntBits(sprite.getFrameV(g));
	}

	private static void rotateVertex(Vector3f vertex, @Nullable net.minecraft.client.render.model.json.ModelRotation rotation) {
		if (rotation != null) {
			Vector3f vector3f;
			Vector3f vector3f2;
			switch (rotation.axis()) {
				case X:
					vector3f = new Vector3f(1.0F, 0.0F, 0.0F);
					vector3f2 = new Vector3f(0.0F, 1.0F, 1.0F);
					break;
				case Y:
					vector3f = new Vector3f(0.0F, 1.0F, 0.0F);
					vector3f2 = new Vector3f(1.0F, 0.0F, 1.0F);
					break;
				case Z:
					vector3f = new Vector3f(0.0F, 0.0F, 1.0F);
					vector3f2 = new Vector3f(1.0F, 1.0F, 0.0F);
					break;
				default:
					throw new IllegalArgumentException("There are only 3 axes");
			}

			Quaternionf quaternionf = new Quaternionf().rotationAxis(rotation.angle() * (float) (Math.PI / 180.0), vector3f);
			if (rotation.rescale()) {
				if (Math.abs(rotation.angle()) == 22.5F) {
					vector3f2.mul(MIN_SCALE);
				} else {
					vector3f2.mul(MAX_SCALE);
				}

				vector3f2.add(1.0F, 1.0F, 1.0F);
			} else {
				vector3f2.set(1.0F, 1.0F, 1.0F);
			}

			transformVertex(vertex, new Vector3f(rotation.origin()), new Matrix4f().rotation(quaternionf), vector3f2);
		}
	}

	private static void transformVertex(Vector3f vertex, AffineTransformation transformation) {
		if (transformation != AffineTransformation.identity()) {
			transformVertex(vertex, new Vector3f(0.5F, 0.5F, 0.5F), transformation.getMatrix(), new Vector3f(1.0F, 1.0F, 1.0F));
		}
	}

	private static void transformVertex(Vector3f vertex, Vector3fc vector3fc, Matrix4fc matrix4fc, Vector3fc vector3fc2) {
		Vector4f vector4f = matrix4fc.transform(new Vector4f(vertex.x() - vector3fc.x(), vertex.y() - vector3fc.y(), vertex.z() - vector3fc.z(), 1.0F));
		vector4f.mul(new Vector4f(vector3fc2, 1.0F));
		vertex.set(vector4f.x() + vector3fc.x(), vector4f.y() + vector3fc.y(), vector4f.z() + vector3fc.z());
	}

	private static Direction decodeDirection(int[] rotationMatrix) {
		Vector3f vector3f = bakeVectors(rotationMatrix, 0);
		Vector3f vector3f2 = bakeVectors(rotationMatrix, 8);
		Vector3f vector3f3 = bakeVectors(rotationMatrix, 16);
		Vector3f vector3f4 = new Vector3f(vector3f).sub(vector3f2);
		Vector3f vector3f5 = new Vector3f(vector3f3).sub(vector3f2);
		Vector3f vector3f6 = new Vector3f(vector3f5).cross(vector3f4).normalize();
		if (!vector3f6.isFinite()) {
			return Direction.UP;
		} else {
			Direction direction = null;
			float f = 0.0F;

			for (Direction direction2 : Direction.values()) {
				float g = vector3f6.dot(direction2.getFloatVector());
				if (g >= 0.0F && g > f) {
					f = g;
					direction = direction2;
				}
			}

			return direction == null ? Direction.UP : direction;
		}
	}

	private static float bakeVectorX(int[] is, int i) {
		return Float.intBitsToFloat(is[i]);
	}

	private static float bakeVectorY(int[] is, int i) {
		return Float.intBitsToFloat(is[i + 1]);
	}

	private static float bakeVectorZ(int[] is, int i) {
		return Float.intBitsToFloat(is[i + 2]);
	}

	private static Vector3f bakeVectors(int[] is, int i) {
		return new Vector3f(bakeVectorX(is, i), bakeVectorY(is, i), bakeVectorZ(is, i));
	}

	private static void encodeDirection(int[] rotationMatrix, Direction direction) {
		int[] is = new int[rotationMatrix.length];
		System.arraycopy(rotationMatrix, 0, is, 0, rotationMatrix.length);
		float[] fs = new float[Direction.values().length];
		fs[CubeFace.DirectionIds.WEST] = 999.0F;
		fs[CubeFace.DirectionIds.DOWN] = 999.0F;
		fs[CubeFace.DirectionIds.NORTH] = 999.0F;
		fs[CubeFace.DirectionIds.EAST] = -999.0F;
		fs[CubeFace.DirectionIds.UP] = -999.0F;
		fs[CubeFace.DirectionIds.SOUTH] = -999.0F;

		for (int i = 0; i < 4; i++) {
			int j = 8 * i;
			float f = bakeVectorX(is, j);
			float g = bakeVectorY(is, j);
			float h = bakeVectorZ(is, j);
			if (f < fs[CubeFace.DirectionIds.WEST]) {
				fs[CubeFace.DirectionIds.WEST] = f;
			}

			if (g < fs[CubeFace.DirectionIds.DOWN]) {
				fs[CubeFace.DirectionIds.DOWN] = g;
			}

			if (h < fs[CubeFace.DirectionIds.NORTH]) {
				fs[CubeFace.DirectionIds.NORTH] = h;
			}

			if (f > fs[CubeFace.DirectionIds.EAST]) {
				fs[CubeFace.DirectionIds.EAST] = f;
			}

			if (g > fs[CubeFace.DirectionIds.UP]) {
				fs[CubeFace.DirectionIds.UP] = g;
			}

			if (h > fs[CubeFace.DirectionIds.SOUTH]) {
				fs[CubeFace.DirectionIds.SOUTH] = h;
			}
		}

		CubeFace cubeFace = CubeFace.getFace(direction);

		for (int jx = 0; jx < 4; jx++) {
			int k = 8 * jx;
			CubeFace.Corner corner = cubeFace.getCorner(jx);
			float hx = fs[corner.xSide];
			float l = fs[corner.ySide];
			float m = fs[corner.zSide];
			rotationMatrix[k] = Float.floatToRawIntBits(hx);
			rotationMatrix[k + 1] = Float.floatToRawIntBits(l);
			rotationMatrix[k + 2] = Float.floatToRawIntBits(m);

			for (int n = 0; n < 4; n++) {
				int o = 8 * n;
				float p = bakeVectorX(is, o);
				float q = bakeVectorY(is, o);
				float r = bakeVectorZ(is, o);
				if (MathHelper.approximatelyEquals(hx, p) && MathHelper.approximatelyEquals(l, q) && MathHelper.approximatelyEquals(m, r)) {
					rotationMatrix[k + 4] = is[o + 4];
					rotationMatrix[k + 4 + 1] = is[o + 4 + 1];
				}
			}
		}
	}

	public static void calculatePosition(int[] is, Consumer<Vector3f> consumer) {
		for (int i = 0; i < 4; i++) {
			consumer.accept(bakeVectors(is, 8 * i));
		}
	}
}
