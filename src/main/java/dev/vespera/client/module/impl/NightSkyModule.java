package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fx;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.Quality;
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
    private final NumberSetting starCount = setting(new NumberSetting("count", "Звёзд", 700, 100, 900, 50));
    private final BoolSetting nebula = setting(new BoolSetting("nebula", "Млечный путь", true));
    private final BoolSetting always = setting(new BoolSetting("always", "Видно и днём (слабо)", false));

    private static final int CURTAINS = 6, COLS = 40, MAX_STARS = 900, BLOBS = 26;
    private static final double DOME = 100.0;

    /** Направления столбцов сияния: [curtain][col] → {азимут, низ, верх (радианы)} — база, без колыхания. */
    private final double[][][] col = new double[CURTAINS][COLS][3];
    private final float[][] colVis = new float[CURTAINS][COLS];      // 0..1 — видно ли небо в эту сторону (плавно)
    private final float[][] colTgt = new float[CURTAINS][COLS];      // последний результат луча (0 или 1)
    private final double[][] star = new double[MAX_STARS][4];        // dx, dy, dz, яркость
    private final float[] starVis = new float[MAX_STARS];
    private final float[] starTgt = new float[MAX_STARS];
    private final double[][] blob = new double[BLOBS][5];           // dx, dy, dz, радиус (рад), оттенок
    private final float[] blobVis = new float[BLOBS];
    private final float[] blobTgt = new float[BLOBS];
    /** Сглаженная «ночность» — чтобы небо не мигало, если время на кадр прыгнуло. */
    private float nightSmooth = -1f;
    private final double[][] rayH = new double[CURTAINS][COLS];      // высота лучей (доля)
    private int cursor;
    private Vec3d lastCam = Vec3d.ZERO;
    private final float[] a = new float[3], b = new float[3];
    private final float[] rq = new float[8], bq = new float[8];
    private final Fx.Dots dots = new Fx.Dots();
    private final Fx.Lines lines = new Fx.Lines();

    public NightSkyModule() {
        super("Night Sky", "Северное сияние и звёздное небо ночью", Category.VISUALS);
        about("Ночью на небе появляется северное сияние (фиолетовое, зелёное, розовое или радужное) "
                + "и много мерцающих звёзд. Не рисуется на блоках — только там, где видно небо.");
        // До первого луча считаем, что небо видно — иначе первые секунды всё «включается» кусками
        for (float[] row : colVis) java.util.Arrays.fill(row, 1f);
        for (float[] row : colTgt) java.util.Arrays.fill(row, 1f);
        java.util.Arrays.fill(starVis, 1f);
        java.util.Arrays.fill(starTgt, 1f);
        java.util.Arrays.fill(blobVis, 1f);
        java.util.Arrays.fill(blobTgt, 1f);
        Random r = new Random(1337);
        for (int c = 0; c < CURTAINS; c++) {
            // занавесы равномерно по всему горизонту, чтобы сияние было «вокруг»
            double start = c * (Math.PI * 2 / CURTAINS) + r.nextDouble() * 0.5;
            double span = Math.toRadians(55 + r.nextDouble() * 30);
            double low = Math.toRadians(3 + r.nextDouble() * 10);
            for (int i = 0; i < COLS; i++) {
                col[c][i][0] = start + span * i / (COLS - 1);
                col[c][i][1] = low + Math.sin(i * 0.35 + c) * Math.toRadians(2.5);
                col[c][i][2] = low + Math.toRadians(22 + r.nextDouble() * 14);
                rayH[c][i] = 0.45 + r.nextDouble() * 0.55;
            }
        }
        for (int s2 = 0; s2 < MAX_STARS; s2++) {
            double az = r.nextDouble() * Math.PI * 2;
            double el = Math.asin(0.03 + r.nextDouble() * 0.97);    // равномерно по куполу, выше горизонта
            star[s2][0] = Math.cos(el) * Math.cos(az);
            star[s2][1] = Math.sin(el);
            star[s2][2] = Math.cos(el) * Math.sin(az);
            star[s2][3] = Math.pow(r.nextDouble(), 4);              // мало ярких, очень много мелких
        }
        // «Млечный путь»: облачка вдоль наклонной полосы через всё небо
        double tilt = Math.toRadians(62);
        for (int i = 0; i < BLOBS; i++) {
            double t = i / (double) BLOBS * Math.PI;                 // полукруг над головой
            double x = Math.cos(t), y = Math.sin(t) * Math.cos(tilt), z = Math.sin(t) * Math.sin(tilt);
            double jit = (r.nextDouble() - 0.5) * 0.18;
            double len = Math.sqrt(x * x + (y + jit) * (y + jit) + z * z);
            blob[i][0] = x / len; blob[i][1] = Math.abs((y + jit) / len); blob[i][2] = z / len;
            blob[i][3] = Math.toRadians(7 + r.nextDouble() * 9);
            blob[i][4] = r.nextDouble();
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
        // Плавно догоняем целевую «ночность» (~0.7 с): один кадр с дневным временем больше не даёт вспышку
        float target = night(mc);
        if (nightSmooth < 0f) nightSmooth = target;
        nightSmooth += (target - nightSmooth) * Math.min(1f, dt * 1.5f);
        float nf = nightSmooth;
        if (nf <= 0.01f) return;

        Vec3d cam = new Vec3d(Projection.camX(), Projection.camY(), Projection.camZ());
        if (cam.squaredDistanceTo(lastCam) > 4) { // сильно сместились — пересчитать всё сразу «грубо»: сброс курсора
            cursor = 0;
            lastCam = cam;
        }
        updateVisibility(mc, cam, Math.round(40 + 40 * Quality.level()));
        smoothVisibility(dt);

        double time = System.currentTimeMillis() / 1000.0;
        float k = intensity.get() * nf;
        // свечение складывается (аддитивно) — как настоящий свет на тёмном небе
        nvgGlobalCompositeBlendFunc(vg, NVG_SRC_ALPHA, NVG_ONE);
        try {
            if (nebula.on()) drawNebula(vg, cam, k);
            if (stars.on()) drawStars(vg, cam, time, k);
            if (aurora.on()) drawAurora(vg, cam, time, k);
        } finally {
            nvgGlobalCompositeOperation(vg, NVG_SOURCE_OVER);
        }
    }

    // ------------------------------------------------------------ видимость неба

    /** Пересчитываем budget лучей за кадр по кругу: сначала столбцы сияния, потом звёзды. */
    private void updateVisibility(MinecraftClient mc, Vec3d cam, int budget) {
        int total = CURTAINS * COLS + MAX_STARS + BLOBS;
        for (int n = 0; n < budget; n++) {
            int i = cursor;
            cursor = (cursor + 1) % total;
            if (i < CURTAINS * COLS) {
                int c = i / COLS, j = i % COLS;
                double az = col[c][j][0], el = col[c][j][1];
                boolean open = clear(mc, cam, Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az));
                colTgt[c][j] = open ? 1f : 0f;
            } else if (i < CURTAINS * COLS + MAX_STARS) {
                int s = i - CURTAINS * COLS;
                starTgt[s] = clear(mc, cam, star[s][0], star[s][1], star[s][2]) ? 1f : 0f;
            } else {
                int q = i - CURTAINS * COLS - MAX_STARS;
                blobTgt[q] = clear(mc, cam, blob[q][0], blob[q][1], blob[q][2]) ? 1f : 0f;
            }
        }
    }

    /** Видимость меняется плавно (~0.25 с), а не 0/1 — иначе куски неба мигают при каждом пересчёте лучей. */
    private void smoothVisibility(float dt) {
        float k = Math.min(1f, dt * 4f);
        for (int c = 0; c < CURTAINS; c++)
            for (int j = 0; j < COLS; j++) colVis[c][j] += (colTgt[c][j] - colVis[c][j]) * k;
        for (int s = 0; s < MAX_STARS; s++) starVis[s] += (starTgt[s] - starVis[s]) * k;
        for (int q = 0; q < BLOBS; q++) blobVis[q] += (blobTgt[q] - blobVis[q]) * k;
    }

    private boolean clear(MinecraftClient mc, Vec3d cam, double dx, double dy, double dz) {
        Vec3d end = cam.add(dx * 160, dy * 160, dz * 160);
        HitResult hr = mc.world.raycast(new RaycastContext(cam, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player));
        return hr.getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------ звёзды

    /** Звёзды рисуются пакетами (Fx.Dots / Fx.Lines): ~20 вызовов отрисовки вместо 2000+. */
    private void drawStars(long vg, Vec3d cam, double time, float k) {
        int n = Math.min(MAX_STARS, Math.round(starCount.get() * Math.min(1f, Quality.scale())));
        boolean glow = Quality.glow();
        for (int s = 0; s < n; s++) {
            if (starVis[s] <= 0.02f) continue;
            double[] st = star[s];
            if (!Projection.project(cam.x + st[0] * DOME, cam.y + st[1] * DOME, cam.z + st[2] * DOME, a)) continue;
            float tw = (float) (0.55 + 0.45 * Math.sin(time * (1.3 + st[3] * 2) + s * 1.7));
            float br = (float) (0.25 + 0.75 * st[3]) * tw * Math.min(1f, k) * starVis[s];
            float r = 0.55f + (float) st[3] * 1.9f;
            if (st[3] > 0.6 && glow) { // яркие — с ореолом и лучиками
                dots.add(a[0], a[1], r * 4.5f, 0xFFDCE6FF, br * 0.12f);
                lines.add(a[0] - r * 5, a[1], a[0] + r * 5, a[1], 0.8f, 0xFFFFFFFF, br * 0.35f);
                lines.add(a[0], a[1] - r * 5, a[0], a[1] + r * 5, 0.8f, 0xFFFFFFFF, br * 0.35f);
            }
            dots.add(a[0], a[1], r, s % 5 == 0 ? 0xFFCFE0FF : 0xFFFFFFFF, br);
        }
        dots.flush(vg);
        lines.flush(vg);
    }

    /** Млечный путь: мягкие фиолетово-синие облачка (радиальный градиент). */
    private void drawNebula(long vg, Vec3d cam, float k) {
        for (int i = 0; i < BLOBS; i++) {
            if (blobVis[i] <= 0.02f) continue;
            double[] q = blob[i];
            if (!Projection.project(cam.x + q[0] * DOME, cam.y + q[1] * DOME, cam.z + q[2] * DOME, a)) continue;
            // радиус в пикселях: проецируем точку, смещённую на угол q[3] вверх
            double up = Math.min(1, q[1] + q[3]);
            double hz = Math.sqrt(Math.max(0, 1 - up * up)), hz0 = Math.sqrt(q[0] * q[0] + q[2] * q[2]);
            if (hz0 < 1e-6 || !Projection.project(cam.x + q[0] / hz0 * hz * DOME, cam.y + up * DOME,
                    cam.z + q[2] / hz0 * hz * DOME, b)) continue;
            float rad = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (rad < 2) continue;
            int c = Draw.lerpColor(0xFF6A3AB8, 0xFF3A4FB8, (float) q[4]);
            nvgRadialGradient(vg, a[0], a[1], rad * 0.1f, rad, Draw.c1(c, 0.16f * k * blobVis[i]), Draw.c2(c, 0f),
                    dev.vespera.client.render.WorldDraw.paint());
            nvgBeginPath(vg);
            nvgCircle(vg, a[0], a[1], rad);
            nvgFillPaint(vg, dev.vespera.client.render.WorldDraw.paint());
            nvgFill(vg);
        }
    }

    // ------------------------------------------------------------ сияние

    private int[] colors(double time, double u) {
        return switch (palette.index()) {
            case 1 -> new int[]{0xFF3DFFB0, 0xFF2BC8A0, 0xFF6A5BFF};
            case 2 -> new int[]{0xFFFF6FB4, 0xFFE0408A, 0xFF8A4DFF};
            case 3 -> new int[]{Draw.hue((float) (u * 0.6 + time * 0.03)), Draw.hue((float) (u * 0.6 + time * 0.03 + 0.15)), 0xFF5A4DFF};
            default -> new int[]{0xFFFF4FA3, 0xFFB45CFF, 0xFF4A55E8};    // низ малиновый, середина фиолет, верх синий
        };
    }

    private void drawAurora(long vg, Vec3d cam, double time, float k) {
        int step = Math.min(4, Quality.chunk()); // на низком качестве — шире полосы, меньше заливок
        boolean glow = Quality.glow();
        for (int c = 0; c < CURTAINS; c++) {
            double sway = time * (0.07 + c * 0.02);
            for (int j = 0; j < COLS - 1; j += step) {
                int j2 = Math.min(COLS - 1, j + step);
                float vis = Math.min(colVis[c][j], colVis[c][j2]);
                if (vis <= 0.02f) continue;
                double u0 = j / (double) (COLS - 1), u1 = j2 / (double) (COLS - 1);
                // края занавеса мягко гаснут
                float edge = (float) Math.min(1, Math.min(u0, 1 - u1) * 6 + 0.05);
                double[] p0 = point(c, j, sway, time), p1 = point(c, j2, sway, time);
                // мерцающие «лучи»: яркость полосы меняется по времени
                float ray = (float) (0.55 + 0.45 * Math.sin(time * 1.7 + j * 0.9 + c * 2.0)
                        * Math.sin(time * 0.6 + j * 0.37));
                float alpha = k * edge * ray * 0.55f * vis;
                if (alpha <= 0.01f) continue;
                int[] cl = colors(time, u0 + c * 0.3);
                // нижняя половина: яркий низ → середина
                band(vg, cam, p0, p1, 0.0, 0.45, cl[0], alpha, cl[1], alpha * 0.75f);
                // верхняя половина: середина → прозрачный верх
                band(vg, cam, p0, p1, 0.45, 1.0, cl[1], alpha * 0.75f, cl[2], 0f);
                // вертикальные лучи: тонкие яркие штрихи разной высоты (как на фото)
                if (glow && j % 2 == 0) rays(vg, cam, p0, p1, rayH[c][j], cl, alpha * 1.3f, time, c * 100 + j);
            }
        }
    }

    private void rays(long vg, Vec3d cam, double[] p0, double[] p1, double hk, int[] cl, float alpha, double time, int seed) {
        for (int n = 0; n < 3; n++) {
            double f = (n + 0.5) / 3.0;
            double az = p0[0] + (p1[0] - p0[0]) * f;
            double lo = p0[1] + (p1[1] - p0[1]) * f;
            double hi = p0[2] + (p1[2] - p0[2]) * f;
            double flick = 0.6 + 0.4 * Math.sin(time * (2.0 + n * 0.7) + seed * 1.3);
            double top = lo + (hi - lo) * hk * (0.75 + 0.25 * Math.sin(time * 0.8 + seed + n));
            float[] q = rq;
            if (!dir(cam, az, lo, q, 0) || !dir(cam, az, top, q, 2)) continue;
            if (Math.abs(q[1] - q[3]) < 1f) continue;
            nvgLinearGradient(vg, q[0], q[1], q[2], q[3], Draw.c1(Draw.lerpColor(cl[0], 0xFFFFFFFF, 0.25f),
                    (float) (alpha * flick)), Draw.c2(cl[1], 0f), dev.vespera.client.render.WorldDraw.paint());
            nvgBeginPath(vg);
            nvgMoveTo(vg, q[0], q[1]);
            nvgLineTo(vg, q[2], q[3]);
            nvgStrokeWidth(vg, 1.6f + n % 2);
            nvgStrokePaint(vg, dev.vespera.client.render.WorldDraw.paint());
            nvgStroke(vg);
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
        float[] q = bq;
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
