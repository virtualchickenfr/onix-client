package net.minecraft.client.texture;

import com.mojang.blaze3d.textures.GpuTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface Animator extends AutoCloseable {
	void tick(int x, int y, GpuTexture texture);

	void close();
}
