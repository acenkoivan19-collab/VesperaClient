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
import dev.vespera.client.util.CosmeticTargets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * China Hat — широкая полупрозрачная «стеклянная» шляпа-конус с неоновым ободком.
 *
 * Как рисуется (всё через NanoVG + проекцию мира):
 *  1. Грани конуса (N секторов) сортируются от дальних к ближним и заливаются
 *     полупрозрачным градиентом «светлая вершина → чуть темнее у полей».
 *     Яркость каждой грани зависит от того, как она повёрнута к «свету» — получается объём.
 *  2. По краю полей — неоновый ободок в три прохода: широкое мягкое свечение,
 *     средний слой и тонкая светлая сердцевина. Цвет ободка плавно переливается
 *     между двумя цветами по кругу (и крутится, если включено вращение).
 */
public final class ChinaHatModule extends HudModule {

    private final ColorSetting hatColor = setting(new ColorSetting("hatColor", "Цвет шляпы", 0xFFE4ECFF));
    private final ColorSetting rimColor = setting(new ColorSetting("rimColor", "Цвет ободка", 0xFF5C7CFF));
    private final ColorSetting rimColor2 = setting(new ColorSetting("rimColor2", "Второй цвет ободка", 0xFF9A6BFF));
    private final NumberSetting size = setting(new NumberSetting("size", "Размер", 10, 5, 20, 1));
    private final NumberSetting opacity = setting(new NumberSetting("opacity", "Прозрачность шляпы", 0.55f, 0.15f, 1.0f, 0.05f));
    private final NumberSetting glow = setting(new NumberSetting("glow", "Свечение ободка", 1.0f, 0.0f, 2.0f, 0.1f));
    private final NumberSetting seat = setting(new NumberSetting("seat", "Посадка (ниже ↔ выше)", 0f, -10f, 10f, 1f));
    private final BoolSetting spin = setting(new BoolSetting("spin", "Вращение переливания", true));
    private final ChoiceSetting targets = setting(new ChoiceSetting("targets", "На ком", 0, "Себе", "Себе и игрокам")
            .hints("Только на тебе (вид от 3-го лица, F5)", "И на других игроках, которых ты видишь напрямую"));

    /** Число секторов: больше — глаже край. */
    private static final int N = 36;
    /** Направление «света» в плоскости XZ (сверху-спереди-слева). */
    private static final double LIGHT_X = -0.6, LIGHT_Z = -0.8;

    private final double[] px = new double[N + 1], pz = new double[N + 1];
    private final float[] sx = new float[N + 1], sy = new float[N + 1];
    private final boolean[] ok = new boolean[N + 1];
    private final Integer[] order = new Integer[N];
    private final double[] dist = new double[N];
    private final float[] apex = new float[3], tmp = new float[3];

    public ChinaHatModule() {
        super("China Hat", "Стеклянная шляпа-конус с неоновым ободком", Category.VISUALS);
        about("Широкая полупрозрачная шляпа-конус над головой с мягким неоновым ободком, "
                + "цвет которого переливается по кругу. Видна от 3-го лица (F5).");
        for (int i = 0; i < N; i++) order[i] = i;
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !Projection.isReady()) return;
        float tp = Projection.tickProgress(mc);
        double ang = spin.on() ? System.currentTimeMillis() / 1000.0 * 1.2 : 0.0;
        for (LivingEntity e : CosmeticTargets.get(mc, targets.index() == 1)) drawFor(vg, mc, e, tp, ang);
    }

    private void drawFor(long vg, MinecraftClient mc, LivingEntity ent, float tp, double ang) {
        double sz = size.get();
        double rad = sz * 0.065;          // 10 → 0.65 блока: широкие поля, как на референсе
        double hgt = rad * 0.42;          // пологий конус
        Vec3d p = ent.getLerpedPos(tp);
        // Шляпа сидит прямо на голове: поля чуть ниже макушки (макушка ≈ глаза + 0.18).
        // Считаем от высоты глаз — так шляпа правильно опускается при приседании и плавании.
        double eyeY = p.y + (ent.getEyeY() - ent.getY());
        double by = eyeY + 0.10 + seat.get() * 0.01;
        double ay = by + hgt;

        if (!Projection.project(p.x, ay, p.z, apex)) return;

        for (int i = 0; i <= N; i++) {
            double t = 2 * Math.PI * i / N;
            px[i] = p.x + Math.cos(t) * rad;
            pz[i] = p.z + Math.sin(t) * rad;
            ok[i] = Projection.project(px[i], by, pz[i], tmp);
            sx[i] = tmp[0];
            sy[i] = tmp[1];
        }

        // ---- 1. грани: от дальних к ближним (иначе полупрозрачность «перемешивается»)
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        for (int i = 0; i < N; i++) {
            double mx = (px[i] + px[i + 1]) / 2, mz = (pz[i] + pz[i + 1]) / 2;
            dist[i] = (mx - cam.x) * (mx - cam.x) + (mz - cam.z) * (mz - cam.z) + (by - cam.y) * (by - cam.y);
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(dist[b], dist[a]));

        int base = hatColor.argb();
        float op = (float) opacity.get();
        for (int k = 0; k < N; k++) {
            int i = order[k];
            if (!ok[i] || !ok[i + 1]) continue;
            // нормаль грани в плоскости XZ → насколько грань «смотрит на свет»
            double mid = 2 * Math.PI * (i + 0.5) / N;
            double lit = 0.5 + 0.5 * (Math.cos(mid) * LIGHT_X + Math.sin(mid) * LIGHT_Z); // 0..1
            int top = Draw.lerpColor(base, 0xFFFFFFFF, 0.35f);
            int bottom = Draw.lerpColor(darker(base, 0.72f), base, (float) lit);
            float aTop = op * (0.55f + 0.35f * (float) lit);
            float aBot = op * (0.70f + 0.30f * (float) lit);

            float bx = (sx[i] + sx[i + 1]) / 2f, byS = (sy[i] + sy[i + 1]) / 2f;
            if (Math.abs(bx - apex[0]) + Math.abs(byS - apex[1]) < 0.5f) byS += 0.5f;
            nvgLinearGradient(vg, apex[0], apex[1], bx, byS, Draw.c1(top, aTop), Draw.c2(bottom, aBot), WorldDraw.paint());
            nvgBeginPath(vg);
            nvgMoveTo(vg, apex[0], apex[1]);
            nvgLineTo(vg, sx[i], sy[i]);
            nvgLineTo(vg, sx[i + 1], sy[i + 1]);
            nvgClosePath(vg);
            nvgFillPaint(vg, WorldDraw.paint());
            nvgFill(vg);
        }

        // ---- 2. неоновый ободок: свечение → средний слой → светлая сердцевина
        float px1 = Projection.pixelsPerBlock(apex[2]);
        float core = Math.max(1.2f, px1 * 0.018f);
        float g = (float) glow.get();
        int c1 = rimColor.argb(), c2 = rimColor2.argb();
        if (g > 0.01f) {
            rim(vg, c1, c2, ang, core * 7f, 0.10f * g);
            rim(vg, c1, c2, ang, core * 4f, 0.22f * g);
        }
        rim(vg, c1, c2, ang, core * 2f, 0.85f);
        rimCore(vg, core * 0.8f, 0.7f);
    }

    /** Ободок по краю полей: каждый отрезок своим цветом (переливание c1 ↔ c2 по кругу). */
    private void rim(long vg, int c1, int c2, double ang, float width, float alpha) {
        nvgLineCap(vg, NVG_ROUND);
        nvgLineJoin(vg, NVG_ROUND);
        nvgStrokeWidth(vg, width);
        for (int i = 0; i < N; i++) {
            if (!ok[i] || !ok[i + 1]) continue;
            double t = 2 * Math.PI * (i + 0.5) / N + ang;
            float mix = (float) (0.5 + 0.5 * Math.sin(t));
            nvgBeginPath(vg);
            nvgMoveTo(vg, sx[i], sy[i]);
            nvgLineTo(vg, sx[i + 1], sy[i + 1]);
            nvgStrokeColor(vg, Draw.c1(Draw.lerpColor(c1, c2, mix), alpha));
            nvgStroke(vg);
        }
    }

    /** Тонкая почти белая линия по центру ободка — «горячая» сердцевина неона. */
    private void rimCore(long vg, float width, float alpha) {
        nvgBeginPath(vg);
        boolean pen = false;
        for (int i = 0; i <= N; i++) {
            if (!ok[i]) { pen = false; continue; }
            if (pen) nvgLineTo(vg, sx[i], sy[i]); else nvgMoveTo(vg, sx[i], sy[i]);
            pen = true;
        }
        nvgStrokeWidth(vg, width);
        nvgStrokeColor(vg, Draw.c1(0xFFEFF3FF, alpha));
        nvgStroke(vg);
    }

    private static int darker(int argb, float k) {
        int r = (int) (((argb >> 16) & 255) * k), gg = (int) (((argb >> 8) & 255) * k), b = (int) ((argb & 255) * k);
        return (argb & 0xFF000000) | (r << 16) | (gg << 8) | b;
    }
}
