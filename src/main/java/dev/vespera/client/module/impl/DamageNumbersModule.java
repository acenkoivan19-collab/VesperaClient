package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.Projection;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Damage Numbers — всплывающие цифры урона/лечения над существами.
 * Раз в тик сравниваем здоровье видимых существ (до 20 блоков) с прошлым тиком.
 * «Не читы»: только для тех, кого видно напрямую (canSee).
 */
public final class DamageNumbersModule extends HudModule {

    private final ColorSetting dmgColor = setting(new ColorSetting("dmg", "Цвет урона", 0xFFFF5A5A));
    private final ColorSetting healColor = setting(new ColorSetting("heal", "Цвет лечения", 0xFF6BE38C));
    private final BoolSetting heals = setting(new BoolSetting("heals", "Показывать лечение", true));
    private final BoolSetting hearts = setting(new BoolSetting("hearts", "В сердечках (÷2)", false));
    private final NumberSetting size = setting(new NumberSetting("size", "Размер", 1f, 0.6f, 2f, 0.1f));

    private final Int2FloatOpenHashMap last = new Int2FloatOpenHashMap();
    /** {x, y, z, значение (−урон / +лечение), рождение мс, разброс} */
    private final List<double[]> pops = new ArrayList<>();

    public DamageNumbersModule() {
        super("Damage Numbers", "Всплывающие цифры урона над существами", Category.VISUALS);
        about("Когда существо получает урон или лечится, над ним всплывает цифра и плавно тает. "
                + "Только для тех, кого видно напрямую.");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    protected void onDisable() {
        last.clear();
        pops.clear();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) { last.clear(); return; }
        long now = System.currentTimeMillis();
        Box area = mc.player.getBoundingBox().expand(20, 10, 20);
        Int2FloatOpenHashMap seen = new Int2FloatOpenHashMap();
        for (Entity e : mc.world.getOtherEntities(mc.player, area)) {
            if (!(e instanceof LivingEntity le)) continue;
            float hp = le.getHealth();
            seen.put(le.getId(), hp);
            if (!last.containsKey(le.getId())) continue;
            float d = hp - last.get(le.getId());
            if (Math.abs(d) < 0.05f || !mc.player.canSee(le)) continue;
            if (d > 0 && !heals.on()) continue;
            pops.add(new double[]{le.getX(), le.getY() + le.getHeight() + 0.15, le.getZ(), d, now, Math.random() * 2 - 1});
        }
        last.clear();
        last.putAll(seen);
        while (pops.size() > 40) pops.remove(0);
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        if (!Projection.isReady() || pops.isEmpty()) return;
        long now = System.currentTimeMillis();
        float[] o = new float[3];
        for (Iterator<double[]> it = pops.iterator(); it.hasNext(); ) {
            double[] p = it.next();
            float t = (now - (long) p[4]) / 1100f;
            if (t >= 1f) { it.remove(); continue; }
            double rise = 0.7 * (1 - Math.pow(1 - t, 3));
            if (!Projection.project(p[0] + p[5] * 0.25, p[1] + rise, p[2], o)) continue;
            float pop = t < 0.15f ? 0.6f + t / 0.15f * 0.55f : 1.15f - Math.min(0.15f, (t - 0.15f) * 0.6f);
            float fs = Math.max(10f, Math.min(28f, Projection.pixelsPerBlock(o[2]) * 0.22f)) * size.get() * pop;
            float a = t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f;
            double v = Math.abs(p[3]) / (hearts.on() ? 2.0 : 1.0);
            String s = (p[3] < 0 ? "-" : "+") + (v >= 10 ? String.format(Locale.ROOT, "%.0f", v)
                    : String.format(Locale.ROOT, "%.1f", v));
            int c = p[3] < 0 ? dmgColor.argb() : healColor.argb();
            Draw.text(vg, s, o[0] + 1.2f, o[1] + 1.4f, Fonts.SEMIBOLD, fs, Draw.CENTER_MID, 0xFF000000, a * 0.55f);
            Draw.text(vg, s, o[0], o[1], Fonts.SEMIBOLD, fs, Draw.CENTER_MID, c, a);
        }
    }
}
