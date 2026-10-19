package net.minecraft.client.texture;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.util.profiler.ScopedProfiler;

@Environment(EnvType.CLIENT)
public abstract class SpriteAtlasHolder implements ResourceReloader, AutoCloseable {
	private final SpriteAtlasTexture atlas;
	private final Identifier sourcePath;
	private final Set<ResourceMetadataSerializer<?>> metadataReaders;

	public SpriteAtlasHolder(TextureManager textureManager, Identifier atlasId, Identifier sourcePath) {
		this(textureManager, atlasId, sourcePath, SpriteLoader.METADATA_SERIALIZERS);
	}

	public SpriteAtlasHolder(TextureManager textureManager, Identifier atlasId, Identifier sourcePath, Set<ResourceMetadataSerializer<?>> metadataReaders) {
		this.sourcePath = sourcePath;
		this.atlas = new SpriteAtlasTexture(atlasId);
		textureManager.registerTexture(this.atlas.getId(), this.atlas);
		this.metadataReaders = metadataReaders;
	}

	protected Sprite getSprite(Identifier objectId) {
		return this.atlas.getSprite(objectId);
	}

	@Override
	public final CompletableFuture<Void> reload(ResourceReloader.Synchronizer synchronizer, ResourceManager resourceManager, Executor executor, Executor executor2) {
		return SpriteLoader.fromAtlas(this.atlas)
			.load(resourceManager, this.sourcePath, 0, executor, this.metadataReaders)
			.thenCompose(SpriteLoader.StitchResult::whenComplete)
			.thenCompose(synchronizer::whenPrepared)
			.thenAcceptAsync(this::afterReload, executor2);
	}

	private void afterReload(SpriteLoader.StitchResult stitchResult) {
		try (ScopedProfiler scopedProfiler = Profilers.get().scoped("upload")) {
			this.atlas.upload(stitchResult);
		}
	}

	public void close() {
		this.atlas.clear();
	}
}
