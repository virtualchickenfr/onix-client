package dev.onix.compat;

/** Minecraft 1.21.8 and older: stand-in for 1.21.9's net.minecraft.client.input.MouseButtonInfo. */
public record MouseButtonInfo(int button, int modifiers) {
}
