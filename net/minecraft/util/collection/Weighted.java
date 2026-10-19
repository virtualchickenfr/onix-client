package net.minecraft.util.collection;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import net.minecraft.util.dynamic.Codecs;
import org.slf4j.Logger;

/**
 * A data value with an associated weight. Weighted values are used in
 * {@linkplain Pool pools}.
 */
public record Weighted<T>(T value, int weight) {
	private static final Logger LOGGER = LogUtils.getLogger();

	public Weighted(T value, int weight) {
		if (weight < 0) {
			throw (IllegalArgumentException)Util.getFatalOrPause((T)(new IllegalArgumentException("Weight should be >= 0")));
		} else {
			if (weight == 0 && SharedConstants.isDevelopment) {
				LOGGER.warn("Found 0 weight, make sure this is intentional!");
			}

			this.value = value;
			this.weight = weight;
		}
	}

	public static <E> Codec<Weighted<E>> createCodec(Codec<E> dataCodec) {
		return createCodec(dataCodec.fieldOf("data"));
	}

	public static <E> Codec<Weighted<E>> createCodec(MapCodec<E> dataCodec) {
		return RecordCodecBuilder.create(
			instance -> instance.group(dataCodec.forGetter(Weighted::value), Codecs.NON_NEGATIVE_INT.fieldOf("weight").forGetter(Weighted::weight))
				.apply(instance, Weighted::new)
		);
	}

	public <U> Weighted<U> transform(Function<T, U> function) {
		return (Weighted<U>)(new Weighted<>(function.apply(this.value()), this.weight));
	}
}
