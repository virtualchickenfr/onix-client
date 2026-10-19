package net.minecraft.entity;

import org.jetbrains.annotations.Nullable;

public interface Attackable {
	@Nullable
	LivingEntity getLastAttacker();
}
