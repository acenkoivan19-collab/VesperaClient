package dev.vespera.fx.module;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.Module;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.NumberSetting;

/**
 * Настройки качества всех визуалов (читает {@link dev.vespera.fx.render.Quality}).
 * Модуль выключен — работает «Авто» на 60 FPS, свечение и прятанье за блоками включены.
 */
public final class RenderQualityModule extends Module {

    private final ChoiceSetting mode = setting(new ChoiceSetting("mode", "Качество", 0,
            "Авто", "Низкое", "Среднее", "Высокое", "Ультра")
            .hints("Само снижает детали, когда FPS падает ниже цели, и возвращает, когда запас есть",
                    "Минимум частиц и сегментов — для слабых ПК",
                    "Баланс красоты и скорости",
                    "Полная детализация",
                    "Больше частиц и гладкие линии — для мощных ПК"));
    private final NumberSetting target = setting(new NumberSetting("target", "Целевой FPS (для «Авто»)", 60f, 30f, 240f, 10f));
    private final BoolSetting glow = setting(new BoolSetting("glow", "Свечение (ореолы вокруг эффектов)", true));
    private final BoolSetting occlusion = setting(new BoolSetting("occlusion", "Прятать эффекты за блоками", true));

    public RenderQualityModule() {
        super("Render Quality", "Качество визуалов и защита FPS", Category.VISUALS);
        about("Управляет детализацией всех эффектов клиента. В режиме «Авто» эффекты сами становятся "
                + "проще, если FPS проседает, и возвращают красоту, когда игра снова идёт плавно. "
                + "Пока модуль выключен, работает «Авто» с целью 60 FPS.");
    }

    public int mode() {
        return mode.index();
    }

    public float targetFps() {
        return target.get();
    }

    public boolean glowOn() {
        return glow.on();
    }

    public boolean occlusionOn() {
        return occlusion.on();
    }
}
