package net.minecraft.client.render.model;

import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Util;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.AffineTransformations;
import net.minecraft.util.math.AxisRotation;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.DirectionTransformation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

@Environment(EnvType.CLIENT)
public enum ModelRotation implements ModelBakeSettings {
	X0_Y0(AxisRotation.R0, AxisRotation.R0),
	X0_Y90(AxisRotation.R0, AxisRotation.R90),
	X0_Y180(AxisRotation.R0, AxisRotation.R180),
	X0_Y270(AxisRotation.R0, AxisRotation.R270),
	X90_Y0(AxisRotation.R90, AxisRotation.R0),
	X90_Y90(AxisRotation.R90, AxisRotation.R90),
	X90_Y180(AxisRotation.R90, AxisRotation.R180),
	X90_Y270(AxisRotation.R90, AxisRotation.R270),
	X180_Y0(AxisRotation.R180, AxisRotation.R0),
	X180_Y90(AxisRotation.R180, AxisRotation.R90),
	X180_Y180(AxisRotation.R180, AxisRotation.R180),
	X180_Y270(AxisRotation.R180, AxisRotation.R270),
	X270_Y0(AxisRotation.R270, AxisRotation.R0),
	X270_Y90(AxisRotation.R270, AxisRotation.R90),
	X270_Y180(AxisRotation.R270, AxisRotation.R180),
	X270_Y270(AxisRotation.R270, AxisRotation.R270);

	private static final ModelRotation[][] ROTATION_MAP = Util.make(
		new ModelRotation[AxisRotation.values().length][AxisRotation.values().length], modelRotations -> {
			for (ModelRotation modelRotation : values()) {
				modelRotations[modelRotation.xRotation.ordinal()][modelRotation.yRotation.ordinal()] = modelRotation;
			}
		}
	);
	private final AxisRotation xRotation;
	private final AxisRotation yRotation;
	final AffineTransformation rotation;
	private final DirectionTransformation directionTransformation;
	final Map<Direction, Matrix4fc> faces = new EnumMap(Direction.class);
	final Map<Direction, Matrix4fc> invertedFaces = new EnumMap(Direction.class);
	private final ModelRotation.UVModel uvModel = new ModelRotation.UVModel(this);

	private ModelRotation(final AxisRotation x, final AxisRotation y) {
		this.xRotation = x;
		this.yRotation = y;
		this.directionTransformation = DirectionTransformation.fromRotations(x, y);
		if (this.directionTransformation != DirectionTransformation.IDENTITY) {
			this.rotation = new AffineTransformation(new Matrix4f(this.directionTransformation.getMatrix()));
		} else {
			this.rotation = AffineTransformation.identity();
		}

		for (Direction direction : Direction.values()) {
			Matrix4fc matrix4fc = AffineTransformations.getTransformed(this.rotation, direction).getMatrix();
			this.faces.put(direction, matrix4fc);
			this.invertedFaces.put(direction, matrix4fc.invertAffine(new Matrix4f()));
		}
	}

	@Override
	public AffineTransformation getRotation() {
		return this.rotation;
	}

	public static ModelRotation rotate(AxisRotation xRotation, AxisRotation yRotation) {
		return ROTATION_MAP[xRotation.ordinal()][yRotation.ordinal()];
	}

	public DirectionTransformation getDirectionTransformation() {
		return this.directionTransformation;
	}

	public ModelBakeSettings getUVModel() {
		return this.uvModel;
	}

	@Environment(EnvType.CLIENT)
	record UVModel(ModelRotation parent) implements ModelBakeSettings {
		@Override
		public AffineTransformation getRotation() {
			return this.parent.rotation;
		}

		@Override
		public Matrix4fc forward(Direction facing) {
			return (Matrix4fc)this.parent.faces.getOrDefault(facing, TRANSFORM_NONE);
		}

		@Override
		public Matrix4fc reverse(Direction facing) {
			return (Matrix4fc)this.parent.invertedFaces.getOrDefault(facing, TRANSFORM_NONE);
		}
	}
}
