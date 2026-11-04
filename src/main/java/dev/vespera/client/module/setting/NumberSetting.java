package dev.vespera.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Числовой слайдер (ширина, размер…). */
public final class NumberSetting extends Setting<Float> {

    private final float min, max, step;

    public NumberSetting(String key, String label, float def, float min, float max, float step) {
        super(key, label, def);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public float min() { return min; }
    public float max() { return max; }
    public float step() { return step; }

    public float get() {
        return value;
    }

    @Override
    protected Float sanitize(Float v) {
        if (v == null) return def();
        float s = step > 0 ? Math.round(v / step) * step : v;
        return Math.max(min, Math.min(max, s));
    }

    @Override
    public void write(JsonObject o) {
        o.addProperty(key(), Math.round(value * 1000f) / 1000f);
    }

    @Override
    public void read(JsonElement e) {
        if (e != null && e.isJsonPrimitive()) value = sanitize(e.getAsFloat());
    }
}
