package dev.vespera.client.render;

import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.ModuleConfig;
import net.minecraft.util.math.Box;

import org.lwjgl.nanovg.NVGPaint;

import java.util.function.DoubleUnaryOperator;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Примитивы в мировых координатах, нарисованные NanoVG через {@link Projection}.
 * Рисуются поверх кадра (без теста глубины), поэтому видны и сквозь блоки.
 */
public final class WorldDraw {

    private static final float[] A = new float[3], B = new float[3], C = new float[3];
    private static final int[][] BOX_EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final int[][] BOX_FACES = {
            {0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}
    };
    private static final float[][] CORNERS = new float[8][3];
    private static final float[][] Q = new float[4][3];
    private static final NVGPaint PAINT = NVGPaint.malloc();
    private static final boolean[] VISIBLE = new boolean[8];

    private WorldDraw() {}

    /** Горизонтальное кольцо. glow — дополнительный широкий полупрозрачный проход. */
    public static void ring(long vg, double cx, double cy, double cz, double radius, int segments,
                            float width, int argb, float alpha, boolean glow) {
        if (glow) ring(vg, cx, cy, cz, radius, segments, width * 4f, argb, alpha * 0.18f, false);
        nvgBeginPath(vg);
        boolean pen = false;
        for (int i = 0; i <= segments; i++) {
            double a = i * Math.PI * 2.0 / segments;
            if (Projection.project(cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius, A)) {
                if (pen) nvgLineTo(vg, A[0], A[1]); else nvgMoveTo(vg, A[0], A[1]);
                pen = true;
            } else {
                pen = false;
            }
        }
        nvgStrokeWidth(vg, width);
        nvgLineCap(vg, NVG_ROUND);
        nvgLineJoin(vg, NVG_ROUND);
        nvgStrokeColor(vg, Draw.c1(argb, alpha));
        nvgStroke(vg);
    }

    /** Конус: вершина (ax,ay,az) и горизонтальное основание. Заливка + обводка основания. */
    public static void cone(long vg, double ax, double ay, double az, double bx, double by, double bz,
                            double radius, int segments, int fill, float fillAlpha, int edge, float edgeAlpha) {
        if (!Projection.project(ax, ay, az, A)) return;
        float apexX = A[0], apexY = A[1];
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2.0 / segments, a1 = (i + 1) * Math.PI * 2.0 / segments;
            if (!Projection.project(bx + Math.cos(a0) * radius, by, bz + Math.sin(a0) * radius, B)) continue;
            if (!Projection.project(bx + Math.cos(a1) * radius, by, bz + Math.sin(a1) * radius, C)) continue;
            nvgBeginPath(vg);
            nvgMoveTo(vg, apexX, apexY);
            nvgLineTo(vg, B[0], B[1]);
            nvgLineTo(vg, C[0], C[1]);
            nvgClosePath(vg);
            nvgFillColor(vg, Draw.c1(fill, fillAlpha));
            nvgFill(vg);
        }
        ring(vg, bx, by, bz, radius, segments, 1.4f, edge, edgeAlpha, true);
    }

    /** Отрезок в мире. */
    public static boolean line(long vg, double x1, double y1, double z1, double x2, double y2, double z2,
                               float width, int argb, float alpha) {
        if (!Projection.project(x1, y1, z1, A) || !Projection.project(x2, y2, z2, B)) return false;
        nvgBeginPath(vg);
        nvgMoveTo(vg, A[0], A[1]);
        nvgLineTo(vg, B[0], B[1]);
        nvgStrokeWidth(vg, width);
        nvgLineCap(vg, NVG_ROUND);
        nvgStrokeColor(vg, Draw.c1(argb, alpha));
        nvgStroke(vg);
        return true;
    }

    /** Каркас и полупрозрачные грани коробки (обводка блока). */
    public static void box(long vg, Box b, int edge, float edgeAlpha, int fill, float fillAlpha) {
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 4) == 0 ? b.minY : b.maxY;
            double z = (i & 2) == 0 ? b.minZ : b.maxZ;
            VISIBLE[i] = Projection.project(x, y, z, CORNERS[i]);
        }
        if (fillAlpha > 0f) {
            for (int[] f : BOX_FACES) {
                if (!(VISIBLE[f[0]] && VISIBLE[f[1]] && VISIBLE[f[2]] && VISIBLE[f[3]])) continue;
                nvgBeginPath(vg);
                nvgMoveTo(vg, CORNERS[f[0]][0], CORNERS[f[0]][1]);
                for (int k = 1; k < 4; k++) nvgLineTo(vg, CORNERS[f[k]][0], CORNERS[f[k]][1]);
                nvgClosePath(vg);
                nvgFillColor(vg, Draw.c1(fill, fillAlpha));
                nvgFill(vg);
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            nvgBeginPath(vg);
            for (int[] e : BOX_EDGES) {
                if (!VISIBLE[e[0]] || !VISIBLE[e[1]]) continue;
                nvgMoveTo(vg, CORNERS[e[0]][0], CORNERS[e[0]][1]);
                nvgLineTo(vg, CORNERS[e[1]][0], CORNERS[e[1]][1]);
            }
            nvgStrokeWidth(vg, pass == 0 ? 5f : 1.6f);
            nvgLineCap(vg, NVG_ROUND);
            nvgStrokeColor(vg, Draw.c1(edge, pass == 0 ? edgeAlpha * 0.18f : edgeAlpha));
            nvgStroke(vg);
        }
    }

    /**
     * Экранный прямоугольник, в который проецируется коробка.
     * @return false, если хоть один угол позади камеры
     */
    public static boolean screenRect(Box b, float[] rect) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 4) == 0 ? b.minY : b.maxY;
            double z = (i & 2) == 0 ? b.minZ : b.maxZ;
            if (!Projection.project(x, y, z, A)) return false;
            minX = Math.min(minX, A[0]); minY = Math.min(minY, A[1]);
            maxX = Math.max(maxX, A[0]); maxY = Math.max(maxY, A[1]);
        }
        rect[0] = minX; rect[1] = minY; rect[2] = maxX - minX; rect[3] = maxY - minY;
        return true;
    }

    /** Залитый горизонтальный диск (рисуется, только если весь диск перед камерой). */
    public static void disc(long vg, double cx, double cy, double cz, double radius, int segments,
                            int argb, float alpha) {
        nvgBeginPath(vg);
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2.0 / segments;
            if (!Projection.project(cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius, A)) return;
            if (i == 0) nvgMoveTo(vg, A[0], A[1]); else nvgLineTo(vg, A[0], A[1]);
        }
        nvgClosePath(vg);
        nvgFillColor(vg, Draw.c1(argb, alpha));
        nvgFill(vg);
    }

    /**
     * Горизонтальный контур с радиусом, зависящим от угла (звезда, круг…).
     * Толщина задаётся в БЛОКАХ и переводится в пиксели по глубине центра.
     */
    public static void polarRing(long vg, double cx, double cy, double cz, DoubleUnaryOperator radiusAt,
                                 int segments, double widthBlocks, int argb, float alpha) {
        if (!Projection.project(cx, cy, cz, A)) return;
        float px = Math.max(1f, (float) widthBlocks * Projection.pixelsPerBlock(A[2]));
        nvgBeginPath(vg);
        boolean pen = false;
        for (int i = 0; i <= segments; i++) {
            double a = i * Math.PI * 2.0 / segments;
            double r = radiusAt.applyAsDouble(a);
            if (Projection.project(cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r, B)) {
                if (pen) nvgLineTo(vg, B[0], B[1]); else nvgMoveTo(vg, B[0], B[1]);
                pen = true;
            } else {
                pen = false;
            }
        }
        nvgStrokeWidth(vg, px);
        nvgLineCap(vg, NVG_ROUND);
        nvgLineJoin(vg, NVG_ROUND);
        nvgStrokeColor(vg, Draw.c1(argb, alpha));
        nvgStroke(vg);
    }

    private static boolean projectQuad(double[] p) {
        for (int i = 0; i < 4; i++) {
            if (!Projection.project(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], Q[i])) return false;
        }
        return true;
    }

    private static void quadPath(long vg) {
        nvgBeginPath(vg);
        nvgMoveTo(vg, Q[0][0], Q[0][1]);
        nvgLineTo(vg, Q[1][0], Q[1][1]);
        nvgLineTo(vg, Q[2][0], Q[2][1]);
        nvgLineTo(vg, Q[3][0], Q[3][1]);
        nvgClosePath(vg);
    }

    /** Залитый четырёхугольник: p = {x1,y1,z1, x2,y2,z2, x3,y3,z3, x4,y4,z4}. */
    public static void quad(long vg, double[] p, int argb, float alpha) {
        if (!projectQuad(p)) return;
        quadPath(vg);
        nvgFillColor(vg, Draw.c1(argb, alpha));
        nvgFill(vg);
    }

    /**
     * Четырёхугольник с градиентом: ребро 1-2 цвета top, ребро 3-4 цвета bottom
     * (аналог quadGrad из TerraHUD — крылья, грани шляпы).
     */
    public static void quadGradient(long vg, double[] p, int top, float topAlpha, int bottom, float bottomAlpha) {
        if (!projectQuad(p)) return;
        float tx = (Q[0][0] + Q[1][0]) / 2f, ty = (Q[0][1] + Q[1][1]) / 2f;
        float bx = (Q[2][0] + Q[3][0]) / 2f, by = (Q[2][1] + Q[3][1]) / 2f;
        if (Math.abs(tx - bx) + Math.abs(ty - by) < 0.5f) by += 0.5f;
        nvgLinearGradient(vg, tx, ty, bx, by, Draw.c1(top, topAlpha), Draw.c2(bottom, bottomAlpha), PAINT);
        quadPath(vg);
        nvgFillPaint(vg, PAINT);
        nvgFill(vg);
    }

    /** Отрезок с толщиной в блоках (лента, луч взгляда). */
    public static void beam(long vg, double x1, double y1, double z1, double x2, double y2, double z2,
                            double widthBlocks, int argb, float alpha) {
        if (!Projection.project(x1, y1, z1, A) || !Projection.project(x2, y2, z2, B)) return;
        float px = Math.max(1f, (float) widthBlocks * Projection.pixelsPerBlock((A[2] + B[2]) / 2f));
        nvgBeginPath(vg);
        nvgMoveTo(vg, A[0], A[1]);
        nvgLineTo(vg, B[0], B[1]);
        nvgStrokeWidth(vg, px);
        nvgLineCap(vg, NVG_ROUND);
        nvgStrokeColor(vg, Draw.c1(argb, alpha));
        nvgStroke(vg);
    }

    // ---------------------------------------------------------------- раскладка HUD

    /** Экранный прямоугольник модуля после раскладки: {x, y, w, h} в HUD-координатах. */
    public static float[] layoutRect(HudModule m, float screenW, float screenH) {
        ModuleConfig.Layout l = ModuleConfig.layout(m);
        float w = m.boundsW() * l.scale, h = m.boundsH() * l.scale;
        // позиция — доля экрана; клампим, чтобы модуль не уезжал за край при смене разрешения
        float x = Math.max(0f, Math.min(screenW - w, l.x * screenW));
        float y = Math.max(0f, Math.min(screenH - h, l.y * screenH));
        return new float[]{x, y, w, h};
    }

    /**
     * Единый хелпер раскладки: translate в позицию из layout, scale и прозрачность.
     * Вызывается ModuleManager'ом ДО renderHud каждого layoutable-модуля внутри nvgSave/nvgRestore.
     * @return true, если модуль надо рисовать (alpha > 0)
     */
    public static boolean applyLayout(long vg, HudModule m, float screenW, float screenH) {
        ModuleConfig.Layout l = ModuleConfig.layout(m);
        if (l.alpha <= 0.001f) return false;
        float[] r = layoutRect(m, screenW, screenH);
        nvgTranslate(vg, r[0], r[1]);
        nvgScale(vg, l.scale, l.scale);
        nvgGlobalAlpha(vg, l.alpha);
        return true;
    }
}
