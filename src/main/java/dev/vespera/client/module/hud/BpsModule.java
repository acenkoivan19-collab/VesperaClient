package dev.vespera.client.module.hud;

import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.render.Icons;
import dev.vespera.client.util.MotionTracker;

import java.util.List;
import java.util.Locale;

/** Горизонтальная скорость игрока (сглаженная, MotionTracker). */
public final class BpsModule extends InfoHudModule {

    private final ChoiceSetting unit = setting(new ChoiceSetting("unit", "Единицы", 0, "блоков/с", "км/ч")
            .hints("Блоков в секунду, как в TerraHUD", "Километры в час (1 блок = 1 метр)"));

    public BpsModule() {
        super("Speed (BPS)", "Скорость в блоках в секунду", Icons.Glyph.RUN);
        about("Показывает, как быстро ты двигаешься по горизонтали — в блоках в секунду или км/ч.");
    }

    @Override
    public float defaultY() {
        return 0.67f;
    }

    @Override
    protected void collect(List<Row> out) {
        double bps = MotionTracker.bps();
        if (unit.index() == 1) out.add(new Row("СКОРОСТЬ", String.format(Locale.ROOT, "%.1f км/ч", bps * 3.6)));
        else out.add(new Row("СКОРОСТЬ", String.format(Locale.ROOT, "%.2f бл/с", bps)));
    }
}
