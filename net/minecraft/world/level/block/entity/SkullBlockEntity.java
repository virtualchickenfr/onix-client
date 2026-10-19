package net.minecraft.world.level.block.entity;

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
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Services;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class SkullBlockEntity extends BlockEntity {
	private static final String TAG_PROFILE = "profile";
	private static final String TAG_NOTE_BLOCK_SOUND = "note_block_sound";
	private static final String TAG_CUSTOM_NAME = "custom_name";
	@Nullable
	private static Executor mainThreadExecutor;
	@Nullable
	private static LoadingCache<String, CompletableFuture<Optional<GameProfile>>> profileCacheByName;
	@Nullable
	private static LoadingCache<UUID, CompletableFuture<Optional<GameProfile>>> profileCacheById;
	public static final Executor CHECKED_MAIN_THREAD_EXECUTOR = runnable -> {
		Executor executor = mainThreadExecutor;
		if (executor != null) {
			executor.execute(runnable);
		}
	};
	@Nullable
	private ResolvableProfile owner;
	@Nullable
	private ResourceLocation noteBlockSound;
	private int animationTickCount;
	private boolean isAnimating;
	@Nullable
	private Component customName;

	public SkullBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.SKULL, blockPos, blockState);
	}

	public static void setup(Services services, Executor executor) {
		mainThreadExecutor = executor;
		final BooleanSupplier booleanSupplier = () -> profileCacheById == null;
		profileCacheByName = CacheBuilder.newBuilder()
			.expireAfterAccess(Duration.ofMinutes(10L))
			.maximumSize(256L)
			.build(new CacheLoader<String, CompletableFuture<Optional<GameProfile>>>() {
				public CompletableFuture<Optional<GameProfile>> load(String string) {
					return SkullBlockEntity.fetchProfileByName(string, services);
				}
			});
		profileCacheById = CacheBuilder.newBuilder()
			.expireAfterAccess(Duration.ofMinutes(10L))
			.maximumSize(256L)
			.build(new CacheLoader<UUID, CompletableFuture<Optional<GameProfile>>>() {
				public CompletableFuture<Optional<GameProfile>> load(UUID uUID) {
					return SkullBlockEntity.fetchProfileById(uUID, services, booleanSupplier);
				}
			});
	}

	static CompletableFuture<Optional<GameProfile>> fetchProfileByName(String string, Services services) {
		return services.profileCache()
			.getAsync(string)
			.thenCompose(
				optional -> {
					LoadingCache<UUID, CompletableFuture<Optional<GameProfile>>> loadingCache = profileCacheById;
					return loadingCache != null && !optional.isEmpty()
						? loadingCache.getUnchecked(optional.get().getId()).thenApply(optional2 -> optional2.or(() -> optional))
						: CompletableFuture.completedFuture(Optional.empty());
				}
			);
	}

	static CompletableFuture<Optional<GameProfile>> fetchProfileById(UUID uUID, Services services, BooleanSupplier booleanSupplier) {
		return CompletableFuture.supplyAsync(() -> {
			if (booleanSupplier.getAsBoolean()) {
				return Optional.empty();
			}

			ProfileResult profileResult = services.sessionService().fetchProfile(uUID, true);
			return Optional.ofNullable(profileResult).map(ProfileResult::profile);
		}, Util.backgroundExecutor().forName("fetchProfile"));
	}

	public static void clear() {
		mainThreadExecutor = null;
		profileCacheByName = null;
		profileCacheById = null;
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		valueOutput.storeNullable("profile", ResolvableProfile.CODEC, this.owner);
		valueOutput.storeNullable("note_block_sound", ResourceLocation.CODEC, this.noteBlockSound);
		valueOutput.storeNullable("custom_name", ComponentSerialization.CODEC, this.customName);
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.setOwner(valueInput.read("profile", ResolvableProfile.CODEC).orElse(null));
		this.noteBlockSound = valueInput.read("note_block_sound", ResourceLocation.CODEC).orElse(null);
		this.customName = parseCustomNameSafe(valueInput, "custom_name");
	}

	public static void animation(Level level, BlockPos blockPos, BlockState blockState, SkullBlockEntity skullBlockEntity) {
		if (blockState.hasProperty(SkullBlock.POWERED) && blockState.getValue(SkullBlock.POWERED)) {
			skullBlockEntity.isAnimating = true;
			skullBlockEntity.animationTickCount++;
		} else {
			skullBlockEntity.isAnimating = false;
		}
	}

	public float getAnimation(float f) {
		return this.isAnimating ? this.animationTickCount + f : this.animationTickCount;
	}

	@Nullable
	public ResolvableProfile getOwnerProfile() {
		return this.owner;
	}

	@Nullable
	public ResourceLocation getNoteBlockSound() {
		return this.noteBlockSound;
	}

	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return this.saveCustomOnly(provider);
	}

	public void setOwner(@Nullable ResolvableProfile resolvableProfile) {
		synchronized (this) {
			this.owner = resolvableProfile;
		}

		this.updateOwnerProfile();
	}

	private void updateOwnerProfile() {
		if (this.owner != null && !this.owner.isResolved()) {
			this.owner.resolve().thenAcceptAsync(resolvableProfile -> {
				this.owner = resolvableProfile;
				this.setChanged();
			}, CHECKED_MAIN_THREAD_EXECUTOR);
		} else {
			this.setChanged();
		}
	}

	public static CompletableFuture<Optional<GameProfile>> fetchGameProfile(String string) {
		LoadingCache<String, CompletableFuture<Optional<GameProfile>>> loadingCache = profileCacheByName;
		return loadingCache != null && StringUtil.isValidPlayerName(string) ? loadingCache.getUnchecked(string) : CompletableFuture.completedFuture(Optional.empty());
	}

	public static CompletableFuture<Optional<GameProfile>> fetchGameProfile(UUID uUID) {
		LoadingCache<UUID, CompletableFuture<Optional<GameProfile>>> loadingCache = profileCacheById;
		return loadingCache != null ? loadingCache.getUnchecked(uUID) : CompletableFuture.completedFuture(Optional.empty());
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		super.applyImplicitComponents(dataComponentGetter);
		this.setOwner(dataComponentGetter.get(DataComponents.PROFILE));
		this.noteBlockSound = dataComponentGetter.get(DataComponents.NOTE_BLOCK_SOUND);
		this.customName = dataComponentGetter.get(DataComponents.CUSTOM_NAME);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		builder.set(DataComponents.PROFILE, this.owner);
		builder.set(DataComponents.NOTE_BLOCK_SOUND, this.noteBlockSound);
		builder.set(DataComponents.CUSTOM_NAME, this.customName);
	}

	@Override
	public void removeComponentsFromTag(ValueOutput valueOutput) {
		super.removeComponentsFromTag(valueOutput);
		valueOutput.discard("profile");
		valueOutput.discard("note_block_sound");
		valueOutput.discard("custom_name");
	}
}
