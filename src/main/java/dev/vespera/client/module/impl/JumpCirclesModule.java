package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Projection;
import dev.vespera.client.ui.Anim;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Iterator;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Jump Circles — светящееся неоновое кольцо на земле под ногами при прыжке.
 *
 * Кольцо расходится от ног (easeOutCubic) и гаснет. Рисуется в три прохода:
 * широкое мягкое свечение → цветной слой → тонкая почти белая сердцевина.
 * Толщина считается в блоках и переводится в пиксели с учётом перспективы,
 * поэтому вблизи кольцо толще, вдали — тоньше, как настоящий объект на земле.
 * По кругу бежит яркий «блик»: одна сторона кольца ярче, другая мягко тает.
 */
public final class JumpCirclesModule extends HudModule {

    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFF7FE3FF));
    private final NumberSetting radius = setting(new NumberSetting("radius", "Размер", 1.1f, 0.4f, 3f, 0.1f));
    private final NumberSetting count = setting(new NumberSetting("count", "Колец за прыжок", 1, 1, 3, 1));
    private final NumberSetting thick = setting(new NumberSetting("thick", "Толщина", 1.0f, 0.4f, 2.5f, 0.1f));
    private final NumberSetting bright = setting(new NumberSetting("bright", "Яркость", 0.95f, 0.2f, 1f, 0.05f));
    private final NumberSetting lifeSec = setting(new NumberSetting("life", "Время жизни (сек)", 1.1f, 0.4f, 3f, 0.1f));
    private final BoolSetting highlight = setting(new BoolSetting("highlight", "Бегущий блик", true));

    private static final int SEG = 72;

    /** {x, y, z, рождение мс} */
    private final ArrayDeque<double[]> rings = new ArrayDeque<>();
    private boolean wasOnGround = true;
    private final float[] sx = new float[SEG + 1], sy = new float[SEG + 1], sd = new float[SEG + 1];
    private final boolean[] ok = new boolean[SEG + 1];
    private final float[] tmp = new float[3];

    public JumpCirclesModule() {
        super("Jump Circles", "Неоновое кольцо под ногами при прыжке", Category.VISUALS);
        about("Когда прыгаешь, на земле под ногами расходится светящееся неоновое кольцо "
                + "с мягким свечением и бегущим бликом, затем плавно гаснет.");
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
                rings.add(new double[]{mc.player.getX(), mc.player.getY() + 0.03, mc.player.getZ(), now + i * 140});
            while (rings.size() > 9) rings.pollFirst();
        }
        wasOnGround = ground;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        if (!Projection.isReady()) return;
        long now = System.currentTimeMillis();
        long life = (long) (lifeSec.get() * 1000);
        for (Iterator<double[]> it = rings.iterator(); it.hasNext(); ) {
            double[] r = it.next();
            long age = now - (long) r[3];
            if (age > life + 50) { it.remove(); continue; }
            if (age < 0 || age > life) continue;
            float t = age / (float) life;
            double rad = 0.35 + (radius.get() - 0.35) * Anim.easeOutCubic(t);
            // быстро загорается, долго гаснет
            float a = bright.get() * Math.min(1f, t * 8f) * (1f - t) * (1f - t * 0.3f);
            double phase = highlight.on() ? now / 1000.0 * 2.2 + r[3] * 0.001 : 0.8;
            drawRing(vg, r[0], r[1], r[2], rad, a, phase);
        }
    }

    private void drawRing(long vg, double x, double y, double z, double rad, float a, double phase) {
        for (int i = 0; i <= SEG; i++) {
            double ang = 2 * Math.PI * i / SEG;
            ok[i] = Projection.project(x + Math.cos(ang) * rad, y, z + Math.sin(ang) * rad, tmp);
            sx[i] = tmp[0];
            sy[i] = tmp[1];
            sd[i] = tmp[2];
        }
        int c = color.argb();
        int core = Draw.lerpColor(c, 0xFFFFFFFF, 0.7f);
        float th = thick.get();
        nvgLineCap(vg, NVG_ROUND);
        // широкие проходы рисуем первыми, чтобы сердцевина была поверх
        pass(vg, 0.16 * th, c, a * 0.10f, phase, 0.55f);
        pass(vg, 0.08 * th, c, a * 0.28f, phase, 0.65f);
        pass(vg, 0.035 * th, c, a * 0.85f, phase, 0.75f);
        pass(vg, 0.012 * th, core, a * 0.95f, phase, 0.85f);
    }

    /**
     * Один проход кольца: каждый отрезок своей толщины (перспектива) и яркости (блик).
     * widthBlocks — толщина в блоках; hl — насколько сильно блик влияет на яркость.
     */
    private void pass(long vg, double widthBlocks, int col, float alpha, double phase, float hl) {
        if (alpha <= 0.004f) return;
        for (int i = 0; i < SEG; i++) {
            if (!ok[i] || !ok[i + 1]) continue;
            double ang = 2 * Math.PI * (i + 0.5) / SEG;
            float k = (float) (0.5 + 0.5 * Math.cos(ang - phase));        // 1 — центр блика
            float segA = alpha * ((1f - hl) + hl * (0.25f + 0.75f * k * k));
            float px = Projection.pixelsPerBlock((sd[i] + sd[i + 1]) / 2f);
            nvgBeginPath(vg);
            nvgMoveTo(vg, sx[i], sy[i]);
            nvgLineTo(vg, sx[i + 1], sy[i + 1]);
            nvgStrokeWidth(vg, Math.max(1f, (float) widthBlocks * px));
            nvgStrokeColor(vg, Draw.c1(col, segA));
            nvgStroke(vg);
        }
    }
}
