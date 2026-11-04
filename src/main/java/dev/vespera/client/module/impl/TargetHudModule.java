package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.HudStyle;
import dev.vespera.client.render.ItemIcons;
import dev.vespera.client.ui.Anim;
import dev.vespera.client.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.Locale;

/**
 * Панель цели. Цель — последняя ударенная сущность (6 сек, AttackEntityCallback)
 * или та, что под прицелом (crosshairTarget). Режимы:
 *  «Подробный» — буква-аватар, имя, ХП, броня, дистанция, предмет в руке;
 *  «Компактный» — имя и полоса ХП;
 *  «Полоса ХП» — только полоса здоровья с цифрами (порт TerraHUD «ХП в таргете»).
 */
public final class TargetHudModule extends HudModule {

    private static final long ATTACK_MEMORY_MS = 6000;
    private static final double MAX_DISTANCE = 12.0;

    private static LivingEntity lastAttacked;
    private static long lastAttackMs;

    private final ChoiceSetting mode = setting(new ChoiceSetting("mode", "Вид", 0, "Подробный", "Компактный", "Полоса ХП")
            .hints("Аватар, имя, ХП, броня, дистанция и предмет в руке", "Только имя и полоса здоровья",
                    "Одна полоса ХП с цифрами — минимум места"));
    private final ChoiceSetting source = setting(new ChoiceSetting("source", "Чья цель", 0, "Удар + прицел", "Только прицел")
            .hints("Последний, кого ты ударил (6 сек), иначе — под прицелом", "Только сущность под прицелом"));
    private final BoolSetting hearts = setting(new BoolSetting("hearts", "ХП в сердцах (÷2)", true));

    private final Anim visible = new Anim(0f, 12f);
    private final Anim health = new Anim(1f, 10f);
    private LivingEntity shown;

    public TargetHudModule() {
        super("Target HUD", "[Дубль TerraHUD: Target HUD] Здоровье и броня цели", Category.COMBAT);
        about("Показывает, с кем ты сражаешься: имя, здоровье с плавной полосой, броню и дистанцию. "
                + "Есть компактные режимы и режим «только полоса ХП».");
    }

    /** Вызывается из AttackEntityCallback. */
    public static void onAttack(Entity target) {
        if (target instanceof LivingEntity living) {
            lastAttacked = living;
            lastAttackMs = System.currentTimeMillis();
        }
    }

    @Override
    protected void onDisable() {
        shown = null;
        visible.snap(0f);
    }

    @Override
    public float defaultX() {
        return 0.52f;
    }

    @Override
    public float defaultY() {
        return 0.55f;
    }

    private LivingEntity findTarget(MinecraftClient mc) {
        if (mc.player == null) return null;
        if (source.index() == 0 && lastAttacked != null && lastAttacked.isAlive()
                && System.currentTimeMillis() - lastAttackMs < ATTACK_MEMORY_MS
                && mc.player.distanceTo(lastAttacked) < MAX_DISTANCE) {
            return lastAttacked;
        }
        return mc.targetedEntity instanceof LivingEntity l && l.isAlive() ? l : null;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LivingEntity target = findTarget(mc);
        if (target == null && isEditing()) target = mc.player; // превью в Редакторе HUD
        if (target != null) shown = target;
        visible.target = target != null ? 1f : 0f;
        step(visible, dt);

        int m = mode.index();
        float pw = m == 0 ? 200f : m == 1 ? 170f : 150f, ph = m == 0 ? 60f : m == 1 ? 40f : 22f;
        size(pw, ph);
        if (shown == null || visible.value < 0.01f) return;

        float max = Math.max(1f, shown.getMaxHealth());
        float hp = Math.max(0f, shown.getHealth());
        health.target = hp / max;
        step(health, dt);
        float a = Anim.easeOutCubic(visible.value);
        float y0 = (1f - a) * 6f;
        String hpTxt = hearts.on() ? String.format(Locale.ROOT, "%.1f", hp / 2f) : String.format(Locale.ROOT, "%.0f", hp);
        String hpFull = hpTxt + (hearts.on() ? " / " + String.format(Locale.ROOT, "%.0f", max / 2f) : " / " + String.format(Locale.ROOT, "%.0f", max));

        HudStyle.panel(vg, this, 0, y0, pw, ph, m == 2 ? 7f : 11f);
        String name = shown.getName().getString();

        if (m == 2) { // только полоса ХП
            bar(vg, 8f, y0 + 8f, pw - 16f - 52f, 6f, a);
            Draw.textTabular(vg, hpFull, pw - 8f, y0 + ph / 2f, Fonts.MEDIUM, 10.5f, Draw.RIGHT_MID, Theme.TEXT, a);
            return;
        }
        float tx = HudStyle.padLeft(this);
        if (m == 0) {
            float as = 40f, ax = 10f, ay = y0 + (ph - as) / 2f;
            Draw.linear(vg, ax, ay, as, as, 10f, ax, ay, ax + as, ay + as, accent(), 0.9f * a, Theme.ACCENT_2, 0.9f * a);
            Draw.text(vg, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT),
                    ax + as / 2f, ay + as / 2f + 0.5f, Fonts.SEMIBOLD, 17f, Draw.CENTER_MID, 0xFFFFFFFF, a);
            tx = ax + as + 10f;
        }
        float right = pw - 10f;
        float tw = right - tx - (m == 0 ? 22f : 0f);
        Draw.text(vg, Draw.ellipsize(vg, name, Fonts.SEMIBOLD, 13f, tw), tx, y0 + 13f,
                Fonts.SEMIBOLD, 13f, Draw.LEFT_MID, Theme.TEXT, a);

        if (m == 0) {
            // предмет в руке цели — маленькая иконка справа от имени
            var stack = shown.getMainHandStack();
            if (!stack.isEmpty()) {
                int img = ItemIcons.item(stack.getItem());
                if (img > 0) ItemIcons.draw(vg, img, right - 16f, y0 + 5f, 16f, a);
            }
            float dist = mc.player != null ? mc.player.distanceTo(shown) : 0f;
            String info = String.format(Locale.ROOT, "%s HP  ·  %d брони  ·  %.1f м", hpFull, shown.getArmor(), dist);
            Draw.text(vg, Draw.ellipsize(vg, info, Fonts.REGULAR, 10.5f, right - tx), tx, y0 + 30f,
                    Fonts.REGULAR, 10.5f, Draw.LEFT_MID, Theme.TEXT_DIM, a);
            bar(vg, tx, y0 + 42f, right - tx, 6f, a);
        } else {
            Draw.textTabular(vg, hpFull, right, y0 + 13f, Fonts.MEDIUM, 10.5f, Draw.RIGHT_MID, Theme.TEXT_DIM, a);
            bar(vg, tx, y0 + 26f, right - tx, 6f, a);
        }
    }

    /** Полоса ХП: красный → акцент по мере заполнения. */
    private void bar(long vg, float x, float y, float w, float h, float a) {
        Draw.rect(vg, x, y, w, h, h / 2f, 0xFF000000, 0.45f * a);
        float t = Anim.clamp01(health.value);
        int c = Draw.lerpColor(0xFFFF4D6D, accent(), t);
        Draw.linear(vg, x, y, Math.max(h, w * t), h, h / 2f, x, y, x + w, y, c, a, Theme.ACCENT_2, a);
    }
}
