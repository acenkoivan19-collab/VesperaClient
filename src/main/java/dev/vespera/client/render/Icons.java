package dev.vespera.client.render;

import dev.vespera.client.module.Category;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Векторные иконки, нарисованные путями NanoVG — без текстур и icon-шрифтов,
 * поэтому они чёткие при любом масштабе. Координаты задаются в сетке 16×16
 * с центром в (0,0).
 */
public final class Icons {

    private Icons() {}

    public static void draw(long vg, Category cat, float cx, float cy, float size, int argb, float a) {
        begin(vg, cx, cy, size, argb, a);
        switch (cat) {
            case ALL -> {
                square(vg, -7, -7);
                square(vg, 1, -7);
                square(vg, -7, 1);
                square(vg, 1, 1);
            }
            case COMBAT -> { // меч
                line(vg, -3, 3, 6.5f, -6.5f);
                line(vg, -5.8f, 0.2f, -0.2f, 5.8f);
                line(vg, -3, 3, -6.5f, 6.5f);
            }
            case VISUALS -> { // глаз
                nvgBeginPath(vg);
                nvgEllipse(vg, 0, 0, 7.5f, 4.6f);
                nvgStroke(vg);
                dot(vg, 0, 0, 2.2f);
            }
            case WORLD -> { // глобус
                nvgBeginPath(vg);
                nvgCircle(vg, 0, 0, 7f);
                nvgStroke(vg);
                nvgBeginPath(vg);
                nvgEllipse(vg, 0, 0, 3f, 7f);
                nvgStroke(vg);
                line(vg, -7, 0, 7, 0);
            }
            case INTERFACE -> { // окно
                nvgBeginPath(vg);
                nvgRoundedRect(vg, -7, -6, 14, 12, 2.2f);
                nvgStroke(vg);
                line(vg, -7, -2.2f, 7, -2.2f);
                dot(vg, -4.6f, -4.1f, 0.8f);
                dot(vg, -2.4f, -4.1f, 0.8f);
            }
            case UTILITY -> { // слайдеры
                line(vg, -7, -4.5f, 7, -4.5f);
                line(vg, -7, 0, 7, 0);
                line(vg, -7, 4.5f, 7, 4.5f);
                dot(vg, -2.5f, -4.5f, 2f);
                dot(vg, 3f, 0, 2f);
                dot(vg, -0.5f, 4.5f, 2f);
            }
            case COSMETICS -> star(vg);
            case APPEARANCE -> { // палитра
                nvgBeginPath(vg);
                nvgCircle(vg, 0, 0, 7f);
                nvgStroke(vg);
                dot(vg, -3.2f, -1.8f, 1.4f);
                dot(vg, 0.6f, -3.8f, 1.4f);
                dot(vg, 3.6f, -0.6f, 1.4f);
                dot(vg, 1.2f, 3.4f, 1.9f);
            }
        }
        nvgRestore(vg);
    }

    public static void search(long vg, float cx, float cy, float size, int argb, float a) {
        begin(vg, cx, cy, size, argb, a);
        nvgBeginPath(vg);
        nvgCircle(vg, -1.5f, -1.5f, 5.2f);
        nvgStroke(vg);
        line(vg, 2.4f, 2.4f, 6.5f, 6.5f);
        nvgRestore(vg);
    }

    public static void cross(long vg, float cx, float cy, float size, int argb, float a) {
        begin(vg, cx, cy, size, argb, a);
        line(vg, -4, -4, 4, 4);
        line(vg, 4, -4, -4, 4);
        nvgRestore(vg);
    }

    // ---------------------------------------------------------------- helpers

    private static void begin(long vg, float cx, float cy, float size, int argb, float a) {
        float u = size / 16f;
        nvgSave(vg);
        nvgTranslate(vg, cx, cy);
        nvgScale(vg, u, u);
        nvgStrokeColor(vg, Draw.c1(argb, a));
        nvgFillColor(vg, Draw.c1(argb, a));
        nvgStrokeWidth(vg, 1.7f);
        nvgLineCap(vg, NVG_ROUND);
        nvgLineJoin(vg, NVG_ROUND);
    }

    private static void line(long vg, float x1, float y1, float x2, float y2) {
        nvgBeginPath(vg);
        nvgMoveTo(vg, x1, y1);
        nvgLineTo(vg, x2, y2);
        nvgStroke(vg);
    }

    private static void dot(long vg, float x, float y, float r) {
        nvgBeginPath(vg);
        nvgCircle(vg, x, y, r);
        nvgFill(vg);
    }

    private static void square(long vg, float x, float y) {
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, 6, 6, 1.6f);
        nvgFill(vg);
    }

    private static void star(long vg) {
        nvgBeginPath(vg);
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            float r = (i % 2 == 0) ? 7.6f : 3.3f;
            float x = (float) Math.cos(ang) * r;
            float y = (float) Math.sin(ang) * r + 0.6f;
            if (i == 0) nvgMoveTo(vg, x, y); else nvgLineTo(vg, x, y);
        }
        nvgClosePath(vg);
        nvgFill(vg);
    }

    // ---------------------------------------------------------------- значки HUD-модулей

    public enum Glyph { BOLT, SIGNAL, PIN, MOUSE, CLOCK, TIMER, CHIP, RUN, SERVER, CROSSHAIR, FOOD, MUSIC }

    /** Маленькие значки для HUD-панелей (сетка 16×16, центр 0,0). */
    public static void glyph(long vg, Glyph g, float cx, float cy, float size, int argb, float a) {
        begin(vg, cx, cy, size, argb, a);
        switch (g) {
            case BOLT -> {
                nvgBeginPath(vg);
                nvgMoveTo(vg, 1.5f, -7.5f); nvgLineTo(vg, -4.5f, 1f); nvgLineTo(vg, -0.5f, 1f);
                nvgLineTo(vg, -1.5f, 7.5f); nvgLineTo(vg, 4.5f, -1f); nvgLineTo(vg, 0.5f, -1f);
                nvgClosePath(vg);
                nvgFill(vg);
            }
            case SIGNAL -> {
                for (int i = 0; i < 4; i++) {
                    float hgt = 3f + i * 3f;
                    nvgBeginPath(vg);
                    nvgRoundedRect(vg, -6.5f + i * 3.6f, 6.5f - hgt, 2.4f, hgt, 0.8f);
                    nvgFill(vg);
                }
            }
            case PIN -> {
                nvgBeginPath(vg);
                nvgCircle(vg, 0, -2.5f, 4.5f);
                nvgStroke(vg);
                line(vg, 0, 2f, 0, 7.5f);
                dot(vg, 0, -2.5f, 1.5f);
            }
            case MOUSE -> {
                nvgBeginPath(vg);
                nvgRoundedRect(vg, -4.5f, -7f, 9f, 14f, 4.5f);
                nvgStroke(vg);
                line(vg, 0, -7f, 0, -2.5f);
            }
            case CLOCK, TIMER -> {
                nvgBeginPath(vg);
                nvgCircle(vg, 0, g == Glyph.TIMER ? 1f : 0f, 6.5f);
                nvgStroke(vg);
                float oy = g == Glyph.TIMER ? 1f : 0f;
                line(vg, 0, oy, 0, oy - 3.8f);
                line(vg, 0, oy, 2.8f, oy + 1.8f);
                if (g == Glyph.TIMER) line(vg, -2f, -7.5f, 2f, -7.5f);
            }
            case CHIP -> {
                nvgBeginPath(vg);
                nvgRoundedRect(vg, -5f, -5f, 10f, 10f, 1.8f);
                nvgStroke(vg);
                for (int i = -1; i <= 1; i++) {
                    line(vg, i * 3f, -7.5f, i * 3f, -5f);
                    line(vg, i * 3f, 5f, i * 3f, 7.5f);
                }
                dot(vg, 0, 0, 1.6f);
            }
            case RUN -> { // стрелка-«ускорение»
                line(vg, -7f, 0, 4f, 0);
                line(vg, 0.5f, -4.5f, 5f, 0);
                line(vg, 0.5f, 4.5f, 5f, 0);
                line(vg, -7f, -4f, -3f, -4f);
                line(vg, -7f, 4f, -3f, 4f);
            }
            case SERVER -> {
                for (int i = 0; i < 2; i++) {
                    nvgBeginPath(vg);
                    nvgRoundedRect(vg, -6.5f, -6.5f + i * 7f, 13f, 6f, 1.6f);
                    nvgStroke(vg);
                    dot(vg, -3.5f, -3.5f + i * 7f, 0.9f);
                }
            }
            case CROSSHAIR -> {
                nvgBeginPath(vg);
                nvgCircle(vg, 0, 0, 5f);
                nvgStroke(vg);
                line(vg, 0, -7.5f, 0, -3f);
                line(vg, 0, 3f, 0, 7.5f);
                line(vg, -7.5f, 0, -3f, 0);
                line(vg, 3f, 0, 7.5f, 0);
            }
            case FOOD -> { // окорок
                nvgBeginPath(vg);
                nvgEllipse(vg, 1.5f, -1.5f, 5.2f, 4.2f);
                nvgFill(vg);
                line(vg, -2f, 2f, -6f, 6.5f);
                dot(vg, -6.5f, 7f, 1.4f);
            }
            case MUSIC -> {
                nvgBeginPath(vg);
                nvgEllipse(vg, -3f, 4.5f, 2.6f, 2f);
                nvgFill(vg);
                nvgBeginPath(vg);
                nvgEllipse(vg, 4f, 3f, 2.6f, 2f);
                nvgFill(vg);
                line(vg, -0.6f, 4.5f, -0.6f, -6f);
                line(vg, 6.4f, 3f, 6.4f, -7.5f);
                line(vg, -0.6f, -6f, 6.4f, -7.5f);
            }
        }
        nvgRestore(vg);
    }
}
