package net.minecraft.world.item.component;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class CustomData {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final CustomData EMPTY = new CustomData(new CompoundTag());
	private static final String TYPE_TAG = "id";
	public static final Codec<CustomData> CODEC = Codec.withAlternative(CompoundTag.CODEC, TagParser.FLATTENED_CODEC)
		.xmap(CustomData::new, customData -> customData.tag);
	public static final Codec<CustomData> CODEC_WITH_ID = CODEC.validate(
		customData -> customData.getUnsafe().getString("id").isPresent()
			? DataResult.success(customData)
			: DataResult.error(() -> "Missing id for entity in: " + customData)
	);
	@Deprecated
	public static final StreamCodec<ByteBuf, CustomData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(CustomData::new, customData -> customData.tag);
	private final CompoundTag tag;

	private CustomData(CompoundTag compoundTag) {
		this.tag = compoundTag;
	}

	public static CustomData of(CompoundTag compoundTag) {
		return new CustomData(compoundTag.copy());
	}

	public boolean matchedBy(CompoundTag compoundTag) {
		return NbtUtils.compareNbt(compoundTag, this.tag, true);
	}

	public static void update(DataComponentType<CustomData> dataComponentType, ItemStack itemStack, Consumer<CompoundTag> consumer) {
		CustomData customData = itemStack.getOrDefault(dataComponentType, EMPTY).update(consumer);
		if (customData.tag.isEmpty()) {
			itemStack.remove(dataComponentType);
		} else {
			itemStack.set(dataComponentType, customData);
		}
	}

	public static void set(DataComponentType<CustomData> dataComponentType, ItemStack itemStack, CompoundTag compoundTag) {
		if (!compoundTag.isEmpty()) {
			itemStack.set(dataComponentType, of(compoundTag));
		} else {
			itemStack.remove(dataComponentType);
		}
	}

	public CustomData update(Consumer<CompoundTag> consumer) {
		CompoundTag compoundTag = this.tag.copy();
		consumer.accept(compoundTag);
		return new CustomData(compoundTag);
	}

	@Nullable
	public ResourceLocation parseEntityId() {
		return this.tag.read("id", ResourceLocation.CODEC).orElse(null);
	}

	@Nullable
	public <T> T parseEntityType(HolderLookup.Provider provider, ResourceKey<? extends Registry<T>> resourceKey) {
		ResourceLocation resourceLocation = this.parseEntityId();
		return resourceLocation == null
			? null
			: provider.lookup(resourceKey)
				.flatMap(registryLookup -> registryLookup.get(ResourceKey.create(resourceKey, resourceLocation)))
				.map(Holder::value)
				.orElse(null);
	}

	public void loadInto(Entity entity) {
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
			entity.saveWithoutId(tagValueOutput);
			CompoundTag compoundTag = tagValueOutput.buildResult();
			UUID uUID = entity.getUUID();
			compoundTag.merge(this.tag);
			entity.load(TagValueInput.create(scopedCollector, entity.registryAccess(), compoundTag));
			entity.setUUID(uUID);
		}
	}

	public boolean loadInto(BlockEntity blockEntity, HolderLookup.Provider provider) {
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LOGGER)) {
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, provider);
			blockEntity.saveCustomOnly(tagValueOutput);
			CompoundTag compoundTag = tagValueOutput.buildResult();
			CompoundTag compoundTag2 = compoundTag.copy();
			compoundTag.merge(this.tag);
			if (!compoundTag.equals(compoundTag2)) {
				try {
					blockEntity.loadCustomOnly(TagValueInput.create(scopedCollector, provider, compoundTag));
					blockEntity.setChanged();
					return true;
				} catch (Exception exception) {
					LOGGER.warn("Failed to apply custom data to block entity at {}", blockEntity.getBlockPos(), exception);

					try {
						blockEntity.loadCustomOnly(TagValueInput.create(scopedCollector.forChild(() -> "(rollback)"), provider, compoundTag2));
					} catch (Exception exception2) {
						LOGGER.warn("Failed to rollback block entity at {} after failure", blockEntity.getBlockPos(), exception2);
					}
				}
			}

			return false;
		}
	}

	public <T> DataResult<CustomData> update(DynamicOps<Tag> dynamicOps, MapEncoder<T> mapEncoder, T object) {
		return mapEncoder.encode(object, dynamicOps, dynamicOps.mapBuilder()).build(this.tag).map(tag -> new CustomData((CompoundTag)tag));
	}

	public <T> DataResult<T> read(MapDecoder<T> mapDecoder) {
		return this.read(NbtOps.INSTANCE, mapDecoder);
	}

	public <T> DataResult<T> read(DynamicOps<Tag> dynamicOps, MapDecoder<T> mapDecoder) {
		MapLike<Tag> mapLike = dynamicOps.getMap(this.tag).getOrThrow();
		return mapDecoder.decode(dynamicOps, mapLike);
	}

	public int size() {
		return this.tag.size();
	}

	public boolean isEmpty() {
		return this.tag.isEmpty();
	}

	public CompoundTag copyTag() {
		return this.tag.copy();
	}

	public boolean contains(String string) {
		return this.tag.contains(string);
	}

	@Override
	public boolean equals(Object object) {
		if (object == this) {
			return true;
		} else {
			return object instanceof CustomData customData ? this.tag.equals(customData.tag) : false;
		}
	}

	@Override
	public int hashCode() {
		return this.tag.hashCode();
	}

	@Override
	public String toString() {
		return this.tag.toString();
	}

	@Deprecated
	public CompoundTag getUnsafe() {
		return this.tag;
	}
}
