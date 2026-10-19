package net.minecraft.block;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.CollisionView;
import org.jetbrains.annotations.Nullable;

public interface ShapeContext {
	static ShapeContext absent() {
		return EntityShapeContext.ABSENT;
	}

	static ShapeContext of(Entity entity) {
		return (ShapeContext)(switch (entity) {
			case AbstractMinecartEntity abstractMinecartEntity -> AbstractMinecartEntity.areMinecartImprovementsEnabled(abstractMinecartEntity.getWorld())
				? new ExperimentalMinecartShapeContext(abstractMinecartEntity, false)
				: new EntityShapeContext(entity, false, false);
			default -> new EntityShapeContext(entity, false, false);
		});
	}

	static ShapeContext of(Entity entity, boolean collidesWithFluid) {
		return new EntityShapeContext(entity, collidesWithFluid, false);
	}

	static ShapeContext ofPlacement(@Nullable Entity placer) {
		return new EntityShapeContext(
			placer != null ? placer.isDescending() : false,
			true,
			placer != null ? placer.getY() : -Double.MAX_VALUE,
			placer instanceof LivingEntity livingEntityx ? livingEntityx.getMainHandStack() : ItemStack.EMPTY,
			placer instanceof LivingEntity livingEntity ? state -> livingEntity.canWalkOnFluid(state) : state -> false,
			placer
		);
	}

	boolean isDescending();

	boolean isAbove(VoxelShape shape, BlockPos pos, boolean defaultValue);

	boolean isHolding(Item item);

	boolean canWalkOnFluid(FluidState stateAbove, FluidState state);

	VoxelShape getCollisionShape(BlockState state, CollisionView world, BlockPos pos);

	default boolean isPlacement() {
		return false;
	}
}
