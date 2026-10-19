package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.FileUtil;
import net.minecraft.ResourceLocationException;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class StructureTemplateManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final String STRUCTURE_RESOURCE_DIRECTORY_NAME = "structure";
	private static final String STRUCTURE_GENERATED_DIRECTORY_NAME = "structures";
	private static final String STRUCTURE_FILE_EXTENSION = ".nbt";
	private static final String STRUCTURE_TEXT_FILE_EXTENSION = ".snbt";
	private final Map<ResourceLocation, Optional<StructureTemplate>> structureRepository = Maps.newConcurrentMap();
	private final DataFixer fixerUpper;
	private ResourceManager resourceManager;
	private final Path generatedDir;
	private final List<StructureTemplateManager.Source> sources;
	private final HolderGetter<Block> blockLookup;
	private static final FileToIdConverter RESOURCE_LISTER = new FileToIdConverter("structure", ".nbt");

	public StructureTemplateManager(
		ResourceManager resourceManager, LevelStorageSource.LevelStorageAccess levelStorageAccess, DataFixer dataFixer, HolderGetter<Block> holderGetter
	) {
		this.resourceManager = resourceManager;
		this.fixerUpper = dataFixer;
		this.generatedDir = levelStorageAccess.getLevelPath(LevelResource.GENERATED_DIR).normalize();
		this.blockLookup = holderGetter;
		Builder<StructureTemplateManager.Source> builder = ImmutableList.builder();
		builder.add(new StructureTemplateManager.Source(this::loadFromGenerated, this::listGenerated));
		if (SharedConstants.IS_RUNNING_IN_IDE) {
			builder.add(new StructureTemplateManager.Source(this::loadFromTestStructures, this::listTestStructures));
		}

		builder.add(new StructureTemplateManager.Source(this::loadFromResource, this::listResources));
		this.sources = builder.build();
	}

	public StructureTemplate getOrCreate(ResourceLocation resourceLocation) {
		Optional<StructureTemplate> optional = this.get(resourceLocation);
		if (optional.isPresent()) {
			return optional.get();
		}

		StructureTemplate structureTemplate = new StructureTemplate();
		this.structureRepository.put(resourceLocation, Optional.of(structureTemplate));
		return structureTemplate;
	}

	public Optional<StructureTemplate> get(ResourceLocation resourceLocation) {
		return this.structureRepository.computeIfAbsent(resourceLocation, this::tryLoad);
	}

	public Stream<ResourceLocation> listTemplates() {
		return this.sources.stream().flatMap(source -> source.lister().get()).distinct();
	}

	private Optional<StructureTemplate> tryLoad(ResourceLocation resourceLocation) {
		for (StructureTemplateManager.Source source : this.sources) {
			try {
				Optional<StructureTemplate> optional = source.loader().apply(resourceLocation);
				if (optional.isPresent()) {
					return optional;
				}
			} catch (Exception var5) {
			}
		}

		return Optional.empty();
	}

	public void onResourceManagerReload(ResourceManager resourceManager) {
		this.resourceManager = resourceManager;
		this.structureRepository.clear();
	}

	private Optional<StructureTemplate> loadFromResource(ResourceLocation resourceLocation) {
		ResourceLocation resourceLocation2 = RESOURCE_LISTER.idToFile(resourceLocation);
		return this.load(() -> this.resourceManager.open(resourceLocation2), throwable -> LOGGER.error("Couldn't load structure {}", resourceLocation, throwable));
	}

	private Stream<ResourceLocation> listResources() {
		return RESOURCE_LISTER.listMatchingResources(this.resourceManager).keySet().stream().map(RESOURCE_LISTER::fileToId);
	}

	private Optional<StructureTemplate> loadFromTestStructures(ResourceLocation resourceLocation) {
		return this.loadFromSnbt(resourceLocation, StructureUtils.testStructuresDir);
	}

	private Stream<ResourceLocation> listTestStructures() {
		if (!Files.isDirectory(StructureUtils.testStructuresDir)) {
			return Stream.empty();
		}

		List<ResourceLocation> list = new ArrayList<>();
		this.listFolderContents(StructureUtils.testStructuresDir, "minecraft", ".snbt", list::add);
		return list.stream();
	}

	private Optional<StructureTemplate> loadFromGenerated(ResourceLocation resourceLocation) {
		if (!Files.isDirectory(this.generatedDir)) {
			return Optional.empty();
		}

		Path path = this.createAndValidatePathToGeneratedStructure(resourceLocation, ".nbt");
		return this.load(() -> new FileInputStream(path.toFile()), throwable -> LOGGER.error("Couldn't load structure from {}", path, throwable));
	}

	private Stream<ResourceLocation> listGenerated() {
		if (!Files.isDirectory(this.generatedDir)) {
			return Stream.empty();
		}

		try {
			List<ResourceLocation> list = new ArrayList<>();

			try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(this.generatedDir, pathx -> Files.isDirectory(pathx))) {
				for (Path path : directoryStream) {
					String string = path.getFileName().toString();
					Path path2 = path.resolve("structures");
					this.listFolderContents(path2, string, ".nbt", list::add);
				}
			}

			return list.stream();
		} catch (IOException iOException) {
			return Stream.empty();
		}
	}

	private void listFolderContents(Path path, String string, String string2, Consumer<ResourceLocation> consumer) {
		int i = string2.length();
		Function<String, String> function = stringx -> stringx.substring(0, stringx.length() - i);

		try (Stream<Path> stream = Files.find(
				path, Integer.MAX_VALUE, (pathx, basicFileAttributes) -> basicFileAttributes.isRegularFile() && pathx.toString().endsWith(string2)
			)) {
			stream.forEach(path2 -> {
				try {
					consumer.accept(ResourceLocation.fromNamespaceAndPath(string, function.apply(this.relativize(path, path2))));
				} catch (ResourceLocationException resourceLocationException) {
					LOGGER.error("Invalid location while listing folder {} contents", path, resourceLocationException);
				}
			});
		} catch (IOException iOException) {
			LOGGER.error("Failed to list folder {} contents", path, iOException);
		}
	}

	private String relativize(Path path, Path path2) {
		return path.relativize(path2).toString().replace(File.separator, "/");
	}

	private Optional<StructureTemplate> loadFromSnbt(ResourceLocation resourceLocation, Path path) {
		if (!Files.isDirectory(path)) {
			return Optional.empty();
		}

		Path path2 = FileUtil.createPathToResource(path, resourceLocation.getPath(), ".snbt");

		try (BufferedReader bufferedReader = Files.newBufferedReader(path2)) {
			String string = IOUtils.toString(bufferedReader);
			return Optional.of(this.readStructure(NbtUtils.snbtToStructure(string)));
		} catch (NoSuchFileException noSuchFileException) {
			return Optional.empty();
		} catch (IOException | CommandSyntaxException exception) {
			LOGGER.error("Couldn't load structure from {}", path2, exception);
			return Optional.empty();
		}
	}

	private Optional<StructureTemplate> load(StructureTemplateManager.InputStreamOpener inputStreamOpener, Consumer<Throwable> consumer) {
		try (
			InputStream inputStream = inputStreamOpener.open();
			InputStream inputStream2 = new FastBufferedInputStream(inputStream);
		) {
			return Optional.of(this.readStructure(inputStream2));
		} catch (FileNotFoundException fileNotFoundException) {
			return Optional.empty();
		} catch (Throwable throwable) {
			consumer.accept(throwable);
			return Optional.empty();
		}
	}

	private StructureTemplate readStructure(InputStream inputStream) throws IOException {
		CompoundTag compoundTag = NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
		return this.readStructure(compoundTag);
	}

	public StructureTemplate readStructure(CompoundTag compoundTag) {
		StructureTemplate structureTemplate = new StructureTemplate();
		int i = NbtUtils.getDataVersion(compoundTag, 500);
		structureTemplate.load(this.blockLookup, DataFixTypes.STRUCTURE.updateToCurrentVersion(this.fixerUpper, compoundTag, i));
		return structureTemplate;
	}

	public boolean save(ResourceLocation resourceLocation) {
		Optional<StructureTemplate> optional = this.structureRepository.get(resourceLocation);
		if (optional.isEmpty()) {
			return false;
		}

		StructureTemplate structureTemplate = optional.get();
		Path path = this.createAndValidatePathToGeneratedStructure(resourceLocation, SharedConstants.DEBUG_SAVE_STRUCTURES_AS_SNBT ? ".snbt" : ".nbt");
		Path path2 = path.getParent();
		if (path2 == null) {
			return false;
		}

		try {
			Files.createDirectories(Files.exists(path2) ? path2.toRealPath() : path2);
		} catch (IOException iOException) {
			LOGGER.error("Failed to create parent directory: {}", path2);
			return false;
		}

		CompoundTag compoundTag = structureTemplate.save(new CompoundTag());
		if (SharedConstants.DEBUG_SAVE_STRUCTURES_AS_SNBT) {
			try {
				NbtToSnbt.writeSnbt(CachedOutput.NO_CACHE, path, NbtUtils.structureToSnbt(compoundTag));
			} catch (Throwable throwable) {
				return false;
			}
		} else {
			try (OutputStream outputStream = new FileOutputStream(path.toFile())) {
				NbtIo.writeCompressed(compoundTag, outputStream);
			} catch (Throwable throwable) {
				return false;
			}
		}

		return true;
	}

	public Path createAndValidatePathToGeneratedStructure(ResourceLocation resourceLocation, String string) {
		if (resourceLocation.getPath().contains("//")) {
			throw new ResourceLocationException("Invalid resource path: " + resourceLocation);
		}

		try {
			Path path = this.generatedDir.resolve(resourceLocation.getNamespace());
			Path path2 = path.resolve("structures");
			Path path3 = FileUtil.createPathToResource(path2, resourceLocation.getPath(), string);
			if (path3.startsWith(this.generatedDir) && FileUtil.isPathNormalized(path3) && FileUtil.isPathPortable(path3)) {
				return path3;
			} else {
				throw new ResourceLocationException("Invalid resource path: " + path3);
			}
		} catch (InvalidPathException invalidPathException) {
			throw new ResourceLocationException("Invalid resource path: " + resourceLocation, invalidPathException);
		}
	}

	public void remove(ResourceLocation resourceLocation) {
		this.structureRepository.remove(resourceLocation);
	}

	@FunctionalInterface
	interface InputStreamOpener {
		InputStream open() throws IOException;
	}

	record Source(Function<ResourceLocation, Optional<StructureTemplate>> loader, Supplier<Stream<ResourceLocation>> lister) {
	}
}
