package net.minecraft.client.server;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.net.SocketAddress;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class IntegratedPlayerList extends PlayerList {
	private static final Logger LOGGER = LogUtils.getLogger();
	@Nullable
	private CompoundTag playerData;

	public IntegratedPlayerList(IntegratedServer integratedServer, LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess, PlayerDataStorage playerDataStorage) {
		super(integratedServer, layeredRegistryAccess, playerDataStorage, 8);
		this.setViewDistance(10);
	}

	@Override
	protected void save(ServerPlayer serverPlayer) {
		if (this.getServer().isSingleplayerOwner(serverPlayer.getGameProfile())) {
			try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(serverPlayer.problemPath(), LOGGER)) {
				TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, serverPlayer.registryAccess());
				serverPlayer.saveWithoutId(tagValueOutput);
				this.playerData = tagValueOutput.buildResult();
			}
		}

		super.save(serverPlayer);
	}

	@Override
	public Component canPlayerLogin(SocketAddress socketAddress, GameProfile gameProfile) {
		return this.getServer().isSingleplayerOwner(gameProfile) && this.getPlayerByName(gameProfile.getName()) != null
			? Component.translatable("multiplayer.disconnect.name_taken")
			: super.canPlayerLogin(socketAddress, gameProfile);
	}

	public IntegratedServer getServer() {
		return (IntegratedServer)super.getServer();
	}

	@Nullable
	@Override
	public CompoundTag getSingleplayerData() {
		return this.playerData;
	}
}
