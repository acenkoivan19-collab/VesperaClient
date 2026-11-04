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
import dev.vespera.client.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

/** Светящаяся обводка и заливка блока под прицелом (плавно перетекает между блоками). */
public final class BlockOverlayModule extends HudModule {

    private final Anim minX = new Anim(0, 28f), minY = new Anim(0, 28f), minZ = new Anim(0, 28f);
    private final Anim maxX = new Anim(0, 28f), maxY = new Anim(0, 28f), maxZ = new Anim(0, 28f);
    private final Anim alpha = new Anim(0f, 16f);
    private boolean hasBox;

    private final ChoiceSetting mode = setting(new ChoiceSetting("mode", "Вид", 0, "Контур + заливка", "Контур", "Заливка")
            .hints("Светящиеся рёбра и лёгкая заливка граней", "Только рёбра блока", "Только полупрозрачные грани"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", ColorSetting.THEME));
    private final NumberSetting fill = setting(new NumberSetting("fill", "Плотность заливки", 0.10f, 0.03f, 0.4f, 0.01f));
    private final BoolSetting smooth = setting(new BoolSetting("smooth", "Плавный переход", true));

    public BlockOverlayModule() {
        super("Block Overlay", "[Дубль TerraHUD: Контур блока] Обводка блока под прицелом", Category.VISUALS);
        about("Подсвечивает блок под прицелом: контур, заливка или оба. Подсветка плавно "
                + "перетекает между блоками.");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    protected void onDisable() {
        hasBox = false;
        alpha.snap(0f);
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !Projection.isReady()) return;

        Box target = null;
        if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
            if (!shape.isEmpty()) target = shape.getBoundingBox().offset(pos.getX(), pos.getY(), pos.getZ()).expand(0.002);
        }

        if (target != null) {
            if (!hasBox || !smooth.on()) { // первый блок — без «прилёта» из (0,0,0)
                minX.snap((float) target.minX); minY.snap((float) target.minY); minZ.snap((float) target.minZ);
                maxX.snap((float) target.maxX); maxY.snap((float) target.maxY); maxZ.snap((float) target.maxZ);
                hasBox = true;
            }
            minX.target = (float) target.minX; minY.target = (float) target.minY; minZ.target = (float) target.minZ;
            maxX.target = (float) target.maxX; maxY.target = (float) target.maxY; maxZ.target = (float) target.maxZ;
        }
        alpha.target = target != null ? 1f : 0f;
        alpha.update(dt);
        for (Anim a : new Anim[]{minX, minY, minZ, maxX, maxY, maxZ}) a.update(dt);
        if (!hasBox || alpha.value < 0.01f) {
            if (alpha.value < 0.01f) hasBox = false;
            return;
        }

        Box b = new Box(minX.value, minY.value, minZ.value, maxX.value, maxY.value, maxZ.value);
        int c = color.argb(), m = mode.index();
        WorldDraw.box(vg, b, c, m == 2 ? 0f : 0.95f * alpha.value, c, m == 1 ? 0f : fill.get() * alpha.value);
    }
}
