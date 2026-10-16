package gg.carbon.client.module;

import gg.carbon.client.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

public abstract class Module {
	protected static final Minecraft mc = Minecraft.getInstance();

	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;

	protected Module(String id, String name, String description, Category category) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public Category category() {
		return category;
	}

	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public boolean enabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		if (enabled) onEnable();
		else onDisable();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	/** Two letter monogram shown on the module card. */
	public String icon() {
		String[] parts = name.split(" ");
		if (parts.length > 1) return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
		return (name.length() > 1 ? name.substring(0, 2) : name).toUpperCase();
	}

	protected void onEnable() {}

	protected void onDisable() {}

	public void onTick() {}

	/** Optional key that switches this module on and off (works while it is disabled too). */
	public gg.carbon.client.setting.KeySetting toggleKey() {
		return null;
	}

	/** Called for every key press while no screen is open. Return true to consume. */
	public boolean onKey(int key, int action) {
		return false;
	}

	/** Extra module data persisted next to the settings (e.g. waypoints). */
	public com.google.gson.JsonObject saveExtra() {
		return null;
	}

	public void loadExtra(com.google.gson.JsonObject object) {}
}
