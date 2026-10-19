package net.minecraft.client.render.model.json;

import java.util.function.UnaryOperator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AxisRotation;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface ModelVariantOperator extends UnaryOperator<ModelVariant> {
	ModelVariantOperator.Settings<AxisRotation> ROTATION_X = ModelVariant::withRotationX;
	ModelVariantOperator.Settings<AxisRotation> ROTATION_Y = ModelVariant::withRotationY;
	ModelVariantOperator.Settings<Identifier> MODEL = ModelVariant::withModel;
	ModelVariantOperator.Settings<Boolean> UV_LOCK = ModelVariant::withUVLock;

	default ModelVariantOperator then(ModelVariantOperator variant) {
		return variantx -> (ModelVariant)variant.apply((ModelVariant)this.apply(variantx));
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface Settings<T> {
		ModelVariant apply(ModelVariant variant, T value);

		default ModelVariantOperator withValue(T value) {
			return setting -> this.apply(setting, value);
		}
	}
}
