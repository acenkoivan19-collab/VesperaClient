package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.Projection;
import dev.vespera.client.ui.Anim;
import net.minecraft.entity.Entity;

import java.util.ArrayDeque;
import java.util.Iterator;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Хит-партиклы — порт TerraHUD «Хит-партиклы» (TerraHudClient.onAttack + drawHitBurst).
 *
 * На каждый удар — вспышка в точке попадания (0.6 роста цели), живёт 420 мс:
 * размер 14 → 40 px (smoothstep) с пружинным появлением за 120 мс, яркость (1−t)².
 * 7 стилей: 0 кольцо, 1 звезда, 2 орбита, 3 вспышка-круг, 4 крест, 5 искры, 6 пузыри (×комбо).
 * Пиксели TerraHUD — в GUI-масштабе, у нас HUD = 2× GUI px, поэтому всё × K.
 */
public final class HitBurstModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Вид вспышки", 0,
            "Кольцо", "Звезда", "Орбита", "Вспышка", "Крест", "Искры", "Пузырь")
            .hints("8 лучей и двойное кольцо", "10 лучей разной длины", "3 точки кружат вокруг метки",
                    "Яркое кольцо и короткие лучи", "Оси и диагонали", "Разлетающиеся точки",
                    "Всплывающий круг со счётчиком комбо ×N"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFFFFD166));
    private final NumberSetting size = setting(new NumberSetting("size", "Размер", 1f, 0.5f, 2f, 0.05f));
    private static int COLOR = 0xFFFFD166; // текущий цвет кадра (для статических помощников)

    private static final long LIFE_MS = 420;
    private static final float K = 2f;

    /** {x, y, z, рождение мс} */
    private static final ArrayDeque<double[]> BURSTS = new ArrayDeque<>();
    private static int combo;
    private static long lastHitAt;

    private final float[] p = new float[3];

    public HitBurstModule() {
        super("Hit Burst", "[Дубль TerraHUD: Хит-партиклы] Вспышка и серия ударов", Category.COMBAT);
        about("При каждом твоём ударе в точке попадания появляется короткая стилизованная вспышка. "
                + "7 видов, цвет и размер настраиваются.");
    }

    /** Из AttackEntityCallback (локальный игрок ударил сущность). */
    public static void onAttack(Entity target) {
        if (!ModuleManager.get(HitBurstModule.class).isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastHitAt > 3000) combo = 0;
        combo++;
        lastHitAt = now;
        BURSTS.add(new double[]{target.getX(), target.getY() + target.getHeight() * 0.6, target.getZ(), now});
        while (BURSTS.size() > 8) BURSTS.pollFirst();
    }

    @Override
    protected void onDisable() {
        BURSTS.clear();
        combo = 0;
        lastHitAt = 0;
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        if (!Projection.isReady()) return;
        COLOR = color.argb();
        long now = System.currentTimeMillis();
        for (Iterator<double[]> it = BURSTS.iterator(); it.hasNext(); ) {
            double[] b = it.next();
            long age = now - (long) b[3];
            if (age >= LIFE_MS) { it.remove(); continue; }
            if (age < 0) continue;
            double t = age / (double) LIFE_MS;
            float f = (float) ((1 - t) * (1 - t));
            double te = t * t * (3 - 2 * t);
            if (!Projection.project(b[0], b[1], b[2], p)) continue;
            float scale = (float) ((14 + 26 * te) * (0.6 + 0.4 * easeOutBack(age / 120f))) * K * size.get();
            burst(vg, p[0], p[1], scale, age, f, style.index());
        }
    }

    private static float easeOutBack(float t) {
        t = Anim.clamp01(t);
        float k = 1.70158f, u = t - 1;
        return 1 + (k + 1) * u * u * u + k * u * u;
    }

    private static void line(long vg, float x1, float y1, float x2, float y2, float wdt, float a) {
        nvgBeginPath(vg);
        nvgMoveTo(vg, x1, y1);
        nvgLineTo(vg, x2, y2);
        nvgStrokeWidth(vg, wdt);
        nvgLineCap(vg, NVG_ROUND);
        nvgStrokeColor(vg, Draw.c1(COLOR, a));
        nvgStroke(vg);
    }

    private static void ring(long vg, float x, float y, float r, float wdt, int color, float a) {
        nvgBeginPath(vg);
        nvgCircle(vg, x, y, r);
        nvgStrokeWidth(vg, wdt);
        nvgStrokeColor(vg, Draw.c1(color, a));
        nvgStroke(vg);
    }

    private static void dot(long vg, float x, float y, float r, int color, float a) {
        Draw.circle(vg, x, y, r, color, a);
    }

    private static void burst(long vg, float sx, float sy, float scale, long age, float f, int style) {
        float glow = 70f / 255f * f, mid = 200f / 255f * f, hot = f;
        int white = 0xFFFFFFFF;
        switch (style) {
            case 1 -> { // звезда: 10 лучей разной длины + ядро
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI * 2 * i / 10 + age * 0.01;
                    double rr = scale * (i % 2 == 0 ? 1.0 : 0.45);
                    line(vg, sx, sy, sx + (float) (Math.cos(a) * rr), sy + (float) (Math.sin(a) * rr), K, mid);
                }
                dot(vg, sx, sy, 2.5f * K, COLOR, hot);
                dot(vg, sx, sy, 1.5f * K, white, f);
            }
            case 2 -> { // орбита: 3 точки + кольцо-метка
                for (int i = 0; i < 3; i++) {
                    double a = age * 0.02 + 2 * Math.PI * i / 3;
                    float ox = sx + (float) (Math.cos(a) * scale * 0.7), oy = sy + (float) (Math.sin(a) * scale * 0.7);
                    dot(vg, ox, oy, 3.5f * K, COLOR, glow);
                    dot(vg, ox, oy, 2.5f * K, COLOR, mid);
                    dot(vg, ox, oy, 1.5f * K, white, f);
                }
                ring(vg, sx, sy, scale * 0.7f, K, COLOR, mid);
            }
            case 3 -> { // вспышка-круг: кольцо + 12 лучей + ядро
                float rr = scale * 0.55f;
                ring(vg, sx, sy, rr, 2 * K, COLOR, hot);
                ring(vg, sx, sy, rr + 2 * K, K, COLOR, glow);
                for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12 + age * 0.015;
                    double r0 = scale * 0.55, r1 = scale * (0.9 + 0.25 * ((i * 37) % 3) / 2.0);
                    line(vg, sx + (float) (Math.cos(a) * r0), sy + (float) (Math.sin(a) * r0),
                            sx + (float) (Math.cos(a) * r1), sy + (float) (Math.sin(a) * r1), K, mid);
                }
                dot(vg, sx, sy, 2.5f * K, white, f);
            }
            case 4 -> { // крест: 4 оси + диагонали + кольцо
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI / 2 * i + age * 0.008;
                    line(vg, sx, sy, sx + (float) (Math.cos(a) * scale), sy + (float) (Math.sin(a) * scale), K, hot);
                    double d = a + Math.PI / 4;
                    line(vg, sx, sy, sx + (float) (Math.cos(d) * scale * 0.55), sy + (float) (Math.sin(d) * scale * 0.55), K, mid);
                }
                ring(vg, sx, sy, scale * 0.35f, K, COLOR, glow);
                dot(vg, sx, sy, 1.5f * K, white, f);
            }
            case 5 -> { // искры: 10 разлетающихся точек
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI * 2 * i / 10 + age * 0.012 + i;
                    double rr = scale * (0.3 + 0.7 * (age / (double) LIFE_MS)) * (0.7 + 0.3 * ((i * 53) % 5) / 4.0);
                    float ix = sx + (float) (Math.cos(a) * rr), iy = sy + (float) (Math.sin(a) * rr);
                    dot(vg, ix, iy, 2.5f * K, COLOR, glow);
                    dot(vg, ix, iy, 1.5f * K, COLOR, mid);
                }
            }
            case 6 -> { // пузыри: всплывающий круг + ×комбо
                float rise = age / (float) LIFE_MS;
                float by = sy - 18f * K * rise;
                float br = scale * (0.35f + 0.45f * rise);
                ring(vg, sx, by, br, 2 * K, COLOR, mid);
                ring(vg, sx, by, br + 2 * K, K, COLOR, glow);
                Draw.text(vg, "×" + Math.max(1, combo), sx, by, Fonts.SEMIBOLD, 9f * K, Draw.CENTER_MID, white, f);
            }
            default -> { // 0 кольцо: 8 лучей + двойное кольцо + ядро
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i + age * 0.02;
                    line(vg, sx, sy, sx + (float) (Math.cos(a) * scale), sy + (float) (Math.sin(a) * scale), K, mid);
                }
                float rr = scale * 0.6f;
                ring(vg, sx, sy, rr, K, COLOR, mid);
                ring(vg, sx, sy, rr + 3 * K, K, COLOR, glow);
                dot(vg, sx, sy, 1.5f * K, white, f);
            }
        }
    }
}
