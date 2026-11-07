package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Projection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.Random;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Night Sky — северное сияние и звёздное небо ночью (как в шейдерах, но без шейдеров).
 *
 * Сияние — несколько «занавесов» на куполе неба: вертикальные полосы с градиентом
 * (яркий низ → прозрачный верх), которые медленно колышутся и мерцают лучами.
 * Звёзды — сотни точек разной яркости, мерцают, самые яркие с ореолом.
 *
 * NanoVG рисует поверх кадра, поэтому, чтобы сияние не лезло на горы, деревья и потолок,
 * для каждой полосы и звезды проверяем луч от камеры (raycast): если по пути блок — не рисуем.
 * Направления неподвижны в мире, значит проверка зависит только от позиции камеры —
 * пересчитываем её по кусочку каждый кадр.
 */
public final class NightSkyModule extends HudModule {

    private final ChoiceSetting palette = setting(new ChoiceSetting("palette", "Цвет сияния", 0, "Фиолетовое", "Зелёное", "Розовое", "Радужное")
            .hints("Фиолетово-розовое, как на скриншоте", "Классическое зелёное северное сияние",
                    "Розово-малиновое", "Переливается разными цветами"));
    private final BoolSetting aurora = setting(new BoolSetting("aurora", "Северное сияние", true));
    private final BoolSetting stars = setting(new BoolSetting("stars", "Яркие звёзды", true));
    private final NumberSetting intensity = setting(new NumberSetting("intensity", "Яркость", 0.8f, 0.2f, 1.5f, 0.05f));
    private final NumberSetting starCount = setting(new NumberSetting("count", "Звёзд", 260, 60, 500, 20));
    private final BoolSetting always = setting(new BoolSetting("always", "Видно и днём (слабо)", false));

    private static final int CURTAINS = 3, COLS = 46, MAX_STARS = 500;
    private static final double DOME = 100.0;

    /** Направления столбцов сияния: [curtain][col] → {азимут, низ, верх (радианы)} — база, без колыхания. */
    private final double[][][] col = new double[CURTAINS][COLS][3];
    private final float[][] colVis = new float[CURTAINS][COLS];      // 0..1 — видно ли небо в эту сторону
    private final double[][] star = new double[MAX_STARS][4];        // dx, dy, dz, яркость
    private final float[] starVis = new float[MAX_STARS];
    private int cursor;
    private Vec3d lastCam = Vec3d.ZERO;
    private final float[] a = new float[3], b = new float[3];

    public NightSkyModule() {
        super("Night Sky", "Северное сияние и звёздное небо ночью", Category.VISUALS);
        about("Ночью на небе появляется северное сияние (фиолетовое, зелёное, розовое или радужное) "
                + "и много мерцающих звёзд. Не рисуется на блоках — только там, где видно небо.");
        Random r = new Random(1337);
        for (int c = 0; c < CURTAINS; c++) {
            double start = r.nextDouble() * Math.PI * 2;
            double span = Math.toRadians(70 + r.nextDouble() * 50);
            double low = Math.toRadians(12 + c * 7 + r.nextDouble() * 6);
            for (int i = 0; i < COLS; i++) {
                col[c][i][0] = start + span * i / (COLS - 1);
                col[c][i][1] = low;
                col[c][i][2] = low + Math.toRadians(18 + r.nextDouble() * 10);
            }
        }
        for (int s = 0; s < MAX_STARS; s++) {
            double az = r.nextDouble() * Math.PI * 2;
            double el = Math.asin(0.05 + r.nextDouble() * 0.95);    // равномерно по куполу, выше горизонта
            star[s][0] = Math.cos(el) * Math.cos(az);
            star[s][1] = Math.sin(el);
            star[s][2] = Math.cos(el) * Math.sin(az);
            star[s][3] = Math.pow(r.nextDouble(), 3);               // мало ярких, много тусклых
        }
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    /** 0 днём → 1 глубокой ночью (с мягкими сумерками). */
    private float night(MinecraftClient mc) {
        long t = Math.floorMod(mc.world.getTimeOfDay(), 24000L);
        float n;
        if (t >= 13000 && t <= 23000) n = 1f;
        else if (t > 12000 && t < 13000) n = (t - 12000) / 1000f;
        else if (t > 23000) n = 1f - (t - 23000) / 1000f;
        else n = 0f;
        if (always.on()) n = Math.max(n, 0.25f);
        return n * (1f - mc.world.getRainGradient(1f));
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || !Projection.isReady()) return;
        if (mc.world.getRegistryKey() != World.OVERWORLD) return;
        float nf = night(mc);
        if (nf <= 0.01f) return;

        Vec3d cam = new Vec3d(Projection.camX(), Projection.camY(), Projection.camZ());
        if (cam.squaredDistanceTo(lastCam) > 4) { // сильно сместились — пересчитать всё сразу «грубо»: сброс курсора
            cursor = 0;
            lastCam = cam;
        }
        updateVisibility(mc, cam, 70);

        double time = System.currentTimeMillis() / 1000.0;
        float k = intensity.get() * nf;
        // свечение складывается (аддитивно) — как настоящий свет на тёмном небе
        nvgGlobalCompositeBlendFunc(vg, NVG_SRC_ALPHA, NVG_ONE);
        try {
            if (stars.on()) drawStars(vg, cam, time, k);
            if (aurora.on()) drawAurora(vg, cam, time, k);
        } finally {
            nvgGlobalCompositeOperation(vg, NVG_SOURCE_OVER);
        }
    }

    // ------------------------------------------------------------ видимость неба

    /** Пересчитываем budget лучей за кадр по кругу: сначала столбцы сияния, потом звёзды. */
    private void updateVisibility(MinecraftClient mc, Vec3d cam, int budget) {
        int total = CURTAINS * COLS + MAX_STARS;
        for (int n = 0; n < budget; n++) {
            int i = cursor;
            cursor = (cursor + 1) % total;
            if (i < CURTAINS * COLS) {
                int c = i / COLS, j = i % COLS;
                double az = col[c][j][0], el = col[c][j][1];
                boolean open = clear(mc, cam, Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az));
                colVis[c][j] = open ? 1f : 0f;
            } else {
                int s = i - CURTAINS * COLS;
                starVis[s] = clear(mc, cam, star[s][0], star[s][1], star[s][2]) ? 1f : 0f;
            }
        }
    }

    private boolean clear(MinecraftClient mc, Vec3d cam, double dx, double dy, double dz) {
        Vec3d end = cam.add(dx * 160, dy * 160, dz * 160);
        HitResult hr = mc.world.raycast(new RaycastContext(cam, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player));
        return hr.getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------ звёзды

    private void drawStars(long vg, Vec3d cam, double time, float k) {
        int n = (int) starCount.get();
        for (int s = 0; s < n; s++) {
            if (starVis[s] <= 0f) continue;
            double[] st = star[s];
            if (!Projection.project(cam.x + st[0] * DOME, cam.y + st[1] * DOME, cam.z + st[2] * DOME, a)) continue;
            float tw = (float) (0.55 + 0.45 * Math.sin(time * (1.3 + st[3] * 2) + s * 1.7));
            float br = (float) (0.25 + 0.75 * st[3]) * tw * Math.min(1f, k);
            float r = 0.7f + (float) st[3] * 1.6f;
            if (st[3] > 0.6) { // яркие — с ореолом и лучиками
                Draw.circle(vg, a[0], a[1], r * 4.5f, 0xFFDCE6FF, br * 0.12f);
                nvgBeginPath(vg);
                nvgMoveTo(vg, a[0] - r * 5, a[1]); nvgLineTo(vg, a[0] + r * 5, a[1]);
                nvgMoveTo(vg, a[0], a[1] - r * 5); nvgLineTo(vg, a[0], a[1] + r * 5);
                nvgStrokeWidth(vg, 0.8f);
                nvgStrokeColor(vg, Draw.c1(0xFFFFFFFF, br * 0.35f));
                nvgStroke(vg);
            }
            Draw.circle(vg, a[0], a[1], r, 0xFFFFFFFF, br);
        }
    }

    // ------------------------------------------------------------ сияние

    private int[] colors(double time, double u) {
        return switch (palette.index()) {
            case 1 -> new int[]{0xFF3DFFB0, 0xFF2BC8A0, 0xFF6A5BFF};
            case 2 -> new int[]{0xFFFF6FB4, 0xFFE0408A, 0xFF8A4DFF};
            case 3 -> new int[]{Draw.hue((float) (u * 0.6 + time * 0.03)), Draw.hue((float) (u * 0.6 + time * 0.03 + 0.15)), 0xFF5A4DFF};
            default -> new int[]{0xFFC86BFF, 0xFFFF5FA8, 0xFF5B4DFF};    // низ, середина, верх
        };
    }

    private void drawAurora(long vg, Vec3d cam, double time, float k) {
        for (int c = 0; c < CURTAINS; c++) {
            double sway = time * (0.07 + c * 0.02);
            for (int j = 0; j < COLS - 1; j++) {
                float vis = Math.min(colVis[c][j], colVis[c][j + 1]);
                if (vis <= 0f) continue;
                double u0 = j / (double) (COLS - 1), u1 = (j + 1) / (double) (COLS - 1);
                // края занавеса мягко гаснут
                float edge = (float) Math.min(1, Math.min(u0, 1 - u1) * 6 + 0.05);
                double[] p0 = point(c, j, sway, time), p1 = point(c, j + 1, sway, time);
                // мерцающие «лучи»: яркость полосы меняется по времени
                float ray = (float) (0.55 + 0.45 * Math.sin(time * 1.7 + j * 0.9 + c * 2.0)
                        * Math.sin(time * 0.6 + j * 0.37));
                float alpha = k * edge * ray * 0.55f;
                if (alpha <= 0.01f) continue;
                int[] cl = colors(time, u0 + c * 0.3);
                // нижняя половина: яркий низ → середина
                band(vg, cam, p0, p1, 0.0, 0.45, cl[0], alpha, cl[1], alpha * 0.75f);
                // верхняя половина: середина → прозрачный верх
                band(vg, cam, p0, p1, 0.45, 1.0, cl[1], alpha * 0.75f, cl[2], 0f);
            }
        }
    }

    /** Точка столбца с колыханием: {азимут, низ, верх}. */
    private double[] point(int c, int j, double sway, double time) {
        double[] base = col[c][j];
        double az = base[0] + Math.sin(sway + j * 0.22) * 0.05;
        double lo = base[1] + Math.sin(time * 0.3 + j * 0.31 + c) * 0.025;
        double hi = base[2] + Math.sin(time * 0.45 + j * 0.27 + c * 1.7) * 0.06;
        return new double[]{az, lo, hi};
    }

    /** Кусок полосы между долями высоты f0..f1 (0 = низ, 1 = верх) с вертикальным градиентом. */
    private void band(long vg, Vec3d cam, double[] p0, double[] p1, double f0, double f1,
                      int cBot, float aBot, int cTop, float aTop) {
        double e00 = p0[1] + (p0[2] - p0[1]) * f0, e01 = p0[1] + (p0[2] - p0[1]) * f1;
        double e10 = p1[1] + (p1[2] - p1[1]) * f0, e11 = p1[1] + (p1[2] - p1[1]) * f1;
        float[] q = new float[8];
        if (!dir(cam, p0[0], e00, q, 0) || !dir(cam, p1[0], e10, q, 2)
                || !dir(cam, p1[0], e11, q, 4) || !dir(cam, p0[0], e01, q, 6)) return;
        float bx = (q[0] + q[2]) / 2, by = (q[1] + q[3]) / 2, tx = (q[4] + q[6]) / 2, ty = (q[5] + q[7]) / 2;
        if (Math.abs(bx - tx) + Math.abs(by - ty) < 0.5f) ty -= 0.5f;
        nvgLinearGradient(vg, bx, by, tx, ty, Draw.c1(cBot, aBot), Draw.c2(cTop, aTop),
                dev.vespera.client.render.WorldDraw.paint());
        nvgBeginPath(vg);
        nvgMoveTo(vg, q[0], q[1]);
        nvgLineTo(vg, q[2], q[3]);
        nvgLineTo(vg, q[4], q[5]);
        nvgLineTo(vg, q[6], q[7]);
        nvgClosePath(vg);
        nvgFillPaint(vg, dev.vespera.client.render.WorldDraw.paint());
        nvgFill(vg);
    }

    private boolean dir(Vec3d cam, double az, double el, float[] q, int o) {
        double ce = Math.cos(el);
        if (!Projection.project(cam.x + ce * Math.cos(az) * DOME, cam.y + Math.sin(el) * DOME,
                cam.z + ce * Math.sin(az) * DOME, b)) return false;
        q[o] = b[0];
        q[o + 1] = b[1];
        return true;
    }
}
