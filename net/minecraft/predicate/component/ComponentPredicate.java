package net.minecraft.predicate.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;

public interface ComponentPredicate {
	Codec<Map<ComponentPredicate.Type<?>, ComponentPredicate>> PREDICATES_MAP_CODEC = Codec.dispatchedMap(
		Registries.DATA_COMPONENT_PREDICATE_TYPE.getCodec(), ComponentPredicate.Type::getPredicateCodec
	);
	PacketCodec<RegistryByteBuf, ComponentPredicate.Typed<?>> SINGLE_PREDICATE_PACKET_CODEC = PacketCodecs.registryValue(
			RegistryKeys.DATA_COMPONENT_PREDICATE_TYPE
		)
		.dispatch(ComponentPredicate.Typed::type, ComponentPredicate.Type::getTypedPacketCodec);
	PacketCodec<RegistryByteBuf, Map<ComponentPredicate.Type<?>, ComponentPredicate>> PREDICATES_MAP_PACKET_CODEC = SINGLE_PREDICATE_PACKET_CODEC.collect(
			PacketCodecs.toList(64)
		)
		.xmap(
			list -> (Map)list.stream().collect(Collectors.toMap(ComponentPredicate.Typed::type, ComponentPredicate.Typed::predicate)),
			map -> map.entrySet().stream().map(ComponentPredicate.Typed::fromEntry).toList()
		);

	static MapCodec<ComponentPredicate.Typed<?>> createCodec(String predicateFieldName) {
		return Registries.DATA_COMPONENT_PREDICATE_TYPE
			.getCodec()
			.dispatchMap(predicateFieldName, ComponentPredicate.Typed::type, ComponentPredicate.Type::getTypedCodec);
	}

	boolean test(ComponentsAccess components);

	public static final class Type<T extends ComponentPredicate> {
		private final Codec<T> predicateCodec;
		private final MapCodec<ComponentPredicate.Typed<T>> typedCodec;
		private final PacketCodec<RegistryByteBuf, ComponentPredicate.Typed<T>> typedPacketCodec;

		public Type(Codec<T> predicateCodec) {
			this.predicateCodec = predicateCodec;
			this.typedCodec = RecordCodecBuilder.mapCodec(
				instance -> instance.group(predicateCodec.fieldOf("value").forGetter(ComponentPredicate.Typed::predicate))
					.apply(instance, predicate -> new ComponentPredicate.Typed<>(this, (T)predicate))
			);
			this.typedPacketCodec = PacketCodecs.registryCodec(predicateCodec)
				.xmap(predicate -> new ComponentPredicate.Typed<>(this, (T)predicate), ComponentPredicate.Typed::predicate);
		}

		public Codec<T> getPredicateCodec() {
			return this.predicateCodec;
		}

		public MapCodec<ComponentPredicate.Typed<T>> getTypedCodec() {
			return this.typedCodec;
		}

		public PacketCodec<RegistryByteBuf, ComponentPredicate.Typed<T>> getTypedPacketCodec() {
			return this.typedPacketCodec;
		}
	}

	public record Typed<T extends ComponentPredicate>(ComponentPredicate.Type<T> type, T predicate) {
		private static <T extends ComponentPredicate> ComponentPredicate.Typed<T> fromEntry(Entry<ComponentPredicate.Type<?>, T> entry) {
			return new ComponentPredicate.Typed<>((ComponentPredicate.Type<T>)entry.getKey(), (T)entry.getValue());
		}
	}
}
