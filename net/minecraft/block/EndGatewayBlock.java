package net.minecraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

public class EndGatewayBlock extends BlockWithEntity implements Portal {
	public static final MapCodec<EndGatewayBlock> CODEC = createCodec(EndGatewayBlock::new);

	@Override
	public MapCodec<EndGatewayBlock> getCodec() {
		return CODEC;
	}

	public EndGatewayBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new EndGatewayBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, BlockEntityType.END_GATEWAY, world.isClient ? EndGatewayBlockEntity::clientTick : EndGatewayBlockEntity::serverTick);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (blockEntity instanceof EndGatewayBlockEntity) {
			int i = ((EndGatewayBlockEntity)blockEntity).getDrawnSidesCount();

			for (int j = 0; j < i; j++) {
				double d = pos.getX() + random.nextDouble();
				double e = pos.getY() + random.nextDouble();
				double f = pos.getZ() + random.nextDouble();
				double g = (random.nextDouble() - 0.5) * 0.5;
				double h = (random.nextDouble() - 0.5) * 0.5;
				double k = (random.nextDouble() - 0.5) * 0.5;
				int l = random.nextInt(2) * 2 - 1;
				if (random.nextBoolean()) {
					f = pos.getZ() + 0.5 + 0.25 * l;
					k = random.nextFloat() * 2.0F * l;
				} else {
					d = pos.getX() + 0.5 + 0.25 * l;
					g = random.nextFloat() * 2.0F * l;
				}

				world.addParticle(ParticleTypes.PORTAL, d, e, f, g, h, k);
			}
		}
	}

	@Override
	public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
		return ItemStack.EMPTY;
	}

	@Override
	protected boolean canBucketPlace(BlockState state, Fluid fluid) {
		return false;
	}

	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (entity.canUsePortals(false)
			&& !world.isClient
			&& world.getBlockEntity(pos) instanceof EndGatewayBlockEntity endGatewayBlockEntity
			&& !endGatewayBlockEntity.needsCooldownBeforeTeleporting()) {
			entity.tryUsePortal(this, pos);
			EndGatewayBlockEntity.startTeleportCooldown(world, pos, state, endGatewayBlockEntity);
		}
	}

	@Nullable
	@Override
	public TeleportTarget createTeleportTarget(ServerWorld world, Entity entity, BlockPos pos) {
		if (world.getBlockEntity(pos) instanceof EndGatewayBlockEntity endGatewayBlockEntity) {
			Vec3d vec3d = endGatewayBlockEntity.getOrCreateExitPortalPos(world, pos);
			return vec3d != null
				? new TeleportTarget(world, vec3d, getTeleportVelocity(entity), entity.getYaw(), entity.getPitch(), TeleportTarget.ADD_PORTAL_CHUNK_TICKET)
				: null;
		} else {
			return null;
		}
	}

	private static Vec3d getTeleportVelocity(Entity entity) {
		return entity instanceof EnderPearlEntity ? new Vec3d(0.0, -1.0, 0.0) : entity.getVelocity();
	}
}
