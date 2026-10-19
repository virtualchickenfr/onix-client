package net.minecraft.entity;

import java.util.UUID;
import net.minecraft.world.EntityView;
import org.jetbrains.annotations.Nullable;

public interface Tameable {
	@Nullable
	UUID getOwnerUuid();

	EntityView getWorld();

	@Nullable
	default LivingEntity getOwner() {
		UUID uUID = this.getOwnerUuid();
		return uUID == null ? null : this.getWorld().getPlayerByUuid(uUID);
	}
}
