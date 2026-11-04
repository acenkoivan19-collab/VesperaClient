package dev.vespera.client.module.hud;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.HudStyle;
import dev.vespera.client.ui.Anim;
import dev.vespera.client.ui.Theme;

/**
 * Комбо: серия ударов подряд. Каждый удар (AttackEntityCallback) +1, серия сбрасывается,
 * если не бил дольше окна (по умолчанию 3 сек, как в TerraHUD). Число «подпрыгивает»
 * на каждый удар, полоса показывает, сколько осталось до сброса.
 */
public final class ComboModule extends HudModule {

    private static int combo, best;
    private static long lastHit;
    private static boolean bump;

    private final ChoiceSetting look = setting(new ChoiceSetting("look", "Вид", 0, "Число + полоса", "Только число")
            .hints("Счётчик ×N и полоса времени до сброса серии", "Только крупное ×N"));
    private final NumberSetting window = setting(new NumberSetting("window", "Окно серии (сек)", 3f, 1f, 6f, 0.5f));
    private final Anim pop = new Anim(0f, 10f);
    private final Anim fade = new Anim(0f, 10f);

    public ComboModule() {
        super("Combo", "Серия ударов подряд", Category.COMBAT);
        about("Считает удары подряд: каждое попадание +1, серия обнуляется, если перестал бить. "
                + "Рядом — лучшая серия за сессию.");
    }

    /** Из AttackEntityCallback (локальный игрок ударил сущность). */
    public static void onAttack() {
        ComboModule m = ModuleManager.get(ComboModule.class);
        if (!m.isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastHit > (long) (m.window.get() * 1000)) combo = 0;
        combo++;
        best = Math.max(best, combo);
        lastHit = now;
        bump = true;
    }

    @Override
    protected void onDisable() {
        combo = 0;
        best = 0;
        lastHit = 0;
    }

    @Override
    public float defaultX() {
        return 0.47f;
    }

    @Override
    public float defaultY() {
        return 0.40f;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        long now = System.currentTimeMillis();
        long win = (long) (window.get() * 1000);
        boolean active = combo > 0 && now - lastHit < win;
        int shownCombo = active ? combo : isEditing() ? 5 : combo;
        if (bump) { pop.value = 1f; bump = false; }
        pop.target = 0f;
        step(pop, dt);
        fade.target = active || isEditing() ? 1f : 0f;
        step(fade, dt);

        boolean bar = look.index() == 0;
        float bw = 96f, bh = bar ? 46f : 36f;
        size(bw, bh);
        if (fade.value < 0.01f) return;
        float a = fade.value;

        HudStyle.panel(vg, this, 0, 0, bw, bh);
        float s = 1f + 0.25f * Anim.easeOutCubic(pop.value);
        Draw.textTabular(vg, "×" + shownCombo, 12f, 18f, Fonts.SEMIBOLD, 18f * s, Draw.LEFT_MID,
                Draw.lerpColor(Theme.TEXT, accent(), pop.value), a);
        Draw.text(vg, "лучшая " + best, bw - 10f, 18f, Fonts.REGULAR, 10f, Draw.RIGHT_MID, Theme.TEXT_DIM, a);
        if (bar) {
            float left = active ? 1f - (now - lastHit) / (float) win : 0.6f;
            Draw.rect(vg, 12f, bh - 12f, bw - 24f, 3f, 1.5f, 0xFF000000, 0.45f * a);
            Draw.rect(vg, 12f, bh - 12f, Math.max(3f, (bw - 24f) * left), 3f, 1.5f, accent(), a);
        }
    }
}
