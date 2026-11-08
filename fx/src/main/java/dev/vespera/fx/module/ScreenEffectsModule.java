package dev.vespera.fx.module;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.fx.render.Fx;
import dev.vespera.client.render.Projection;
import dev.vespera.fx.render.Quality;
import dev.vespera.client.render.WorldDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Эффекты экрана (рисуются под HUD): мягкая виньетка, пульс по краям при низком здоровье,
 * красная вспышка при уроне и линии скорости при быстром полёте/беге.
 * Только картинка — ничего не подсказывает сверх того, что игрок и так видит.
 */
public final class ScreenEffectsModule extends HudModule {

    private final NumberSetting vignette = setting(new NumberSetting("vignette", "Виньетка (затемнение краёв)", 0.35f, 0f, 1f, 0.05f));
    private final ColorSetting vignetteColor = setting(new ColorSetting("vcolor", "Цвет виньетки", 0xFF000000));
    private final BoolSetting lowHp = setting(new BoolSetting("lowhp", "Пульс при низком здоровье", true));
    private final NumberSetting lowHpAt = setting(new NumberSetting("lowhpat", "Порог здоровья (HP)", 8f, 2f, 14f, 1f));
    private final BoolSetting hurt = setting(new BoolSetting("hurt", "Вспышка при уроне", true));
    private final BoolSetting speedLines = setting(new BoolSetting("speed", "Линии скорости", true));
    private final NumberSetting speedFrom = setting(new NumberSetting("speedfrom", "Линии с какой скорости (бл/с)", 9f, 5f, 30f, 0.5f));

    private static final int RED = 0xFFE0283A;

    private int lastHurt;
    private long hurtAt;
    private float speedK;              // 0..1 — сила линий скорости (плавно)
    private Vec3d lastPos;
    private long lastPosNs;
    private float speedBps;
    private final Fx.Lines lines = new Fx.Lines();

    public ScreenEffectsModule() {
        super("Screen Effects", "Виньетка, пульс при низком HP, вспышка урона, линии скорости", Category.VISUALS);
        about("Кинематографичные эффекты экрана: мягкое затемнение краёв, красный пульс по краям, "
                + "когда мало здоровья, короткая вспышка при получении урона и линии скорости, "
                + "когда летишь на элитрах или быстро бежишь.");
    }

    @Override
    public int layer() {
        return LAYER_TINT;
    }

    @Override
    protected void onDisable() {
        lastPos = null;
        speedK = 0f;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        if (p.hurtTime > lastHurt && p.hurtTime > 0) hurtAt = System.currentTimeMillis();
        lastHurt = p.hurtTime;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || isEditing()) return;
        float m = Math.min(w, h);

        // 1) мягкая виньетка
        float v = vignette.get();
        if (v > 0.01f) edges(vg, w, h, m * 0.55f, m * 0.75f, vignetteColor.argb(), v * 0.85f);

        // 2) пульс при низком здоровье: чем меньше HP, тем сильнее и чаще «сердцебиение»
        float hp = p.getHealth();
        if (lowHp.on() && hp > 0f && hp <= lowHpAt.get()) {
            float lack = 1f - hp / lowHpAt.get();                 // 0..1
            double rate = 1.1 + 1.4 * lack;                         // ударов в секунду
            double ph = (System.nanoTime() / 1e9 * rate) % 1.0;
            // двойной удар сердца: тук-тук … пауза
            float beat = (float) (Math.exp(-Math.pow((ph - 0.08) / 0.06, 2)) + 0.6 * Math.exp(-Math.pow((ph - 0.3) / 0.07, 2)));
            float a = (0.18f + 0.32f * lack) * (0.55f + 0.45f * beat);
            edges(vg, w, h, m * 0.42f, m * 0.6f, RED, a);
        }

        // 3) вспышка при уроне
        if (hurt.on()) {
            long age = System.currentTimeMillis() - hurtAt;
            if (age >= 0 && age < 450) {
                float a = 0.45f * (1f - Fx.smooth(0f, 1f, age / 450f));
                edges(vg, w, h, m * 0.35f, m * 0.7f, RED, a);
            }
        }

        // 4) линии скорости
        if (speedLines.on()) speed(vg, mc, p, w, h, dt);
    }

    /** Края экрана цветом c (box-градиент: центр прозрачный, к краям — цвет). */
    private static void edges(long vg, float w, float h, float inset, float feather, int c, float a) {
        float ix = Math.min(inset, w * 0.45f), iy = Math.min(inset, h * 0.45f);
        nvgBoxGradient(vg, ix * 0.5f, iy * 0.5f, w - ix, h - iy, Math.min(w, h) * 0.25f, feather,
                Draw.c1(c, 0f), Draw.c2(c, Math.min(1f, a)), WorldDraw.paint());
        nvgBeginPath(vg);
        nvgRect(vg, 0, 0, w, h);
        nvgFillPaint(vg, WorldDraw.paint());
        nvgFill(vg);
    }

    private void speed(long vg, MinecraftClient mc, ClientPlayerEntity p, float w, float h, float dt) {
        // скорость по интерполированной позиции (бл/с), сглаженная
        Vec3d pos = p.getLerpedPos(Projection.tickProgress(mc));
        long now = System.nanoTime();
        if (lastPos != null) {
            double sec = (now - lastPosNs) / 1e9;
            if (sec > 0.001) {
                double inst = Math.min(80, pos.distanceTo(lastPos) / sec);
                speedBps += (float) (inst - speedBps) * Math.min(1f, (float) sec * 6f);
            }
        }
        lastPos = pos;
        lastPosNs = now;
        float from = speedFrom.get();
        float want = Math.max(0f, Math.min(1f, (speedBps - from) / (from * 1.2f)));
        speedK += (want - speedK) * Math.min(1f, dt * 4f);
        if (speedK <= 0.01f) return;

        int n = Quality.count(34);
        float cx = w / 2f, cy = h / 2f;
        float maxR = (float) Math.hypot(cx, cy);
        double time = now / 1e9;
        for (int i = 0; i < n; i++) {
            // каждая линия живёт свой цикл и появляется под новым углом
            double rate = 1.6 + 1.2 * Fx.rnd(i * 3.1);
            double cyc = time * rate + Fx.rnd(i * 7.7);
            long id = (long) Math.floor(cyc);
            float ph = (float) (cyc - id);
            double ang = Fx.rnd(i * 13.3 + id * 0.731) * Math.PI * 2;
            float r0 = maxR * (0.42f + 0.5f * ph);
            float len = maxR * (0.06f + 0.16f * speedK) * (0.6f + 0.4f * (float) Fx.rnd(i + id * 1.7));
            float a = speedK * 0.5f * (float) Math.sin(Math.PI * ph);
            float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
            lines.add(cx + dx * r0, cy + dy * r0, cx + dx * (r0 + len), cy + dy * (r0 + len),
                    1.2f + 0.8f * speedK, 0xFFFFFFFF, a);
        }
        lines.flush(vg);
    }
}
