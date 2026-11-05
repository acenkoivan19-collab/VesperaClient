package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Projection;
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
        long now = System.currentTimeMillis();
        long life = (long) (length.get() * 1000);
        while (!trail.isEmpty() && now - (long) trail.peekFirst()[3] > life) trail.pollFirst();
        if (mc.player == null) { trail.clear(); return; }
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        if (lastT > 0) {
            double sec = Math.max(1, now - lastT) / 1000.0;
            double moved = Math.sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ));
            if (moved > 8) trail.clear(); // телепорт/смерть — начинаем заново
            else if (moved / sec >= minSpeed.get() && moved > 0.02)
                trail.addLast(new double[]{x, y, z, now, mc.player.getHeight()});
        }
        while (trail.size() > 120) trail.pollFirst();
        lastX = x;
        lastY = y;
        lastZ = z;
        lastT = now;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Projection.isReady() || mc.player == null || trail.isEmpty()) return;
        long now = System.currentTimeMillis();
        double life = length.get() * 1000.0;
        List<double[]> pts = new ArrayList<>(trail);
        int st = style.index();
        if (st <= 2) { // сплошным стилям — хвост до текущей позиции
            Vec3d cur = mc.player.getLerpedPos(Projection.tickProgress(mc));
            pts.add(new double[]{cur.x, cur.y, cur.z, now, mc.player.getHeight()});
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
            float f = (fade(now - a[3], life) + fade(now - b[3], life)) / 2f;
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
            float f = (fade(now - a[3], life) + fade(now - b[3], life)) / 2f;
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
            float f = (fade(now - a[3], life) + fade(now - b[3], life)) / 2f;
            if (f <= 0.01f) continue;
            int c = colorAt(f, now);
            double ya = a[1] + a[4] * 0.5, yb = b[1] + b[4] * 0.5;
            WorldDraw.beam(vg, a[0], ya, a[2], b[0], yb, b[2], 0.16 * height.get(), c, op * f * 0.25f);
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
            float f = fade(now - b[3], life);
            if (f <= 0.01f) continue;
            int c = colorAt(f, now);
            double r = 0.12 + 0.12 * height.get();
            WorldDraw.disc(vg, b[0], b[1] + 0.03, b[2], r * 2.0, 20, c, op * f * 0.25f);
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
            float f = fade(now - b[3], life);
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

    private static float fade(double age, double life) {
        float t = 1f - (float) Math.min(1.0, Math.max(0.0, age / life));
        return t * t;
    }
}
