package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fx;
import dev.vespera.client.render.Occlusion;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.Quality;
import dev.vespera.client.render.WorldDraw;
import dev.vespera.client.ui.Theme;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Эффекты блоков: осколки при ломании, вспышка контура при установке, ударная волна взрыва.
 * Всё только там, где игрок реально видит место (луч из камеры).
 */
public final class BlockEffectsModule extends HudModule {

    private final BoolSetting onBreak = setting(new BoolSetting("break", "Осколки при ломании", true));
    private final BoolSetting onPlace = setting(new BoolSetting("place", "Вспышка при установке", true));
    private final BoolSetting onBoom = setting(new BoolSetting("boom", "Ударная волна взрывов", true));
    private final ChoiceSetting palette = setting(new ChoiceSetting("palette", "Цвет", 0, "Цвет блока", "Тема", "Свой")
            .hints("Осколки и контур — в цвет самого блока", "Цвет темы клиента", "Цвет ниже"));
    private final ColorSetting color = setting(new ColorSetting("color", "Свой цвет", 0xFF8FD3FF));

    private static final int BREAK = 0, PLACE = 1, BOOM = 2;
    /** {тип, x, y, z, цвет, время мс, радиус, seed} — для блоков x,y,z = угол блока, для взрыва — центр. */
    private static final ArrayDeque<double[]> FX = new ArrayDeque<>();
    /** Ждём, появится ли блок: {x1,y1,z1, x2,y2,z2, крайний срок мс, можно ли первую позицию}. */
    private static final List<Object[]> PENDING = new ArrayList<>();

    private final float[] p = new float[3];
    private final Fx.Dots dots = new Fx.Dots();

    public BlockEffectsModule() {
        super("Block Effects", "Осколки при ломании, вспышка при установке, волна взрыва", Category.VISUALS);
        about("Когда ты ломаешь блок, он разлетается светящимися осколками его цвета. Поставленный блок "
                + "коротко вспыхивает контуром. Взрывы рядом расходятся ударной волной. "
                + "Эффекты видны только там, куда ты сам смотришь без преград.");
    }

    // ------------------------------------------------------------ события

    /** ClientPlayerBlockBreakEvents.AFTER — локальный игрок сломал блок. */
    public static void onBreak(ClientWorld world, BlockPos pos, BlockState state) {
        BlockEffectsModule m = ModuleManager.get(BlockEffectsModule.class);
        if (!m.isEnabled() || !m.onBreak.on()) return;
        if (!Occlusion.clear(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) return;
        int c = state.getMapColor(world, pos).color;
        m.add(BREAK, pos.getX(), pos.getY(), pos.getZ(), c == 0 ? 0xFF9A9AA4 : (c | 0xFF000000), 0);
    }

    /** UseBlockCallback — игрок кликнул ПКМ по блоку с блоком в руке: ждём, появится ли он. */
    public static void onUse(PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockEffectsModule m = ModuleManager.get(BlockEffectsModule.class);
        if (!m.isEnabled() || !m.onPlace.on()) return;
        if (!(player.getStackInHand(hand).getItem() instanceof BlockItem)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        BlockPos a = hit.getBlockPos(), b = a.offset(hit.getSide());
        BlockState sa = mc.world.getBlockState(a), sb = mc.world.getBlockState(b);
        PENDING.add(new Object[]{a, sa, b, sb, System.currentTimeMillis() + 500, sa.isReplaceable()});
    }

    /** Взрыв (пакет сервера). */
    public static void onExplosion(Vec3d center, float radius) {
        BlockEffectsModule m = ModuleManager.get(BlockEffectsModule.class);
        if (!m.isEnabled() || !m.onBoom.on()) return;
        if (Projection.distanceTo(center.x, center.y, center.z) > 64) return;
        if (!Occlusion.clear(center.x, center.y + 0.5, center.z)) return;
        m.add(BOOM, center.x, center.y, center.z, 0xFFFFB347, Math.max(1.5, radius));
    }

    private void add(int type, double x, double y, double z, int c, double radius) {
        FX.add(new double[]{type, x, y, z, c, System.currentTimeMillis(), radius, Math.random() * 1000});
        while (FX.size() > 24) FX.pollFirst();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (PENDING.isEmpty() || mc.world == null) return;
        long now = System.currentTimeMillis();
        for (Iterator<Object[]> it = PENDING.iterator(); it.hasNext(); ) {
            Object[] q = it.next();
            BlockPos hit = null;
            BlockPos a = (BlockPos) q[0], b = (BlockPos) q[2];
            BlockState na = mc.world.getBlockState(a), nb = mc.world.getBlockState(b);
            if ((Boolean) q[5] && na != q[1] && !na.isAir()) hit = a;
            else if (nb != q[3] && !nb.isAir()) hit = b;
            if (hit != null) {
                // место должно быть видно: луч к точке чуть ближе к камере, чем центр блока
                Vec3d c = Vec3d.ofCenter(hit);
                Vec3d cam = new Vec3d(Projection.camX(), Projection.camY(), Projection.camZ());
                Vec3d toward = c.add(cam.subtract(c).normalize().multiply(0.75));
                if (Occlusion.clear(toward.x, toward.y, toward.z)) {
                    int col = mc.world.getBlockState(hit).getMapColor(mc.world, hit).color;
                    add(PLACE, hit.getX(), hit.getY(), hit.getZ(), col == 0 ? 0xFFD0D0D8 : (col | 0xFF000000), 0);
                }
                it.remove();
            } else if (now > (Long) q[4]) {
                it.remove();
            }
        }
    }

    @Override
    protected void onDisable() {
        FX.clear();
        PENDING.clear();
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    private int tint(int blockColor) {
        return switch (palette.index()) {
            case 1 -> Theme.ACCENT;
            case 2 -> color.argb();
            default -> blockColor;
        };
    }

    // ------------------------------------------------------------ отрисовка

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        if (!Projection.isReady() || FX.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean glow = Quality.glow();
        for (Iterator<double[]> it = FX.iterator(); it.hasNext(); ) {
            double[] e = it.next();
            long age = now - (long) e[5];
            int type = (int) e[0];
            long life = type == BREAK ? 900 : type == PLACE ? 650 : 1100;
            if (age > life) { it.remove(); continue; }
            if (age < 0) continue;
            float t = age / (float) life;
            int c = type == BOOM ? (palette.index() == 0 ? (int) e[4] : tint((int) e[4])) : tint((int) e[4]);
            switch (type) {
                case BREAK -> shards(vg, e, age, t, c, glow);
                case PLACE -> placed(vg, e, t, c, glow);
                default -> boom(vg, e, t, c, glow);
            }
        }
        dots.flush(vg);
    }

    /** Осколки: вылетают из центра блока, падают и ложатся на уровень пола блока. */
    private void shards(long vg, double[] e, long age, float t, int c, boolean glow) {
        // контур блока коротко расходится
        float ta = Math.min(1f, age / 320f);
        if (ta < 1f) {
            double g = 0.5 * Fx.easeOutCubic(ta) * 0.25;
            Box b = new Box(e[1] - g, e[2] - g, e[3] - g, e[1] + 1 + g, e[2] + 1 + g, e[3] + 1 + g);
            WorldDraw.box(vg, b, Draw.lerpColor(c, 0xFFFFFFFF, 0.4f), 0.8f * (1f - ta), c, 0.12f * (1f - ta));
        }
        int n = Quality.count(16);
        double sec = age / 1000.0;
        float fade = 1f - Fx.smooth(0.55f, 1f, t);
        int[] shade = {Draw.lerpColor(c, 0xFF000000, 0.25f), c, Draw.lerpColor(c, 0xFFFFFFFF, 0.3f)};
        for (int k = 0; k < 3; k++) { // три оттенка — три пачки
            nvgBeginPath(vg);
            boolean any = false;
            for (int i = k; i < n; i += 3) {
                double r1 = Fx.rnd(e[7] + i), r2 = Fx.rnd(e[7] + i * 1.9 + 3), r3 = Fx.rnd(e[7] + i * 2.7 + 7);
                double ang = r1 * Math.PI * 2, spd = 1.2 + 2.2 * r2;
                double x = e[1] + 0.5 + Math.cos(ang) * spd * sec * 0.9;
                double z = e[3] + 0.5 + Math.sin(ang) * spd * sec * 0.9;
                double y = e[2] + 0.5 + (2.2 + 2.0 * r3) * sec - 9.0 * sec * sec;
                y = Math.max(e[2] + 0.04, y); // лежат на полу
                if (!Projection.project(x, y, z, p)) continue;
                float s = Math.max(1f, Projection.pixelsPerBlock(p[2]) * (0.07f + 0.06f * (float) r3));
                nvgRect(vg, p[0] - s / 2, p[1] - s / 2, s, s);
                if (glow && k == 2) dots.add(p[0], p[1], s * 1.8f, c, 0.12f * fade);
                any = true;
            }
            if (!any) continue;
            nvgFillColor(vg, Draw.c1(shade[k], 0.95f * fade));
            nvgFill(vg);
        }
    }

    /** Установка: контур «встаёт на место» (чуть больше → ровно), заливка вспыхивает и гаснет. */
    private void placed(long vg, double[] e, float t, int c, boolean glow) {
        float snap = Fx.easeOutCubic(Math.min(1f, t * 2.5f));
        double g = 0.08 * (1 - snap);
        Box b = new Box(e[1] - g, e[2] - g, e[3] - g, e[1] + 1 + g, e[2] + 1 + g, e[3] + 1 + g);
        float a = 1f - Fx.smooth(0.3f, 1f, t);
        int edge = Draw.lerpColor(c, 0xFFFFFFFF, 0.55f);
        WorldDraw.box(vg, b, edge, 0.95f * a, c, 0.22f * a * (1f - t));
        if (glow) { // искры на верхних углах
            for (int i = 0; i < 4; i++) {
                double x = e[1] + ((i & 1) == 0 ? -0.02 : 1.02), z = e[3] + ((i & 2) == 0 ? -0.02 : 1.02);
                if (!Projection.project(x, e[2] + 1.02 + 0.3 * t, z, p)) continue;
                float s = Math.max(1f, Projection.pixelsPerBlock(p[2]) * 0.05f);
                dots.add(p[0], p[1], s * 3f, edge, 0.25f * a);
                dots.add(p[0], p[1], s, 0xFFFFFFFF, a);
            }
        }
    }

    /** Взрыв: вспышка в центре и две ударные волны по горизонтали. */
    private void boom(long vg, double[] e, float t, int c, boolean glow) {
        double rad = e[6];
        float fade = 1f - Fx.smooth(0.25f, 1f, t);
        int seg = Math.max(20, Quality.count(56));
        double r1 = rad * (0.3 + 1.5 * Fx.easeOutQuint(t));
        WorldDraw.ring(vg, e[1], e[2] + 0.1, e[3], r1, seg, 3f, c, fade, glow);
        double r2 = rad * (0.2 + 1.0 * Fx.easeOutQuint(Math.max(0f, t - 0.08f) / 0.92f));
        WorldDraw.ring(vg, e[1], e[2] + 0.6, e[3], r2, seg, 2f, Draw.lerpColor(c, 0xFFFFFFFF, 0.5f), fade * 0.6f, false);
        if (glow && Projection.project(e[1], e[2] + 0.5, e[3], p)) {
            float flash = (1f - Fx.smooth(0f, 0.35f, t));
            Fx.additive(vg);
            Fx.glow(vg, p[0], p[1], Projection.pixelsPerBlock(p[2]) * (float) rad * 1.2f, 0xFFFFD08A, 0.55f * flash);
            Fx.normal(vg);
        }
    }
}
