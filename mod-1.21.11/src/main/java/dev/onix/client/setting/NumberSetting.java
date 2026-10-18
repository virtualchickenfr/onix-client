package dev.onix.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** A number with a range and a step, shown as a slider. */
public final class NumberSetting extends Setting<Double, NumberSetting> {
    private final double min;
    private final double max;
    private final double step;
    private String unit = "";

    public NumberSetting(String key, String name, double defaultValue, double min, double max, double step) {
        super(key, name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    /** Text appended to the value, e.g. "px", "%", "s". */
    public NumberSetting unit(String unit) {
        this.unit = unit;
        return this;
    }

    @Override
    protected Double sanitize(Double candidate) {
        if (candidate == null || candidate.isNaN()) return null;
        double v = Math.max(min, Math.min(max, candidate));
        if (step > 0) v = Math.round((v - min) / step) * step + min;
        // avoid 0.30000000000000004 style noise
        v = Math.round(v * 10000.0) / 10000.0;
        return Math.max(min, Math.min(max, v));
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }

    public float getFloat() { return value.floatValue(); }
    public int getInt() { return (int) Math.round(value); }

    /** Slider position from 0 to 1. */
    public double progress() {
        return max == min ? 0 : (value - min) / (max - min);
    }

    public void setProgress(double progress) {
        set(min + (max - min) * Math.max(0, Math.min(1, progress)));
    }

    public void nudge(int direction) {
        set(value + direction * (step > 0 ? step : (max - min) / 100.0));
    }

    public String display() {
        String number;
        if (step >= 1) number = String.valueOf(getInt());
        else if (step >= 0.1) number = String.format(Locale.ROOT, "%.1f", value);
        else number = String.format(Locale.ROOT, "%.2f", value);
        if (unit.isEmpty()) return number;
        return unit.equals("%") || unit.equals("x") || unit.equals("°") ? number + unit : number + " " + unit;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsDouble());
    }
}
