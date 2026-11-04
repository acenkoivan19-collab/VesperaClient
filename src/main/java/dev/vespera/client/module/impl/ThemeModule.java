package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.Module;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.ui.Theme;
import net.minecraft.client.MinecraftClient;

/**
 * Акцентный цвет всего клиента. Меню и HUD читают Theme.ACCENT каждый кадр —
 * цвет меняется сразу. Выключение возвращает фирменную бирюзу Vespera.
 */
public final class ThemeModule extends Module {

    private static final int DEFAULT_ACCENT = Theme.ACCENT, DEFAULT_ACCENT_2 = Theme.ACCENT_2;
    private static final int[][] PALETTES = {
            {0xFFA78BFF, 0xFFFF6FD8}, // фиолет
            {0xFFFF6FA8, 0xFFFFB86B}, // роза
            {0xFFFFB347, 0xFFFF6B6B}, // янтарь
            {0xFFE6E6EA, 0xFF9A9AA6}, // графит
    };

    private final ChoiceSetting palette = setting(new ChoiceSetting("palette", "Палитра", 0, "Фиолет", "Роза", "Янтарь", "Графит")
            .hints("Фиолетовый → розовый", "Розовый → персиковый", "Оранжевый → коралловый",
                    "Монохром: светло-серый акцент"));

    public ThemeModule() {
        super("Theme", "Акцентный цвет клиента", Category.APPEARANCE);
        about("Меняет акцентный цвет всего клиента — меню, HUD и эффектов, которые используют "
                + "цвет темы. Выключи модуль, чтобы вернуть бирюзу Vespera.");
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        apply(); // смена палитры при включённом модуле — сразу
    }

    private void apply() {
        int[] p = PALETTES[palette.index()];
        Theme.ACCENT = p[0];
        Theme.ACCENT_2 = p[1];
    }

    @Override
    protected void onDisable() {
        Theme.ACCENT = DEFAULT_ACCENT;
        Theme.ACCENT_2 = DEFAULT_ACCENT_2;
    }
}
