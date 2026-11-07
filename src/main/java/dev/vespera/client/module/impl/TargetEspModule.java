package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.WorldDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Target ESP — красивый маркер на том, кого ты бьёшь.
 * «Не читы»: рисуется ТОЛЬКО для цели в прямой видимости (canSee) и только после твоего удара.
 * Стили: Кольцо (ездит вверх-вниз со шлейфом), Призраки (светящиеся огоньки по спирали),
 * Маркер (вращающийся ромб с уголками на экране).
 */
public final class TargetEspModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Стиль", 0, "Кольцо", "Призраки", "Маркер")
            .hints("Светящееся кольцо скользит по цели вверх-вниз со шлейфом",
                    "Три огонька летают вокруг цели по спирали",
                    "Вращающийся ромб с уголками поверх цели"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0));
    private final ColorSetting hurtColor = setting(new ColorSetting("hurt", "Цвет при попадании", 0xFFFF5A6E));
    private final NumberSetting speed = setting(new NumberSetting("speed", "Скорость", 1f, 0.3f, 3f, 0.1f));
    private final NumberSetting hold = setting(new NumberSetting("hold", "Держать цель (сек)", 4f, 1f, 10f, 0.5f));

    private static LivingEntity target;
    private static long hitAt;
    private float alpha;

    public TargetEspModule() {
        super("Target ESP", "Маркер на цели, которую бьёшь", Category.VISUALS);
        about("После удара на цели появляется эффект: кольцо, огоньки или маркер. "
                + "Рисуется только когда цель видно напрямую, за стенами — нет.");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    public static void onAttack(Entity e) {
        if (e instanceof LivingEntity le) {
            target = le;
            hitAt = System.currentTimeMillis();
        }
    }

    @Override
    protected void onDisable() {
        target = null;
        alpha = 0;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !Projection.isReady()) return;
        LivingEntity t = target;
        boolean valid = t != null && t.isAlive() && !t.isRemoved()
                && System.currentTimeMillis() - hitAt < hold.get() * 1000
                && mc.player.distanceTo(t) < 24 && mc.player.canSee(t);
        alpha += ((valid ? 1f : 0f) - alpha) * Math.min(1f, dt * 8f);
        if (t == null || alpha < 0.02f) { if (!valid && alpha < 0.02f) target = null; return; }

        Vec3d p = t.getLerpedPos(Projection.tickProgress(mc));
        double th = t.getHeight(), r = t.getWidth() * 0.75 + 0.15;
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        int c = Draw.lerpColor(color.argb(), hurtColor.argb(), t.hurtTime > 0 ? t.hurtTime / 10f : 0f);

        switch (style.index()) {
            case 1 -> ghosts(vg, p, th, r, time, c);
            case 2 -> marker(vg, p, th, time, c);
            default -> ring(vg, p, th, r, time, c);
        }
    }

    private void ring(long vg, Vec3d p, double th, double r, double time, int c) {
        for (int k = 5; k >= 0; k--) {           // шлейф: несколько колец с отставанием
            double s = 0.5 + 0.5 * Math.sin(time * 2.2 - k * 0.12);
            double y = p.y + 0.05 + s * (th - 0.1);
            float a = alpha * (k == 0 ? 1f : 0.35f * (1f - k / 6f));
            WorldDraw.ring(vg, p.x, y, p.z, r, 48, k == 0 ? 2.2f : 1.6f, c, a, k == 0);
        }
    }

    private void ghosts(long vg, Vec3d p, double th, double r, double time, int c) {
        float[] o = new float[3];
        for (int g = 0; g < 3; g++) {
            for (int k = 14; k >= 0; k--) {       // огонёк + хвост из точек
                double tt = time * 2.0 - k * 0.045 + g * (Math.PI * 2 / 3);
                double y = p.y + th * (0.5 + 0.42 * Math.sin(time * 1.3 + g * 2.1 - k * 0.03));
                if (!Projection.project(p.x + Math.cos(tt) * r, y, p.z + Math.sin(tt) * r, o)) continue;
                float px = Projection.pixelsPerBlock(o[2]);
                float sz = Math.max(1.2f, px * 0.09f * (1f - k / 16f));
                float a = alpha * (1f - k / 15f);
                if (k == 0) Draw.circle(vg, o[0], o[1], sz * 3.2f, c, a * 0.18f);
                Draw.circle(vg, o[0], o[1], sz, k == 0 ? Draw.lerpColor(c, 0xFFFFFFFF, 0.5f) : c, a);
            }
        }
    }

    private void marker(long vg, Vec3d p, double th, double time, int c) {
        float[] o = new float[3];
        if (!Projection.project(p.x, p.y + th * 0.55, p.z, o)) return;
        float s = Math.max(14f, Math.min(70f, Projection.pixelsPerBlock(o[2]) * 0.55f));
        nvgSave(vg);
        nvgTranslate(vg, o[0], o[1]);
        nvgRotate(vg, (float) (time * 1.6));
        float a = alpha;
        nvgBeginPath(vg);
        nvgMoveTo(vg, 0, -s); nvgLineTo(vg, s, 0); nvgLineTo(vg, 0, s); nvgLineTo(vg, -s, 0); nvgClosePath(vg);
        nvgFillColor(vg, Draw.c1(c, a * 0.10f));
        nvgFill(vg);
        nvgStrokeWidth(vg, 4f);
        nvgStrokeColor(vg, Draw.c1(c, a * 0.18f));
        nvgStroke(vg);
        nvgStrokeWidth(vg, 1.6f);
        nvgStrokeColor(vg, Draw.c1(c, a));
        nvgStroke(vg);
        // уголки снаружи
        float g = s * 1.35f, l = s * 0.35f;
        nvgBeginPath(vg);
        for (int q = 0; q < 4; q++) {
            nvgSave(vg);
            nvgRotate(vg, (float) (q * Math.PI / 2));
            nvgMoveTo(vg, g - l, -g); nvgLineTo(vg, g, -g); nvgLineTo(vg, g, -g + l);
            nvgRestore(vg);
        }
        nvgStrokeWidth(vg, 2f);
        nvgStrokeColor(vg, Draw.c1(Draw.lerpColor(c, 0xFFFFFFFF, 0.35f), a * 0.9f));
        nvgStroke(vg);
        nvgRestore(vg);
    }
}
