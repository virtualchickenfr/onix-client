package com.mojang.blaze3d.systems;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.annotation.DeobfuscateClass;

@Environment(EnvType.CLIENT)
@DeobfuscateClass
public class ScissorState {
	private boolean enabled;
	private int x;
	private int y;
	private int width;
	private int height;

	public void enable(int x, int y, int width, int height) {
		this.enabled = true;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public void disable() {
		this.enabled = false;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public int getX() {
		return this.x;
	}

	public int getY() {
		return this.y;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public void copyFrom(ScissorState other) {
		this.enabled = other.enabled;
		this.x = other.x;
		this.y = other.y;
		this.width = other.width;
		this.height = other.height;
	}
}
