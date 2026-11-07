package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Projection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Атмосфера — частицы вокруг игрока: снег, светлячки, лепестки, звёздная пыль.
 * Живут в кубе ~14 блоков вокруг камеры; уходя за край, перерождаются с другой стороны.
 */
public final class WorldParticlesModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Частицы", 0, "Снег", "Светлячки", "Лепестки", "Звёздная пыль")
            .hints("Мягкие снежинки медленно падают и покачиваются",
                    "Светящиеся огоньки плавают и мерцают",
                    "Розовые лепестки кружатся и опадают",
                    "Крошечные искры висят в воздухе и переливаются"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет (T — по стилю)", 0));
    private final NumberSetting count = setting(new NumberSetting("count", "Количество", 160, 30, 400, 10));
    private final NumberSetting size = setting(new NumberSetting("size", "Размер", 1f, 0.5f, 2f, 0.1f));

    private static final int MAX = 400;
    private static final double R = 14;
    private final double[][] pt = new double[MAX][6];   // x, y, z, фаза, скорость, seed
    private boolean init;
    private final Random rnd = new Random();
    private int lastStyle = -1;

    public WorldParticlesModule() {
        super("World Particles", "Снег, светлячки, лепестки вокруг тебя", Category.VISUALS);
        about("Добавляет атмосферу: вокруг тебя падает снег, летают светлячки, кружатся лепестки "
                + "или висит звёздная пыль. Только картинка, на игру не влияет.");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    protected void onDisable() {
        init = false;
    }

    private void spawn(double[] p, Vec3d c, boolean anywhere) {
        p[0] = c.x + (rnd.nextDouble() * 2 - 1) * R;
        p[2] = c.z + (rnd.nextDouble() * 2 - 1) * R;
        p[1] = anywhere || style.index() != 0 && style.index() != 2 ? c.y + (rnd.nextDouble() * 2 - 1) * R * 0.6 : c.y + R * 0.6;
        p[3] = rnd.nextDouble() * Math.PI * 2;
        p[4] = 0.6 + rnd.nextDouble() * 0.8;
        p[5] = rnd.nextDouble();
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !Projection.isReady()) return;
        Vec3d c = new Vec3d(Projection.camX(), Projection.camY(), Projection.camZ());
        int st = style.index();
        if (!init || st != lastStyle) {
            for (double[] p : pt) spawn(p, c, true);
            init = true;
            lastStyle = st;
        }
        int n = (int) count.get();
        float k = Math.min(0.1f, dt);
        double time = System.currentTimeMillis() / 1000.0;
        float[] o = new float[3];
        int base = switch (st) {
            case 1 -> 0xFFD8FF7A;
            case 2 -> 0xFFFFB7D5;
            case 3 -> 0xFFBFD8FF;
            default -> 0xFFFFFFFF;
        };
        int col = color.isTheme() ? base : color.argb();
        for (int i = 0; i < n; i++) {
            double[] p = pt[i];
            double sp = p[4];
            switch (st) {
                case 1 -> { p[0] += Math.sin(time * 0.7 + p[3]) * 0.4 * k; p[1] += Math.cos(time * 0.9 + p[3]) * 0.25 * k; p[2] += Math.cos(time * 0.6 + p[3] * 1.3) * 0.4 * k; }
                case 2 -> { p[1] -= 0.9 * sp * k; p[0] += Math.sin(time * 1.6 + p[3]) * 0.8 * k; p[2] += 0.35 * k; }
                case 3 -> { p[1] += Math.sin(time + p[3]) * 0.05 * k; }
                default -> { p[1] -= 1.1 * sp * k; p[0] += Math.sin(time * 1.1 + p[3]) * 0.3 * k; }
            }
            if (Math.abs(p[0] - c.x) > R || Math.abs(p[2] - c.z) > R || p[1] < c.y - R * 0.6 || p[1] > c.y + R * 0.7) {
                spawn(p, c, false);
                continue;
            }
            if (!Projection.project(p[0], p[1], p[2], o)) continue;
            float px = Projection.pixelsPerBlock(o[2]);
            float d = (float) Math.sqrt((p[0] - c.x) * (p[0] - c.x) + (p[1] - c.y) * (p[1] - c.y) + (p[2] - c.z) * (p[2] - c.z));
            float fade = Math.max(0f, Math.min(1f, (float) (R - d) / 4f)) * Math.min(1f, d / 1.5f); // гаснут у края и у самой камеры
            if (fade <= 0.02f) continue;
            float s = size.get();
            switch (st) {
                case 1 -> {
                    float blink = (float) (0.35 + 0.65 * Math.max(0, Math.sin(time * 2.2 * sp + p[3])));
                    float r = Math.max(1f, px * 0.035f * s);
                    Draw.circle(vg, o[0], o[1], r * 4f, col, 0.14f * blink * fade);
                    Draw.circle(vg, o[0], o[1], r, Draw.lerpColor(col, 0xFFFFFFFF, 0.5f), blink * fade);
                }
                case 2 -> {
                    float r = Math.max(1.2f, px * 0.05f * s);
                    nvgSave(vg);
                    nvgTranslate(vg, o[0], o[1]);
                    nvgRotate(vg, (float) (time * 2 * sp + p[3]));
                    nvgScale(vg, 1f, (float) (0.35 + 0.65 * Math.abs(Math.sin(time * 3 * sp + p[3]))));
                    nvgBeginPath(vg);
                    nvgEllipse(vg, 0, 0, r, r * 0.6f);
                    nvgFillColor(vg, Draw.c1(col, 0.85f * fade));
                    nvgFill(vg);
                    nvgRestore(vg);
                }
                case 3 -> {
                    float tw = (float) (0.3 + 0.7 * Math.abs(Math.sin(time * 1.7 * sp + p[3])));
                    float r = Math.max(0.8f, px * 0.016f * s);
                    int cc = color.isTheme() ? Draw.hue((float) p[5] * 0.25f + 0.55f) : col;
                    Draw.circle(vg, o[0], o[1], r * 3f, cc, 0.12f * tw * fade);
                    Draw.circle(vg, o[0], o[1], r, 0xFFFFFFFF, tw * fade);
                }
                default -> {
                    float r = Math.max(1f, px * 0.03f * s);
                    Draw.circle(vg, o[0], o[1], r, col, 0.8f * fade);
                }
            }
        }
    }
}
