package net.minecraft.block.entity;

import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.inventory.LootableInventory;
import net.minecraft.inventory.SingleStackInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

public class DecoratedPotBlockEntity extends BlockEntity implements LootableInventory, SingleStackInventory.SingleStackBlockEntityInventory {
	public static final String SHERDS_NBT_KEY = "sherds";
	public static final String ITEM_NBT_KEY = "item";
	public static final int field_46660 = 1;
	public long lastWobbleTime;
	@Nullable
	public DecoratedPotBlockEntity.WobbleType lastWobbleType;
	private Sherds sherds;
	private ItemStack stack = ItemStack.EMPTY;
	@Nullable
	protected RegistryKey<LootTable> lootTableId;
	protected long lootTableSeed;

	public DecoratedPotBlockEntity(BlockPos pos, BlockState state) {
		super(BlockEntityType.DECORATED_POT, pos, state);
		this.sherds = Sherds.DEFAULT;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (!this.sherds.equals(Sherds.DEFAULT)) {
			nbt.put("sherds", Sherds.CODEC, this.sherds);
		}

		if (!this.writeLootTable(nbt) && !this.stack.isEmpty()) {
			nbt.put("item", ItemStack.CODEC, registries.getOps(NbtOps.INSTANCE), this.stack);
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		this.sherds = (Sherds)nbt.get("sherds", Sherds.CODEC).orElse(Sherds.DEFAULT);
		if (!this.readLootTable(nbt)) {
			RegistryOps<NbtElement> registryOps = registries.getOps(NbtOps.INSTANCE);
			this.stack = (ItemStack)nbt.get("item", ItemStack.CODEC, registryOps).orElse(ItemStack.EMPTY);
		} else {
			this.stack = ItemStack.EMPTY;
		}
	}

	public BlockEntityUpdateS2CPacket toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return this.createComponentlessNbt(registries);
	}

	public Direction getHorizontalFacing() {
		return this.getCachedState().get(Properties.HORIZONTAL_FACING);
	}

	public Sherds getSherds() {
		return this.sherds;
	}

	public static ItemStack getStackWith(Sherds sherds) {
		ItemStack itemStack = Items.DECORATED_POT.getDefaultStack();
		itemStack.set(DataComponentTypes.POT_DECORATIONS, sherds);
		return itemStack;
	}

	@Nullable
	@Override
	public RegistryKey<LootTable> getLootTable() {
		return this.lootTableId;
	}

	@Override
	public void setLootTable(@Nullable RegistryKey<LootTable> lootTable) {
		this.lootTableId = lootTable;
	}

	@Override
	public long getLootTableSeed() {
		return this.lootTableSeed;
	}

	@Override
	public void setLootTableSeed(long lootTableSeed) {
		this.lootTableSeed = lootTableSeed;
	}

	@Override
	protected void addComponents(ComponentMap.Builder builder) {
		super.addComponents(builder);
		builder.add(DataComponentTypes.POT_DECORATIONS, this.sherds);
		builder.add(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(this.stack)));
	}

	@Override
	protected void readComponents(ComponentsAccess components) {
		super.readComponents(components);
		this.sherds = components.getOrDefault(DataComponentTypes.POT_DECORATIONS, Sherds.DEFAULT);
		this.stack = components.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).copyFirstStack();
	}

	@Override
	public void removeFromCopiedStackNbt(NbtCompound nbt) {
		super.removeFromCopiedStackNbt(nbt);
		nbt.remove("sherds");
		nbt.remove("item");
	}

	@Override
	public ItemStack getStack() {
		this.generateLoot(null);
		return this.stack;
	}

	@Override
	public ItemStack decreaseStack(int count) {
		this.generateLoot(null);
		ItemStack itemStack = this.stack.split(count);
		if (this.stack.isEmpty()) {
			this.stack = ItemStack.EMPTY;
		}

		return itemStack;
	}

	@Override
	public void setStack(ItemStack stack) {
		this.generateLoot(null);
		this.stack = stack;
	}

	@Override
	public BlockEntity asBlockEntity() {
		return this;
	}

	public void wobble(DecoratedPotBlockEntity.WobbleType wobbleType) {
		if (this.world != null && !this.world.isClient()) {
			this.world.addSyncedBlockEvent(this.getPos(), this.getCachedState().getBlock(), 1, wobbleType.ordinal());
		}
	}

	@Override
	public boolean onSyncedBlockEvent(int type, int data) {
		if (this.world != null && type == 1 && data >= 0 && data < DecoratedPotBlockEntity.WobbleType.values().length) {
			this.lastWobbleTime = this.world.getTime();
			this.lastWobbleType = DecoratedPotBlockEntity.WobbleType.values()[data];
			return true;
		} else {
			return super.onSyncedBlockEvent(type, data);
		}
	}

	public static enum WobbleType {
		POSITIVE(7),
		NEGATIVE(10);

		public final int lengthInTicks;

		private WobbleType(final int lengthInTicks) {
			this.lengthInTicks = lengthInTicks;
		}
	}
}
