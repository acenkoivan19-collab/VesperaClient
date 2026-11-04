package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.Module;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * Зум на клавишу (по умолчанию C, меняется в управлении).
 * FOV меняет GameRendererMixin через {@link #applyZoom(float)}.
 */
public final class ZoomModule extends Module {

    private final ChoiceSetting mode = setting(new ChoiceSetting("mode", "Клавиша C", 0, "Зажать", "Переключать")
            .hints("Зум, пока клавиша зажата", "Нажал — зум включён, нажал ещё раз — выключен"));
    private final NumberSetting factor = setting(new NumberSetting("factor", "Кратность", 4f, 1.5f, 10f, 0.5f));
    private final BoolSetting smooth = setting(new BoolSetting("smooth", "Плавный переход", true));

    private static KeyBinding key;
    private static float progress;  // 0 — нет зума, 1 — полный
    private static long lastNs = -1L;
    private static boolean toggled, wasDown;

    public ZoomModule() {
        super("Zoom", "Приближение на клавишу C", Category.UTILITY);
        about("Приближает изображение, как подзорная труба. Можно держать клавишу или "
                + "переключать зум нажатием; кратность настраивается.");
    }

    public static void setKey(KeyBinding binding) {
        key = binding;
    }

    @Override
    protected void onDisable() {
        progress = 0f;
        toggled = false;
    }

    /** Вызывается из GameRendererMixin для каждого FOV мира (только если модуль включён). */
    public static float applyZoom(float fov) {
        ZoomModule m = ModuleManager.get(ZoomModule.class);
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean down = key != null && mc.currentScreen == null && key.isPressed();
        if (m.mode.index() == 1) {
            if (down && !wasDown) toggled = !toggled;
        } else {
            toggled = down;
        }
        wasDown = down;

        long now = System.nanoTime();
        float dt = lastNs < 0 ? 0f : Math.min((now - lastNs) / 1e9f, 0.1f);
        lastNs = now;
        float target = toggled ? 1f : 0f;
        if (m.smooth.on()) progress += (target - progress) * (1f - (float) Math.exp(-14f * dt));
        else progress = target;
        if (progress < 0.001f) return fov;
        return fov / (1f + (m.factor.get() - 1f) * progress);
    }
}
