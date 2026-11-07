package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.WorldDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Kill Effect — эффект, когда умирает тот, кого ты недавно ударил (за последние 8 секунд).
 * Стили: Молния, Души (огоньки улетают вверх по спирали), Волна (кольца по земле + столб света).
 * Только картинка. Цель должна быть видна напрямую в момент смерти.
 */
public final class KillEffectModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Эффект", 0, "Молния", "Души", "Волна")
            .hints("Ветвистая молния бьёт с неба в место смерти",
                    "Светящиеся огоньки улетают вверх по спирали",
                    "Кольца расходятся по земле, вверх бьёт столб света"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFFB9D7FF));

    private static final List<Object[]> hit = new ArrayList<>();   // {entity, время удара}
    /** {x, y, z, рождение мс, seed} */
    private final List<double[]> fx = new ArrayList<>();

    public KillEffectModule() {
        super("Kill Effect", "Эффект, когда умирает твоя цель", Category.VISUALS);
        about("Когда умирает тот, кого ты недавно бил, в этом месте бьёт молния, улетают души "
                + "или расходится волна света. Только картинка.");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    public static void onAttack(Entity e) {
        if (!(e instanceof LivingEntity)) return;
        hit.removeIf(o -> o[0] == e);
        hit.add(new Object[]{e, System.currentTimeMillis()});
        if (hit.size() > 16) hit.remove(0);
    }

    @Override
    protected void onDisable() {
        hit.clear();
        fx.clear();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) { hit.clear(); return; }
        long now = System.currentTimeMillis();
        for (Iterator<Object[]> it = hit.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            LivingEntity e = (LivingEntity) o[0];
            if (now - (long) o[1] > 8000) { it.remove(); continue; }
            if (e.isDead() || e.getHealth() <= 0f) {
                if (mc.player.distanceTo(e) < 32 && mc.player.canSee(e))
                    fx.add(new double[]{e.getX(), e.getY(), e.getZ(), now, Math.random() * 1000});
                it.remove();
            }
        }
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        if (!Projection.isReady() || fx.isEmpty()) return;
        long now = System.currentTimeMillis();
        int c = color.argb();
        for (Iterator<double[]> it = fx.iterator(); it.hasNext(); ) {
            double[] f = it.next();
            float t = (now - (long) f[3]) / 1600f;
            if (t >= 1f) { it.remove(); continue; }
            switch (style.index()) {
                case 1 -> souls(vg, f, t, c);
                case 2 -> wave(vg, f, t, c);
                default -> lightning(vg, f, t, c);
            }
        }
    }

    private void lightning(long vg, double[] f, float t, int c) {
        if (t > 0.45f) { wave(vg, f, Math.min(1f, (t - 0.45f) / 0.55f) * 0.6f + 0.4f, c); return; }
        float a = (float) (0.55 + 0.45 * Math.sin(t * 90)) * (1f - t / 0.45f);   // мерцание
        java.util.Random r = new java.util.Random((long) f[4]);
        double x = f[0], y = f[1] + 14, z = f[2];
        while (y > f[1]) {
            double nx = x + (r.nextDouble() - 0.5) * 0.9, ny = y - 0.8 - r.nextDouble() * 0.9, nz = z + (r.nextDouble() - 0.5) * 0.9;
            if (ny < f[1]) { ny = f[1]; nx = f[0]; nz = f[2]; }
            WorldDraw.beam(vg, x, y, z, nx, ny, nz, 0.22, c, a * 0.25f);
            WorldDraw.beam(vg, x, y, z, nx, ny, nz, 0.06, 0xFFFFFFFF, a);
            if (r.nextDouble() < 0.25) { // ответвление
                double bx = nx + (r.nextDouble() - 0.5) * 1.6, by = ny - 0.8, bz = nz + (r.nextDouble() - 0.5) * 1.6;
                WorldDraw.beam(vg, nx, ny, nz, bx, by, bz, 0.04, c, a * 0.8f);
            }
            x = nx; y = ny; z = nz;
        }
        WorldDraw.ring(vg, f[0], f[1] + 0.05, f[2], 0.6 + t * 2, 40, 3f, c, a, true);
    }

    private void souls(long vg, double[] f, float t, int c) {
        float[] o = new float[3];
        for (int i = 0; i < 9; i++) {
            double ang = i * (Math.PI * 2 / 9) + t * 5 + f[4];
            double rad = 0.3 + t * 0.9;
            double y = f[1] + 0.3 + t * t * 3.2 + (i % 3) * 0.2;
            if (!Projection.project(f[0] + Math.cos(ang) * rad, y, f[2] + Math.sin(ang) * rad, o)) continue;
            float px = Projection.pixelsPerBlock(o[2]);
            float r = Math.max(1.5f, px * 0.07f);
            float a = (1f - t) * Math.min(1f, t * 6f);
            Draw.circle(vg, o[0], o[1], r * 3.5f, c, a * 0.18f);
            Draw.circle(vg, o[0], o[1], r, Draw.lerpColor(c, 0xFFFFFFFF, 0.6f), a);
        }
    }

    private void wave(long vg, double[] f, float t, int c) {
        float a = (1f - t);
        for (int k = 0; k < 3; k++) {
            float tt = Math.max(0f, t - k * 0.12f);
            if (tt <= 0f) continue;
            double r = 0.3 + (1 - Math.pow(1 - tt, 3)) * 2.6;
            WorldDraw.ring(vg, f[0], f[1] + 0.05, f[2], r, 56, 2.5f, c, a * (1f - k * 0.25f), true);
        }
        WorldDraw.beam(vg, f[0], f[1], f[2], f[0], f[1] + 3.5 * Math.min(1f, t * 3f), f[2], 0.5, c, a * 0.18f);
        WorldDraw.beam(vg, f[0], f[1], f[2], f[0], f[1] + 3.5 * Math.min(1f, t * 3f), f[2], 0.12, 0xFFFFFFFF, a * 0.6f);
    }
}
