package net.minecraft.client.render.item.property.numeric;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CrossbowPullProperty implements NumericProperty {
	public static final MapCodec<CrossbowPullProperty> CODEC = MapCodec.unit(new CrossbowPullProperty());

	@Override
	public float getValue(ItemStack stack, @Nullable ClientWorld world, @Nullable LivingEntity holder, int seed) {
		if (holder == null) {
			return 0.0F;
		} else if (CrossbowItem.isCharged(stack)) {
			return 0.0F;
		} else {
			int i = CrossbowItem.getPullTime(stack, holder);
			return (float)UseDurationProperty.getTicksUsedSoFar(stack, holder) / i;
		}
	}

	@Override
	public MapCodec<CrossbowPullProperty> getCodec() {
		return CODEC;
	}
}
