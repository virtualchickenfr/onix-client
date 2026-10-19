package net.minecraft.server.packs.resources;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.GsonHelper;

public interface ResourceMetadata {
	ResourceMetadata EMPTY = new ResourceMetadata() {
		@Override
		public <T> Optional<T> getSection(MetadataSectionType<T> metadataSectionType) {
			return Optional.empty();
		}
	};
	IoSupplier<ResourceMetadata> EMPTY_SUPPLIER = () -> EMPTY;

	static ResourceMetadata fromJsonStream(InputStream inputStream) throws IOException {
		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			final JsonObject jsonObject = GsonHelper.parse(bufferedReader);
			return new ResourceMetadata() {
				@Override
				public <T> Optional<T> getSection(MetadataSectionType<T> metadataSectionType) {
					String string = metadataSectionType.name();
					if (jsonObject.has(string)) {
						T object = metadataSectionType.codec().parse(JsonOps.INSTANCE, jsonObject.get(string)).getOrThrow(JsonParseException::new);
						return Optional.of(object);
					} else {
						return Optional.empty();
					}
				}
			};
		}
	}

	<T> Optional<T> getSection(MetadataSectionType<T> metadataSectionType);

	default ResourceMetadata copySections(Collection<MetadataSectionType<?>> collection) {
		ResourceMetadata.Builder builder = new ResourceMetadata.Builder();

		for (MetadataSectionType<?> metadataSectionType : collection) {
			this.copySection(builder, metadataSectionType);
		}

		return builder.build();
	}

	private <T> void copySection(ResourceMetadata.Builder builder, MetadataSectionType<T> metadataSectionType) {
		this.getSection(metadataSectionType).ifPresent(object -> builder.put(metadataSectionType, (T)object));
	}

	class Builder {
		private final ImmutableMap.Builder<MetadataSectionType<?>, Object> map = ImmutableMap.builder();

		public <T> ResourceMetadata.Builder put(MetadataSectionType<T> metadataSectionType, T object) {
			this.map.put(metadataSectionType, object);
			return this;
		}

		public ResourceMetadata build() {
			final ImmutableMap<MetadataSectionType<?>, Object> immutableMap = this.map.build();
			return immutableMap.isEmpty() ? ResourceMetadata.EMPTY : new ResourceMetadata() {
				@Override
				public <T> Optional<T> getSection(MetadataSectionType<T> metadataSectionType) {
					return Optional.ofNullable((T)immutableMap.get(metadataSectionType));
				}
			};
		}
	}
}
