package gg.carbon.client.setting;

import com.google.gson.JsonElement;
import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
	private final String name;
	private final String description;
	protected T value;
	private final T defaultValue;
	private BooleanSupplier visible = () -> true;

	protected Setting(String name, String description, T defaultValue) {
		this.name = name;
		this.description = description;
		this.value = defaultValue;
		this.defaultValue = defaultValue;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	public void reset() {
		set(defaultValue);
	}

	public T defaultValue() {
		return defaultValue;
	}

	public boolean visible() {
		return visible.getAsBoolean();
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleIf(BooleanSupplier condition) {
		this.visible = condition;
		return (S) this;
	}

	public String configKey() {
		return name.toLowerCase().replace(' ', '_');
	}

	public abstract JsonElement save();

	public abstract void load(JsonElement element);
}
