package net.minecraft.util.math;

import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public enum AxisRotation {
	R0(0),
	R90(1),
	R180(2),
	R270(3);

	public static final Codec<AxisRotation> CODEC = Codec.INT.comapFlatMap(degrees -> {
		return switch (MathHelper.floorMod(degrees, 360)) {
			case 0 -> DataResult.success(R0);
			case 90 -> DataResult.success(R90);
			case 180 -> DataResult.success(R180);
			case 270 -> DataResult.success(R270);
			default -> DataResult.error(() -> "Invalid rotation " + degrees + " found, only 0/90/180/270 allowed");
		};
	}, rotation -> {
		return switch (rotation) {
			case R0 -> 0;
			case R90 -> 90;
			case R180 -> 180;
			case R270 -> 270;
		};
	});
	public final int index;

	private AxisRotation(final int index) {
		this.index = index;
	}

	@Deprecated
	public static AxisRotation fromDegrees(int degrees) {
		return switch (MathHelper.floorMod(degrees, 360)) {
			case 0 -> R0;
			case 90 -> R90;
			case 180 -> R180;
			case 270 -> R270;
			default -> throw new JsonParseException("Invalid rotation " + degrees + " found, only 0/90/180/270 allowed");
		};
	}

	public int rotate(int index) {
		return (index + this.index) % 4;
	}
}
