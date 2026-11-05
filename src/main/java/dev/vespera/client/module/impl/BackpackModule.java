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
 * Рюкзак на спине — объёмная воксельная косметика собственного дизайна (CosmeticModels).
 * В панели настроек — живое превью на манекене.
 */
public final class BackpackModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Рюкзак", 0, CosmeticModels.BACKPACK_NAMES)
            .hints(CosmeticModels.BACKPACK_HINTS));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет перекраски", 0xFF9B7BFF));
    private final NumberSetting recolor = setting(new NumberSetting("recolor", "Перекраска", 0f, 0f, 1f, 0.05f));
    private final NumberSetting size = setting(new NumberSetting("size", "Размер", 1f, 0.7f, 1.3f, 0.05f));
    private final ChoiceSetting targets = setting(new ChoiceSetting("targets", "На ком", 0, "Себе", "Себе и игрокам")
            .hints("Только на тебе (вид от 3-го лица, F5)", "И на других игроках, которых ты видишь напрямую"));

    private final Voxel voxel = new Voxel();

    public BackpackModule() {
        super("Backpack", "Рюкзак на спине", Category.COSMETICS);
        about("Объёмный рюкзак на спине: лягушонок, кожаный, «молния», красный или фирменный Vespera. "
                + "Выбранный рюкзак видно в превью. Виден от 3-го лица (F5).");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !Projection.isReady()) return;
        float tp = Projection.tickProgress(mc);
        for (LivingEntity e : CosmeticTargets.get(mc, targets.index() == 1)) {
            Vec3d p = e.getLerpedPos(tp);
            double y = p.y - (e.isInSneakingPose() ? 0.18 : 0);
            drawAt(vg, p.x, y, p.z, e.getHeight(), e.getBodyYaw());
        }
    }

    private void drawAt(long vg, double x, double y, double z, double hgt, double yawDeg) {
        voxel.reset();
        voxel.translate(0, 17, 2).scale(size.get()).translate(0, -17, -2);   // масштаб от центра спины
        CosmeticModels.backpack(voxel, style.index(), color.argb(), recolor.get());
        voxel.render(vg, x, y, z, yawDeg, hgt / 1.8 / 16.0, 1f);
    }

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
        double t = System.currentTimeMillis() / 1000.0;
        Projection.beginPreview(x, y, w, h, 0, 1.15, 0, 3.6, Math.sin(t * 0.6) * 45, 12, 34);
        try {
            voxel.reset().mannequin().render(vg, 0, 0, 0, 0, 1 / 16.0, 1f);
            drawAt(vg, 0, 0, 0, 1.8, 0);
        } finally {
            Projection.endPreview();
        }
    }
}
