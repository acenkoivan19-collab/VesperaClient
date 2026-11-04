package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.Module;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;

/**
 * Выключает тяжёлые эффекты. onEnable запоминает твои настройки, onDisable полностью
 * возвращает их. Смена режима при включённом модуле применяется сразу (onTick).
 */
public final class FpsBoostModule extends Module {

    private final ChoiceSetting mode = setting(new ChoiceSetting("mode", "Режим", 1, "Мягкий", "Сильный")
            .hints("Выключает только тени сущностей", "Выключает тени сущностей и облака"));
    private final BoolSetting keepClouds = setting(new BoolSetting("keepClouds", "Оставить облака в «Сильном»", false));

    private Boolean savedShadows;
    private CloudRenderMode savedClouds;

    public FpsBoostModule() {
        super("FPS Boost", "[Дубль TerraHUD: FPS Boost] Без теней и облаков", Category.UTILITY);
        about("Отключает самые дорогие эффекты, чтобы поднять FPS. При выключении модуля "
                + "твои настройки графики возвращаются как были.");
    }

    @Override
    protected void onEnable() {
        GameOptions o = MinecraftClient.getInstance().options;
        savedShadows = o.getEntityShadows().getValue();
        savedClouds = o.getCloudRenderMode().getValue();
        apply(o);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        apply(mc.options);
    }

    private void apply(GameOptions o) {
        if (savedShadows == null) return;
        if (o.getEntityShadows().getValue()) o.getEntityShadows().setValue(false);
        boolean noClouds = mode.index() == 1 && !keepClouds.on();
        CloudRenderMode want = noClouds ? CloudRenderMode.OFF : savedClouds;
        if (o.getCloudRenderMode().getValue() != want) o.getCloudRenderMode().setValue(want);
    }

    @Override
    protected void onDisable() {
        GameOptions o = MinecraftClient.getInstance().options;
        if (savedShadows != null) o.getEntityShadows().setValue(savedShadows);
        if (savedClouds != null) o.getCloudRenderMode().setValue(savedClouds);
        savedShadows = null;
        savedClouds = null;
    }
}
