package net.minecraft.entity.spawn;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.predicate.NumberRange;

public record MoonBrightnessSpawnCondition(NumberRange.DoubleRange range) implements SpawnCondition {
	public static final MapCodec<MoonBrightnessSpawnCondition> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(NumberRange.DoubleRange.CODEC.fieldOf("range").forGetter(MoonBrightnessSpawnCondition::range))
			.apply(instance, MoonBrightnessSpawnCondition::new)
	);

	public boolean test(SpawnContext spawnContext) {
		return this.range.test(spawnContext.world().toServerWorld().getMoonSize());
	}

	@Override
	public MapCodec<MoonBrightnessSpawnCondition> getCodec() {
		return CODEC;
	}
}
