package net.minecraft.entity;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.Set;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public interface Tameable {
	@Nullable
	LazyEntityReference<LivingEntity> getOwnerReference();

	World getWorld();

	@Nullable
	default LivingEntity getOwner() {
		return (LivingEntity)LazyEntityReference.resolve(this.getOwnerReference(), this.getWorld(), LivingEntity.class);
	}

	@Nullable
	default LivingEntity getTopLevelOwner() {
		Set<Object> set = new ObjectArraySet<>();
		LivingEntity livingEntity = this.getOwner();
		set.add(this);

		while (livingEntity instanceof Tameable) {
			Tameable tameable = (Tameable)livingEntity;
			LivingEntity livingEntity2 = tameable.getOwner();
			if (set.contains(livingEntity2)) {
				return null;
			}

			set.add(livingEntity);
			livingEntity = tameable.getOwner();
		}

		return livingEntity;
	}
}
