package com.mojang.blaze3d.buffers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.annotation.DeobfuscateClass;

@Environment(EnvType.CLIENT)
@DeobfuscateClass
public enum BufferType {
	VERTICES,
	INDICES,
	PIXEL_PACK,
	COPY_READ,
	COPY_WRITE,
	PIXEL_UNPACK,
	UNIFORM;
}
