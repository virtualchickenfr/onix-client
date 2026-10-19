package net.minecraft.entity;

import org.jetbrains.annotations.Nullable;

public interface Targeter {
	@Nullable
	LivingEntity getTarget();
}
