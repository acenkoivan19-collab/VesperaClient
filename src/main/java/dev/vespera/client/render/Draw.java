package dev.vespera.client.render;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Примитивы Soft UI поверх NanoVG: скруглённые панели, мягкие тени,
 * внутренние тени, свечение контура, градиенты и текст.
 * Все цвета — ARGB int (0xAARRGGBB). Скретч-объекты переиспользуются:
 * nvgFillColor / nvgFillPaint копируют значение, поэтому это безопасно.
 */
public final class Draw {

    public static final int LEFT_MID = NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE;
    public static final int CENTER_MID = NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE;
    public static final int RIGHT_MID = NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE;

    private static final NVGColor C1 = NVGColor.malloc();
    private static final NVGColor C2 = NVGColor.malloc();
    private static final NVGPaint PAINT = NVGPaint.malloc();
    private static final FloatBuffer BOUNDS = MemoryUtil.memAllocFloat(4);

    private Draw() {}

    // ---------------------------------------------------------------- colors

    public static NVGColor c1(int argb, float alpha) { return set(C1, argb, alpha); }
    public static NVGColor c2(int argb, float alpha) { return set(C2, argb, alpha); }

    private static NVGColor set(NVGColor c, int argb, float alphaMul) {
        c.r(((argb >> 16) & 0xFF) / 255f);
        c.g(((argb >> 8) & 0xFF) / 255f);
        c.b((argb & 0xFF) / 255f);
        c.a(((argb >>> 24) & 0xFF) / 255f * alphaMul);
        return c;
    }

    /** Цвет радуги по оттенку h (0..1, по кругу), насыщенный и яркий. */
    public static int hue(float h) {
        h = h - (float) Math.floor(h);
        return java.awt.Color.HSBtoRGB(h, 0.65f, 1f) | 0xFF000000;
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24), ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24), br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24)
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    // ---------------------------------------------------------------- shapes

    public static void rect(long vg, float x, float y, float w, float h, float r, int argb) {
        rect(vg, x, y, w, h, r, argb, 1f);
    }

    public static void rect(long vg, float x, float y, float w, float h, float r, int argb, float a) {
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgFillColor(vg, c1(argb, a));
        nvgFill(vg);
    }

    public static void stroke(long vg, float x, float y, float w, float h, float r,
                              float width, int argb, float a) {
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgStrokeWidth(vg, width);
        nvgStrokeColor(vg, c1(argb, a));
        nvgStroke(vg);
    }

    public static void circle(long vg, float cx, float cy, float r, int argb, float a) {
        nvgBeginPath(vg);
        nvgCircle(vg, cx, cy, r);
        nvgFillColor(vg, c1(argb, a));
        nvgFill(vg);
    }

    /** Скруглённый прямоугольник, залитый линейным градиентом (x0,y0)→(x1,y1). */
    public static void linear(long vg, float x, float y, float w, float h, float r,
                              float x0, float y0, float x1, float y1,
                              int ca, float aa, int cb, float ab) {
        nvgLinearGradient(vg, x0, y0, x1, y1, c1(ca, aa), c2(cb, ab), PAINT);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgFillPaint(vg, PAINT);
        nvgFill(vg);
    }

    /** Круг с диагональным градиентом (аватар, логотип). */
    public static void circleGradient(long vg, float cx, float cy, float r, int ca, int cb, float a) {
        nvgLinearGradient(vg, cx - r, cy - r, cx + r, cy + r, c1(ca, a), c2(cb, a), PAINT);
        nvgBeginPath(vg);
        nvgCircle(vg, cx, cy, r);
        nvgFillPaint(vg, PAINT);
        nvgFill(vg);
    }

    /** Картинка NanoVG (nvgCreateImage*), вписанная в скруглённый прямоугольник. */
    public static void image(long vg, int image, float x, float y, float w, float h, float r, float alpha) {
        nvgImagePattern(vg, x, y, w, h, 0f, image, alpha, PAINT);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgFillPaint(vg, PAINT);
        nvgFill(vg);
    }

    // ---------------------------------------------------------------- soft UI

    /** Мягкая внешняя тень (рисуется только снаружи фигуры). */
    public static void shadow(long vg, float x, float y, float w, float h, float r,
                              float blur, float offsetY, int argb, float a) {
        nvgBoxGradient(vg, x, y + offsetY, w, h, r * 1.2f, blur, c1(argb, a), c2(argb, 0f), PAINT);
        nvgBeginPath(vg);
        nvgRect(vg, x - blur, y - blur + offsetY, w + blur * 2, h + blur * 2);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgPathWinding(vg, NVG_HOLE);
        nvgFillPaint(vg, PAINT);
        nvgFill(vg);
    }

    /** Свечение контура (Border Glow) — та же тень, но цветная и без смещения. */
    public static void glow(long vg, float x, float y, float w, float h, float r,
                            float size, int argb, float a) {
        shadow(vg, x, y, w, h, r, size, 0f, argb, a);
    }

    /** Внутренняя тень: прозрачный центр, затемнённые края. */
    public static void innerShadow(long vg, float x, float y, float w, float h, float r,
                                   float size, int argb, float a) {
        nvgBoxGradient(vg, x, y + size * 0.15f, w, h, r, size, c1(argb, 0f), c2(argb, a), PAINT);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgFillPaint(vg, PAINT);
        nvgFill(vg);
    }

    /** Мягкий блик сверху — даёт эффект «объёмного» блока. */
    public static void topHighlight(long vg, float x, float y, float w, float h, float r, float a) {
        linear(vg, x, y, w, h, r, x, y, x, y + h * 0.55f, 0xFFFFFFFF, a, 0xFFFFFFFF, 0f);
    }

    // ---------------------------------------------------------------- text

    public static void text(long vg, String s, float x, float y,
                            String font, float size, int align, int argb, float a) {
        nvgFontFace(vg, font);
        nvgFontSize(vg, size);
        nvgTextAlign(vg, align);
        nvgFillColor(vg, c1(argb, a));
        nvgText(vg, x, y, s);
    }

    public static float textWidth(long vg, String s, String font, float size) {
        nvgFontFace(vg, font);
        nvgFontSize(vg, size);
        return nvgTextBounds(vg, 0f, 0f, s, BOUNDS);
    }

    /** Обрезает строку с «…», чтобы она влезла в maxW. */
    public static String ellipsize(long vg, String s, String font, float size, float maxW) {
        if (textWidth(vg, s, font, size) <= maxW) return s;
        String dots = "…";
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (textWidth(vg, s.substring(0, mid) + dots, font, size) <= maxW) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo).stripTrailing() + dots;
    }

    // ---------------------------------------------------------------- табличные цифры

    /**
     * Ширина строки с табличными цифрами: каждая цифра занимает ширину «0»,
     * поэтому числа (FPS, координаты, таймеры) не «прыгают» при смене значения.
     */
    public static float tabularWidth(long vg, String s, String font, float size) {
        float dw = textWidth(vg, "0", font, size), w = 0f;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += Character.isDigit(c) ? dw : textWidth(vg, String.valueOf(c), font, size);
        }
        return w;
    }

    /** Текст с табличными цифрами. align: LEFT_MID / CENTER_MID / RIGHT_MID. */
    public static void textTabular(long vg, String s, float x, float y, String font, float size,
                                   int align, int argb, float a) {
        float total = tabularWidth(vg, s, font, size);
        float cx = (align & NVG_ALIGN_RIGHT) != 0 ? x - total : (align & NVG_ALIGN_CENTER) != 0 ? x - total / 2f : x;
        float dw = textWidth(vg, "0", font, size);
        nvgFontFace(vg, font);
        nvgFontSize(vg, size);
        nvgFillColor(vg, c1(argb, a));
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String ch = String.valueOf(c);
            if (Character.isDigit(c)) {
                nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
                nvgText(vg, cx + dw / 2f, y, ch);
                cx += dw;
            } else {
                nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
                nvgText(vg, cx, y, ch);
                cx += textWidth(vg, ch, font, size);
                nvgFontFace(vg, font);
                nvgFontSize(vg, size);
                nvgFillColor(vg, c1(argb, a));
            }
        }
    }

    /** Кольцо-прогресс (0..1) от 12 часов по часовой. */
    public static void progressRing(long vg, float cx, float cy, float r, float width, float progress,
                                    int track, float trackA, int argb, float a) {
        nvgBeginPath(vg);
        nvgCircle(vg, cx, cy, r);
        nvgStrokeWidth(vg, width);
        nvgStrokeColor(vg, c1(track, trackA));
        nvgStroke(vg);
        float p = Math.max(0f, Math.min(1f, progress));
        if (p <= 0.001f) return;
        float a0 = (float) (-Math.PI / 2), a1 = a0 + (float) (Math.PI * 2 * p);
        nvgBeginPath(vg);
        nvgArc(vg, cx, cy, r, a0, a1, NVG_CW);
        nvgStrokeWidth(vg, width);
        nvgLineCap(vg, NVG_ROUND);
        nvgStrokeColor(vg, c1(argb, a));
        nvgStroke(vg);
    }

    /** Текст с переносом по ширине. Возвращает высоту блока. */
    public static float textBox(long vg, String s, float x, float y, float width, String font, float size,
                                int argb, float a) {
        nvgFontFace(vg, font);
        nvgFontSize(vg, size);
        nvgTextLineHeight(vg, 1.3f);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        nvgFillColor(vg, c1(argb, a));
        nvgTextBox(vg, x, y, width, s);
        float h = textBoxHeight(vg, s, width, font, size);
        nvgTextLineHeight(vg, 1f);
        return h;
    }

    public static float textBoxHeight(long vg, String s, float width, String font, float size) {
        nvgFontFace(vg, font);
        nvgFontSize(vg, size);
        nvgTextLineHeight(vg, 1.3f);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        nvgTextBoxBounds(vg, 0f, 0f, width, s, BOUNDS);
        float h = BOUNDS.get(3) - BOUNDS.get(1);
        nvgTextLineHeight(vg, 1f);
        return h;
    }
}
