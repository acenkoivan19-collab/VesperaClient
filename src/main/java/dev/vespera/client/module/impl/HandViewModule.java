package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.Module;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.NumberSetting;

/**
 * Положение и размер рук/предметов от первого лица.
 * Значения применяет HeldItemRendererMixin — только пока модуль включён.
 */
public final class HandViewModule extends Module {

    private final ChoiceSetting hands = new ChoiceSetting("hands", "Какие руки", 0, "Обе", "Только основная")
            .hints("Сдвиг и размер применяются к обеим рукам", "Меняется только правая (основная) рука");
    private final NumberSetting scale = new NumberSetting("scale", "Размер", 0.85f, 0.3f, 1.5f, 0.05f);
    private final NumberSetting offX = new NumberSetting("x", "Сдвиг вбок", 0.06f, -0.5f, 0.5f, 0.01f);
    private final NumberSetting offY = new NumberSetting("y", "Сдвиг вверх/вниз", -0.08f, -0.5f, 0.5f, 0.01f);
    private final NumberSetting offZ = new NumberSetting("z", "Ближе/дальше", -0.05f, -0.5f, 0.5f, 0.01f);

    public HandViewModule() {
        super("Hand View", "[Дубль TerraHUD: Модель в руках] Размер и позиция рук", Category.VISUALS);
        about("Меняет размер и положение рук и предметов от первого лица — например, "
                + "сделать меч поменьше и опустить ниже.");
        setting(hands);
        setting(scale);
        setting(offX);
        setting(offY);
        setting(offZ);
    }

    public boolean mainHandOnly() { return hands.index() == 1; }
    public float scale() { return scale.get(); }
    public float offsetX() { return offX.get(); }
    public float offsetY() { return offY.get(); }
    public float offsetZ() { return offZ.get(); }
}
