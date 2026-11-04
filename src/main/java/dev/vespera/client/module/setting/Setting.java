package dev.vespera.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vespera.client.module.ModuleConfig;

/**
 * Настройка модуля. Значение хранится в ModuleConfig → modules.<модуль>.<key>,
 * дефолт задаётся в коде. Модули читают value() каждый кадр — изменения из ПКМ-панели
 * применяются сразу.
 */
public abstract class Setting<T> {

    private final String key;
    private final String label;
    private final T def;
    protected T value;

    protected Setting(String key, String label, T def) {
        this.key = key;
        this.label = label;
        this.def = def;
        this.value = def;
    }

    public String key() { return key; }
    public String label() { return label; }
    public T def() { return def; }
    public T value() { return value; }

    public void set(T v) {
        T n = sanitize(v);
        if (n.equals(value)) return;
        value = n;
        ModuleConfig.markDirty();
    }

    public void reset() {
        set(def);
    }

    /** Привести значение к допустимому диапазону. */
    protected T sanitize(T v) {
        return v == null ? def : v;
    }

    public abstract void write(JsonObject o);

    public abstract void read(JsonElement e);
}
