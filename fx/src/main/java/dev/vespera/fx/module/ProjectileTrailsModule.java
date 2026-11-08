package dev.vespera.fx.module;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.fx.render.Fx;
import dev.vespera.fx.render.Occlusion;
import dev.vespera.client.render.Projection;
import dev.vespera.fx.render.Quality;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Следы снарядов: стрелы, трезубцы, жемчуг, зелья, снежки, фейерверки оставляют светящийся хвост.
 * Точки добавляются, только пока снаряд виден из камеры (луч), — сквозь стены следов нет.
 */
public final class ProjectileTrailsModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Вид", 0, "Неон", "Искры", "Радуга")
            .hints("Светящаяся линия с ярким центром", "Цепочка мерцающих искр", "Линия переливается цветами"));
    private final BoolSetting byType = setting(new BoolSetting("bytype", "Свой цвет у каждого снаряда", true));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет (стрелы и прочее)", 0));
    private final NumberSetting length = setting(new NumberSetting("length", "Длина следа (сек)", 1.2f, 0.3f, 3f, 0.1f));
    private final BoolSetting ownOnly = setting(new BoolSetting("own", "Только мои снаряды", false));

    /** id → точки {x, y, z, время мс}. */
    private final Map<Integer, ArrayDeque<double[]>> trails = new HashMap<>();
    private final Map<Integer, Integer> colors = new HashMap<>();
    private final Fx.Lines lines = new Fx.Lines();
    private final Fx.Dots dots = new Fx.Dots();
    private final float[] a = new float[3], b = new float[3];

    public ProjectileTrailsModule() {
        super("Projectile Trails", "Светящиеся следы за стрелами, жемчугом, зельями", Category.VISUALS);
        about("За летящими стрелами, трезубцами, жемчугом края, зельями, снежками и фейерверками "
                + "тянется светящийся след: видно, откуда прилетело и куда упало. "
                + "След рисуется, только пока снаряд виден без преград.");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    protected void onDisable() {
        trails.clear();
        colors.clear();
    }

    private int typeColor(Entity e) {
        if (!byType.on()) return color.argb();
        if (e instanceof EnderPearlEntity) return 0xFFB45CFF;
        if (e instanceof PotionEntity) return 0xFFFF6FB4;
        if (e instanceof TridentEntity) return 0xFF4FE3FF;
        if (e instanceof FireworkRocketEntity) return 0xFFFFA14A;
        if (e instanceof SnowballEntity) return 0xFFEAF4FF;
        if (e instanceof WindChargeEntity) return 0xFFBFE8FF;
        if (e instanceof SpectralArrowEntity) return 0xFFFFE45A;
        return color.argb();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { trails.clear(); return; }
        long now = System.currentTimeMillis();
        int max = Quality.count(24);
        int seen = 0;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ProjectileEntity pe)) continue;
            if (ownOnly.on() && pe.getOwner() != mc.player) continue;
            if (e.squaredDistanceTo(mc.player) > 64 * 64) continue;
            if (seen++ >= max) break;
            ArrayDeque<double[]> t = trails.get(e.getId());
            double[] last = t == null ? null : t.peekLast();
            // застрял (стрела в блоке) или висит на месте — новых точек нет, хвост догорает
            if (last != null && Math.abs(last[0] - e.getX()) + Math.abs(last[1] - e.getY()) + Math.abs(last[2] - e.getZ()) < 0.02) continue;
            if (!Occlusion.clear(e.getX(), e.getY(), e.getZ())) continue; // за стеной — не рисуем
            if (t == null) {
                t = new ArrayDeque<>();
                trails.put(e.getId(), t);
                colors.put(e.getId(), typeColor(e));
            }
            t.addLast(new double[]{e.getX(), e.getY(), e.getZ(), now});
            while (t.size() > 80) t.pollFirst();
        }
        // старые точки и пустые следы — убираем
        long life = (long) (length.get() * 1000);
        for (Iterator<Map.Entry<Integer, ArrayDeque<double[]>>> it = trails.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, ArrayDeque<double[]>> en = it.next();
            ArrayDeque<double[]> t = en.getValue();
            while (!t.isEmpty() && now - (long) t.peekFirst()[3] > life) t.pollFirst();
            if (t.isEmpty()) {
                it.remove();
                colors.remove(en.getKey());
            }
        }
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Projection.isReady() || trails.isEmpty() || mc.world == null) return;
        float tp = Projection.tickProgress(mc);
        long now = System.currentTimeMillis();
        double life = length.get() * 1000.0;
        int st = style.index();
        boolean glow = Quality.glow();
        double time = now / 1000.0;
        if (glow) Fx.additive(vg);
        try {
            for (Map.Entry<Integer, ArrayDeque<double[]>> en : trails.entrySet()) {
                int base = colors.getOrDefault(en.getKey(), color.argb());
                double[] prev = null;
                int i = 0;
                int n = en.getValue().size();
                for (double[] pt : en.getValue()) {
                    i++;
                    if (prev == null) { prev = pt; continue; }
                    float f = 1f - (float) Math.min(1.0, (now - pt[3]) / life);     // 1 у головы → 0 в хвосте
                    f = f * f * (3f - 2f * f);
                    if (f <= 0.02f) { prev = pt; continue; }
                    int c = st == 2 ? Draw.hue((float) (i / (double) n * 0.6 + time * 0.3)) : base;
                    if (st == 1) sparks(prev, pt, f, c, time, i);
                    else neon(prev, pt, f, c, glow);
                    prev = pt;
                }
                // голова следа — плавная позиция снаряда в этом кадре (иначе хвост отстаёт на тик)
                Entity e = mc.world.getEntityById(en.getKey());
                if (prev != null && e != null && !e.isRemoved() && now - (long) prev[3] < 120) {
                    Vec3d lp = e.getLerpedPos(tp);
                    double[] head = {lp.x, lp.y, lp.z, now};
                    int c = st == 2 ? Draw.hue((float) (0.6 + time * 0.3)) : base;
                    if (st == 1) sparks(prev, head, 1f, c, time, n + 1);
                    else neon(prev, head, 1f, c, glow);
                }
            }
            lines.flush(vg);
            dots.flush(vg);
        } finally {
            if (glow) Fx.normal(vg);
        }
    }

    private void neon(double[] p0, double[] p1, float f, int c, boolean glow) {
        if (!Projection.project(p0[0], p0[1], p0[2], a) || !Projection.project(p1[0], p1[1], p1[2], b)) return;
        float px = Projection.pixelsPerBlock((a[2] + b[2]) / 2f);
        float wdt = Math.max(1f, Math.min(10f, px * 0.05f * (0.35f + 0.65f * f)));
        if (glow) lines.add(a[0], a[1], b[0], b[1], wdt * 3.5f, c, 0.22f * f);
        lines.add(a[0], a[1], b[0], b[1], wdt, Draw.lerpColor(c, 0xFFFFFFFF, 0.45f), 0.95f * f);
    }

    private void sparks(double[] p0, double[] p1, float f, int c, double time, int i) {
        for (int k = 0; k < 2; k++) {
            double t = k / 2.0;
            double x = p0[0] + (p1[0] - p0[0]) * t, y = p0[1] + (p1[1] - p0[1]) * t, z = p0[2] + (p1[2] - p0[2]) * t;
            if (!Projection.project(x, y, z, a)) continue;
            float s = Math.max(0.8f, Projection.pixelsPerBlock(a[2]) * 0.04f * (0.4f + 0.6f * f));
            float tw = (float) (0.6 + 0.4 * Math.sin(time * 18 + i * 2.3 + k));
            dots.add(a[0], a[1], s * 3f, c, 0.18f * f * tw);
            dots.add(a[0], a[1], s, Draw.lerpColor(c, 0xFFFFFFFF, 0.5f), f * tw);
        }
    }
}
