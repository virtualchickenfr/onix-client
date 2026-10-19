package net.minecraft.client.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.MusicSound;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record MusicInstance(@Nullable MusicSound music, float volume) {
	public MusicInstance(MusicSound music) {
		this(music, 1.0F);
	}

	public boolean shouldReplace(SoundInstance sound) {
		return this.music == null ? false : this.music.shouldReplaceCurrentMusic() && !this.music.getSound().value().id().equals(sound.getId());
	}
}
