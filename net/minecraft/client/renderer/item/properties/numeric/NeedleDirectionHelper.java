package net.minecraft.client.renderer.item.properties.numeric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class NeedleDirectionHelper {
	private final boolean wobble;

	protected NeedleDirectionHelper(boolean bl) {
		this.wobble = bl;
	}

	public float get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity, int i) {
		Entity entity = livingEntity != null ? livingEntity : itemStack.getEntityRepresentation();
		if (entity == null) {
			return 0.0F;
		}

		if (clientLevel == null && entity.level() instanceof ClientLevel clientLevel2) {
			clientLevel = clientLevel2;
		}

		return clientLevel == null ? 0.0F : this.calculate(itemStack, clientLevel, i, entity);
	}

	protected abstract float calculate(ItemStack itemStack, ClientLevel clientLevel, int i, Entity entity);

	protected boolean wobble() {
		return this.wobble;
	}

	protected NeedleDirectionHelper.Wobbler newWobbler(float f) {
		return this.wobble ? standardWobbler(f) : nonWobbler();
	}

	public static NeedleDirectionHelper.Wobbler standardWobbler(float f) {
		return new NeedleDirectionHelper.Wobbler() {
			private float rotation;
			private float deltaRotation;
			private long lastUpdateTick;

			@Override
			public float rotation() {
				return this.rotation;
			}

			@Override
			public boolean shouldUpdate(long l) {
				return this.lastUpdateTick != l;
			}

			@Override
			public void update(long l, float f) {
				this.lastUpdateTick = l;
				float g = Mth.positiveModulo(f - this.rotation + 0.5F, 1.0F) - 0.5F;
				this.deltaRotation += g * 0.1F;
				this.deltaRotation = this.deltaRotation * f;
				this.rotation = Mth.positiveModulo(this.rotation + this.deltaRotation, 1.0F);
			}
		};
	}

	public static NeedleDirectionHelper.Wobbler nonWobbler() {
		return new NeedleDirectionHelper.Wobbler() {
			private float targetValue;

			@Override
			public float rotation() {
				return this.targetValue;
			}

			@Override
			public boolean shouldUpdate(long l) {
				return true;
			}

			@Override
			public void update(long l, float f) {
				this.targetValue = f;
			}
		};
	}

	@Environment(EnvType.CLIENT)
	public interface Wobbler {
		float rotation();

		boolean shouldUpdate(long l);

		void update(long l, float f);
	}
}
