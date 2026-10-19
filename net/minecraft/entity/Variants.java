package net.minecraft.entity;

import java.util.Optional;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public class Variants {
	public static final String VARIANT_NBT_KEY = "variant";

	public static <T> RegistryEntry<T> getOrDefaultOrThrow(DynamicRegistryManager registries, RegistryKey<T> variantKey) {
		Registry<T> registry = registries.getOrThrow(variantKey.getRegistryRef());
		return (RegistryEntry<T>)registry.getOptional(variantKey).or(registry::getDefaultEntry).orElseThrow();
	}

	public static <T> RegistryEntry<T> getDefaultOrThrow(DynamicRegistryManager registries, RegistryKey<? extends Registry<T>> registryRef) {
		return (RegistryEntry<T>)registries.getOrThrow(registryRef).getDefaultEntry().orElseThrow();
	}

	public static <T> void writeVariantToNbt(NbtCompound nbt, RegistryEntry<T> variantEntry) {
		variantEntry.getKey().ifPresent(key -> nbt.put("variant", Identifier.CODEC, key.getValue()));
	}

	public static <T> Optional<RegistryEntry<T>> readVariantFromNbt(
		NbtCompound nbt, DynamicRegistryManager registries, RegistryKey<? extends Registry<T>> registryRef
	) {
		return nbt.get("variant", Identifier.CODEC).map(id -> RegistryKey.of(registryRef, id)).flatMap(registries::getOptionalEntry);
	}
}
