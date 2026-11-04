package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.ui.Anim;
import dev.vespera.client.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/** W A S D, LMB/RMB и пробел с анимацией нажатия (слева снизу). */
public final class KeystrokesModule extends HudModule {

    private static final float K = 30f, G = 4f;

    private final Anim[] anims = new Anim[7];
    private final ChoiceSetting layout = setting(new ChoiceSetting("layout", "Раскладка", 0, "Полная", "WASD", "С CPS")
            .hints("WASD, кнопки мыши и пробел", "Только W A S D", "Вместо LMB/RMB — клики в секунду"));
    private final java.util.ArrayDeque<Long> lc = new java.util.ArrayDeque<>(), rc = new java.util.ArrayDeque<>();
    private boolean wasL, wasR;

    public KeystrokesModule() {
        super("Keystrokes", "[Дубль TerraHUD: Keystrokes] WASD, мышь и пробел", Category.INTERFACE);
        about("Показывает, какие клавиши движения и кнопки мыши ты сейчас жмёшь — удобно для стримов и записи.");
        for (int i = 0; i < anims.length; i++) anims[i] = new Anim(0f, 22f);
    }

    @Override
    public float defaultX() {
        return 0.006f;
    }

    @Override
    public float defaultY() {
        return 0.70f;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null && !isEditing()) return;
        var o = mc.options;
        KeyBinding[] keys = {o.forwardKey, o.leftKey, o.backKey, o.rightKey, o.attackKey, o.useKey, o.jumpKey};

        float total = K * 3 + G * 2;
        float x0 = 0f, y0 = 0f;

        int mode = layout.index();
        if (mode == 1) size(total, K * 2 + G); // только WASD
        else size(total, K * 3 + G * 3 + 18f);
        key(vg, 0, keys, "W", x0 + K + G, y0, K, K, dt);
        key(vg, 1, keys, "A", x0, y0 + K + G, K, K, dt);
        key(vg, 2, keys, "S", x0 + K + G, y0 + K + G, K, K, dt);
        key(vg, 3, keys, "D", x0 + 2 * (K + G), y0 + K + G, K, K, dt);
        if (mode == 1) return;
        float half = (total - G) / 2f;
        String l = "LMB", r = "RMB";
        if (mode == 2) { // кнопки мыши показывают CPS
            long now = System.currentTimeMillis(), win = mc.getWindow().getHandle();
            boolean ld = org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, 0) == 1, rd = org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, 1) == 1;
            if (ld && !wasL) lc.add(now);
            if (rd && !wasR) rc.add(now);
            wasL = ld; wasR = rd;
            while (!lc.isEmpty() && now - lc.peekFirst() > 1000) lc.pollFirst();
            while (!rc.isEmpty() && now - rc.peekFirst() > 1000) rc.pollFirst();
            l = lc.size() + " CPS";
            r = rc.size() + " CPS";
        }
        key(vg, 4, keys, l, x0, y0 + 2 * (K + G), half, K, dt);
        key(vg, 5, keys, r, x0 + half + G, y0 + 2 * (K + G), half, K, dt);
        key(vg, 6, keys, "", x0, y0 + 3 * (K + G), total, 18f, dt);
    }

    private void key(long vg, int i, KeyBinding[] keys, String label, float x, float y, float w, float h, float dt) {
        Anim a = anims[i];
        a.target = keys[i].isPressed() ? 1f : 0f;
        step(a, dt);
        float t = a.value;

        int acc = accent();
        if (background()) Draw.rect(vg, x, y, w, h, 7f, Draw.lerpColor(Theme.BG, acc, t * 0.85f), 0.82f);
        else if (t > 0.01f) Draw.rect(vg, x, y, w, h, 7f, acc, 0.85f * t);
        if (t > 0.01f && shadow()) Draw.glow(vg, x, y, w, h, 7f, 10f, acc, 0.35f * t);
        Draw.stroke(vg, x + 0.5f, y + 0.5f, w - 1f, h - 1f, 7f, 1f, acc, 0.15f + 0.4f * t);
        if (label.isEmpty()) { // пробел — полоска
            Draw.rect(vg, x + w / 2f - 14f, y + h / 2f - 1f, 28f, 2f, 1f,
                    Draw.lerpColor(Theme.TEXT, Theme.BG, t), 1f);
        } else {
            Draw.text(vg, label, x + w / 2f, y + h / 2f + 0.5f, Fonts.SEMIBOLD, label.length() > 1 ? 11f : 13f,
                    Draw.CENTER_MID, Draw.lerpColor(Theme.TEXT, Theme.BG, t), 1f);
        }
    }
}
