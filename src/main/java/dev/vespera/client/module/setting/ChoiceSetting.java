package dev.vespera.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Выбор одного из 2–4 вариантов (стиль, режим). Значение — индекс. */
public final class ChoiceSetting extends Setting<Integer> {

    private String[] options;
    private String[] hints = new String[0];

    public ChoiceSetting(String key, String label, int def, String... options) {
        super(key, label, def);
        this.options = options;
    }

    public String[] options() {
        return options;
    }

    /** Заменить список вариантов (модуль задаёт свои стили). */
    public void options(String... opts) {
        this.options = opts;
        if (value >= opts.length) value = 0;
    }

    /** Подписи «что меняет» для каждого варианта (показываются под переключателем). */
    public ChoiceSetting hints(String... h) {
        this.hints = h;
        return this;
    }

    /** Подсказка для выбранного варианта или null. */
    public String hint() {
        int i = index();
        return i < hints.length ? hints[i] : null;
    }

    public int index() {
        return Math.max(0, Math.min(options.length - 1, value));
    }

    @Override
    protected Integer sanitize(Integer v) {
        return v == null ? def() : Math.max(0, Math.min(options.length - 1, v));
    }

    @Override
    public void write(JsonObject o) {
        o.addProperty(key(), value);
    }

    @Override
    public void read(JsonElement e) {
        if (e != null && e.isJsonPrimitive()) value = sanitize(e.getAsInt());
    }
}
