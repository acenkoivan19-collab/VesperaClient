package dev.vespera.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Простой воксельный рендер для косметики (крылья, рюкзаки, питомцы) через NanoVG.
 *
 * Модель строится из прямоугольных «кубиков» в пикселях скина (1 px = 1/16 блока)
 * в системе тела: X — вправо, Y — вверх (0 = ноги), Z — назад (спина = +2 px).
 * Есть стек матриц (translate/rotX/rotY/rotZ), чтобы крылья махали, хвост вилял и т.п.
 *
 * При отрисовке кубики переводятся в мир (позиция + поворот тела), проецируются,
 * сортируются от дальних к ближним, у каждого рисуются только грани, смотрящие на камеру,
 * с простым освещением (верх светлее, низ темнее). Работает и в мире, и в превью
 * панели (через Projection.beginPreview).
 */
public final class Voxel {

    /** Грани кубика: индексы углов (бит0 = x1, бит1 = y1, бит2 = z1). */
    private static final int[][] FACES = {
            {0, 4, 6, 2}, {1, 3, 7, 5},   // -X, +X
            {0, 1, 5, 4}, {2, 6, 7, 3},   // -Y, +Y
            {0, 2, 3, 1}, {4, 5, 7, 6}    // -Z, +Z
    };
    private static final double LX = -0.35, LY = 0.85, LZ = -0.40; // направление света (нормировано ниже)
    private static final double LN = Math.sqrt(LX * LX + LY * LY + LZ * LZ);

    private final List<double[]> boxes = new ArrayList<>();   // 24 координаты углов (в px тела)
    private final List<int[]> colors = new ArrayList<>();     // {argb}
    private final double[] m = new double[12];
    private final double[][] stack = new double[16][];
    private int sp;

    // буферы отрисовки
    private final float[] tmp = new float[3];
    private float[][] scr = new float[0][];
    private double[][] world = new double[0][];
    private double[] depth = new double[0];
    private Integer[] order = new Integer[0];

    public Voxel() {
        reset();
    }

    /** Очистить модель и матрицу. */
    public Voxel reset() {
        boxes.clear();
        colors.clear();
        identity();
        sp = 0;
        return this;
    }

    private void identity() {
        Arrays.fill(m, 0);
        m[0] = m[5] = m[10] = 1;
    }

    public Voxel push() {
        stack[sp++] = m.clone();
        return this;
    }

    public Voxel pop() {
        System.arraycopy(stack[--sp], 0, m, 0, 12);
        return this;
    }

    public Voxel translate(double x, double y, double z) {
        m[3] += m[0] * x + m[1] * y + m[2] * z;
        m[7] += m[4] * x + m[5] * y + m[6] * z;
        m[11] += m[8] * x + m[9] * y + m[10] * z;
        return this;
    }

    /** Равномерное масштабирование. */
    public Voxel scale(double k) {
        m[0] *= k; m[1] *= k; m[2] *= k;
        m[4] *= k; m[5] *= k; m[6] *= k;
        m[8] *= k; m[9] *= k; m[10] *= k;
        return this;
    }

    /** Поворот вокруг Y (радианы): x' = x·cos + z·sin, z' = −x·sin + z·cos. */
    public Voxel rotY(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        mul(c, 0, s, 0, 1, 0, -s, 0, c);
        return this;
    }

    /** Поворот вокруг Z (радианы): x' = x·cos − y·sin, y' = x·sin + y·cos. */
    public Voxel rotZ(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        mul(c, -s, 0, s, c, 0, 0, 0, 1);
        return this;
    }

    /** Поворот вокруг X (радианы): y' = y·cos − z·sin, z' = y·sin + z·cos. */
    public Voxel rotX(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        mul(1, 0, 0, 0, c, -s, 0, s, c);
        return this;
    }

    private void mul(double a, double b, double c, double d, double e, double f, double g, double h, double i) {
        double m0 = m[0], m1 = m[1], m2 = m[2], m4 = m[4], m5 = m[5], m6 = m[6], m8 = m[8], m9 = m[9], m10 = m[10];
        m[0] = m0 * a + m1 * d + m2 * g;  m[1] = m0 * b + m1 * e + m2 * h;  m[2] = m0 * c + m1 * f + m2 * i;
        m[4] = m4 * a + m5 * d + m6 * g;  m[5] = m4 * b + m5 * e + m6 * h;  m[6] = m4 * c + m5 * f + m6 * i;
        m[8] = m8 * a + m9 * d + m10 * g; m[9] = m8 * b + m9 * e + m10 * h; m[10] = m8 * c + m9 * f + m10 * i;
    }

    /** Кубик от (x0,y0,z0) до (x1,y1,z1) в текущей системе координат. */
    public Voxel box(double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        double[] c = new double[24];
        for (int k = 0; k < 8; k++) {
            double x = (k & 1) != 0 ? x1 : x0, y = (k & 2) != 0 ? y1 : y0, z = (k & 4) != 0 ? z1 : z0;
            c[k * 3] = m[0] * x + m[1] * y + m[2] * z + m[3];
            c[k * 3 + 1] = m[4] * x + m[5] * y + m[6] * z + m[7];
            c[k * 3 + 2] = m[8] * x + m[9] * y + m[10] * z + m[11];
        }
        boxes.add(c);
        colors.add(new int[]{argb});
        return this;
    }

    /** То же, но X зеркалится для стороны side (−1 = левая): удобно для пар крыльев/ушей. */
    public Voxel boxS(int side, double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        return side >= 0 ? box(x0, y0, z0, x1, y1, z1, argb) : box(-x1, y0, z0, -x0, y1, z1, argb);
    }

    /**
     * Нарисовать модель в мире.
     * @param ox,oy,oz точка «ног» тела в мире
     * @param yawDeg   поворот тела (как bodyYaw Minecraft: 0 = смотрит на +Z)
     * @param unit     сколько блоков в 1 px модели (1/16 для масштаба игрока)
     * @param alpha    общая прозрачность
     */
    public void render(long vg, double ox, double oy, double oz, double yawDeg, double unit, float alpha) {
        int n = boxes.size();
        if (n == 0) return;
        double yaw = Math.toRadians(yawDeg);
        // как у камеры Minecraft: yaw 0 → вперёд +Z, вправо −X
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // вперёд
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // вправо
        double bx = -fx, bz = -fz;                         // назад

        if (scr.length < n * 8) { scr = new float[n * 8][2]; world = new double[n * 8][3]; }
        if (depth.length < n) { depth = new double[n]; order = new Integer[n]; }

        int cnt = 0;
        double cx = Projection.camX(), cy = Projection.camY(), cz = Projection.camZ();
        for (int b = 0; b < n; b++) {
            double[] c = boxes.get(b);
            boolean ok = true;
            double dsum = 0;
            for (int k = 0; k < 8; k++) {
                double x = c[k * 3] * unit, y = c[k * 3 + 1] * unit, z = c[k * 3 + 2] * unit;
                double wx = ox + rx * x + bx * z, wy = oy + y, wz = oz + rz * x + bz * z;
                double[] w = world[b * 8 + k];
                w[0] = wx; w[1] = wy; w[2] = wz;
                if (!Projection.project(wx, wy, wz, tmp)) { ok = false; break; }
                scr[b * 8 + k][0] = tmp[0];
                scr[b * 8 + k][1] = tmp[1];
                dsum += tmp[2];
            }
            if (ok) { depth[b] = dsum / 8; order[cnt++] = b; }
        }
        Arrays.sort(order, 0, cnt, (a, b2) -> Double.compare(depth[b2], depth[a]));

        for (int q = 0; q < cnt; q++) {
            int b = order[q];
            int argb = colors.get(b)[0];
            float a = ((argb >>> 24) / 255f) * alpha;
            if (a <= 0.01f) continue;
            // центр кубика
            double ocx = 0, ocy = 0, ocz = 0;
            for (int k = 0; k < 8; k++) { double[] w = world[b * 8 + k]; ocx += w[0]; ocy += w[1]; ocz += w[2]; }
            ocx /= 8; ocy /= 8; ocz /= 8;
            for (int[] f : FACES) {
                double fcx = 0, fcy = 0, fcz = 0;
                for (int k : f) { double[] w = world[b * 8 + k]; fcx += w[0]; fcy += w[1]; fcz += w[2]; }
                fcx /= 4; fcy /= 4; fcz /= 4;
                double nx = fcx - ocx, ny = fcy - ocy, nz = fcz - ocz;
                // грань видна, если её нормаль смотрит на камеру
                if (nx * (fcx - cx) + ny * (fcy - cy) + nz * (fcz - cz) >= 0) continue;
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl < 1e-9) continue;
                double lit = (nx * LX + ny * LY + nz * LZ) / (nl * LN);
                float shade = (float) (0.62 + 0.38 * Math.max(0, lit) + (ny > 0 ? 0.06 : 0));
                int col = shade(argb, Math.min(1.08f, shade));
                nvgBeginPath(vg);
                nvgMoveTo(vg, scr[b * 8 + f[0]][0], scr[b * 8 + f[0]][1]);
                for (int i = 1; i < 4; i++) nvgLineTo(vg, scr[b * 8 + f[i]][0], scr[b * 8 + f[i]][1]);
                nvgClosePath(vg);
                nvgFillColor(vg, Draw.c1(col, a));
                nvgFill(vg);
                // тонкая обводка тем же цветом закрывает щели сглаживания между гранями
                if (a > 0.9f) {
                    nvgStrokeWidth(vg, 0.6f);
                    nvgStrokeColor(vg, Draw.c1(col, a));
                    nvgStroke(vg);
                }
            }
        }
    }

    private static int shade(int argb, float k) {
        int r = Math.min(255, (int) (((argb >> 16) & 255) * k));
        int g = Math.min(255, (int) (((argb >> 8) & 255) * k));
        int b = Math.min(255, (int) ((argb & 255) * k));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Подмешать к цвету tint (0 — без изменений, 1 — полностью tint), альфу сохранить. */
    public static int tint(int argb, int tint, float k) {
        if (k <= 0f) return argb;
        int a = argb & 0xFF000000;
        return a | (Draw.lerpColor(argb | 0xFF000000, tint | 0xFF000000, k) & 0x00FFFFFF);
    }

    /** Манекен игрока (серый) для превью: чтобы было видно, где сидит косметика. */
    public Voxel mannequin() {
        int leg = 0xFF4C5664, body = 0xFF667385, arm = 0xFF5A6677, head = 0xFF7C8898;
        box(-4, 0, -2, 0, 12, 2, leg).box(0, 0, -2, 4, 12, 2, leg);
        box(-4, 12, -2, 4, 24, 2, body);
        box(-8, 12, -2, -4, 24, 2, arm).box(4, 12, -2, 8, 24, 2, arm);
        box(-4, 24, -4, 4, 32, 4, head);
        return this;
    }
}
