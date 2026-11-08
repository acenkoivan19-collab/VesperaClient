package dev.vespera.client.module.impl;

import dev.vespera.client.module.ModuleManager;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.Module;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Время суток только на клиенте. Каждый тик (после пакета времени от сервера)
 * перезаписывает timeOfDay клиентского мира. После выключения сервер вернёт
 * реальное время со следующим пакетом (~1 сек).
 */
public final class TimeChangerModule extends Module {

    private static final long[] PRESETS = {1000L, 6000L, 12500L, 18000L};

    private final ChoiceSetting time = setting(new ChoiceSetting("time", "Время", 3, "Утро", "День", "Закат", "Ночь", "Своё")
            .hints("Раннее утро, солнце низко", "Полдень, максимум света", "Красный закат",
                    "Полночь, звёзды и луна", "Час из слайдера ниже"));
    private final NumberSetting hour = setting(new NumberSetting("hour", "Свой час (0–24)", 21f, 0f, 24f, 0.5f));

    public TimeChangerModule() {
        super("Time Changer", "[Дубль TerraHUD: Время мира] Своё время суток", Category.WORLD);
        about("Ставит выбранное время суток только у тебя на экране — на сервере и у других "
                + "игроков время не меняется.");
    }

    @Override
    public void onTick(MinecraftClient mc) {
        apply(mc);
    }

    private void apply(MinecraftClient mc) {
        if (mc.world == null) return;
        int i = time.index();
        // час 0 в Minecraft = 6:00 утра → сдвиг на 6 часов
        long t = i < PRESETS.length ? PRESETS[i] : (long) (((hour.get() - 6f + 24f) % 24f) * 1000f);
        mc.world.getLevelProperties().setTimeOfDay(t);
    }

    /**
     * Сервер раз в секунду присылает своё время. Пакет применяется между тиками, и при высоком FPS
     * несколько кадров рисовались с серверным (дневным) временем — небо мигало. Поэтому время
     * ставим заново сразу после пакета и перед каждым кадром (миксины ClientPlayNetworkHandler и GameRenderer).
     */
    public static void reapply() {
        TimeChangerModule m = ModuleManager.get(TimeChangerModule.class);
        if (m == null || !m.isEnabled()) return;
        m.apply(MinecraftClient.getInstance());
    }
}
