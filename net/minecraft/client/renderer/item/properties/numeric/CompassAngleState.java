package net.minecraft.client.renderer.item.properties.numeric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CompassAngleState extends NeedleDirectionHelper {
	public static final MapCodec<CompassAngleState> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.BOOL.optionalFieldOf("wobble", true).forGetter(NeedleDirectionHelper::wobble),
				CompassAngleState.CompassTarget.CODEC.fieldOf("target").forGetter(CompassAngleState::target)
			)
			.apply(instance, CompassAngleState::new)
	);
	private final NeedleDirectionHelper.Wobbler wobbler;
	private final NeedleDirectionHelper.Wobbler noTargetWobbler;
	private final CompassAngleState.CompassTarget compassTarget;
	private final RandomSource random = RandomSource.create();

	public CompassAngleState(boolean bl, CompassAngleState.CompassTarget compassTarget) {
		super(bl);
		this.wobbler = this.newWobbler(0.8F);
		this.noTargetWobbler = this.newWobbler(0.8F);
		this.compassTarget = compassTarget;
	}

	@Override
	protected float calculate(ItemStack itemStack, ClientLevel clientLevel, int i, Entity entity) {
		GlobalPos globalPos = this.compassTarget.get(clientLevel, itemStack, entity);
		long l = clientLevel.getGameTime();
		return !isValidCompassTargetPos(entity, globalPos)
			? this.getRandomlySpinningRotation(i, l)
			: this.getRotationTowardsCompassTarget(entity, l, globalPos.pos());
	}

	private float getRandomlySpinningRotation(int i, long l) {
		if (this.noTargetWobbler.shouldUpdate(l)) {
			this.noTargetWobbler.update(l, this.random.nextFloat());
		}

		float f = this.noTargetWobbler.rotation() + hash(i) / 2.1474836E9F;
		return Mth.positiveModulo(f, 1.0F);
	}

	private float getRotationTowardsCompassTarget(Entity entity, long l, BlockPos blockPos) {
		float f = (float)getAngleFromEntityToPos(entity, blockPos);
		float g = getWrappedVisualRotationY(entity);
		float h;
		if (entity instanceof Player player && player.isLocalPlayer() && player.level().tickRateManager().runsNormally()) {
			if (this.wobbler.shouldUpdate(l)) {
				this.wobbler.update(l, 0.5F - (g - 0.25F));
			}

			h = f + this.wobbler.rotation();
		} else {
			h = 0.5F - (g - 0.25F - f);
		}

		return Mth.positiveModulo(h, 1.0F);
	}

	private static boolean isValidCompassTargetPos(Entity entity, @Nullable GlobalPos globalPos) {
		return globalPos != null && globalPos.dimension() == entity.level().dimension() && !(globalPos.pos().distToCenterSqr(entity.position()) < 1.0E-5F);
	}

	private static double getAngleFromEntityToPos(Entity entity, BlockPos blockPos) {
		Vec3 vec3 = Vec3.atCenterOf(blockPos);
		return Math.atan2(vec3.z() - entity.getZ(), vec3.x() - entity.getX()) / (float) (Math.PI * 2);
	}

	private static float getWrappedVisualRotationY(Entity entity) {
		return Mth.positiveModulo(entity.getVisualRotationYInDegrees() / 360.0F, 1.0F);
	}

	private static int hash(int i) {
		return i * 1327217883;
	}

	protected CompassAngleState.CompassTarget target() {
		return this.compassTarget;
	}

	@Environment(EnvType.CLIENT)
	public enum CompassTarget implements StringRepresentable {
		NONE("none") {
			@Nullable
			@Override
			public GlobalPos get(ClientLevel clientLevel, ItemStack itemStack, Entity entity) {
				return null;
			}
		},
		LODESTONE("lodestone") {
			@Nullable
			@Override
			public GlobalPos get(ClientLevel clientLevel, ItemStack itemStack, Entity entity) {
				LodestoneTracker lodestoneTracker = itemStack.get(DataComponents.LODESTONE_TRACKER);
				return lodestoneTracker != null ? lodestoneTracker.target().orElse(null) : null;
			}
		},
		SPAWN("spawn") {
			@Override
			public GlobalPos get(ClientLevel clientLevel, ItemStack itemStack, Entity entity) {
				return GlobalPos.of(clientLevel.dimension(), clientLevel.getSharedSpawnPos());
			}
		},
		RECOVERY("recovery") {
			@Nullable
			@Override
			public GlobalPos get(ClientLevel clientLevel, ItemStack itemStack, Entity entity) {
				return entity instanceof Player player ? player.getLastDeathLocation().orElse(null) : null;
			}
		};

		public static final Codec<CompassAngleState.CompassTarget> CODEC = StringRepresentable.fromEnum(CompassAngleState.CompassTarget::values);
		private final String name;

		CompassTarget(final String string2) {
			this.name = string2;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		@Nullable
		abstract GlobalPos get(ClientLevel clientLevel, ItemStack itemStack, Entity entity);
	}
}
