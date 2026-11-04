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
import dev.vespera.client.util.MotionTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Крылья — порт TerraHUD «Крылья и нимб», часть «крылья» (WorldRings.drawWingsFor).
 *
 * Два слоя полупрозрачных «перьев» на каждую сторону, растут от плеч (0.74 роста),
 * взмах flap = 0.3 + 0.55·(0.5 + 0.5·sin(фаза)). Скорость взмаха: 0.006/мс стоя,
 * 0.009 если недавно двигался, 0.014 в полёте/на элитрах. Нимб — отдельный модуль Halo.
 */
public final class WingsModule extends HudModule {

    private final ChoiceSetting shape = setting(new ChoiceSetting("shape", "Форма", 0, "Перья", "Острые", "Бабочка")
            .hints("Два слоя мягких перьев (как в TerraHUD)", "Один длинный узкий слой, взмах резче",
                    "Широкие округлые крылья из двух лопастей"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFFF2EFE3));
    private final NumberSetting size = setting(new NumberSetting("size", "Размах", 10, 5, 20, 1));
    private final NumberSetting opacity = setting(new NumberSetting("opacity", "Яркость", 0.8f, 0.2f, 1f, 0.05f));
    private final ChoiceSetting targets = setting(new ChoiceSetting("targets", "На ком", 0, "Себе", "Себе и игрокам")
            .hints("Только на тебе (вид от 3-го лица, F5)", "И на других игроках, которых ты видишь напрямую"));

    private long lastT;
    private double phase;

    public WingsModule() {
        super("Wings", "[Дубль TerraHUD: Крылья и нимб] Машущие крылья за спиной", Category.COSMETICS);
        about("Полупрозрачные машущие крылья за спиной. В полёте и на элитрах машут быстрее. "
                + "Видны от 3-го лица (F5).");
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

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !Projection.isReady()) return;

        long now = System.currentTimeMillis();
        long step = lastT == 0 ? 0 : Math.max(0, Math.min(100, now - lastT));
        lastT = now;
        float rate = 0.006f;
        if (mc.player.isGliding() || mc.player.getAbilities().flying) rate = 0.014f;
        else if (now - MotionTracker.lastMoveMs() < 1200) rate = 0.009f;
        phase += step * rate;
        double flap = 0.3 + 0.55 * (0.5 + 0.5 * Math.sin(phase));

        if (shape.index() == 1) flap = 0.25 + 0.7 * Math.pow(0.5 + 0.5 * Math.sin(phase * 1.3), 2); // резче
        float len = 0.35f + size.get() * 0.045f * (shape.index() == 1 ? 1.25f : 1f);
        float tp = Projection.tickProgress(mc);
        for (LivingEntity e : CosmeticTargets.get(mc, targets.index() == 1)) drawFor(vg, e, tp, len, flap);
    }

    private void drawFor(long vg, LivingEntity ent, float tp, float len, double flap) {
        int COLOR = color.argb();
        float op = opacity.get();
        int sh = shape.index();
        Vec3d p = ent.getLerpedPos(tp);
        double hgt = ent.getHeight();
        Vec3d look = ent.getRotationVec(tp);
        double lx = look.x, lz = look.z, ll = Math.hypot(lx, lz);
        if (ll < 1e-4) { lx = 0; lz = 1; ll = 1; }
        lx /= ll; lz /= ll;
        double rx = lz, rz = -lx;   // вправо
        double bx = -lx, bz = -lz;  // назад
        double sy = p.y + hgt * 0.74;  // плечи
        double spread = Math.cos(flap), lift = Math.sin(flap);

        for (int side = -1; side <= 1; side += 2) {
            double shx = p.x + rx * 0.22 * side + bx * 0.2;
            double shz = p.z + rz * 0.22 * side + bz * 0.2;
            double tipx = p.x + rx * side * (0.22 + len * spread) + bx * 0.25;
            double tipz = p.z + rz * side * (0.22 + len * spread) + bz * 0.25;
            double tipy = sy + 0.15 + len * lift * 0.7;
            if (sh == 1) { // острые: один длинный узкий клин
                WorldDraw.quadGradient(vg, new double[]{
                        shx, sy + 0.1, shz, tipx, tipy + 0.1, tipz, tipx, tipy - 0.15, tipz, shx, sy - 0.35, shz},
                        COLOR, op, COLOR, 0f);
                continue;
            }
            if (sh == 2) { // бабочка: верхняя большая и нижняя малая лопасти
                WorldDraw.quadGradient(vg, new double[]{
                        shx, sy + 0.1, shz, tipx, tipy + 0.25, tipz, tipx, tipy - 0.45, tipz, shx, sy - 0.15, shz},
                        COLOR, op, COLOR, op * 0.25f);
                double lx2 = shx + (tipx - shx) * 0.7, lz2 = shz + (tipz - shz) * 0.7;
                WorldDraw.quadGradient(vg, new double[]{
                        shx, sy - 0.15, shz, lx2, tipy - 0.5, lz2, lx2, tipy - 0.95, lz2, shx, sy - 0.45, shz},
                        COLOR, op * 0.8f, COLOR, op * 0.1f);
                continue;
            }
            // перья: основной слой 200 → 0 сверху вниз
            WorldDraw.quadGradient(vg, new double[]{
                    shx, sy + 0.15, shz, tipx, tipy, tipz, tipx, tipy - 0.55, tipz, shx, sy - 0.5, shz},
                    COLOR, op, COLOR, 0f);
            // нижний слой 90 → 0
            WorldDraw.quadGradient(vg, new double[]{
                    shx, sy - 0.1, shz, tipx, tipy - 0.25, tipz, tipx, tipy - 0.8, tipz, shx, sy - 0.6, shz},
                    COLOR, op * 0.45f, COLOR, 0f);
        }
    }
}
