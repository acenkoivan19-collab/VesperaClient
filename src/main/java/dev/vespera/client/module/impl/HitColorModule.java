package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;

import static org.lwjgl.nanovg.NanoVG.*;

import dev.vespera.client.render.Draw;

/**
 * Hit color — порт TerraHUD «Hit color» (hurtTint в TerraHudClient.render).
 * Экран вспыхивает цветом, когда урон получаешь ТЫ: прозрачность
 * 110/255 · hurtTime / maxHurtTime, гаснет вместе с ванильной анимацией урона.
 */
public final class HitColorModule extends HudModule {

    private final ChoiceSetting mode = setting(new ChoiceSetting("mode", "Вид", 0, "Весь экран", "Края")
            .hints("Ровная цветная вспышка по всему экрану", "Вспышка только по краям экрана (виньетка)"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFFFF3030));
    private final NumberSetting strength = setting(new NumberSetting("strength", "Сила", 0.43f, 0.1f, 1f, 0.05f));

    private static final org.lwjgl.nanovg.NVGPaint PAINT = org.lwjgl.nanovg.NVGPaint.malloc();

    public HitColorModule() {
        super("Hit Color", "[Дубль TerraHUD: Hit color] Вспышка цвета при получении урона", Category.COMBAT);
        about("Когда урон получаешь ты, экран коротко вспыхивает выбранным цветом и гаснет "
                + "вместе с анимацией урона.");
    }

    @Override
    public int layer() {
        return LAYER_TINT;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.hurtTime <= 0 || mc.player.maxHurtTime <= 0) return;
        float a = Math.max(0f, Math.min(1f, strength.get() * mc.player.hurtTime / mc.player.maxHurtTime));
        int c = color.argb();
        nvgBeginPath(vg);
        nvgRect(vg, 0, 0, w, h);
        if (mode.index() == 1) {
            float r = (float) Math.hypot(w, h) / 2f;
            nvgRadialGradient(vg, w / 2f, h / 2f, r * 0.45f, r, Draw.c1(c, 0f), Draw.c2(c, Math.min(1f, a * 1.8f)), PAINT);
            nvgFillPaint(vg, PAINT);
        } else {
            nvgFillColor(vg, Draw.c1(c, a));
        }
        nvgFill(vg);
    }
}
