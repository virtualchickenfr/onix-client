package net.minecraft.item;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.block.Block;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.predicate.BlockPredicate;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.dynamic.Codecs;
import org.jetbrains.annotations.Nullable;

/**
 * Checks if a block predicate stored inside {@link ItemStack}'s NBT
 * matches the block in a world. The predicate must be stored inside
 * the {@code key} sub NBT of the item stack.
 * 
 * <p>The result is cached to reduce cost for successive lookups
 * on the same block.
 * 
 * @apiNote This is used to implement checks for restrictions specified
 * using {@code CanPlaceOn} or {@code CanDestroy}.
 */
public class BlockPredicatesChecker {
	public static final Codec<BlockPredicatesChecker> CODEC = Codecs.listOrSingle(BlockPredicate.CODEC, Codecs.nonEmptyList(BlockPredicate.CODEC.listOf()))
		.xmap(BlockPredicatesChecker::new, checker -> checker.predicates);
	public static final PacketCodec<RegistryByteBuf, BlockPredicatesChecker> PACKET_CODEC = PacketCodec.tuple(
		BlockPredicate.PACKET_CODEC.collect(PacketCodecs.toList()), blockPredicatesChecker -> blockPredicatesChecker.predicates, BlockPredicatesChecker::new
	);
	public static final Text CAN_BREAK_TEXT = Text.translatable("item.canBreak").formatted(Formatting.GRAY);
	public static final Text CAN_PLACE_TEXT = Text.translatable("item.canPlace").formatted(Formatting.GRAY);
	private static final Text CAN_USE_UNKNOWN_TEXT = Text.translatable("item.canUse.unknown").formatted(Formatting.GRAY);
	private final List<BlockPredicate> predicates;
	@Nullable
	private List<Text> tooltipText;
	@Nullable
	private CachedBlockPosition cachedPos;
	private boolean lastResult;
	private boolean nbtAware;

	public BlockPredicatesChecker(List<BlockPredicate> predicates) {
		this.predicates = predicates;
	}

	private static boolean canUseCache(CachedBlockPosition pos, @Nullable CachedBlockPosition cachedPos, boolean nbtAware) {
		if (cachedPos == null || pos.getBlockState() != cachedPos.getBlockState()) {
			return false;
		} else if (!nbtAware) {
			return true;
		} else if (pos.getBlockEntity() == null && cachedPos.getBlockEntity() == null) {
			return true;
		} else if (pos.getBlockEntity() != null && cachedPos.getBlockEntity() != null) {
			DynamicRegistryManager dynamicRegistryManager = pos.getWorld().getRegistryManager();
			return Objects.equals(pos.getBlockEntity().createNbtWithId(dynamicRegistryManager), cachedPos.getBlockEntity().createNbtWithId(dynamicRegistryManager));
		} else {
			return false;
		}
	}

	/**
	 * {@return true if any of the predicates in the {@code stack}'s NBT
	 * matched against the block at {@code pos}, false otherwise}
	 */
	public boolean check(CachedBlockPosition cachedPos) {
		if (canUseCache(cachedPos, this.cachedPos, this.nbtAware)) {
			return this.lastResult;
		} else {
			this.cachedPos = cachedPos;
			this.nbtAware = false;

			for (BlockPredicate blockPredicate : this.predicates) {
				if (blockPredicate.test(cachedPos)) {
					this.nbtAware = this.nbtAware | blockPredicate.hasNbt();
					this.lastResult = true;
					return true;
				}
			}

			this.lastResult = false;
			return false;
		}
	}

	private List<Text> getOrCreateTooltipText() {
		if (this.tooltipText == null) {
			this.tooltipText = createTooltipText(this.predicates);
		}

		return this.tooltipText;
	}

	public void addTooltips(Consumer<Text> adder) {
		this.getOrCreateTooltipText().forEach(adder);
	}

	private static List<Text> createTooltipText(List<BlockPredicate> blockPredicates) {
		for (BlockPredicate blockPredicate : blockPredicates) {
			if (blockPredicate.blocks().isEmpty()) {
				return List.of(CAN_USE_UNKNOWN_TEXT);
			}
		}

		return blockPredicates.stream()
			.flatMap(predicate -> ((RegistryEntryList)predicate.blocks().orElseThrow()).stream())
			.distinct()
			.map(block -> ((Block)block.value()).getName().formatted(Formatting.DARK_GRAY))
			.toList();
	}

	public boolean equals(Object o) {
		if (this == o) {
			return true;
		} else {
			return o instanceof BlockPredicatesChecker blockPredicatesChecker ? this.predicates.equals(blockPredicatesChecker.predicates) : false;
		}
	}

	public int hashCode() {
		return this.predicates.hashCode();
	}

	public String toString() {
		return "AdventureModePredicate{predicates=" + this.predicates + "}";
	}
}
