package dev.vespera.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Тумблер подопции (фон, тень, анимация…). */
public final class BoolSetting extends Setting<Boolean> {

    public BoolSetting(String key, String label, boolean def) {
        super(key, label, def);
    }

    public boolean on() {
        return value;
    }

    public void toggle() {
        set(!value);
    }

    @Override
    public void write(JsonObject o) {
        o.addProperty(key(), value);
    }

    @Override
    public void read(JsonElement e) {
        if (e != null && e.isJsonPrimitive()) value = e.getAsBoolean();
    }
}
