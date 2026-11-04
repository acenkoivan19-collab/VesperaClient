package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Projection;
import dev.vespera.client.util.MotionTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Рой — порт TerraHUD «Рой (Swarm)» (WorldRings, блок «Рой»).
 *
 * Светящиеся «мошки» кружат вокруг игрока; 4 режима: кольцо, сфера (точки Фибоначчи),
 * спираль, волна. Рой виден, пока ты двигаешься, и плавно гаснет за 3 сек простоя.
 * Мошка — билборд, поэтому в NanoVG это круг в экранных координатах
 * с размером по глубине (0.07 блока ядро, ×2.4 ореол).
 */
public final class SwarmModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Узор", 0, "Орбита", "Сфера", "Спираль", "Волна")
            .hints("Кольцо мошек вокруг пояса", "Мошки равномерно по сфере вокруг тебя",
                    "Винт снизу вверх", "Кольцо с бегущей вертикальной волной"));
    private final ChoiceSetting palette = setting(new ChoiceSetting("palette", "Палитра", 0, "Один цвет", "Радуга", "Тема")
            .hints("Все мошки одного цвета", "Каждая мошка своего оттенка", "Переход между цветами темы"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFF9FC39A));
    private final NumberSetting count = setting(new NumberSetting("count", "Количество", 24, 4, 64, 1));
    private final NumberSetting radius = setting(new NumberSetting("radius", "Радиус", 1.0f, 0.4f, 2.5f, 0.1f));
    private final NumberSetting speed = setting(new NumberSetting("speed", "Скорость", 5, 1, 10, 1));

    private final float[] p = new float[3];

    public SwarmModule() {
        super("Swarm", "[Дубль TerraHUD: Рой (Swarm)] Частицы кружат вокруг тебя", Category.VISUALS);
        about("Вокруг тебя кружат светящиеся мошки. Рой виден, пока ты двигаешься, "
                + "и плавно гаснет через 3 секунды на месте.");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !Projection.isReady()) return;
        long now = System.currentTimeMillis();
        float idle = Math.max(0f, Math.min(1f, 1f - (now - MotionTracker.lastMoveMs()) / 3000f));
        if (idle <= 0f) return;

        int n = (int) count.get();
        double radius = this.radius.get();
        double speed = 0.0006 * this.speed.get();
        int mode = style.index();
        Vec3d c = mc.player.getLerpedPos(Projection.tickProgress(mc));

        for (int i = 0; i < n; i++) {
            double x, y, z;
            if (mode == 1) { // сфера: точки Фибоначчи, вращение вокруг Y
                double ga = Math.PI * (3 - Math.sqrt(5));
                double yy = 1 - (i / (double) Math.max(1, n - 1)) * 2;
                double rr = Math.sqrt(Math.max(0, 1 - yy * yy));
                double th = ga * i + now * speed;
                x = c.x + Math.cos(th) * rr * radius;
                y = c.y + 1.0 + yy * radius;
                z = c.z + Math.sin(th) * rr * radius;
            } else if (mode == 2) { // спираль вверх
                double t = (i / (double) n + now * speed * 0.15) % 1.0;
                double ang = t * Math.PI * 4 + i;
                double rr = radius * (0.6 + 0.4 * t);
                x = c.x + Math.cos(ang) * rr;
                y = c.y + 0.2 + t * 2.0;
                z = c.z + Math.sin(ang) * rr;
            } else if (mode == 3) { // волна
                double phase = now * speed + 2 * Math.PI * i / n;
                double rp = radius * (1 + 0.18 * Math.sin(now * 0.003 + i));
                x = c.x + Math.cos(phase) * rp;
                y = c.y + 1.0 + Math.sin(now * 0.004 + i * 1.7) * 0.9;
                z = c.z + Math.sin(phase) * rp;
            } else { // кольцо-орбита
                double phase = now * speed + 2 * Math.PI * i / n;
                x = c.x + Math.cos(phase) * radius;
                y = c.y + 1.0 + Math.sin(now * 0.002 + i * 1.7) * 0.7;
                z = c.z + Math.sin(phase) * radius;
            }
            if (!Projection.project(x, y, z, p)) continue;
            float core = Math.max(1f, 0.07f * Projection.pixelsPerBlock(p[2]));
            int col = switch (palette.index()) {
                case 1 -> java.awt.Color.HSBtoRGB((i / (float) n + now / 6000f) % 1f, 0.6f, 1f) | 0xFF000000;
                case 2 -> Draw.lerpColor(dev.vespera.client.ui.Theme.ACCENT, dev.vespera.client.ui.Theme.ACCENT_2,
                        (float) (0.5 + 0.5 * Math.sin(i * 0.7 + now / 700.0)));
                default -> color.argb();
            };
            Draw.circle(vg, p[0], p[1], core * 2.4f, col, 70f / 255f * idle);
            Draw.circle(vg, p[0], p[1], core, col, 235f / 255f * idle);
        }
    }
}
