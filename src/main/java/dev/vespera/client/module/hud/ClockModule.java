package dev.vespera.client.module.hud;

import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.render.Icons;

import java.time.LocalTime;
import java.util.List;

/** Реальное время. */
public final class ClockModule extends InfoHudModule {

    private final BoolSetting seconds = setting(new BoolSetting("seconds", "Секунды", false));

    public ClockModule() {
        super("Clock", "Реальное время", Icons.Glyph.CLOCK);
        about("Показывает реальное время на компьютере, по желанию с секундами.");
    }

    @Override
    public float defaultY() {
        return 0.32f;
    }

    @Override
    protected void collect(List<Row> out) {
        LocalTime t = LocalTime.now();
        out.add(new Row("ВРЕМЯ", seconds.on()
                ? String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond())
                : String.format("%02d:%02d", t.getHour(), t.getMinute())));
    }
}
