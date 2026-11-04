package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.WorldDraw;
import dev.vespera.client.util.CosmeticTargets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Шляпа — порт TerraHUD «Шляпа» (WorldRings.drawHatOn).
 *
 * Конус из 10 граней над головой (рост + 0.28): радиус SIZE/20, высота 0.25 + SIZE·0.02.
 * Грани — градиент от яркой вершины (220) к основанию (110), поля — веер (70).
 * Вращение 1.5 рад/сек (или фиксированный угол 0.4, если SPIN выключен).
 */
public final class ChinaHatModule extends HudModule {

    private final ChoiceSetting shape = setting(new ChoiceSetting("shape", "Форма", 0, "Конус", "Плоская")
            .hints("Высокий острый конус (как в TerraHUD)", "Широкая низкая «соломенная» шляпа"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFFFFD166));
    private final NumberSetting size = setting(new NumberSetting("size", "Размер", 10, 5, 20, 1));
    private final BoolSetting spin = setting(new BoolSetting("spin", "Вращение", true));
    private final ChoiceSetting targets = setting(new ChoiceSetting("targets", "На ком", 0, "Себе", "Себе и игрокам")
            .hints("Только на тебе (вид от 3-го лица, F5)", "И на других игроках, которых ты видишь напрямую"));

    private static final int N = 10;

    public ChinaHatModule() {
        super("China Hat", "[Дубль TerraHUD: Шляпа] Вращающийся конус над головой", Category.VISUALS);
        about("Полупрозрачная шляпа-конус над головой, может медленно вращаться. "
                + "Видна от 3-го лица (F5).");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !Projection.isReady()) return;
        int sz = (int) size.get();
        double rad = sz / 20.0, hgt = 0.25 + sz * 0.02;
        if (shape.index() == 1) { rad *= 1.35; hgt *= 0.4; }
        double ang = spin.on() ? System.currentTimeMillis() / 1000.0 * 1.5 : 0.4;
        float tp = Projection.tickProgress(mc);
        for (LivingEntity e : CosmeticTargets.get(mc, targets.index() == 1)) drawFor(vg, e, tp, rad, hgt, ang);
    }

    private void drawFor(long vg, LivingEntity ent, float tp, double rad, double hgt, double ang) {
        int COLOR = color.argb();
        Vec3d p = ent.getLerpedPos(tp);
        double by = p.y + ent.getHeight() + 0.28;
        double[] px = new double[N + 1], pz = new double[N + 1];
        for (int i = 0; i <= N; i++) {
            double t = ang + 2 * Math.PI * i / N;
            px[i] = p.x + Math.cos(t) * rad;
            pz[i] = p.z + Math.sin(t) * rad;
        }
        for (int i = 0; i < N; i++) {
            // поля: веер от центра к рёбрам
            WorldDraw.quad(vg, new double[]{p.x, by, p.z, p.x, by, p.z,
                    px[i + 1], by, pz[i + 1], px[i], by, pz[i]}, COLOR, 70f / 255f);
            // грань конуса: вершина (вырожденное ребро) → основание
            WorldDraw.quadGradient(vg, new double[]{p.x, by + hgt, p.z, p.x, by + hgt, p.z,
                    px[i + 1], by, pz[i + 1], px[i], by, pz[i]}, COLOR, 220f / 255f, COLOR, 110f / 255f);
        }
    }
}
