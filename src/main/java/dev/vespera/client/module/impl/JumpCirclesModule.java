package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.WorldDraw;
import dev.vespera.client.ui.Anim;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Кольца прыжка — порт TerraHUD «Кольца прыжка» (WorldRings + TerraHudClient.tickMovement).
 *
 * Спавн: игрок оторвался от земли и летит вверх (vy > 0.05) → COUNT волн с шагом 90 мс.
 * Кольцо живёт 900 мс: радиус 0.3 → MAX_RADIUS (easeOutCubic), яркость падает линейно.
 * Текстура TerraHUD рисует линию на 0.68 радиуса квада и мягкий ореол вокруг —
 * здесь то же самое векторно: линия + широкий тусклый проход + внешний ореол ×1.45.
 */
public final class JumpCirclesModule extends HudModule {

    // ---- настройки (значения по умолчанию TerraHUD)
    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Форма", 0, "Круг", "Звезда", "Двойное", "Квадрат")
            .hints("Ровное светящееся кольцо", "Пятиконечная звезда на земле",
                    "Кольцо и второе, поменьше, внутри", "Квадратная рамка из полос"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFF9FC39A));
    private final NumberSetting count = setting(new NumberSetting("count", "Волн за прыжок", 3, 1, 5, 1));
    private final NumberSetting radius = setting(new NumberSetting("radius", "Размер", 1.6f, 0.5f, 3f, 0.1f));
    private final NumberSetting thick = setting(new NumberSetting("thick", "Толщина", 2, 1, 4, 1));
    private final NumberSetting bright = setting(new NumberSetting("bright", "Яркость", 0.92f, 0.15f, 1f, 0.05f));

    private static final long LIFE_MS = 900;
    private static final double TEX_EDGE = 0.68;  // где линия в текстуре круга

    /** {x, y, z, рождение в мс} */
    private final ArrayDeque<double[]> rings = new ArrayDeque<>();
    private boolean wasOnGround = true;

    public JumpCirclesModule() {
        super("Jump Circles", "[Дубль TerraHUD: Кольца прыжка] Светящиеся кольца под ногами", Category.VISUALS);
        about("Когда прыгаешь, под ногами расходятся светящиеся кольца — несколько волн подряд. "
                + "Форма, цвет, размер и яркость настраиваются.");
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
            for (int i = 0; i < (int) count.get(); i++) {
                rings.add(new double[]{mc.player.getX(), mc.player.getY() + 0.06, mc.player.getZ(), now + i * 90});
            }
            while (rings.size() > 8) rings.pollFirst();
        }
        wasOnGround = ground;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        if (!Projection.isReady()) return;
        long now = System.currentTimeMillis();
        int style = this.style.index();
        int thick = (int) this.thick.get();
        double maxR = radius.get();

        for (Iterator<double[]> it = rings.iterator(); it.hasNext(); ) {
            double[] r = it.next();
            long age = now - (long) r[3];
            if (age > LIFE_MS + 50) { it.remove(); continue; }
            if (age < 0 || age > LIFE_MS) continue;

            float f = 1f - age / (float) LIFE_MS;
            double rad = 0.3 + (maxR - 0.3) * Anim.easeOutCubic(age / (float) LIFE_MS);
            float a = bright.get() * f;
            double x = r[0], y = r[1] + 0.05, z = r[2];

            if (style == 3) {
                square(vg, x, y, z, rad, 0.07 * thick, a);
                continue;
            }
            ring(vg, x, y, z, rad, style, a);
            if (style == 2) ring(vg, x, y + 0.01, z, rad * 0.55, 0, Math.min(1f, a * 1.2f)); // двойное
            ring(vg, x, y + 0.005, z, rad * 1.45, style, a / 6f);                            // ореол
            if (thick > 1) ring(vg, x, y + 0.01 * thick, z, rad, style, a / 3f);              // толщина
        }
    }

    /** Кольцо (круг или звезда) так, как его даёт текстура TerraHUD размером rad. */
    private void ring(long vg, double x, double y, double z, double rad, int style, float a) {
        int c = color.argb();
        java.util.function.DoubleUnaryOperator edge = style == 1
                ? ang -> rad * (0.42 + 0.34 * Math.pow(Math.abs(Math.cos(ang * 2.5)), 0.7))
                : ang -> rad * TEX_EDGE;
        int seg = style == 1 ? 90 : 56;
        WorldDraw.polarRing(vg, x, y, z, edge, seg, rad * 0.60, c, a * 0.35f); // мягкое свечение
        WorldDraw.polarRing(vg, x, y, z, edge, seg, rad * 0.16, c, a);         // линия
    }

    /** Квадратная рамка из 4 полос шириной wdt (стиль 3). */
    private void square(long vg, double x, double y, double z, double rad, double wdt, float a) {
        int COLOR = color.argb();
        double x0 = x - rad, x1 = x + rad, z0 = z - rad, z1 = z + rad;
        WorldDraw.quad(vg, new double[]{x0, y, z0 - wdt, x1, y, z0 - wdt, x1, y, z0 + wdt, x0, y, z0 + wdt}, COLOR, a);
        WorldDraw.quad(vg, new double[]{x0, y, z1 - wdt, x1, y, z1 - wdt, x1, y, z1 + wdt, x0, y, z1 + wdt}, COLOR, a);
        WorldDraw.quad(vg, new double[]{x0 - wdt, y, z0, x0 + wdt, y, z0, x0 + wdt, y, z1, x0 - wdt, y, z1}, COLOR, a);
        WorldDraw.quad(vg, new double[]{x1 - wdt, y, z0, x1 + wdt, y, z0, x1 + wdt, y, z1, x1 - wdt, y, z1}, COLOR, a);
    }
}
