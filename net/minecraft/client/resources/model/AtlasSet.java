package net.minecraft.client.resources.model;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.sprite.FabricAtlasPreparation;
import net.minecraft.Util;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class AtlasSet implements AutoCloseable {
	private final Map<ResourceLocation, AtlasSet.AtlasEntry> atlases;

	public AtlasSet(Map<ResourceLocation, ResourceLocation> map, TextureManager textureManager) {
		this.atlases = map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, entry -> {
			TextureAtlas textureAtlas = new TextureAtlas(entry.getKey());
			textureManager.register(entry.getKey(), textureAtlas);
			return new AtlasSet.AtlasEntry(textureAtlas, entry.getValue());
		}));
	}

	public TextureAtlas getAtlas(ResourceLocation resourceLocation) {
		return this.atlases.get(resourceLocation).atlas();
	}

	@Override
	public void close() {
		this.atlases.values().forEach(AtlasSet.AtlasEntry::close);
		this.atlases.clear();
	}

	public Map<ResourceLocation, CompletableFuture<AtlasSet.StitchResult>> scheduleLoad(ResourceManager resourceManager, int i, Executor executor) {
		return Util.mapValues(
			this.atlases,
			atlasEntry -> SpriteLoader.create(atlasEntry.atlas)
				.loadAndStitch(resourceManager, atlasEntry.atlasInfoLocation, i, executor)
				.thenApply(preparations -> new AtlasSet.StitchResult(atlasEntry.atlas, preparations))
		);
	}

	@Environment(EnvType.CLIENT)
	record AtlasEntry(TextureAtlas atlas, ResourceLocation atlasInfoLocation) implements AutoCloseable {

		@Override
		public void close() {
			this.atlas.clearTextureData();
		}
	}

	/**
	 * <p>Interface {@link net.fabricmc.fabric.api.renderer.v1.sprite.FabricAtlasPreparation} injected by mod fabric-renderer-api-v1</p>
	 */
	@Environment(EnvType.CLIENT)
	public static class StitchResult implements FabricAtlasPreparation {
		private final TextureAtlas atlas;
		private final SpriteLoader.Preparations preparations;

		public StitchResult(TextureAtlas textureAtlas, SpriteLoader.Preparations preparations) {
			this.atlas = textureAtlas;
			this.preparations = preparations;
		}

		@Nullable
		public TextureAtlasSprite getSprite(ResourceLocation resourceLocation) {
			return this.preparations.regions().get(resourceLocation);
		}

		public TextureAtlasSprite missing() {
			return this.preparations.missing();
		}

		public CompletableFuture<Void> readyForUpload() {
			return this.preparations.readyForUpload();
		}

		public void upload() {
			this.atlas.upload(this.preparations);
		}
	}
}
