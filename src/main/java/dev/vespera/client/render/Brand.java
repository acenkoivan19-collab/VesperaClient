package dev.vespera.client.render;

import dev.vespera.client.ui.Theme;

import static org.lwjgl.nanovg.NanoVG.*;

/** Бренд клиента: имя и скруглённый квадратный логотип (assets/vespera/textures/logo.png). */
public final class Brand {

    public static final String NAME = "Vespera Client";

    private Brand() {}

    /**
     * Скруглённый квадрат с логотипом + тонкая обводка и мягкое свечение.
     * Если картинка не загрузилась — векторная «V» на градиенте.
     */
    public static void logo(long vg, float x, float y, float size, float radius, float alpha) {
        Draw.glow(vg, x, y, size, size, radius, size * 0.45f, Theme.ACCENT, 0.28f * alpha);

        int img = NvgRenderer.logoImage();
        if (img > 0) {
            Draw.image(vg, img, x, y, size, size, radius, alpha);
        } else {
            Draw.linear(vg, x, y, size, size, radius, x, y, x + size, y + size,
                    0xFF1B1A3A, alpha, 0xFF0E1630, alpha);
            float u = size / 16f, cx = x + size / 2f, cy = y + size / 2f;
            nvgBeginPath(vg);
            nvgMoveTo(vg, cx - 5f * u, cy - 4f * u);
            nvgLineTo(vg, cx, cy + 5f * u);
            nvgLineTo(vg, cx + 5f * u, cy - 4f * u);
            nvgStrokeWidth(vg, 1.6f * u);
            nvgLineCap(vg, NVG_ROUND);
            nvgLineJoin(vg, NVG_ROUND);
            nvgStrokeColor(vg, Draw.c1(Theme.ACCENT, alpha));
            nvgStroke(vg);
        }

        Draw.topHighlight(vg, x, y, size, size, radius, 0.10f * alpha);
        Draw.stroke(vg, x + 0.5f, y + 0.5f, size - 1f, size - 1f, radius, 1f, Theme.ACCENT, 0.35f * alpha);
    }
}
