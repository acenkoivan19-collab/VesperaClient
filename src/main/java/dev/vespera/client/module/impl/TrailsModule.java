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
import dev.vespera.client.render.WorldDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Trails — шлейф за спиной. Стили (идеи из популярных визуал-модов, код свой):
 *  0 Занавес  — полупрозрачная вертикальная полоса от ног до плеч, растворяется к концу;
 *  1 Лента    — гладкая горизонтальная лента на уровне пояса;
 *  2 Линия    — светящаяся неоновая линия у пояса;
 *  3 Следы    — светящиеся пятна на земле там, где ты прошёл;
 *  4 Кольца   — кольца, которые поднимаются вверх над пройденным путём;
 *  5 Волны    — круги, расходящиеся по земле позади.
 * Палитра: один цвет / градиент «Цвет → Второй цвет» по длине / радуга.
 *
 * Последний участок тянется до текущей (интерполированной) позиции игрока —
 * шлейф растёт прямо из персонажа и не отстаёт.
 */
public final class TrailsModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Стиль", 0,
            "Занавес", "Лента", "Линия", "Следы", "Кольца", "Волны")
            .hints("Полупрозрачная полоса от ног до плеч, растворяется к концу",
                    "Гладкая горизонтальная лента на уровне пояса",
                    "Светящаяся неоновая линия у пояса",
                    "Светящиеся пятна на земле там, где ты прошёл",
                    "Кольца поднимаются вверх над пройденным путём",
                    "Круги расходятся по земле позади тебя"));
    private final ChoiceSetting palette = setting(new ChoiceSetting("palette", "Палитра", 0, "Один цвет", "Градиент", "Радуга")
            .hints("Весь шлейф цвета «Цвет»", "От «Цвета» у тебя до «Второго цвета» в конце",
                    "Переливается всеми цветами радуги"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFFD2E2FF));
    private final ColorSetting color2 = setting(new ColorSetting("color2", "Второй цвет", 0xFFB07CFF));
    private final NumberSetting length = setting(new NumberSetting("time", "Длина (сек)", 1.6f, 0.4f, 4f, 0.1f));
    private final NumberSetting height = setting(new NumberSetting("height", "Высота / ширина", 0.85f, 0.3f, 1.1f, 0.05f));
    private final NumberSetting opacity = setting(new NumberSetting("opacity", "Яркость", 0.55f, 0.1f, 1f, 0.05f));
    private final BoolSetting edge = setting(new BoolSetting("edge", "Светлая кромка (Занавес)", true));
    private final NumberSetting minSpeed = setting(new NumberSetting("speed", "С какой скорости (бл/с)", 1.0f, 0f, 6f, 0.5f));

    /** {x, yНог, z, время мс, рост} */
    private final ArrayDeque<double[]> trail = new ArrayDeque<>();
    private double lastX, lastY, lastZ;
    private long lastT;
    /** Минимальное расстояние между точками (блоки). */
    private static final double SPACING = 0.12;
    /** На сколько частей делим каждый участок при сглаживании. */
    /** Сколько промежуточных точек сплайна на отрезок — по качеству (3 / 2 / 1). */
    private static int subdiv() {
        float l = Quality.level();
        return l >= 1.6f ? 3 : l >= 0.7f ? 2 : 1;
    }
    /** Позиция игрока в этом кадре и с какого расстояния от него начинать шлейф. */
    private double curX, curZ, startDist;

    public TrailsModule() {
        super("Trails", "Шлейф за спиной: занавес, лента, линия, следы, кольца, волны", Category.VISUALS);
        about("Пока двигаешься, за тобой тянется шлейф. Стили: полупрозрачный занавес, лента, "
                + "неоновая линия, следы на земле, поднимающиеся кольца или волны. Видно от 3-го лица (F5).");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    protected void onDisable() {
        trail.clear();
        lastT = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) trail.clear();
    }

    /**
     * Точки ставим каждый КАДР по интерполированной позиции (а не раз в тик):
     * при 144+ FPS шлейф получается гладким, без «ступенек» в 20 тиков/сек.
     */
    private void sample(MinecraftClient mc, long now, double life) {
        while (!trail.isEmpty() && now - (long) trail.peekFirst()[3] > life) trail.pollFirst();
        Vec3d p = mc.player.getLerpedPos(Projection.tickProgress(mc));
        if (lastT > 0) {
            double dx = p.x - lastX, dy = p.y - lastY, dz = p.z - lastZ;
            double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (moved > 8) { trail.clear(); lastX = p.x; lastY = p.y; lastZ = p.z; lastT = now; return; }
            if (moved < SPACING) return;               // копим расстояние до следующей точки
            double sec = Math.max(1, now - lastT) / 1000.0;
            if (moved / sec < minSpeed.get()) { lastX = p.x; lastY = p.y; lastZ = p.z; lastT = now; return; }
        }
        trail.addLast(new double[]{p.x, p.y, p.z, now, mc.player.getHeight()});
        while (trail.size() > 260) trail.pollFirst();
        lastX = p.x;
        lastY = p.y;
        lastZ = p.z;
        lastT = now;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Projection.isReady() || mc.player == null) return;
        long now = System.currentTimeMillis();
        double life = length.get() * 1000.0;
        sample(mc, now, life);
        if (trail.isEmpty()) return;
        List<double[]> pts = new ArrayList<>(trail);
        int st = style.index();
        // где сейчас игрок и откуда начинать шлейф: от 1-го лица — только за спиной,
        // чтобы при взгляде вниз под ногами не было полосы
        Vec3d me = mc.player.getLerpedPos(Projection.tickProgress(mc));
        curX = me.x;
        curZ = me.z;
        startDist = mc.options.getPerspective().isFirstPerson() ? 1.1 : 0.3;
        if (st <= 2) { // сплошным стилям — хвост до текущей позиции
            Vec3d cur = mc.player.getLerpedPos(Projection.tickProgress(mc));
            pts.add(new double[]{cur.x, cur.y, cur.z, now, mc.player.getHeight()});
            pts = smoothPath(pts); // сплайн Катмулла-Рома: плавные изгибы вместо ломаной
        }
        switch (st) {
            case 1 -> ribbon(vg, pts, now, life);
            case 2 -> line(vg, pts, now, life);
            case 3 -> steps(vg, pts, now, life);
            case 4 -> rings(vg, pts, now, life, true);
            case 5 -> rings(vg, pts, now, life, false);
            default -> curtain(vg, pts, now, life);
        }
    }

    // ------------------------------------------------------------ стили

    private void curtain(long vg, List<double[]> pts, long now, double life) {
        float op = opacity.get(), hk = height.get();
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            if (same(a, b)) continue;
            float f = (fade(now - a[3], life) + fade(now - b[3], life)) / 2f * near(a, b);
            if (f <= 0.01f) continue;
            int c = colorAt(f, now);
            int top = Draw.lerpColor(c, 0xFFFFFFFF, 0.45f);
            double ta = a[1] + a[4] * hk, tb = b[1] + b[4] * hk;
            WorldDraw.quadGradient(vg, new double[]{
                    a[0], ta, a[2], b[0], tb, b[2],
                    b[0], b[1] + 0.05, b[2], a[0], a[1] + 0.05, a[2]}, top, op * f * 0.85f, c, op * f * 0.25f);
            if (edge.on()) WorldDraw.line(vg, a[0], ta, a[2], b[0], tb, b[2],
                    Math.max(1f, 2.2f * f), top, Math.min(1f, op * f * 1.3f));
        }
    }

    /** Горизонтальная лента на уровне пояса: ширина = «Высота / ширина» × 0.45 блока. */
    private void ribbon(long vg, List<double[]> pts, long now, double life) {
        float op = opacity.get();
        double half = height.get() * 0.22;
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            double dx = b[0] - a[0], dz = b[2] - a[2], len = Math.hypot(dx, dz);
            if (len < 1e-3) continue;
            float f = (fade(now - a[3], life) + fade(now - b[3], life)) / 2f * near(a, b);
            if (f <= 0.01f) continue;
            double nx = -dz / len * half * (0.4 + 0.6 * f), nz = dx / len * half * (0.4 + 0.6 * f);
            double ya = a[1] + a[4] * 0.5, yb = b[1] + b[4] * 0.5;
            int c = colorAt(f, now);
            WorldDraw.quad(vg, new double[]{a[0] - nx, ya, a[2] - nz, a[0] + nx, ya, a[2] + nz,
                    b[0] + nx, yb, b[2] + nz, b[0] - nx, yb, b[2] - nz}, c, op * f * 0.75f);
        }
    }

    /** Неоновая линия у пояса: свечение + яркая сердцевина, толщина с перспективой. */
    private void line(long vg, List<double[]> pts, long now, double life) {
        float op = opacity.get();
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            if (same(a, b)) continue;
            float f = (fade(now - a[3], life) + fade(now - b[3], life)) / 2f * near(a, b);
            if (f <= 0.01f) continue;
            int c = colorAt(f, now);
            double ya = a[1] + a[4] * 0.5, yb = b[1] + b[4] * 0.5;
            if (Quality.glow()) WorldDraw.beam(vg, a[0], ya, a[2], b[0], yb, b[2], 0.16 * height.get(), c, op * f * 0.25f);
            WorldDraw.beam(vg, a[0], ya, a[2], b[0], yb, b[2], 0.045 * height.get(),
                    Draw.lerpColor(c, 0xFFFFFFFF, 0.5f), Math.min(1f, op * f * 1.6f));
        }
    }

    /** Светящиеся пятна на земле через каждые ~0.45 блока пути. */
    private void steps(long vg, List<double[]> pts, long now, double life) {
        float op = opacity.get();
        double acc = 1e9;
        for (int i = pts.size() - 1; i >= 1; i--) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            acc += Math.hypot(b[0] - a[0], b[2] - a[2]);
            if (acc < 0.45) continue;
            acc = 0;
            float f = fade(now - b[3], life) * near(b, b);
            if (f <= 0.01f) continue;
            int c = colorAt(f, now);
            double r = 0.12 + 0.12 * height.get();
            if (Quality.glow()) WorldDraw.disc(vg, b[0], b[1] + 0.03, b[2], r * 2.0, 20, c, op * f * 0.25f);
            WorldDraw.disc(vg, b[0], b[1] + 0.035, b[2], r, 20, Draw.lerpColor(c, 0xFFFFFFFF, 0.35f), op * f);
        }
    }

    /** Кольца на пути: rise=true — поднимаются вверх; false — расходятся волной по земле. */
    private void rings(long vg, List<double[]> pts, long now, double life, boolean rise) {
        float op = opacity.get();
        double acc = 1e9;
        for (int i = pts.size() - 1; i >= 1; i--) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            acc += Math.hypot(b[0] - a[0], b[2] - a[2]);
            if (acc < (rise ? 0.55 : 0.9)) continue;
            acc = 0;
            double age = Math.min(1.0, (now - b[3]) / life);
            float f = fade(now - b[3], life) * near(b, b);
            if (f <= 0.01f) continue;
            int c = colorAt(f, now);
            if (rise) {
                double y = b[1] + 0.1 + age * b[4] * height.get();
                WorldDraw.ring(vg, b[0], y, b[2], 0.32, 32, Math.max(1f, 2.5f * f), c, op * f, true);
            } else {
                double r = 0.25 + age * 1.1 * height.get();
                WorldDraw.ring(vg, b[0], b[1] + 0.04, b[2], r, 40, Math.max(1f, 2.5f * f), c, op * f, true);
            }
        }
    }

    // ------------------------------------------------------------ helpers

    /** Цвет точки шлейфа: f = 1 у игрока → 0 в конце. */
    private int colorAt(float f, long now) {
        return switch (palette.index()) {
            case 1 -> Draw.lerpColor(color2.argb(), color.argb(), f);
            case 2 -> Draw.hue((1f - f) * 0.8f + now / 4000f);
            default -> color.argb();
        };
    }

    private static boolean same(double[] a, double[] b) {
        return Math.hypot(b[0] - a[0], b[2] - a[2]) < 1e-3 && Math.abs(b[1] - a[1]) < 1e-3;
    }

    /**
     * Множитель «близко к игроку»: участки ближе startDist не рисуются, дальше — плавно
     * проявляются на протяжении 0.6 блока. Так шлейф выходит из-за спины, а не из-под ног.
     */
    private float near(double[] a, double[] b) {
        double mx = (a[0] + b[0]) / 2 - curX, mz = (a[2] + b[2]) / 2 - curZ;
        float t = (float) ((Math.sqrt(mx * mx + mz * mz) - startDist) / 0.6);
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    /** Мягкое затухание к концу шлейфа (smoothstep — без резкого обрыва). */
    private static float fade(double age, double life) {
        float t = 1f - (float) Math.min(1.0, Math.max(0.0, age / life));
        return t * t * (3f - 2f * t);
    }

    /** Сглаживание ломаной сплайном Катмулла-Рома (позиция), время и рост — линейно. */
    private static List<double[]> smoothPath(List<double[]> in) {
        int n = in.size();
        if (n < 3) return in;
        int SUBDIV = subdiv();
        if (SUBDIV <= 1) return in;
        List<double[]> out = new ArrayList<>(n * SUBDIV);
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = in.get(Math.max(0, i - 1)), p1 = in.get(i), p2 = in.get(i + 1), p3 = in.get(Math.min(n - 1, i + 2));
            for (int s = 0; s < SUBDIV; s++) {
                double t = s / (double) SUBDIV, t2 = t * t, t3 = t2 * t;
                double[] q = new double[5];
                for (int k = 0; k < 3; k++)
                    q[k] = 0.5 * (2 * p1[k] + (-p0[k] + p2[k]) * t
                            + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                            + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3);
                q[3] = p1[3] + (p2[3] - p1[3]) * t;
                q[4] = p1[4] + (p2[4] - p1[4]) * t;
                out.add(q);
            }
        }
        out.add(in.get(n - 1));
        return out;
    }
}
