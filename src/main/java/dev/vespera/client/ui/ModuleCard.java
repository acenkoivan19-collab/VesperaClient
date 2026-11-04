package dev.vespera.client.ui;

import dev.vespera.client.module.Module;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.Icons;

/**
 * Карточка модуля: иконка слева, название + описание, тумблер справа.
 * Тумблер НЕ хранит своё состояние: каждый кадр он берёт его из
 * module.isEnabled(). Поэтому GUI всегда показывает реальное состояние —
 * даже если модуль переключили биндом, из конфига или он сам выключился.
 */
public final class ModuleCard {

    private static final float RADIUS = 10f;
    private static final float ICON = 38f;
    private static final float TOGGLE_W = 42f, TOGGLE_H = 24f;

    private final Module module;
    private final Anim hover = new Anim(0f, 16f);
    private final Anim toggle;
    private final Anim pulse = new Anim(0f, 6f); // вспышка при клике

    // последние экранные границы (логические px) — для hit-test
    private float bx, by, bw, bh;
    private boolean onScreen;

    private String descCache;
    private float descCacheW = -1f;

    public ModuleCard(Module module) {
        this.module = module;
        this.toggle = new Anim(module.isEnabled() ? 1f : 0f, 14f);
    }

    /** В карточке — описание без служебной пометки «[Дубль TerraHUD: …]» (она есть в ПКМ-панели). */
    private String shortDesc() {
        String d = module.description();
        return d.startsWith("[") && d.contains("] ") ? d.substring(d.indexOf("] ") + 2) : d;
    }

    public Module module() {
        return module;
    }

    /** Визуальный отклик на клик (вспышка подсветки). */
    public void pulse() {
        pulse.value = 1f;
        pulse.target = 0f;
    }

    public void hide() {
        onScreen = false;
    }

    public boolean hit(float mx, float my) {
        return onScreen && mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    public void render(long vg, float x, float y, float w, float h,
                       float mx, float my, boolean mouseInViewport, float dt) {
        bx = x; by = y; bw = w; bh = h;
        onScreen = true;

        hover.target = (mouseInViewport && hit(mx, my)) ? 1f : 0f;
        toggle.target = module.isEnabled() ? 1f : 0f;
        hover.update(dt);
        toggle.update(dt);
        pulse.update(dt);
        float hv = hover.value, t = toggle.value, p = pulse.value;

        // строгий минимализм: плоская тёмная карточка, нейтральный тонкий контур, без свечения.
        // Акцент — только у включённого тумблера.
        int bg = Draw.lerpColor(Theme.CARD, Theme.CARD_HOVER, Math.max(hv, p * 0.6f));
        Draw.rect(vg, x, y, w, h, RADIUS, bg);
        Draw.stroke(vg, x + 0.5f, y + 0.5f, w - 1f, h - 1f, RADIUS, 1f, Theme.BORDER, 0.7f + 0.3f * hv);

        // иконка на плоской плитке; у включённого — чуть светлее, но без цвета
        float ix = x + 16f, iy = y + (h - ICON) / 2f;
        Draw.rect(vg, ix, iy, ICON, ICON, 9f, Theme.ICON_BG);
        int iconColor = Draw.lerpColor(Theme.TEXT_MUTED, Theme.TEXT, Math.max(t, hv * 0.4f));
        Icons.draw(vg, module.category(), ix + ICON / 2f, iy + ICON / 2f, 16f, iconColor, 1f);

        // текст
        float tx = ix + ICON + 14f;
        float textMaxW = (x + w - 16f - TOGGLE_W - 12f) - tx;
        Draw.text(vg, Draw.ellipsize(vg, module.name(), Fonts.SEMIBOLD, 14f, textMaxW),
                tx, y + h / 2f - 8f, Fonts.SEMIBOLD, 14f, Draw.LEFT_MID, Theme.TEXT, 1f);
        if (descCacheW != textMaxW) {
            descCache = Draw.ellipsize(vg, shortDesc(), Fonts.REGULAR, 11.5f, textMaxW);
            descCacheW = textMaxW;
        }
        Draw.text(vg, descCache, tx, y + h / 2f + 10f,
                Fonts.REGULAR, 11.5f, Draw.LEFT_MID, Theme.TEXT_DIM, 1f);

        // тумблер
        Toggle.draw(vg, x + w - 16f - TOGGLE_W, y + (h - TOGGLE_H) / 2f, TOGGLE_W, TOGGLE_H, t, hv);
    }
}
