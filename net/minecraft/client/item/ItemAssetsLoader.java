package net.minecraft.client.item;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ItemAssetsLoader {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceFinder FINDER = ResourceFinder.json("items");

	public static CompletableFuture<ItemAssetsLoader.Result> load(ResourceManager resourceManager, Executor executor) {
		return CompletableFuture.supplyAsync(() -> FINDER.findResources(resourceManager), executor)
			.thenCompose(
				resources -> {
					List<CompletableFuture<ItemAssetsLoader.Definition>> list = new ArrayList(resources.size());
					resources.forEach(
						(id, resource) -> list.add(
							CompletableFuture.supplyAsync(
								() -> {
									Identifier identifier2 = FINDER.toResourceId(id);

									try {
										Reader reader = resource.getReader();

										ItemAssetsLoader.Definition var5;
										try {
											ItemAsset itemAsset = (ItemAsset)ItemAsset.CODEC
												.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
												.ifError(error -> LOGGER.error("Couldn't parse item model '{}' from pack '{}': {}", identifier2, resource.getPackId(), error.message()))
												.result()
												.orElse(null);
											var5 = new ItemAssetsLoader.Definition(identifier2, itemAsset);
										} catch (Throwable var7) {
											if (reader != null) {
												try {
													reader.close();
												} catch (Throwable var6) {
													var7.addSuppressed(var6);
												}
											}

											throw var7;
										}

										if (reader != null) {
											reader.close();
										}

										return var5;
									} catch (Exception var8) {
										LOGGER.error("Failed to open item model {} from pack '{}'", id, resource.getPackId(), var8);
										return new ItemAssetsLoader.Definition(identifier2, null);
									}
								},
								executor
							)
						)
					);
					return Util.combineSafe(list).thenApply(definitions -> {
						Map<Identifier, ItemAsset> map = new HashMap();

						for (ItemAssetsLoader.Definition definition : definitions) {
							if (definition.clientItemInfo != null) {
								map.put(definition.id, definition.clientItemInfo);
							}
						}

						return new ItemAssetsLoader.Result(map);
					});
				}
			);
	}

	@Environment(EnvType.CLIENT)
	record Definition(Identifier id, @Nullable ItemAsset clientItemInfo) {
	}

	@Environment(EnvType.CLIENT)
	public record Result(Map<Identifier, ItemAsset> contents) {
	}
}
