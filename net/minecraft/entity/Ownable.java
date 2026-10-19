package net.minecraft.entity;

import org.jetbrains.annotations.Nullable;

public interface Ownable {
	@Nullable
	Entity getOwner();
}
