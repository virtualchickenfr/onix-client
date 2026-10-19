package net.minecraft.block.entity;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import net.minecraft.block.BlockState;
import net.minecraft.block.SkullBlock;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.ApiServices;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class SkullBlockEntity extends BlockEntity {
	private static final String PROFILE_NBT_KEY = "profile";
	private static final String NOTE_BLOCK_SOUND_NBT_KEY = "note_block_sound";
	private static final String CUSTOM_NAME_NBT_KEY = "custom_name";
	@Nullable
	private static Executor currentExecutor;
	@Nullable
	private static LoadingCache<String, CompletableFuture<Optional<GameProfile>>> nameToProfileCache;
	@Nullable
	private static LoadingCache<UUID, CompletableFuture<Optional<GameProfile>>> uuidToProfileCache;
	public static final Executor EXECUTOR = runnable -> {
		Executor executor = currentExecutor;
		if (executor != null) {
			executor.execute(runnable);
		}
	};
	@Nullable
	private ProfileComponent owner;
	@Nullable
	private Identifier noteBlockSound;
	private int poweredTicks;
	private boolean powered;
	@Nullable
	private Text customName;

	public SkullBlockEntity(BlockPos pos, BlockState state) {
		super(BlockEntityType.SKULL, pos, state);
	}

	public static void setServices(ApiServices apiServices, Executor executor) {
		currentExecutor = executor;
		final BooleanSupplier booleanSupplier = () -> uuidToProfileCache == null;
		nameToProfileCache = CacheBuilder.newBuilder()
			.expireAfterAccess(Duration.ofMinutes(10L))
			.maximumSize(256L)
			.build(new CacheLoader<String, CompletableFuture<Optional<GameProfile>>>() {
				public CompletableFuture<Optional<GameProfile>> load(String string) {
					return SkullBlockEntity.fetchProfileByName(string, apiServices);
				}
			});
		uuidToProfileCache = CacheBuilder.newBuilder()
			.expireAfterAccess(Duration.ofMinutes(10L))
			.maximumSize(256L)
			.build(new CacheLoader<UUID, CompletableFuture<Optional<GameProfile>>>() {
				public CompletableFuture<Optional<GameProfile>> load(UUID uUID) {
					return SkullBlockEntity.fetchProfileByUuid(uUID, apiServices, booleanSupplier);
				}
			});
	}

	static CompletableFuture<Optional<GameProfile>> fetchProfileByName(String name, ApiServices apiServices) {
		return apiServices.userCache()
			.findByNameAsync(name)
			.thenCompose(
				optional -> {
					LoadingCache<UUID, CompletableFuture<Optional<GameProfile>>> loadingCache = uuidToProfileCache;
					return loadingCache != null && !optional.isEmpty()
						? loadingCache.getUnchecked(((GameProfile)optional.get()).getId()).thenApply(optional2 -> optional2.or(() -> optional))
						: CompletableFuture.completedFuture(Optional.empty());
				}
			);
	}

	static CompletableFuture<Optional<GameProfile>> fetchProfileByUuid(UUID uuid, ApiServices apiServices, BooleanSupplier booleanSupplier) {
		return CompletableFuture.supplyAsync(() -> {
			if (booleanSupplier.getAsBoolean()) {
				return Optional.empty();
			} else {
				ProfileResult profileResult = apiServices.sessionService().fetchProfile(uuid, true);
				return Optional.ofNullable(profileResult).map(ProfileResult::profile);
			}
		}, Util.getMainWorkerExecutor().named("fetchProfile"));
	}

	public static void clearServices() {
		currentExecutor = null;
		nameToProfileCache = null;
		uuidToProfileCache = null;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putNullable("profile", ProfileComponent.CODEC, this.owner);
		nbt.putNullable("note_block_sound", Identifier.CODEC, this.noteBlockSound);
		nbt.putNullable("custom_name", TextCodecs.CODEC, registries.getOps(NbtOps.INSTANCE), this.customName);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		this.setOwner((ProfileComponent)nbt.get("profile", ProfileComponent.CODEC).orElse(null));
		this.noteBlockSound = (Identifier)nbt.get("note_block_sound", Identifier.CODEC).orElse(null);
		this.customName = tryParseCustomName(nbt.get("custom_name"), registries);
	}

	public static void tick(World world, BlockPos pos, BlockState state, SkullBlockEntity blockEntity) {
		if (state.contains(SkullBlock.POWERED) && (Boolean)state.get(SkullBlock.POWERED)) {
			blockEntity.powered = true;
			blockEntity.poweredTicks++;
		} else {
			blockEntity.powered = false;
		}
	}

	public float getPoweredTicks(float tickProgress) {
		return this.powered ? this.poweredTicks + tickProgress : this.poweredTicks;
	}

	@Nullable
	public ProfileComponent getOwner() {
		return this.owner;
	}

	@Nullable
	public Identifier getNoteBlockSound() {
		return this.noteBlockSound;
	}

	public BlockEntityUpdateS2CPacket toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return this.createComponentlessNbt(registries);
	}

	public void setOwner(@Nullable ProfileComponent profile) {
		synchronized (this) {
			this.owner = profile;
		}

		this.loadOwnerProperties();
	}

	private void loadOwnerProperties() {
		if (this.owner != null && !this.owner.isCompleted()) {
			this.owner.getFuture().thenAcceptAsync(owner -> {
				this.owner = owner;
				this.markDirty();
			}, EXECUTOR);
		} else {
			this.markDirty();
		}
	}

	public static CompletableFuture<Optional<GameProfile>> fetchProfileByName(String name) {
		LoadingCache<String, CompletableFuture<Optional<GameProfile>>> loadingCache = nameToProfileCache;
		return loadingCache != null && StringHelper.isValidPlayerName(name) ? loadingCache.getUnchecked(name) : CompletableFuture.completedFuture(Optional.empty());
	}

	public static CompletableFuture<Optional<GameProfile>> fetchProfileByUuid(UUID uuid) {
		LoadingCache<UUID, CompletableFuture<Optional<GameProfile>>> loadingCache = uuidToProfileCache;
		return loadingCache != null ? loadingCache.getUnchecked(uuid) : CompletableFuture.completedFuture(Optional.empty());
	}

	@Override
	protected void readComponents(ComponentsAccess components) {
		super.readComponents(components);
		this.setOwner(components.get(DataComponentTypes.PROFILE));
		this.noteBlockSound = components.get(DataComponentTypes.NOTE_BLOCK_SOUND);
		this.customName = components.get(DataComponentTypes.CUSTOM_NAME);
	}

	@Override
	protected void addComponents(ComponentMap.Builder builder) {
		super.addComponents(builder);
		builder.add(DataComponentTypes.PROFILE, this.owner);
		builder.add(DataComponentTypes.NOTE_BLOCK_SOUND, this.noteBlockSound);
		builder.add(DataComponentTypes.CUSTOM_NAME, this.customName);
	}

	@Override
	public void removeFromCopiedStackNbt(NbtCompound nbt) {
		super.removeFromCopiedStackNbt(nbt);
		nbt.remove("profile");
		nbt.remove("note_block_sound");
		nbt.remove("custom_name");
	}
}
