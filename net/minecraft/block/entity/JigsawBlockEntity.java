package net.minecraft.block.entity;

import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.block.JigsawBlock;
import net.minecraft.block.enums.Orientation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolBasedGenerator;
import net.minecraft.structure.pool.StructurePools;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;

public class JigsawBlockEntity extends BlockEntity {
	public static final Codec<RegistryKey<StructurePool>> STRUCTURE_POOL_KEY_CODEC = RegistryKey.createCodec(RegistryKeys.TEMPLATE_POOL);
	public static final Identifier DEFAULT_NAME = Identifier.ofVanilla("empty");
	private static final int DEFAULT_PLACEMENT_PRIORITY = 0;
	private static final int DEFAULT_SELECTION_PRIORITY = 0;
	public static final String TARGET_KEY = "target";
	public static final String POOL_KEY = "pool";
	public static final String JOINT_KEY = "joint";
	public static final String PLACEMENT_PRIORITY_KEY = "placement_priority";
	public static final String SELECTION_PRIORITY_KEY = "selection_priority";
	public static final String NAME_KEY = "name";
	public static final String FINAL_STATE_KEY = "final_state";
	public static final String DEFAULT_FINAL_STATE = "minecraft:air";
	private Identifier name = DEFAULT_NAME;
	private Identifier target = DEFAULT_NAME;
	private RegistryKey<StructurePool> pool = StructurePools.EMPTY;
	private JigsawBlockEntity.Joint joint = JigsawBlockEntity.Joint.ROLLABLE;
	private String finalState = "minecraft:air";
	private int placementPriority = 0;
	private int selectionPriority = 0;

	public JigsawBlockEntity(BlockPos pos, BlockState state) {
		super(BlockEntityType.JIGSAW, pos, state);
	}

	public Identifier getName() {
		return this.name;
	}

	public Identifier getTarget() {
		return this.target;
	}

	public RegistryKey<StructurePool> getPool() {
		return this.pool;
	}

	public String getFinalState() {
		return this.finalState;
	}

	public JigsawBlockEntity.Joint getJoint() {
		return this.joint;
	}

	public int getPlacementPriority() {
		return this.placementPriority;
	}

	public int getSelectionPriority() {
		return this.selectionPriority;
	}

	public void setName(Identifier name) {
		this.name = name;
	}

	public void setTarget(Identifier target) {
		this.target = target;
	}

	public void setPool(RegistryKey<StructurePool> pool) {
		this.pool = pool;
	}

	public void setFinalState(String finalState) {
		this.finalState = finalState;
	}

	public void setJoint(JigsawBlockEntity.Joint joint) {
		this.joint = joint;
	}

	public void setPlacementPriority(int placementPriority) {
		this.placementPriority = placementPriority;
	}

	public void setSelectionPriority(int selectionPriority) {
		this.selectionPriority = selectionPriority;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.put("name", Identifier.CODEC, this.name);
		nbt.put("target", Identifier.CODEC, this.target);
		nbt.put("pool", STRUCTURE_POOL_KEY_CODEC, this.pool);
		nbt.putString("final_state", this.finalState);
		nbt.put("joint", JigsawBlockEntity.Joint.CODEC, this.joint);
		nbt.putInt("placement_priority", this.placementPriority);
		nbt.putInt("selection_priority", this.selectionPriority);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		this.name = (Identifier)nbt.get("name", Identifier.CODEC).orElse(DEFAULT_NAME);
		this.target = (Identifier)nbt.get("target", Identifier.CODEC).orElse(DEFAULT_NAME);
		this.pool = (RegistryKey<StructurePool>)nbt.get("pool", STRUCTURE_POOL_KEY_CODEC).orElse(StructurePools.EMPTY);
		this.finalState = nbt.getString("final_state", "minecraft:air");
		this.joint = (JigsawBlockEntity.Joint)nbt.get("joint", JigsawBlockEntity.Joint.CODEC)
			.orElseGet(() -> StructureTemplate.getJointFromFacing(this.getCachedState()));
		this.placementPriority = nbt.getInt("placement_priority", 0);
		this.selectionPriority = nbt.getInt("selection_priority", 0);
	}

	public BlockEntityUpdateS2CPacket toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return this.createComponentlessNbt(registries);
	}

	public void generate(ServerWorld world, int maxDepth, boolean keepJigsaws) {
		BlockPos blockPos = this.getPos().offset(((Orientation)this.getCachedState().get(JigsawBlock.ORIENTATION)).getFacing());
		Registry<StructurePool> registry = world.getRegistryManager().getOrThrow(RegistryKeys.TEMPLATE_POOL);
		RegistryEntry<StructurePool> registryEntry = registry.getOrThrow(this.pool);
		StructurePoolBasedGenerator.generate(world, registryEntry, this.target, maxDepth, blockPos, keepJigsaws);
	}

	public static enum Joint implements StringIdentifiable {
		ROLLABLE("rollable"),
		ALIGNED("aligned");

		public static final StringIdentifiable.EnumCodec<JigsawBlockEntity.Joint> CODEC = StringIdentifiable.createCodec(JigsawBlockEntity.Joint::values);
		private final String name;

		private Joint(final String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return this.name;
		}

		public Text asText() {
			return Text.translatable("jigsaw_block.joint." + this.name);
		}
	}
}
