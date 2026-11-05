package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
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

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Trails — полупрозрачная «занавеска» за спиной.
 *
 * Каждый тик, пока игрок движется, запоминаем точку {x, y ног, z, время, рост}.
 * Между соседними точками рисуем вертикальную полосу от ног до плеч: сверху ярче,
 * книзу прозрачнее, а чем старше точка — тем сильнее она растворяется. По верхнему краю —
 * тонкая светлая кромка. Последний участок тянется до текущей (интерполированной)
 * позиции игрока, поэтому лента не отстаёт и не дёргается.
 */
public final class TrailsModule extends HudModule {

    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFFD2E2FF));
    private final NumberSetting length = setting(new NumberSetting("time", "Длина (сек)", 1.6f, 0.4f, 4f, 0.1f));
    private final NumberSetting height = setting(new NumberSetting("height", "Высота ленты", 0.85f, 0.3f, 1.1f, 0.05f));
    private final NumberSetting opacity = setting(new NumberSetting("opacity", "Яркость", 0.55f, 0.1f, 1f, 0.05f));
    private final BoolSetting edge = setting(new BoolSetting("edge", "Светлая кромка сверху", true));
    private final NumberSetting minSpeed = setting(new NumberSetting("speed", "С какой скорости (бл/с)", 1.0f, 0f, 6f, 0.5f));

    /** {x, yНог, z, время мс, рост} */
    private final ArrayDeque<double[]> trail = new ArrayDeque<>();
    private double lastX, lastY, lastZ;
    private long lastT;

    public TrailsModule() {
        super("Trails", "Полупрозрачная лента-шлейф за спиной", Category.VISUALS);
        about("Пока двигаешься, за тобой тянется широкая полупрозрачная лента от ног до плеч. "
                + "Чем дальше от тебя, тем сильнее она растворяется. Видно от 3-го лица (F5).");
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
            // телепорт/смерть — начинаем ленту заново, иначе через полкарты протянется полоса
            if (moved > 8) trail.clear();
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
        // хвост тянем до текущей позиции, чтобы лента «росла» прямо из игрока
        Vec3d cur = mc.player.getLerpedPos(Projection.tickProgress(mc));
        pts.add(new double[]{cur.x, cur.y, cur.z, now, mc.player.getHeight()});

        int c = color.argb();
        int top = Draw.lerpColor(c, 0xFFFFFFFF, 0.45f);
        float op = opacity.get();
        float hk = height.get();

        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            if (Math.hypot(b[0] - a[0], b[2] - a[2]) < 1e-3 && Math.abs(b[1] - a[1]) < 1e-3) continue;
            // свежесть: 1 у игрока → 0 в конце; плавно (квадрат), чтобы конец растворялся мягко
            float fa = fade(now - a[3], life), fb = fade(now - b[3], life);
            float f = (fa + fb) / 2f;
            if (f <= 0.01f) continue;
            double ya = a[1] + 0.05, yb = b[1] + 0.05;
            double ta = a[1] + a[4] * hk, tb = b[1] + b[4] * hk;
            // порядок вершин для quadGradient: ребро 1-2 = верх, ребро 3-4 = низ
            WorldDraw.quadGradient(vg, new double[]{
                    a[0], ta, a[2], b[0], tb, b[2],
                    b[0], yb, b[2], a[0], ya, a[2]}, top, op * f * 0.85f, c, op * f * 0.25f);
            if (edge.on()) {
                WorldDraw.line(vg, a[0], ta, a[2], b[0], tb, b[2],
                        Math.max(1f, 2.2f * f), top, Math.min(1f, op * f * 1.3f));
            }
        }
    }

    private static float fade(long age, double life) {
        float t = 1f - (float) Math.min(1.0, Math.max(0.0, age / life));
        return t * t;
    }
}
