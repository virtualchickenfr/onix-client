package net.minecraft.client.realms.exception;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class RealmsUploadException extends RuntimeException {
	@Nullable
	public Text getStatus() {
		return null;
	}

	@Nullable
	public Text[] getStatusTexts() {
		return null;
	}
}
