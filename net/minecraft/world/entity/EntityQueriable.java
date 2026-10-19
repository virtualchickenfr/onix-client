package net.minecraft.world.entity;

import java.util.UUID;

public interface EntityQueriable<IdentifiedType extends UniquelyIdentifiable> {
	IdentifiedType getEntity(UUID uuid);
}
