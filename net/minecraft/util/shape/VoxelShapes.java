package net.minecraft.util.shape;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.math.DoubleMath;
import com.google.common.math.IntMath;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.util.Util;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.AxisCycleDirection;
import net.minecraft.util.math.AxisRotation;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.DirectionTransformation;
import net.minecraft.util.math.Vec3d;

public final class VoxelShapes {
	public static final double MIN_SIZE = 1.0E-7;
	public static final double field_31881 = 1.0E-6;
	private static final VoxelShape FULL_CUBE = Util.make(() -> {
		VoxelSet voxelSet = new BitSetVoxelSet(1, 1, 1);
		voxelSet.set(0, 0, 0);
		return new SimpleVoxelShape(voxelSet);
	});
	private static final Vec3d BLOCK_CENTER = new Vec3d(0.5, 0.5, 0.5);
	public static final VoxelShape UNBOUNDED = cuboid(
		Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
	);
	private static final VoxelShape EMPTY = new ArrayVoxelShape(
		new BitSetVoxelSet(0, 0, 0), new DoubleArrayList(new double[]{0.0}), new DoubleArrayList(new double[]{0.0}), new DoubleArrayList(new double[]{0.0})
	);

	public static VoxelShape empty() {
		return EMPTY;
	}

	public static VoxelShape fullCube() {
		return FULL_CUBE;
	}

	public static VoxelShape cuboid(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		if (!(minX > maxX) && !(minY > maxY) && !(minZ > maxZ)) {
			return cuboidUnchecked(minX, minY, minZ, maxX, maxY, maxZ);
		} else {
			throw new IllegalArgumentException("The min values need to be smaller or equals to the max values");
		}
	}

	public static VoxelShape cuboidUnchecked(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		if (!(maxX - minX < 1.0E-7) && !(maxY - minY < 1.0E-7) && !(maxZ - minZ < 1.0E-7)) {
			int i = findRequiredBitResolution(minX, maxX);
			int j = findRequiredBitResolution(minY, maxY);
			int k = findRequiredBitResolution(minZ, maxZ);
			if (i < 0 || j < 0 || k < 0) {
				return new ArrayVoxelShape(
					FULL_CUBE.voxels,
					DoubleArrayList.wrap(new double[]{minX, maxX}),
					DoubleArrayList.wrap(new double[]{minY, maxY}),
					DoubleArrayList.wrap(new double[]{minZ, maxZ})
				);
			} else if (i == 0 && j == 0 && k == 0) {
				return fullCube();
			} else {
				int l = 1 << i;
				int m = 1 << j;
				int n = 1 << k;
				BitSetVoxelSet bitSetVoxelSet = BitSetVoxelSet.create(
					l,
					m,
					n,
					(int)Math.round(minX * l),
					(int)Math.round(minY * m),
					(int)Math.round(minZ * n),
					(int)Math.round(maxX * l),
					(int)Math.round(maxY * m),
					(int)Math.round(maxZ * n)
				);
				return new SimpleVoxelShape(bitSetVoxelSet);
			}
		} else {
			return empty();
		}
	}

	public static VoxelShape cuboid(Box box) {
		return cuboidUnchecked(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
	}

	@VisibleForTesting
	protected static int findRequiredBitResolution(double min, double max) {
		if (!(min < -1.0E-7) && !(max > 1.0000001)) {
			for (int i = 0; i <= 3; i++) {
				int j = 1 << i;
				double d = min * j;
				double e = max * j;
				boolean bl = Math.abs(d - Math.round(d)) < 1.0E-7 * j;
				boolean bl2 = Math.abs(e - Math.round(e)) < 1.0E-7 * j;
				if (bl && bl2) {
					return i;
				}
			}

			return -1;
		} else {
			return -1;
		}
	}

	protected static long lcm(int a, int b) {
		return (long)a * (b / IntMath.gcd(a, b));
	}

	public static VoxelShape union(VoxelShape first, VoxelShape second) {
		return combineAndSimplify(first, second, BooleanBiFunction.OR);
	}

	public static VoxelShape union(VoxelShape first, VoxelShape... others) {
		return (VoxelShape)Arrays.stream(others).reduce(first, VoxelShapes::union);
	}

	public static VoxelShape combineAndSimplify(VoxelShape first, VoxelShape second, BooleanBiFunction function) {
		return combine(first, second, function).simplify();
	}

	public static VoxelShape combine(VoxelShape one, VoxelShape two, BooleanBiFunction function) {
		if (function.apply(false, false)) {
			throw (IllegalArgumentException)Util.getFatalOrPause(new IllegalArgumentException());
		} else if (one == two) {
			return function.apply(true, true) ? one : empty();
		} else {
			boolean bl = function.apply(true, false);
			boolean bl2 = function.apply(false, true);
			if (one.isEmpty()) {
				return bl2 ? two : empty();
			} else if (two.isEmpty()) {
				return bl ? one : empty();
			} else {
				PairList pairList = createListPair(1, one.getPointPositions(Direction.Axis.X), two.getPointPositions(Direction.Axis.X), bl, bl2);
				PairList pairList2 = createListPair(pairList.size() - 1, one.getPointPositions(Direction.Axis.Y), two.getPointPositions(Direction.Axis.Y), bl, bl2);
				PairList pairList3 = createListPair(
					(pairList.size() - 1) * (pairList2.size() - 1), one.getPointPositions(Direction.Axis.Z), two.getPointPositions(Direction.Axis.Z), bl, bl2
				);
				BitSetVoxelSet bitSetVoxelSet = BitSetVoxelSet.combine(one.voxels, two.voxels, pairList, pairList2, pairList3, function);
				return (VoxelShape)(pairList instanceof FractionalPairList && pairList2 instanceof FractionalPairList && pairList3 instanceof FractionalPairList
					? new SimpleVoxelShape(bitSetVoxelSet)
					: new ArrayVoxelShape(bitSetVoxelSet, pairList.getPairs(), pairList2.getPairs(), pairList3.getPairs()));
			}
		}
	}

	public static boolean matchesAnywhere(VoxelShape shape1, VoxelShape shape2, BooleanBiFunction predicate) {
		if (predicate.apply(false, false)) {
			throw (IllegalArgumentException)Util.getFatalOrPause(new IllegalArgumentException());
		} else {
			boolean bl = shape1.isEmpty();
			boolean bl2 = shape2.isEmpty();
			if (!bl && !bl2) {
				if (shape1 == shape2) {
					return predicate.apply(true, true);
				} else {
					boolean bl3 = predicate.apply(true, false);
					boolean bl4 = predicate.apply(false, true);

					for (Direction.Axis axis : AxisCycleDirection.AXES) {
						if (shape1.getMax(axis) < shape2.getMin(axis) - 1.0E-7) {
							return bl3 || bl4;
						}

						if (shape2.getMax(axis) < shape1.getMin(axis) - 1.0E-7) {
							return bl3 || bl4;
						}
					}

					PairList pairList = createListPair(1, shape1.getPointPositions(Direction.Axis.X), shape2.getPointPositions(Direction.Axis.X), bl3, bl4);
					PairList pairList2 = createListPair(pairList.size() - 1, shape1.getPointPositions(Direction.Axis.Y), shape2.getPointPositions(Direction.Axis.Y), bl3, bl4);
					PairList pairList3 = createListPair(
						(pairList.size() - 1) * (pairList2.size() - 1), shape1.getPointPositions(Direction.Axis.Z), shape2.getPointPositions(Direction.Axis.Z), bl3, bl4
					);
					return matchesAnywhere(pairList, pairList2, pairList3, shape1.voxels, shape2.voxels, predicate);
				}
			} else {
				return predicate.apply(!bl, !bl2);
			}
		}
	}

	private static boolean matchesAnywhere(PairList mergedX, PairList mergedY, PairList mergedZ, VoxelSet shape1, VoxelSet shape2, BooleanBiFunction predicate) {
		return !mergedX.forEachPair(
			(x1, x2, index1) -> mergedY.forEachPair(
				(y1, y2, index2) -> mergedZ.forEachPair(
					(z1, z2, index3) -> !predicate.apply(shape1.inBoundsAndContains(x1, y1, z1), shape2.inBoundsAndContains(x2, y2, z2))
				)
			)
		);
	}

	public static double calculateMaxOffset(Direction.Axis axis, Box box, Iterable<VoxelShape> shapes, double maxDist) {
		for (VoxelShape voxelShape : shapes) {
			if (Math.abs(maxDist) < 1.0E-7) {
				return 0.0;
			}

			maxDist = voxelShape.calculateMaxDistance(axis, box, maxDist);
		}

		return maxDist;
	}

	public static boolean isSideCovered(VoxelShape shape, VoxelShape neighbor, Direction direction) {
		if (shape == fullCube() && neighbor == fullCube()) {
			return true;
		} else if (neighbor.isEmpty()) {
			return false;
		} else {
			Direction.Axis axis = direction.getAxis();
			Direction.AxisDirection axisDirection = direction.getDirection();
			VoxelShape voxelShape = axisDirection == Direction.AxisDirection.POSITIVE ? shape : neighbor;
			VoxelShape voxelShape2 = axisDirection == Direction.AxisDirection.POSITIVE ? neighbor : shape;
			BooleanBiFunction booleanBiFunction = axisDirection == Direction.AxisDirection.POSITIVE ? BooleanBiFunction.ONLY_FIRST : BooleanBiFunction.ONLY_SECOND;
			return DoubleMath.fuzzyEquals(voxelShape.getMax(axis), 1.0, 1.0E-7)
				&& DoubleMath.fuzzyEquals(voxelShape2.getMin(axis), 0.0, 1.0E-7)
				&& !matchesAnywhere(
					new SlicedVoxelShape(voxelShape, axis, voxelShape.voxels.getSize(axis) - 1), new SlicedVoxelShape(voxelShape2, axis, 0), booleanBiFunction
				);
		}
	}

	public static boolean adjacentSidesCoverSquare(VoxelShape one, VoxelShape two, Direction direction) {
		if (one != fullCube() && two != fullCube()) {
			Direction.Axis axis = direction.getAxis();
			Direction.AxisDirection axisDirection = direction.getDirection();
			VoxelShape voxelShape = axisDirection == Direction.AxisDirection.POSITIVE ? one : two;
			VoxelShape voxelShape2 = axisDirection == Direction.AxisDirection.POSITIVE ? two : one;
			if (!DoubleMath.fuzzyEquals(voxelShape.getMax(axis), 1.0, 1.0E-7)) {
				voxelShape = empty();
			}

			if (!DoubleMath.fuzzyEquals(voxelShape2.getMin(axis), 0.0, 1.0E-7)) {
				voxelShape2 = empty();
			}

			return !matchesAnywhere(
				fullCube(),
				combine(new SlicedVoxelShape(voxelShape, axis, voxelShape.voxels.getSize(axis) - 1), new SlicedVoxelShape(voxelShape2, axis, 0), BooleanBiFunction.OR),
				BooleanBiFunction.ONLY_FIRST
			);
		} else {
			return true;
		}
	}

	public static boolean unionCoversFullCube(VoxelShape one, VoxelShape two) {
		if (one == fullCube() || two == fullCube()) {
			return true;
		} else {
			return one.isEmpty() && two.isEmpty() ? false : !matchesAnywhere(fullCube(), combine(one, two, BooleanBiFunction.OR), BooleanBiFunction.ONLY_FIRST);
		}
	}

	@VisibleForTesting
	protected static PairList createListPair(int size, DoubleList first, DoubleList second, boolean includeFirst, boolean includeSecond) {
		int i = first.size() - 1;
		int j = second.size() - 1;
		if (first instanceof FractionalDoubleList && second instanceof FractionalDoubleList) {
			long l = lcm(i, j);
			if (size * l <= 256L) {
				return new FractionalPairList(i, j);
			}
		}

		if (first.getDouble(i) < second.getDouble(0) - 1.0E-7) {
			return new DisjointPairList(first, second, false);
		} else if (second.getDouble(j) < first.getDouble(0) - 1.0E-7) {
			return new DisjointPairList(second, first, true);
		} else {
			return (PairList)(i == j && Objects.equals(first, second) ? new IdentityPairList(first) : new SimplePairList(first, second, includeFirst, includeSecond));
		}
	}

	public static VoxelShape transform(VoxelShape shape, DirectionTransformation transformation) {
		return transform(shape, transformation, BLOCK_CENTER);
	}

	public static VoxelShape transform(VoxelShape shape, DirectionTransformation transformation, Vec3d anchor) {
		if (transformation == DirectionTransformation.IDENTITY) {
			return shape;
		} else {
			VoxelSet voxelSet = shape.voxels.transform(transformation);
			if (shape instanceof SimpleVoxelShape && BLOCK_CENTER.equals(anchor)) {
				return new SimpleVoxelShape(voxelSet);
			} else {
				Direction.Axis axis = transformation.map(Direction.Axis.X);
				Direction.Axis axis2 = transformation.map(Direction.Axis.Y);
				Direction.Axis axis3 = transformation.map(Direction.Axis.Z);
				DoubleList doubleList = shape.getPointPositions(axis);
				DoubleList doubleList2 = shape.getPointPositions(axis2);
				DoubleList doubleList3 = shape.getPointPositions(axis3);
				boolean bl = transformation.shouldFlipDirection(axis);
				boolean bl2 = transformation.shouldFlipDirection(axis2);
				boolean bl3 = transformation.shouldFlipDirection(axis3);
				boolean bl4 = axis.choose(bl, bl2, bl3);
				boolean bl5 = axis2.choose(bl, bl2, bl3);
				boolean bl6 = axis3.choose(bl, bl2, bl3);
				return new ArrayVoxelShape(
					voxelSet,
					transform(doubleList, bl4, anchor.getComponentAlongAxis(axis), anchor.x),
					transform(doubleList2, bl5, anchor.getComponentAlongAxis(axis2), anchor.y),
					transform(doubleList3, bl6, anchor.getComponentAlongAxis(axis3), anchor.z)
				);
			}
		}
	}

	@VisibleForTesting
	static DoubleList transform(DoubleList pointPositions, boolean flip, double component, double anchor) {
		if (!flip && component == anchor) {
			return pointPositions;
		} else {
			int i = pointPositions.size();
			DoubleList doubleList = new DoubleArrayList(i);
			int j = flip ? -1 : 1;

			for (int k = flip ? i - 1 : 0; k >= 0 && k < i; k += j) {
				doubleList.add(anchor + j * (pointPositions.getDouble(k) - component));
			}

			return doubleList;
		}
	}

	public static boolean equal(VoxelShape shape1, VoxelShape shape2) {
		return !matchesAnywhere(shape1, shape2, BooleanBiFunction.NOT_SAME);
	}

	public static Map<Direction.Axis, VoxelShape> createHorizontalAxisShapeMap(VoxelShape shape) {
		return createHorizontalAxisShapeMap(shape, BLOCK_CENTER);
	}

	public static Map<Direction.Axis, VoxelShape> createHorizontalAxisShapeMap(VoxelShape shape, Vec3d anchor) {
		return Maps.newEnumMap(
			Map.of(Direction.Axis.Z, shape, Direction.Axis.X, transform(shape, DirectionTransformation.fromRotations(AxisRotation.R0, AxisRotation.R90), anchor))
		);
	}

	public static Map<Direction.Axis, VoxelShape> createAxisShapeMap(VoxelShape shape) {
		return createAxisShapeMap(shape, BLOCK_CENTER);
	}

	public static Map<Direction.Axis, VoxelShape> createAxisShapeMap(VoxelShape shape, Vec3d anchor) {
		return Maps.newEnumMap(
			Map.of(
				Direction.Axis.Z,
				shape,
				Direction.Axis.X,
				transform(shape, DirectionTransformation.fromRotations(AxisRotation.R0, AxisRotation.R90), anchor),
				Direction.Axis.Y,
				transform(shape, DirectionTransformation.fromRotations(AxisRotation.R90, AxisRotation.R0), anchor)
			)
		);
	}

	public static Map<Direction, VoxelShape> createHorizontalFacingShapeMap(VoxelShape shape) {
		return createHorizontalFacingShapeMap(shape, BLOCK_CENTER);
	}

	public static Map<Direction, VoxelShape> createHorizontalFacingShapeMap(VoxelShape shape, Vec3d anchor) {
		return Maps.newEnumMap(
			Map.of(
				Direction.NORTH,
				shape,
				Direction.EAST,
				transform(shape, DirectionTransformation.fromRotations(AxisRotation.R0, AxisRotation.R90), anchor),
				Direction.SOUTH,
				transform(shape, DirectionTransformation.fromRotations(AxisRotation.R0, AxisRotation.R180), anchor),
				Direction.WEST,
				transform(shape, DirectionTransformation.fromRotations(AxisRotation.R0, AxisRotation.R270), anchor)
			)
		);
	}

	public static Map<Direction, VoxelShape> createFacingShapeMap(VoxelShape shape) {
		return createFacingShapeMap(shape, BLOCK_CENTER);
	}

	public static Map<Direction, VoxelShape> createFacingShapeMap(VoxelShape shape, Vec3d anchor) {
		return Maps.newEnumMap(
			Map.of(
				Direction.NORTH,
				shape,
				Direction.EAST,
				transform(shape, DirectionTransformation.fromRotations(AxisRotation.R0, AxisRotation.R90), anchor),
				Direction.SOUTH,
				transform(shape, DirectionTransformation.fromRotations(AxisRotation.R0, AxisRotation.R180), anchor),
				Direction.WEST,
				transform(shape, DirectionTransformation.fromRotations(AxisRotation.R0, AxisRotation.R270), anchor),
				Direction.UP,
				transform(shape, DirectionTransformation.fromRotations(AxisRotation.R270, AxisRotation.R0), anchor),
				Direction.DOWN,
				transform(shape, DirectionTransformation.fromRotations(AxisRotation.R90, AxisRotation.R0), anchor)
			)
		);
	}

	public static Map<BlockFace, Map<Direction, VoxelShape>> createBlockFaceHorizontalFacingShapeMap(VoxelShape shape) {
		return Map.of(
			BlockFace.WALL,
			createHorizontalFacingShapeMap(shape),
			BlockFace.FLOOR,
			createHorizontalFacingShapeMap(transform(shape, DirectionTransformation.fromRotations(AxisRotation.R270, AxisRotation.R0))),
			BlockFace.CEILING,
			createHorizontalFacingShapeMap(transform(shape, DirectionTransformation.fromRotations(AxisRotation.R90, AxisRotation.R180)))
		);
	}

	public interface BoxConsumer {
		void consume(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
	}
}
