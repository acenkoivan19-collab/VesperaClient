package dev.vespera.client.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vespera.client.ui.Theme;

/**
 * Цвет ARGB. Значение 0 = «как тема клиента» (Theme.ACCENT, меняется вместе с темой).
 * В JSON пишется строкой "#AARRGGBB" или "theme".
 */
public final class ColorSetting extends Setting<Integer> {

    public static final int THEME = 0;

    public ColorSetting(String key, String label, int def) {
        super(key, label, def);
    }

    /** Итоговый цвет для отрисовки. */
    public int argb() {
        return value == THEME ? Theme.ACCENT : value | 0xFF000000;
    }

    public boolean isTheme() {
        return value == THEME;
    }

    @Override
    public void write(JsonObject o) {
        o.addProperty(key(), value == THEME ? "theme" : String.format("#%08X", value));
    }

    @Override
    public void read(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return;
        String s = e.getAsString();
        if ("theme".equals(s)) { value = THEME; return; }
        try {
            value = (int) Long.parseLong(s.startsWith("#") ? s.substring(1) : s, 16);
        } catch (NumberFormatException ignored) {}
    }
}
