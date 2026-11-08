package dev.vespera.fx.render;

import dev.vespera.client.render.Draw;
import dev.vespera.client.render.WorldDraw;

import java.util.Arrays;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Общие приёмы для красивых и быстрых эффектов NanoVG.
 *
 * - additive/normal — режим «свет складывается»: ореолы и неон выглядят как настоящее свечение.
 * - {@link Dots} и {@link Lines} — пакетная отрисовка: точки/штрихи одного цвета и яркости
 *   уходят ОДНИМ вызовом nvgFill/nvgStroke вместо сотен. Это главный выигрыш по FPS.
 */
public final class Fx {

    private Fx() {}

    /** Свет складывается с картинкой (ореолы, неон). Обязательно вернуть {@link #normal}. */
    public static void additive(long vg) {
        nvgGlobalCompositeBlendFunc(vg, NVG_SRC_ALPHA, NVG_ONE);
    }

    public static void normal(long vg) {
        nvgGlobalCompositeOperation(vg, NVG_SOURCE_OVER);
    }

    /** Мягкое круглое свечение (радиальный градиент к прозрачному краю). */
    public static void glow(long vg, float x, float y, float r, int color, float a) {
        if (a <= 0.004f || r < 0.5f) return;
        nvgRadialGradient(vg, x, y, r * 0.12f, r, Draw.c1(color, a), Draw.c2(color, 0f), WorldDraw.paint());
        nvgBeginPath(vg);
        nvgCircle(vg, x, y, r);
        nvgFillPaint(vg, WorldDraw.paint());
        nvgFill(vg);
    }

    public static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    public static float easeOutCubic(float t) {
        float u = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - u * u * u;
    }

    public static float easeOutQuint(float t) {
        float u = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - u * u * u * u * u;
    }

    /** Детерминированный «случай» 0..1 по номеру — одинаковый каждый кадр (без мерцания). */
    public static double rnd(double seed) {
        double v = Math.sin(seed * 12.9898 + 78.233) * 43758.5453;
        return v - Math.floor(v);
    }

    // ------------------------------------------------------------ пакет точек

    /**
     * Копит круги, потом рисует их группами «цвет + яркость» (яркость округляется до 1/16).
     * Один экземпляр на модуль, переиспользуется — без аллокаций в кадре.
     */
    public static final class Dots {
        private float[] x = new float[256], y = new float[256], r = new float[256];
        private int[] key = new int[256];
        private long[] order = new long[256];
        private int n;

        public void add(float px, float py, float pr, int argb, float a) {
            if (a <= 0.02f || pr < 0.3f || n >= 65535) return;
            int aq = Math.min(15, Math.round(a * 15f));
            if (aq <= 0) return;
            if (n == x.length) grow();
            x[n] = px;
            y[n] = py;
            r[n] = pr;
            key[n] = ((argb & 0xFFFFFF) << 4) | aq;
            n++;
        }

        private void grow() {
            int m = x.length * 2;
            x = Arrays.copyOf(x, m);
            y = Arrays.copyOf(y, m);
            r = Arrays.copyOf(r, m);
            key = Arrays.copyOf(key, m);
            order = Arrays.copyOf(order, m);
        }

        public void flush(long vg) {
            if (n == 0) return;
            for (int i = 0; i < n; i++) order[i] = ((long) key[i] << 16) | i;
            Arrays.sort(order, 0, n);
            int i = 0;
            while (i < n) {
                long k = order[i] >>> 16;
                nvgBeginPath(vg);
                int j = i;
                while (j < n && (order[j] >>> 16) == k) {
                    int idx = (int) (order[j] & 0xFFFF);
                    nvgCircle(vg, x[idx], y[idx], r[idx]);
                    j++;
                }
                nvgFillColor(vg, Draw.c1((int) (k >>> 4) | 0xFF000000, (k & 15) / 15f));
                nvgFill(vg);
                i = j;
            }
            n = 0;
        }
    }

    // ------------------------------------------------------------ пакет штрихов

    /** То же для отрезков: группы «цвет + яркость + толщина (шаг 0.5 px)». */
    public static final class Lines {
        private float[] c = new float[512]; // x1, y1, x2, y2
        private long[] key = new long[128];
        private long[] order = new long[128];
        private int n;

        public void add(float x1, float y1, float x2, float y2, float width, int argb, float a) {
            if (a <= 0.02f || n >= 65535) return;
            int aq = Math.min(15, Math.round(a * 15f));
            if (aq <= 0) return;
            int wq = Math.max(1, Math.min(63, Math.round(width * 2f)));
            if (n == key.length) {
                key = Arrays.copyOf(key, n * 2);
                order = Arrays.copyOf(order, n * 2);
                c = Arrays.copyOf(c, n * 8);
            }
            c[n * 4] = x1;
            c[n * 4 + 1] = y1;
            c[n * 4 + 2] = x2;
            c[n * 4 + 3] = y2;
            key[n] = ((long) (argb & 0xFFFFFF) << 10) | ((long) wq << 4) | aq;
            n++;
        }

        public void flush(long vg) {
            if (n == 0) return;
            for (int i = 0; i < n; i++) order[i] = (key[i] << 16) | i;
            Arrays.sort(order, 0, n);
            nvgLineCap(vg, NVG_ROUND);
            int i = 0;
            while (i < n) {
                long k = order[i] >>> 16;
                nvgBeginPath(vg);
                int j = i;
                while (j < n && (order[j] >>> 16) == k) {
                    int idx = (int) (order[j] & 0xFFFF) * 4;
                    nvgMoveTo(vg, c[idx], c[idx + 1]);
                    nvgLineTo(vg, c[idx + 2], c[idx + 3]);
                    j++;
                }
                nvgStrokeWidth(vg, ((k >>> 4) & 63) / 2f);
                nvgStrokeColor(vg, Draw.c1((int) (k >>> 10) | 0xFF000000, (k & 15) / 15f));
                nvgStroke(vg);
                i = j;
            }
            n = 0;
        }
    }
}
