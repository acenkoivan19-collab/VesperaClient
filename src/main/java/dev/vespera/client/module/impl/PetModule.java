package dev.vespera.client.module.impl;

import dev.vespera.client.cosmetic.CosmeticModels;
import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.Voxel;
import dev.vespera.client.util.CosmeticTargets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Питомец-компаньон: парит у плеча, мягко покачивается и плавно поворачивается следом
 * за игроком. Воксельная модель собственного дизайна (CosmeticModels). Есть превью.
 */
public final class PetModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Питомец", 0, CosmeticModels.PET_NAMES)
            .hints(CosmeticModels.PET_HINTS));
    private final ChoiceSetting side = setting(new ChoiceSetting("side", "Где", 0, "Справа", "Слева")
            .hints("Парит у правого плеча", "Парит у левого плеча"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет перекраски", 0xFF9B7BFF));
    private final NumberSetting recolor = setting(new NumberSetting("recolor", "Перекраска", 0f, 0f, 1f, 0.05f));
    private final NumberSetting size = setting(new NumberSetting("size", "Размер", 1f, 0.6f, 1.5f, 0.05f));
    private final ChoiceSetting targets = setting(new ChoiceSetting("targets", "На ком", 0, "Себе", "Себе и игрокам")
            .hints("Только у тебя (вид от 3-го лица, F5)", "И у других игроков, которых ты видишь напрямую"));

    private final Voxel voxel = new Voxel();
    private double smoothYaw = Double.NaN;

    public PetModule() {
        super("Pet", "Питомец у плеча", Category.COSMETICS);
        about("Маленький питомец парит у плеча и следует за тобой: пингвин, мишка-табличка, "
                + "рыжий кот или призрачок. Выбранного питомца видно в превью. Виден от 3-го лица (F5).");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    protected void onDisable() {
        smoothYaw = Double.NaN;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !Projection.isReady()) return;
        float tp = Projection.tickProgress(mc);
        for (LivingEntity e : CosmeticTargets.get(mc, targets.index() == 1)) {
            double yaw = e.getBodyYaw();
            if (e == mc.player) { // свой питомец поворачивается плавно, с запаздыванием
                if (Double.isNaN(smoothYaw)) smoothYaw = yaw;
                double d = ((yaw - smoothYaw) % 360 + 540) % 360 - 180;
                smoothYaw += d * Math.min(1, dt * 6);
                yaw = smoothYaw;
            }
            Vec3d p = e.getLerpedPos(tp);
            drawAt(vg, p.x, p.y, p.z, e.getHeight(), yaw, System.currentTimeMillis() / 1000.0 + e.getId());
        }
    }

    private void drawAt(long vg, double x, double y, double z, double hgt, double yawDeg, double t) {
        double r = Math.toRadians(yawDeg);
        double rx = -Math.cos(r), rz = -Math.sin(r);           // вправо
        double bx = Math.sin(r), bz = -Math.cos(r);            // назад
        double s = side.index() == 0 ? 1 : -1;
        double bob = Math.sin(t * 2.2) * 0.06;
        double px = x + rx * 0.78 * s + bx * 0.15, pz = z + rz * 0.78 * s + bz * 0.15;
        double py = y + hgt * 0.62 + bob;
        voxel.reset();
        CosmeticModels.pet(voxel, style.index(), t, color.argb(), recolor.get());
        voxel.render(vg, px, py, pz, yawDeg, size.get() * 0.85 / 16.0, 1f);
    }

    @Override
    public boolean hasPreview() {
        return true;
    }

    @Override
    public float previewHeight() {
        return 130f;
    }

    @Override
    public void renderPreview(long vg, float x, float y, float w, float h) {
        double t = System.currentTimeMillis() / 1000.0;
        // питомца показываем спереди (камера на стороне +Z смотрит на −Z) и медленно вращаем
        Projection.beginPreview(x, y, w, h, 0, 0.42, 0, 2.1, 180 + Math.sin(t * 0.7) * 50, 8, 30);
        try {
            voxel.reset();
            CosmeticModels.pet(voxel, style.index(), t, color.argb(), recolor.get());
            voxel.render(vg, 0, Math.sin(t * 2.2) * 0.04, 0, 0, size.get() * 0.85 / 16.0, 1f);
        } finally {
            Projection.endPreview();
        }
    }
}
