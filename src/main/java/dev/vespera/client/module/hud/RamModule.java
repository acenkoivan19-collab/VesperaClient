package dev.vespera.client.module.hud;

import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.render.Icons;

import java.util.List;

/** Память JVM: занято / максимум, цвет по заполнению. */
public final class RamModule extends InfoHudModule {

    private final BoolSetting percent = setting(new BoolSetting("percent", "Процент", true));

    public RamModule() {
        super("RAM", "Сколько памяти ест игра", Icons.Glyph.CHIP);
        about("Сколько оперативной памяти занимает игра и сколько ей доступно; краснеет, когда память почти кончилась.");
    }

    @Override
    public float defaultY() {
        return 0.42f;
    }

    @Override
    protected void collect(List<Row> out) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
        int pct = max > 0 ? (int) (used * 100 / max) : 0;
        int color = pct < 70 ? 0 : pct < 88 ? 0xFFFFD166 : 0xFFFF5C7A;
        out.add(new Row("RAM", used + " / " + max + " MB" + (percent.on() ? "  " + pct + "%" : ""), color));
    }
}
