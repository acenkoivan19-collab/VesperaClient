package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.WorldDraw;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Следы — порт TerraHUD «Следы» (TerraHudClient.tickTrails + WorldRings, блок «Шлейф»).
 *
 * Точка ставится каждый 2-й тик, если игрок на земле и бежит быстрее 2.5 блока/сек;
 * хранится до 20 точек, каждая живёт 1200 мс. Стиль: 0 — светящиеся «пятна» на земле,
 * 1 — сплошная лента, 2 — оба. Старые точки тусклее и меньше.
 */
public final class TrailsModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Вид следа", 0, "Точки", "Лента", "Оба")
            .hints("Светящиеся пятна на земле там, где ты пробежал", "Сплошная полоса за спиной",
                    "Пятна и лента вместе"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFF9FC39A));
    private final NumberSetting length = setting(new NumberSetting("length", "Длина следа", 20, 8, 40, 1));
    private final NumberSetting minSpeed = setting(new NumberSetting("speed", "С какой скорости (бл/с)", 2.5f, 0.5f, 6f, 0.5f));

    private static final long LIFE_MS = 1200;

    /** {x, y, z, время} */
    private final ArrayDeque<double[]> trail = new ArrayDeque<>();
    private double lastX, lastZ;
    private long lastT;
    private int tickCounter;

    public TrailsModule() {
        super("Trails", "[Дубль TerraHUD: Следы] Шлейф при беге", Category.VISUALS);
        about("Пока бежишь по земле, за тобой остаётся затухающий след — пятна, лента или оба. "
                + "След пропадает через 1.2 секунды.");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    protected void onDisable() {
        trail.clear();
        lastT = 0;
        tickCounter = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        while (!trail.isEmpty() && now - (long) trail.peekFirst()[3] > LIFE_MS) trail.pollFirst();
        if (mc.player == null) return;
        double x = mc.player.getX(), z = mc.player.getZ();
        if (lastT > 0) {
            double sec = Math.max(1, now - lastT) / 1000.0;
            double speed = Math.hypot(x - lastX, z - lastZ) / sec;
            if (speed > minSpeed.get() && mc.player.isOnGround() && (tickCounter++ % 2) == 0) {
                trail.addLast(new double[]{x, mc.player.getY() + 0.08, z, now});
                while (trail.size() > (int) length.get()) trail.pollFirst();
            }
        }
        lastX = x;
        lastZ = z;
        lastT = now;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        if (!Projection.isReady() || trail.isEmpty()) return;
        List<double[]> pts = new ArrayList<>(trail);
        int total = pts.size();
        int style = this.style.index();
        int COLOR = color.argb();

        if (style == 0 || style == 2) {
            for (int i = 0; i < total; i++) {
                double[] t = pts.get(i);
                float f = total > 1 ? (float) i / (total - 1) : 1f;
                float a = 210f * f / 255f;
                if (a <= 0.02f) continue;
                double sz = 0.16 + 0.1 * f;
                // текстура круга TerraHUD: линия на 0.68 размера + ореол ×2.1
                WorldDraw.disc(vg, t[0], t[1] + 0.045, t[2], sz * 2.1 * 0.68, 20, COLOR, a / 4f);
                WorldDraw.disc(vg, t[0], t[1] + 0.04, t[2], sz * 0.68, 20, COLOR, a * 0.8f);
            }
        }
        if ((style == 1 || style == 2) && total > 2) {
            for (int i = 1; i < total; i++) {
                double[] a = pts.get(i - 1), b = pts.get(i);
                float f = (float) i / (total - 1);
                float alpha = 170f * f / 255f;
                if (alpha <= 0.02f) continue;
                double dx = b[0] - a[0], dz = b[2] - a[2];
                double len = Math.hypot(dx, dz);
                if (len < 1e-4) continue;
                double wdt = 0.09 + 0.06 * f;
                double nx = -dz / len * wdt, nz = dx / len * wdt;
                double ay = a[1] + 0.05, by = b[1] + 0.05;
                // ореол: та же полоса ×2.4 и втрое тусклее
                WorldDraw.quad(vg, new double[]{
                        a[0] - nx * 2.4, ay, a[2] - nz * 2.4, a[0] + nx * 2.4, ay, a[2] + nz * 2.4,
                        b[0] + nx * 2.4, by, b[2] + nz * 2.4, b[0] - nx * 2.4, by, b[2] - nz * 2.4}, COLOR, alpha / 3f);
                WorldDraw.quad(vg, new double[]{
                        a[0] - nx, ay, a[2] - nz, a[0] + nx, ay, a[2] + nz,
                        b[0] + nx, by, b[2] + nz, b[0] - nx, by, b[2] - nz}, COLOR, alpha);
            }
        }
    }
}
