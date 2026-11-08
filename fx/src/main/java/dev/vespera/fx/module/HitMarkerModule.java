package dev.vespera.fx.module;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.fx.render.Fx;
import dev.vespera.fx.render.Quality;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Хит-маркер у прицела: короткая метка «попал» при каждом твоём ударе.
 * Критический удар — золотая и крупнее, добивание — красная с кольцом.
 * Чисто визуально: то же, что игрок и так видит (удар уже нанесён).
 */
public final class HitMarkerModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Вид", 0, "Крест", "Уголки", "Кольцо", "Точки")
            .hints("Четыре косые черты вокруг прицела", "Четыре уголка, как рамка",
                    "Тонкое кольцо, которое расходится", "Четыре точки разлетаются в стороны"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет попадания", 0xFFFFFFFF));
    private final BoolSetting crits = setting(new BoolSetting("crits", "Крит — золотой", true));
    private final BoolSetting kills = setting(new BoolSetting("kills", "Добивание — красный", true));
    private final NumberSetting size = setting(new NumberSetting("size", "Размер", 1f, 0.5f, 2f, 0.05f));
    private final NumberSetting duration = setting(new NumberSetting("duration", "Длительность (мс)", 380f, 150f, 900f, 10f));

    private static final int CRIT = 0xFFFFC23D, KILL = 0xFFFF3B4A;

    private static long hitAt;
    private static boolean crit, kill;
    private static Entity target;

    public HitMarkerModule() {
        super("Hit Marker", "Метка попадания у прицела: крит, добивание", Category.COMBAT);
        about("Когда ты бьёшь, у прицела на мгновение появляется метка попадания. "
                + "Критический удар подсвечивается золотым, добивание — красным с кольцом.");
    }

    /** Из AttackEntityCallback: локальный игрок ударил сущность. */
    public static void onAttack(Entity e) {
        HitMarkerModule m = ModuleManager.get(HitMarkerModule.class);
        if (m == null || !m.isEnabled()) return;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        // крит по правилам ванилы: падаешь, не на земле, не в воде/на лестнице/в седле, удар заряжен
        crit = p.fallDistance > 0 && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasVehicle() && !p.isSprinting() && p.getAttackCooldownProgress(0.5f) > 0.9f;
        kill = false;
        target = e;
        hitAt = System.currentTimeMillis();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        // добивание: цель умерла в течение 0.6 с после удара
        if (target != null && System.currentTimeMillis() - hitAt < 600) {
            if (!target.isAlive() || target instanceof LivingEntity le && le.getHealth() <= 0f) {
                kill = true;
                hitAt = System.currentTimeMillis(); // метка добивания живёт полный срок
                target = null;
            }
        } else {
            target = null;
        }
    }

    @Override
    protected void onDisable() {
        target = null;
        hitAt = 0;
    }

    @Override
    public int layer() {
        return LAYER_OVERLAY;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        if (isEditing()) return;
        long age = System.currentTimeMillis() - hitAt;
        float life = duration.get();
        if (age < 0 || age > life) return;
        float t = age / life;
        float pop = Fx.easeOutCubic(Math.min(1f, age / 90f));       // быстрый «щелчок» появления
        float a = 1f - Fx.smooth(0.45f, 1f, t);                     // держится, потом гаснет
        boolean isKill = kill && kills.on(), isCrit = crit && crits.on();
        int c = isKill ? KILL : isCrit ? CRIT : color.argb();
        float k = size.get() * (isKill ? 1.35f : isCrit ? 1.15f : 1f);
        float cx = w / 2f, cy = h / 2f;
        float r0 = (6f + 3f * pop) * k, r1 = (13f + 4f * pop) * k;
        boolean glow = Quality.glow();

        nvgLineCap(vg, NVG_ROUND);
        switch (style.index()) {
            case 1 -> { // уголки
                float d = (9f + 4f * pop) * k, l = 5f * k;
                for (int i = 0; i < 4; i++) {
                    float sx = (i & 1) == 0 ? -1 : 1, sy = (i & 2) == 0 ? -1 : 1;
                    float x = cx + sx * d, y = cy + sy * d;
                    if (glow) polyline(vg, x - sx * l, y, x, y, x, y - sy * l, 4.5f * k, c, a * 0.18f);
                    polyline(vg, x - sx * l, y, x, y, x, y - sy * l, 1.8f * k, c, a);
                }
            }
            case 2 -> { // расходящееся кольцо
                float rr = (5f + 12f * Fx.easeOutCubic(t)) * k;
                if (glow) ring(vg, cx, cy, rr, 5f * k, c, a * 0.18f);
                ring(vg, cx, cy, rr, 1.8f * k, c, a);
            }
            case 3 -> { // точки разлетаются
                float rr = (5f + 10f * Fx.easeOutCubic(t)) * k;
                for (int i = 0; i < 4; i++) {
                    double ang = Math.PI / 4 + Math.PI / 2 * i;
                    float x = cx + (float) Math.cos(ang) * rr, y = cy + (float) Math.sin(ang) * rr;
                    if (glow) Draw.circle(vg, x, y, 4.5f * k, c, a * 0.2f);
                    Draw.circle(vg, x, y, 1.9f * k, c, a);
                }
            }
            default -> { // крест из четырёх косых черт
                for (int i = 0; i < 4; i++) {
                    double ang = Math.PI / 4 + Math.PI / 2 * i;
                    float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
                    if (glow) seg(vg, cx + dx * r0, cy + dy * r0, cx + dx * r1, cy + dy * r1, 5f * k, c, a * 0.18f);
                    seg(vg, cx + dx * r0, cy + dy * r0, cx + dx * r1, cy + dy * r1, 1.9f * k, c, a);
                }
            }
        }
        if (isKill) { // у добивания — дополнительное кольцо-вспышка
            float rr = (10f + 16f * Fx.easeOutCubic(t)) * k;
            ring(vg, cx, cy, rr, 1.4f * k, KILL, a * 0.7f);
        }
    }

    private static void seg(long vg, float x1, float y1, float x2, float y2, float wdt, int c, float a) {
        nvgBeginPath(vg);
        nvgMoveTo(vg, x1, y1);
        nvgLineTo(vg, x2, y2);
        nvgStrokeWidth(vg, wdt);
        nvgStrokeColor(vg, Draw.c1(c, a));
        nvgStroke(vg);
    }

    private static void polyline(long vg, float x1, float y1, float x2, float y2, float x3, float y3,
                                 float wdt, int c, float a) {
        nvgBeginPath(vg);
        nvgMoveTo(vg, x1, y1);
        nvgLineTo(vg, x2, y2);
        nvgLineTo(vg, x3, y3);
        nvgStrokeWidth(vg, wdt);
        nvgStrokeColor(vg, Draw.c1(c, a));
        nvgStroke(vg);
    }

    private static void ring(long vg, float x, float y, float r, float wdt, int c, float a) {
        nvgBeginPath(vg);
        nvgCircle(vg, x, y, r);
        nvgStrokeWidth(vg, wdt);
        nvgStrokeColor(vg, Draw.c1(c, a));
        nvgStroke(vg);
    }
}
