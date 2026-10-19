package net.minecraft.block.entity;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StructureBlock;
import net.minecraft.block.enums.StructureBlockMode;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.processor.BlockRotStructureProcessor;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.StringHelper;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

public class StructureBlockBlockEntity extends BlockEntity implements StructureBoxRendering {
	private static final int field_31367 = 5;
	public static final int field_31364 = 48;
	public static final int field_31365 = 48;
	public static final String AUTHOR_KEY = "author";
	private static final String DEFAULT_AUTHOR = "";
	private static final String DEFAULT_METADATA = "";
	private static final BlockPos DEFAULT_OFFSET = new BlockPos(0, 1, 0);
	private static final Vec3i DEFAULT_SIZE = Vec3i.ZERO;
	private static final BlockRotation DEFAULT_ROTATION = BlockRotation.NONE;
	private static final BlockMirror DEFAULT_MIRROR = BlockMirror.NONE;
	private static final boolean DEFAULT_IGNORE_ENTITIES = true;
	private static final boolean DEFAULT_STRICT = false;
	private static final boolean DEFAULT_POWERED = false;
	private static final boolean DEFAULT_SHOW_AIR = false;
	private static final boolean DEFAULT_SHOW_BOUNDING_BOX = true;
	private static final float DEFAULT_INTEGRITY = 1.0F;
	private static final long DEFAULT_SEED = 0L;
	@Nullable
	private Identifier templateName;
	private String author = "";
	private String metadata = "";
	private BlockPos offset = DEFAULT_OFFSET;
	private Vec3i size = DEFAULT_SIZE;
	private BlockMirror mirror = BlockMirror.NONE;
	private BlockRotation rotation = BlockRotation.NONE;
	private StructureBlockMode mode;
	private boolean ignoreEntities = true;
	private boolean strict = false;
	private boolean powered = false;
	private boolean showAir = false;
	private boolean showBoundingBox = true;
	private float integrity = 1.0F;
	private long seed = 0L;

	public StructureBlockBlockEntity(BlockPos pos, BlockState state) {
		super(BlockEntityType.STRUCTURE_BLOCK, pos, state);
		this.mode = state.get(StructureBlock.MODE);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putString("name", this.getTemplateName());
		nbt.putString("author", this.author);
		nbt.putString("metadata", this.metadata);
		nbt.putInt("posX", this.offset.getX());
		nbt.putInt("posY", this.offset.getY());
		nbt.putInt("posZ", this.offset.getZ());
		nbt.putInt("sizeX", this.size.getX());
		nbt.putInt("sizeY", this.size.getY());
		nbt.putInt("sizeZ", this.size.getZ());
		nbt.put("rotation", BlockRotation.ENUM_NAME_CODEC, this.rotation);
		nbt.put("mirror", BlockMirror.ENUM_NAME_CODEC, this.mirror);
		nbt.put("mode", StructureBlockMode.CODEC, this.mode);
		nbt.putBoolean("ignoreEntities", this.ignoreEntities);
		nbt.putBoolean("strict", this.strict);
		nbt.putBoolean("powered", this.powered);
		nbt.putBoolean("showair", this.showAir);
		nbt.putBoolean("showboundingbox", this.showBoundingBox);
		nbt.putFloat("integrity", this.integrity);
		nbt.putLong("seed", this.seed);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		this.setTemplateName(nbt.getString("name", ""));
		this.author = nbt.getString("author", "");
		this.metadata = nbt.getString("metadata", "");
		int i = MathHelper.clamp(nbt.getInt("posX", DEFAULT_OFFSET.getX()), -48, 48);
		int j = MathHelper.clamp(nbt.getInt("posY", DEFAULT_OFFSET.getY()), -48, 48);
		int k = MathHelper.clamp(nbt.getInt("posZ", DEFAULT_OFFSET.getZ()), -48, 48);
		this.offset = new BlockPos(i, j, k);
		int l = MathHelper.clamp(nbt.getInt("sizeX", DEFAULT_SIZE.getX()), 0, 48);
		int m = MathHelper.clamp(nbt.getInt("sizeY", DEFAULT_SIZE.getY()), 0, 48);
		int n = MathHelper.clamp(nbt.getInt("sizeZ", DEFAULT_SIZE.getZ()), 0, 48);
		this.size = new Vec3i(l, m, n);
		this.rotation = (BlockRotation)nbt.get("rotation", BlockRotation.ENUM_NAME_CODEC).orElse(DEFAULT_ROTATION);
		this.mirror = (BlockMirror)nbt.get("mirror", BlockMirror.ENUM_NAME_CODEC).orElse(DEFAULT_MIRROR);
		this.mode = (StructureBlockMode)nbt.get("mode", StructureBlockMode.CODEC).orElse(StructureBlockMode.DATA);
		this.ignoreEntities = nbt.getBoolean("ignoreEntities", true);
		this.strict = nbt.getBoolean("strict", false);
		this.powered = nbt.getBoolean("powered", false);
		this.showAir = nbt.getBoolean("showair", false);
		this.showBoundingBox = nbt.getBoolean("showboundingbox", true);
		this.integrity = nbt.getFloat("integrity", 1.0F);
		this.seed = nbt.getLong("seed", 0L);
		this.updateBlockMode();
	}

	private void updateBlockMode() {
		if (this.world != null) {
			BlockPos blockPos = this.getPos();
			BlockState blockState = this.world.getBlockState(blockPos);
			if (blockState.isOf(Blocks.STRUCTURE_BLOCK)) {
				this.world.setBlockState(blockPos, blockState.with(StructureBlock.MODE, this.mode), Block.NOTIFY_LISTENERS);
			}
		}
	}

	public BlockEntityUpdateS2CPacket toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return this.createComponentlessNbt(registries);
	}

	public boolean openScreen(PlayerEntity player) {
		if (!player.isCreativeLevelTwoOp()) {
			return false;
		} else {
			if (player.getEntityWorld().isClient) {
				player.openStructureBlockScreen(this);
			}

			return true;
		}
	}

	public String getTemplateName() {
		return this.templateName == null ? "" : this.templateName.toString();
	}

	public boolean hasStructureName() {
		return this.templateName != null;
	}

	public void setTemplateName(@Nullable String templateName) {
		this.setTemplateName(StringHelper.isEmpty(templateName) ? null : Identifier.tryParse(templateName));
	}

	public void setTemplateName(@Nullable Identifier templateName) {
		this.templateName = templateName;
	}

	public void setAuthor(LivingEntity entity) {
		this.author = entity.getName().getString();
	}

	public BlockPos getOffset() {
		return this.offset;
	}

	public void setOffset(BlockPos offset) {
		this.offset = offset;
	}

	public Vec3i getSize() {
		return this.size;
	}

	public void setSize(Vec3i size) {
		this.size = size;
	}

	public BlockMirror getMirror() {
		return this.mirror;
	}

	public void setMirror(BlockMirror mirror) {
		this.mirror = mirror;
	}

	public BlockRotation getRotation() {
		return this.rotation;
	}

	public void setRotation(BlockRotation rotation) {
		this.rotation = rotation;
	}

	public String getMetadata() {
		return this.metadata;
	}

	public void setMetadata(String metadata) {
		this.metadata = metadata;
	}

	public StructureBlockMode getMode() {
		return this.mode;
	}

	public void setMode(StructureBlockMode mode) {
		this.mode = mode;
		BlockState blockState = this.world.getBlockState(this.getPos());
		if (blockState.isOf(Blocks.STRUCTURE_BLOCK)) {
			this.world.setBlockState(this.getPos(), blockState.with(StructureBlock.MODE, mode), Block.NOTIFY_LISTENERS);
		}
	}

	public boolean shouldIgnoreEntities() {
		return this.ignoreEntities;
	}

	public boolean isStrict() {
		return this.strict;
	}

	public void setIgnoreEntities(boolean ignoreEntities) {
		this.ignoreEntities = ignoreEntities;
	}

	public void setStrict(boolean bl) {
		this.strict = bl;
	}

	public float getIntegrity() {
		return this.integrity;
	}

	public void setIntegrity(float integrity) {
		this.integrity = integrity;
	}

	public long getSeed() {
		return this.seed;
	}

	public void setSeed(long seed) {
		this.seed = seed;
	}

	public boolean detectStructureSize() {
		if (this.mode != StructureBlockMode.SAVE) {
			return false;
		} else {
			BlockPos blockPos = this.getPos();
			int i = 80;
			BlockPos blockPos2 = new BlockPos(blockPos.getX() - 80, this.world.getBottomY(), blockPos.getZ() - 80);
			BlockPos blockPos3 = new BlockPos(blockPos.getX() + 80, this.world.getTopYInclusive(), blockPos.getZ() + 80);
			Stream<BlockPos> stream = this.streamCornerPos(blockPos2, blockPos3);
			return getStructureBox(blockPos, stream).filter(box -> {
				int ix = box.getMaxX() - box.getMinX();
				int j = box.getMaxY() - box.getMinY();
				int k = box.getMaxZ() - box.getMinZ();
				if (ix > 1 && j > 1 && k > 1) {
					this.offset = new BlockPos(box.getMinX() - blockPos.getX() + 1, box.getMinY() - blockPos.getY() + 1, box.getMinZ() - blockPos.getZ() + 1);
					this.size = new Vec3i(ix - 1, j - 1, k - 1);
					this.markDirty();
					BlockState blockState = this.world.getBlockState(blockPos);
					this.world.updateListeners(blockPos, blockState, blockState, Block.NOTIFY_ALL);
					return true;
				} else {
					return false;
				}
			}).isPresent();
		}
	}

	/**
	 * Streams positions of {@link StructureBlockMode#CORNER} mode structure blocks with matching names.
	 */
	private Stream<BlockPos> streamCornerPos(BlockPos start, BlockPos end) {
		return BlockPos.stream(start, end)
			.filter(pos -> this.world.getBlockState(pos).isOf(Blocks.STRUCTURE_BLOCK))
			.map(this.world::getBlockEntity)
			.filter(blockEntity -> blockEntity instanceof StructureBlockBlockEntity)
			.map(blockEntity -> (StructureBlockBlockEntity)blockEntity)
			.filter(blockEntity -> blockEntity.mode == StructureBlockMode.CORNER && Objects.equals(this.templateName, blockEntity.templateName))
			.map(BlockEntity::getPos);
	}

	private static Optional<BlockBox> getStructureBox(BlockPos pos, Stream<BlockPos> corners) {
		Iterator<BlockPos> iterator = corners.iterator();
		if (!iterator.hasNext()) {
			return Optional.empty();
		} else {
			BlockPos blockPos = (BlockPos)iterator.next();
			BlockBox blockBox = new BlockBox(blockPos);
			if (iterator.hasNext()) {
				iterator.forEachRemaining(blockBox::encompass);
			} else {
				blockBox.encompass(pos);
			}

			return Optional.of(blockBox);
		}
	}

	public boolean saveStructure() {
		return this.mode != StructureBlockMode.SAVE ? false : this.saveStructure(true);
	}

	public boolean saveStructure(boolean toDisk) {
		if (this.templateName != null && this.world instanceof ServerWorld serverWorld) {
			BlockPos var4 = this.getPos().add(this.offset);
			return saveStructure(serverWorld, this.templateName, var4, this.size, this.ignoreEntities, this.author, toDisk);
		} else {
			return false;
		}
	}

	public static boolean saveStructure(
		ServerWorld world, Identifier templateId, BlockPos start, Vec3i size, boolean ignoreEntities, String author, boolean toDisk
	) {
		StructureTemplateManager structureTemplateManager = world.getStructureTemplateManager();

		StructureTemplate structureTemplate;
		try {
			structureTemplate = structureTemplateManager.getTemplateOrBlank(templateId);
		} catch (InvalidIdentifierException var11) {
			return false;
		}

		structureTemplate.saveFromWorld(world, start, size, !ignoreEntities, Blocks.STRUCTURE_VOID);
		structureTemplate.setAuthor(author);
		if (toDisk) {
			try {
				return structureTemplateManager.saveTemplate(templateId);
			} catch (InvalidIdentifierException var10) {
				return false;
			}
		} else {
			return true;
		}
	}

	public static Random createRandom(long seed) {
		return seed == 0L ? Random.create(Util.getMeasuringTimeMs()) : Random.create(seed);
	}

	public boolean loadAndTryPlaceStructure(ServerWorld world) {
		if (this.mode == StructureBlockMode.LOAD && this.templateName != null) {
			StructureTemplate structureTemplate = (StructureTemplate)world.getStructureTemplateManager().getTemplate(this.templateName).orElse(null);
			if (structureTemplate == null) {
				return false;
			} else if (structureTemplate.getSize().equals(this.size)) {
				this.loadAndPlaceStructure(world, structureTemplate);
				return true;
			} else {
				this.loadStructure(structureTemplate);
				return false;
			}
		} else {
			return false;
		}
	}

	public boolean loadStructure(ServerWorld world) {
		StructureTemplate structureTemplate = this.getStructureTemplate(world);
		if (structureTemplate == null) {
			return false;
		} else {
			this.loadStructure(structureTemplate);
			return true;
		}
	}

	private void loadStructure(StructureTemplate template) {
		this.author = !StringHelper.isEmpty(template.getAuthor()) ? template.getAuthor() : "";
		this.size = template.getSize();
		this.markDirty();
	}

	public void loadAndPlaceStructure(ServerWorld world) {
		StructureTemplate structureTemplate = this.getStructureTemplate(world);
		if (structureTemplate != null) {
			this.loadAndPlaceStructure(world, structureTemplate);
		}
	}

	@Nullable
	private StructureTemplate getStructureTemplate(ServerWorld world) {
		return this.templateName == null ? null : (StructureTemplate)world.getStructureTemplateManager().getTemplate(this.templateName).orElse(null);
	}

	private void loadAndPlaceStructure(ServerWorld world, StructureTemplate template) {
		this.loadStructure(template);
		StructurePlacementData structurePlacementData = new StructurePlacementData()
			.setMirror(this.mirror)
			.setRotation(this.rotation)
			.setIgnoreEntities(this.ignoreEntities)
			.setUpdateNeighbors(this.strict);
		if (this.integrity < 1.0F) {
			structurePlacementData.clearProcessors()
				.addProcessor(new BlockRotStructureProcessor(MathHelper.clamp(this.integrity, 0.0F, 1.0F)))
				.setRandom(createRandom(this.seed));
		}

		BlockPos blockPos = this.getPos().add(this.offset);
		template.place(
			world,
			blockPos,
			blockPos,
			structurePlacementData,
			createRandom(this.seed),
			Block.NOTIFY_LISTENERS | (this.strict ? Block.FORCE_STATE_AND_SKIP_CALLBACKS_AND_DROPS : 0)
		);
	}

	public void unloadStructure() {
		if (this.templateName != null) {
			ServerWorld serverWorld = (ServerWorld)this.world;
			StructureTemplateManager structureTemplateManager = serverWorld.getStructureTemplateManager();
			structureTemplateManager.unloadTemplate(this.templateName);
		}
	}

	public boolean isStructureAvailable() {
		if (this.mode == StructureBlockMode.LOAD && !this.world.isClient && this.templateName != null) {
			ServerWorld serverWorld = (ServerWorld)this.world;
			StructureTemplateManager structureTemplateManager = serverWorld.getStructureTemplateManager();

			try {
				return structureTemplateManager.getTemplate(this.templateName).isPresent();
			} catch (InvalidIdentifierException var4) {
				return false;
			}
		} else {
			return false;
		}
	}

	public boolean isPowered() {
		return this.powered;
	}

	public void setPowered(boolean powered) {
		this.powered = powered;
	}

	public boolean shouldShowAir() {
		return this.showAir;
	}

	public void setShowAir(boolean showAir) {
		this.showAir = showAir;
	}

	public boolean shouldShowBoundingBox() {
		return this.showBoundingBox;
	}

	public void setShowBoundingBox(boolean showBoundingBox) {
		this.showBoundingBox = showBoundingBox;
	}

	@Override
	public StructureBoxRendering.RenderMode getRenderMode() {
		if (this.mode != StructureBlockMode.SAVE && this.mode != StructureBlockMode.LOAD) {
			return StructureBoxRendering.RenderMode.NONE;
		} else if (this.mode == StructureBlockMode.SAVE && this.showAir) {
			return StructureBoxRendering.RenderMode.BOX_AND_INVISIBLE_BLOCKS;
		} else {
			return this.mode != StructureBlockMode.SAVE && !this.showBoundingBox ? StructureBoxRendering.RenderMode.NONE : StructureBoxRendering.RenderMode.BOX;
		}
	}

	@Override
	public StructureBoxRendering.StructureBox getStructureBox() {
		BlockPos blockPos = this.getOffset();
		Vec3i vec3i = this.getSize();
		int i = blockPos.getX();
		int j = blockPos.getZ();
		int k = blockPos.getY();
		int l = k + vec3i.getY();
		int m;
		int n;
		switch (this.mirror) {
			case LEFT_RIGHT:
				m = vec3i.getX();
				n = -vec3i.getZ();
				break;
			case FRONT_BACK:
				m = -vec3i.getX();
				n = vec3i.getZ();
				break;
			default:
				m = vec3i.getX();
				n = vec3i.getZ();
		}

		int o;
		int p;
		int q;
		int r;
		switch (this.rotation) {
			case CLOCKWISE_90:
				o = n < 0 ? i : i + 1;
				p = m < 0 ? j + 1 : j;
				q = o - n;
				r = p + m;
				break;
			case CLOCKWISE_180:
				o = m < 0 ? i : i + 1;
				p = n < 0 ? j : j + 1;
				q = o - m;
				r = p - n;
				break;
			case COUNTERCLOCKWISE_90:
				o = n < 0 ? i + 1 : i;
				p = m < 0 ? j : j + 1;
				q = o + n;
				r = p - m;
				break;
			default:
				o = m < 0 ? i + 1 : i;
				p = n < 0 ? j + 1 : j;
				q = o + m;
				r = p + n;
		}

		return StructureBoxRendering.StructureBox.create(o, k, p, q, l, r);
	}

	public static enum Action {
		UPDATE_DATA,
		SAVE_AREA,
		LOAD_AREA,
		SCAN_AREA;
	}
}
