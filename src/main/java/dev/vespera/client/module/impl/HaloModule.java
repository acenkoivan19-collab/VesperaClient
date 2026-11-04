package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Projection;
import dev.vespera.client.util.CosmeticTargets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Нимб — порт TerraHUD «Крылья и нимб», часть «нимб» (WorldRings.drawHaloFor).
 *
 * Кольцо-билборд (всегда лицом к камере) радиусом 0.32 на высоте рост + 0.55,
 * покачивается ±0.05 (период ~3 сек) + 3 искры по горизонтальной орбите.
 * Текстура круга TerraHUD: линия на 0.68 радиуса и мягкое свечение.
 */
public final class HaloModule extends HudModule {

    private final ChoiceSetting mode = setting(new ChoiceSetting("mode", "Вид", 0, "Ореол", "Кольцо")
            .hints("Кольцо всегда смотрит на камеру, как светящийся диск (TerraHUD)",
                    "Горизонтальное кольцо над головой, видно в перспективе"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFFFFD166));
    private final NumberSetting size = setting(new NumberSetting("size", "Размер", 1f, 0.5f, 2f, 0.05f));
    private final BoolSetting sparks = setting(new BoolSetting("sparks", "Искры по орбите", true));
    private final ChoiceSetting targets = setting(new ChoiceSetting("targets", "На ком", 0, "Себе", "Себе и игрокам")
            .hints("Только на тебе (вид от 3-го лица, F5)", "И на других игроках, которых ты видишь напрямую"));

    private static final double RAD = 0.32;
    private final float[] c = new float[3], s = new float[3];

    public HaloModule() {
        super("Halo", "[Дубль TerraHUD: Крылья и нимб] Светящееся кольцо над головой", Category.COSMETICS);
        about("Светящийся нимб над головой, мягко покачивается; вокруг кружат три искры. "
                + "Виден от 3-го лица (F5).");
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
        float tp = Projection.tickProgress(mc);
        for (LivingEntity e : CosmeticTargets.get(mc, targets.index() == 1)) drawFor(vg, e, tp, now);
    }

    private void drawFor(long vg, LivingEntity ent, float tp, long now) {
        Vec3d p = ent.getLerpedPos(tp);
        int COLOR = color.argb();
        double RAD = HaloModule.RAD * size.get();
        double y = p.y + ent.getHeight() + 0.55 + Math.sin(now / 500.0) * 0.05;
        if (mode.index() == 1) { // горизонтальное кольцо
            dev.vespera.client.render.WorldDraw.ring(vg, p.x, y - 0.2, p.z, RAD, 48, 2.4f, COLOR, 0.95f, true);
            if (sparks.on()) drawSparks(vg, p, y - 0.2, RAD, now, COLOR);
            return;
        }
        if (!Projection.project(p.x, y, p.z, c)) return;
        float ppb = Projection.pixelsPerBlock(c[2]);
        float r = (float) (RAD * 0.68) * ppb;
        float a = 230f / 255f;

        // билборд-кольцо: мягкий ореол + линия
        for (int pass = 0; pass < 2; pass++) {
            nvgBeginPath(vg);
            nvgCircle(vg, c[0], c[1], r);
            nvgStrokeWidth(vg, Math.max(1f, (float) RAD * ppb * (pass == 0 ? 0.6f : 0.16f)));
            nvgStrokeColor(vg, Draw.c1(COLOR, pass == 0 ? a * 0.35f : a));
            nvgStroke(vg);
        }
        if (sparks.on()) drawSparks(vg, p, y, RAD, now, COLOR);
    }

    private void drawSparks(long vg, Vec3d p, double y, double RAD, long now, int COLOR) {
        for (int i = 0; i < 3; i++) {
            double ang = now / 600.0 + 2 * Math.PI * i / 3;
            if (!Projection.project(p.x + Math.cos(ang) * RAD, y, p.z + Math.sin(ang) * RAD, s)) continue;
            float sr = Math.max(1f, 0.05f * Projection.pixelsPerBlock(s[2]));
            Draw.circle(vg, s[0], s[1], sr * 2f, COLOR, 0.25f);
            Draw.circle(vg, s[0], s[1], sr, COLOR, 1f);
        }
    }
}
