package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.Quality;
import dev.vespera.client.ui.Anim;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Iterator;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Jump Circles — эффекты на земле под ногами при прыжке.
 *
 * Стили (идеи собраны по популярным визуал-модам, код свой):
 *  0 Неон         — одно светящееся кольцо: свечение + цветной слой + белая сердцевина, бегущий блик;
 *  1 Двойное      — внешнее кольцо и внутреннее, которое догоняет с задержкой;
 *  2 Ударная волна — залитая полоса, раскрывающаяся от центра (ярче по внешнему краю);
 *  3 Градиент     — кольцо переливается между двумя цветами по кругу;
 *  4 Радуга       — кольцо всех цветов, которое вращается;
 *  5 Шестиугольник — неоновый шестиугольник вместо круга;
 *  6 Искры        — кольцо + искры, разлетающиеся наружу и вверх.
 *
 * Толщина задаётся в блоках и переводится в пиксели с учётом перспективы,
 * поэтому кольцо выглядит как настоящий объект на земле.
 */
public final class JumpCirclesModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Стиль", 0,
            "Неон", "Двойное", "Волна", "Градиент", "Радуга", "Шестиугольник", "Искры")
            .hints("Одно неоновое кольцо с мягким свечением и бегущим бликом",
                    "Внешнее кольцо и внутреннее, которое догоняет его",
                    "Залитая светящаяся полоса, раскрывающаяся от центра",
                    "Кольцо переливается между «Цветом» и «Вторым цветом»",
                    "Вращающееся кольцо всех цветов радуги",
                    "Неоновый шестиугольник вместо круга",
                    "Кольцо и искры, разлетающиеся наружу и вверх"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFF7FE3FF));
    private final ColorSetting color2 = setting(new ColorSetting("color2", "Второй цвет", 0xFFB07CFF));
    private final NumberSetting radius = setting(new NumberSetting("radius", "Размер", 1.1f, 0.4f, 3f, 0.1f));
    private final NumberSetting count = setting(new NumberSetting("count", "Колец за прыжок", 1, 1, 3, 1));
    private final NumberSetting thick = setting(new NumberSetting("thick", "Толщина", 1.0f, 0.4f, 2.5f, 0.1f));
    private final NumberSetting bright = setting(new NumberSetting("bright", "Яркость", 0.95f, 0.2f, 1f, 0.05f));
    private final NumberSetting lifeSec = setting(new NumberSetting("life", "Время жизни (сек)", 1.1f, 0.4f, 3f, 0.1f));
    private final BoolSetting highlight = setting(new BoolSetting("highlight", "Бегущий блик", true));

    private static final int SEG = 96;
    private static final int SPARKS = 16;

    /** {x, y, z, рождение мс, seed, рождение (nanoTime, мс)} */
    private final ArrayDeque<double[]> rings = new ArrayDeque<>();
    private boolean wasOnGround = true;
    private final float[] sx = new float[SEG + 1], sy = new float[SEG + 1], sd = new float[SEG + 1];
    private final float[] ix = new float[SEG + 1], iy = new float[SEG + 1];
    private final boolean[] ok = new boolean[SEG + 1], iok = new boolean[SEG + 1];
    private final int[] segColor = new int[SEG];
    private final float[] tmp = new float[3];

    public JumpCirclesModule() {
        super("Jump Circles", "Эффекты на земле под ногами при прыжке", Category.VISUALS);
        about("Когда прыгаешь, на земле под ногами появляется эффект: неоновое кольцо, двойное, "
                + "ударная волна, градиент, радуга, шестиугольник или искры. Затем он плавно гаснет.");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    protected void onDisable() {
        rings.clear();
        wasOnGround = true;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        boolean ground = mc.player.isOnGround();
        if (wasOnGround && !ground && mc.player.getVelocity().y > 0.05) {
            for (int i = 0; i < (int) count.get(); i++)
                rings.add(new double[]{mc.player.getX(), mc.player.getY() + 0.03, mc.player.getZ(),
                        now + i * 140, Math.random() * 1000, System.nanoTime() / 1e6 + i * 140});
            while (rings.size() > 9) rings.pollFirst();
        }
        wasOnGround = ground;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        if (!Projection.isReady()) return;
        long now = System.currentTimeMillis();
        long life = (long) (lifeSec.get() * 1000);
        int st = style.index();
        for (Iterator<double[]> it = rings.iterator(); it.hasNext(); ) {
            double[] r = it.next();
            long age = now - (long) r[3];
            if (age > life + 50) { it.remove(); continue; }
            if (age < 0 || age > life) continue;
            // время с точностью до долей миллисекунды — без «ступенек» на высоком FPS
            float t = (float) Math.min(1.0, Math.max(0.0, (System.nanoTime() / 1e6 - r[5]) / life));
            float e = easeOutQuint(t);
            double rad = 0.35 + (radius.get() - 0.35) * e;
            // мягкое появление и долгое плавное угасание (smoothstep с обеих сторон)
            float a = bright.get() * smooth(0f, 0.10f, t) * (1f - smooth(0.35f, 1f, t));
            double phase = highlight.on() ? now / 1000.0 * 2.2 + r[4] : 0.8;
            float time = now / 1000f;

            switch (st) {
                case 1 -> { // двойное: внутреннее догоняет внешнее
                    neon(vg, r, rad, a, phase, false, 0, time);
                    double inner = 0.25 + (radius.get() * 0.62 - 0.25) * easeOutQuint(Math.max(0f, t - 0.12f) / 0.88f);
                    neon(vg, r, inner, a * 0.9f, phase + Math.PI, false, 0, time);
                }
                case 2 -> shockwave(vg, r, rad, a, e);
                case 3 -> neon(vg, r, rad, a, phase, false, 1, time);
                case 4 -> neon(vg, r, rad, a, phase, false, 2, time);
                case 5 -> neon(vg, r, rad, a, phase, true, 0, time);
                case 6 -> {
                    neon(vg, r, rad, a, phase, false, 0, time);
                    sparks(vg, r, t, a);
                }
                default -> neon(vg, r, rad, a, phase, false, 0, time);
            }
        }
    }

    // ------------------------------------------------------------ кольцо

    /** Неоновое кольцо. paint: 0 — один цвет, 1 — градиент двух цветов, 2 — радуга. */
    private void neon(long vg, double[] r, double rad, float a, double phase, boolean hex, int paint, float time) {
        project(r[0], r[1], r[2], rad, hex, sx, sy, sd, ok);
        int c = color.argb(), c2 = color2.argb();
        for (int i = 0; i < SEG; i++) {
            double ang = 2 * Math.PI * (i + 0.5) / SEG;
            segColor[i] = switch (paint) {
                case 1 -> Draw.lerpColor(c, c2, (float) (0.5 + 0.5 * Math.sin(ang + phase)));
                case 2 -> Draw.hue((float) (ang / (2 * Math.PI)) + time * 0.25f);
                default -> c;
            };
        }
        float th = thick.get();
        float hl = paint == 2 ? 0.3f : 1f; // у радуги блик слабее, чтобы цвета не гасли
        nvgLineCap(vg, NVG_ROUND);
        // широкие ореолы — только если качество позволяет (самые дорогие проходы)
        if (Quality.glow()) pass(vg, 0.16 * th, false, a * 0.10f, phase, 0.55f * hl);
        pass(vg, 0.08 * th, false, a * 0.28f, phase, 0.65f * hl);
        pass(vg, 0.035 * th, false, a * 0.85f, phase, 0.75f * hl);
        pass(vg, 0.012 * th, true, a * 0.95f, phase, 0.85f * hl);
    }

    /** Точки контура на земле (круг или шестиугольник). */
    private void project(double x, double y, double z, double rad, boolean hex,
                         float[] ox, float[] oy, float[] od, boolean[] okArr) {
        for (int i = 0; i <= SEG; i++) {
            double ang = 2 * Math.PI * i / SEG;
            double rr = rad;
            if (hex) { // радиус шестиугольника по углу
                double k = Math.PI / 3;
                double local = ((ang % k) + k) % k - k / 2;
                rr = rad * Math.cos(k / 2) / Math.cos(local);
            }
            okArr[i] = Projection.project(x + Math.cos(ang) * rr, y, z + Math.sin(ang) * rr, tmp);
            ox[i] = tmp[0];
            oy[i] = tmp[1];
            if (od != null) od[i] = tmp[2];
        }
    }

    /**
     * Один проход кольца: толщина с перспективой, яркость с бликом; core — белая сердцевина.
     * Соседние сегменты склеиваются в один штрих (по {@link Quality#chunk()}): на «Высоком»
     * это в 2 раза меньше вызовов отрисовки, на «Низком» — в 4, а на глаз разницы почти нет.
     */
    private void pass(long vg, double widthBlocks, boolean core, float alpha, double phase, float hl) {
        if (alpha <= 0.004f) return;
        int step = Quality.chunk();
        for (int i = 0; i < SEG; i += step) {
            int end = Math.min(SEG, i + step);
            if (!ok[i]) continue;
            int mid = Math.min(SEG - 1, (i + end) / 2);
            double ang = 2 * Math.PI * (mid + 0.5) / SEG;
            float k = (float) (0.5 + 0.5 * Math.cos(ang - phase));
            float segA = alpha * ((1f - hl) + hl * (0.25f + 0.75f * k * k));
            float px = Projection.pixelsPerBlock((sd[i] + sd[end]) / 2f);
            int col = core ? Draw.lerpColor(segColor[mid], 0xFFFFFFFF, 0.7f) : segColor[mid];
            nvgBeginPath(vg);
            nvgMoveTo(vg, sx[i], sy[i]);
            int drawn = 0;
            for (int j = i + 1; j <= end; j++) {
                if (!ok[j]) break;
                nvgLineTo(vg, sx[j], sy[j]);
                drawn++;
            }
            if (drawn == 0) continue;
            nvgStrokeWidth(vg, Math.max(1f, (float) widthBlocks * px));
            nvgStrokeColor(vg, Draw.c1(col, segA));
            nvgStroke(vg);
        }
    }

    // ------------------------------------------------------------ ударная волна

    /** Залитое кольцо между внутренним и внешним радиусом: прозрачно внутри, ярко по краю. */
    private void shockwave(long vg, double[] r, double rad, float a, float e) {
        double inner = rad * (0.35 + 0.45 * e); // полоса сужается по мере расхождения
        project(r[0], r[1], r[2], rad, false, sx, sy, sd, ok);
        project(r[0], r[1], r[2], inner, false, ix, iy, null, iok);
        int c = color.argb();
        for (int i = 0; i < SEG; i++) {
            if (!ok[i] || !ok[i + 1] || !iok[i] || !iok[i + 1]) continue;
            float ox = (sx[i] + sx[i + 1]) / 2f, oy = (sy[i] + sy[i + 1]) / 2f;
            float qx = (ix[i] + ix[i + 1]) / 2f, qy = (iy[i] + iy[i + 1]) / 2f;
            if (Math.abs(ox - qx) + Math.abs(oy - qy) < 0.5f) qy += 0.5f;
            nvgLinearGradient(vg, qx, qy, ox, oy, Draw.c1(c, 0f), Draw.c2(c, a * 0.55f),
                    dev.vespera.client.render.WorldDraw.paint());
            nvgBeginPath(vg);
            nvgMoveTo(vg, ix[i], iy[i]);
            nvgLineTo(vg, sx[i], sy[i]);
            nvgLineTo(vg, sx[i + 1], sy[i + 1]);
            nvgLineTo(vg, ix[i + 1], iy[i + 1]);
            nvgClosePath(vg);
            nvgFillPaint(vg, dev.vespera.client.render.WorldDraw.paint());
            nvgFill(vg);
        }
        // тонкая яркая кромка по внешнему краю
        for (int i = 0; i < SEG; i++) segColor[i] = c;
        pass(vg, 0.03 * thick.get(), true, a * 0.9f, 0, 0f);
    }

    // ------------------------------------------------------------ искры

    private void sparks(long vg, double[] r, float t, float a) {
        double seed = r[4];
        double maxR = radius.get();
        int c = color.argb();
        for (int i = 0; i < SPARKS; i++) {
            double rnd = frac(Math.sin((seed + i) * 12.9898) * 43758.5453);
            double rnd2 = frac(Math.sin((seed + i) * 78.233) * 12345.678);
            double ang = 2 * Math.PI * (i + rnd * 0.6) / SPARKS;
            double dist = maxR * (0.4 + 0.9 * easeOutQuint(t)) * (0.8 + 0.4 * rnd2);
            double up = (0.15 + 0.6 * rnd) * Math.sin(Math.PI * Math.min(1f, t * 1.2f)); // вверх и вниз
            if (!Projection.project(r[0] + Math.cos(ang) * dist, r[1] + up, r[2] + Math.sin(ang) * dist, tmp)) continue;
            float px = Projection.pixelsPerBlock(tmp[2]);
            float sz = Math.max(1f, (float) (0.035 * (1 - smooth(0.2f, 1f, t) * 0.7)) * px);
            if (Quality.glow()) Draw.circle(vg, tmp[0], tmp[1], sz * 3f, c, a * 0.15f);
            Draw.circle(vg, tmp[0], tmp[1], sz, Draw.lerpColor(c, 0xFFFFFFFF, 0.6f), a);
        }
    }

    private static float easeOutQuint(float t) {
        float u = 1f - t;
        return 1f - u * u * u * u * u;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }
}
