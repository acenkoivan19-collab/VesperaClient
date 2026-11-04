package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.Module;
import dev.vespera.client.module.setting.ChoiceSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Погода только у тебя: каждый тик задаёт силу дождя и грозы на клиенте.
 * После выключения сервер снова пришлёт реальную погоду.
 */
public final class WeatherChangerModule extends Module {

    private final ChoiceSetting weather = setting(new ChoiceSetting("weather", "Погода", 0, "Ясно", "Дождь", "Гроза")
            .hints("Чистое небо, без осадков", "Дождь (в холодных биомах — снег)", "Ливень с тёмным небом"));

    public WeatherChangerModule() {
        super("Weather Changer", "Своя погода — только у тебя", Category.WORLD);
        about("Показывает выбранную погоду только у тебя на экране. На сервере и у других игроков "
                + "погода остаётся настоящей.");
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null) return;
        int w = weather.index();
        mc.world.setRainGradient(w >= 1 ? 1f : 0f);
        mc.world.setThunderGradient(w == 2 ? 1f : 0f);
    }
}
