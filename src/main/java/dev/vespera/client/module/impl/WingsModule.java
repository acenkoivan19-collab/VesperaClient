package dev.vespera.client.module.impl;

import dev.vespera.client.cosmetic.CosmeticModels;
import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.Voxel;
import dev.vespera.client.render.WorldDraw;
import dev.vespera.client.util.CosmeticTargets;
import dev.vespera.client.util.MotionTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Крылья за спиной.
 *  0–2 — полупрозрачные «световые» крылья (Перья / Острые / Бабочка);
 *  3–6 — объёмные воксельные крылья собственного дизайна (Кристальные, Праздничные,
 *        Ангельские, Тёмные перья), см. CosmeticModels.
 * В панели настроек — живое превью на манекене: видно, что именно выбираешь.
 */
public final class WingsModule extends HudModule {

    private static final String[] NAMES = {"Перья", "Острые", "Бабочка",
            CosmeticModels.WING_NAMES[0], CosmeticModels.WING_NAMES[1], CosmeticModels.WING_NAMES[2], CosmeticModels.WING_NAMES[3]};

    private final ChoiceSetting shape = setting(new ChoiceSetting("shape", "Крылья", 0, NAMES)
            .hints("Два слоя мягких светящихся перьев", "Один длинный узкий слой, взмах резче",
                    "Широкие округлые крылья из двух лопастей",
                    CosmeticModels.WING_HINTS[0], CosmeticModels.WING_HINTS[1],
                    CosmeticModels.WING_HINTS[2], CosmeticModels.WING_HINTS[3]));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFFF2EFE3));
    private final NumberSetting recolor = setting(new NumberSetting("recolor", "Перекраска объёмных крыльев", 0f, 0f, 1f, 0.05f));
    private final NumberSetting size = setting(new NumberSetting("size", "Размах", 10, 5, 20, 1));
    private final NumberSetting opacity = setting(new NumberSetting("opacity", "Яркость", 0.8f, 0.2f, 1f, 0.05f));
    private final ChoiceSetting targets = setting(new ChoiceSetting("targets", "На ком", 0, "Себе", "Себе и игрокам")
            .hints("Только на тебе (вид от 3-го лица, F5)", "И на других игроках, которых ты видишь напрямую"));

    private final Voxel voxel = new Voxel();
    private long lastT;
    private double phase;

    public WingsModule() {
        super("Wings", "Крылья за спиной: световые и объёмные", Category.COSMETICS);
        about("Машущие крылья за спиной. Есть световые (Перья, Острые, Бабочка) и объёмные "
                + "(Кристальные, Праздничные, Ангельские, Тёмные перья). Выбранные крылья видно в превью. "
                + "В полёте машут быстрее. Видны от 3-го лица (F5).");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    protected void onDisable() {
        lastT = 0;
        phase = 0;
    }

    private void advance(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        long step = lastT == 0 ? 0 : Math.max(0, Math.min(100, now - lastT));
        lastT = now;
        float rate = 0.006f;
        if (mc.player != null && (mc.player.isGliding() || mc.player.getAbilities().flying)) rate = 0.014f;
        else if (now - MotionTracker.lastMoveMs() < 1200) rate = 0.009f;
        phase += step * rate;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !Projection.isReady()) return;
        advance(mc);
        float tp = Projection.tickProgress(mc);
        for (LivingEntity e : CosmeticTargets.get(mc, targets.index() == 1)) {
            Vec3d p = e.getLerpedPos(tp);
            double y = p.y - (e.isInSneakingPose() ? 0.18 : 0);
            drawAt(vg, p.x, y, p.z, e.getHeight(), e.getBodyYaw());
        }
    }

    /** Нарисовать крылья для тела в точке (x, y, z) с поворотом yawDeg. */
    private void drawAt(long vg, double x, double y, double z, double hgt, double yawDeg) {
        int sh = shape.index();
        double flap01 = 0.5 + 0.5 * Math.sin(phase);
        if (sh >= 3) {
            double fold = 0.35 + 0.45 * flap01;                       // насколько отведены назад
            double lift = 0.05 + 0.18 * (1 - flap01);
            double k = 0.6 + size.get() * 0.04;                       // размах: 10 → ×1.0
            voxel.reset();
            voxel.translate(0, 21.5, 2.6).scale(k).translate(0, -21.5, -2.6); // масштаб от плеч
            CosmeticModels.wings(voxel, sh - 3, fold, lift, System.currentTimeMillis() / 1000.0,
                    color.argb(), recolor.get());
            voxel.render(vg, x, y, z, yawDeg, hgt / 1.8 / 16.0, Math.min(1f, opacity.get() + 0.2f));
            return;
        }
        double flap = 0.3 + 0.55 * flap01;
        if (sh == 1) flap = 0.25 + 0.7 * Math.pow(0.5 + 0.5 * Math.sin(phase * 1.3), 2);
        float len = 0.35f + size.get() * 0.045f * (sh == 1 ? 1.25f : 1f);
        double yaw = Math.toRadians(yawDeg);
        drawLight(vg, x, y, z, hgt, -Math.sin(yaw), Math.cos(yaw), len, flap);
    }

    /** Световые крылья (стили 0–2): полупрозрачные градиентные лопасти. */
    private void drawLight(long vg, double px, double py, double pz, double hgt, double lx, double lz,
                           float len, double flap) {
        int COLOR = color.argb();
        float op = opacity.get();
        int sh = shape.index();
        double rx = lz, rz = -lx;   // вправо
        double bx = -lx, bz = -lz;  // назад
        double sy = py + hgt * 0.74;  // плечи
        double spread = Math.cos(flap), lift = Math.sin(flap);

        for (int side = -1; side <= 1; side += 2) {
            double shx = px + rx * 0.22 * side + bx * 0.2;
            double shz = pz + rz * 0.22 * side + bz * 0.2;
            double tipx = px + rx * side * (0.22 + len * spread) + bx * 0.25;
            double tipz = pz + rz * side * (0.22 + len * spread) + bz * 0.25;
            double tipy = sy + 0.15 + len * lift * 0.7;
            if (sh == 1) {
                WorldDraw.quadGradient(vg, new double[]{
                        shx, sy + 0.1, shz, tipx, tipy + 0.1, tipz, tipx, tipy - 0.15, tipz, shx, sy - 0.35, shz},
                        COLOR, op, COLOR, 0f);
                continue;
            }
            if (sh == 2) {
                WorldDraw.quadGradient(vg, new double[]{
                        shx, sy + 0.1, shz, tipx, tipy + 0.25, tipz, tipx, tipy - 0.45, tipz, shx, sy - 0.15, shz},
                        COLOR, op, COLOR, op * 0.25f);
                double lx2 = shx + (tipx - shx) * 0.7, lz2 = shz + (tipz - shz) * 0.7;
                WorldDraw.quadGradient(vg, new double[]{
                        shx, sy - 0.15, shz, lx2, tipy - 0.5, lz2, lx2, tipy - 0.95, lz2, shx, sy - 0.45, shz},
                        COLOR, op * 0.8f, COLOR, op * 0.1f);
                continue;
            }
            WorldDraw.quadGradient(vg, new double[]{
                    shx, sy + 0.15, shz, tipx, tipy, tipz, tipx, tipy - 0.55, tipz, shx, sy - 0.5, shz},
                    COLOR, op, COLOR, 0f);
            WorldDraw.quadGradient(vg, new double[]{
                    shx, sy - 0.1, shz, tipx, tipy - 0.25, tipz, tipx, tipy - 0.8, tipz, shx, sy - 0.6, shz},
                    COLOR, op * 0.45f, COLOR, 0f);
        }
    }

    // ------------------------------------------------------------ превью

    @Override
    public boolean hasPreview() {
        return true;
    }

    @Override
    public float previewHeight() {
        return 150f;
    }

    @Override
    public void renderPreview(long vg, float x, float y, float w, float h) {
        advance(MinecraftClient.getInstance());
        double t = System.currentTimeMillis() / 1000.0;
        // камера сзади, плавно покачивается влево-вправо, чтобы было видно объём
        double yaw = Math.sin(t * 0.6) * 38;
        Projection.beginPreview(x, y, w, h, 0, 1.25, 0, 4.6, yaw, 10, 32);
        try {
            voxel.reset().mannequin().render(vg, 0, 0, 0, 0, 1 / 16.0, 1f);
            drawAt(vg, 0, 0, 0, 1.8, 0);
        } finally {
            Projection.endPreview();
        }
    }
}
