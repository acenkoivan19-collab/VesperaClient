package dev.vespera.client.ui;

import dev.vespera.client.render.Draw;
import org.lwjgl.nanovg.NVGPaint;

import static org.lwjgl.nanovg.NanoVG.*;

/** Анимированный переключатель (плоский). Акцентный цвет — только во включённом состоянии. */
public final class Toggle {

    private static final NVGPaint PAINT = NVGPaint.malloc();

    private Toggle() {}

    public static void draw(long vg, float x, float y, float w, float h, float t, float hover) {
        float r = h / 2f;
        int track = Draw.lerpColor(Theme.TOGGLE_OFF, Theme.ACCENT, t);

        Draw.rect(vg, x, y, w, h, r, track);

        float baseR = r - 3f;
        float knobR = baseR + hover * 0.5f;
        float travel = w - 6f - 2f * baseR;
        float kx = x + 3f + baseR + travel * Anim.easeInOutCubic(t);
        float ky = y + h / 2f;


        Draw.circle(vg, kx, ky, knobR, Draw.lerpColor(Theme.KNOB_OFF, 0xFFFFFFFF, t), 1f);
    }
}
