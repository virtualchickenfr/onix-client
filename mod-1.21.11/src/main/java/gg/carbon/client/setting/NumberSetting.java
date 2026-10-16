package gg.carbon.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private String suffix = "";

	public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
		super(name, description, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	public NumberSetting suffix(String suffix) {
		this.suffix = suffix;
		return this;
	}

	@Override
	public void set(Double v) {
		double clamped = Math.max(min, Math.min(max, v));
		if (step > 0) clamped = Math.round(clamped / step) * step;
		this.value = Math.max(min, Math.min(max, clamped));
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	public float f() {
		return value.floatValue();
	}

	public int i() {
		return (int) Math.round(value);
	}

	public double fraction() {
		return (value - min) / (max - min);
	}

	public void setFraction(double f) {
		set(min + (max - min) * Math.max(0, Math.min(1, f)));
	}

	public String display() {
		String num;
		if (step >= 1) num = String.valueOf(Math.round(value));
		else if (step >= 0.1) num = String.format(java.util.Locale.ROOT, "%.1f", value);
		else num = String.format(java.util.Locale.ROOT, "%.2f", value);
		return num + suffix;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element.isJsonPrimitive()) set(element.getAsDouble());
	}
}
