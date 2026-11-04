package dev.vespera.client.render;

import dev.vespera.client.module.HudModule;
import dev.vespera.client.ui.Theme;

/**
 * Единый Dark Soft UI для всех экранных модулей. Стиль берётся из ПКМ-настроек модуля:
 *  0 Soft    — тёмная таблетка, мягкая тень, верхний блик, тонкий акцентный контур;
 *  1 Glass   — полупрозрачное «стекло» со светлой кромкой;
 *  2 Минимал — без подложки, только акцентная полоска слева;
 *  3 Контур  — неоновый контур без заливки.
 * Тумблеры «Фон» и «Тень» отключают подложку/тень в любом стиле.
 */
public final class HudStyle {

    public static final float RADIUS = 9f;

    private HudStyle() {}

    public static void panel(long vg, HudModule m, float x, float y, float w, float h) {
        panel(vg, m, x, y, w, h, RADIUS);
    }

    public static void panel(long vg, HudModule m, float x, float y, float w, float h, float r) {
        int acc = m.accent();
        boolean bg = m.background(), sh = m.shadow();
        switch (m.style()) {
            case 1 -> { // Glass
                if (sh) Draw.shadow(vg, x, y, w, h, r, 14f, 3f, 0xFF000000, 0.35f);
                if (bg) {
                    Draw.rect(vg, x, y, w, h, r, Theme.BG, 0.45f);
                    Draw.rect(vg, x, y, w, h, r, 0xFFFFFFFF, 0.06f);
                }
                Draw.topHighlight(vg, x, y, w, h, r, 0.10f);
                Draw.stroke(vg, x + 0.5f, y + 0.5f, w - 1f, h - 1f, r, 1f, 0xFFFFFFFF, 0.16f);
            }
            case 2 -> { // Минимал
                if (bg) Draw.rect(vg, x, y, w, h, r, Theme.BG, 0.35f);
                Draw.rect(vg, x, y + 4f, 2.5f, h - 8f, 1.25f, acc);
            }
            case 3 -> { // Контур
                if (sh) Draw.glow(vg, x, y, w, h, r, 10f, acc, 0.25f);
                if (bg) Draw.rect(vg, x, y, w, h, r, Theme.BG, 0.35f);
                Draw.stroke(vg, x + 0.75f, y + 0.75f, w - 1.5f, h - 1.5f, r, 1.5f, acc, 0.9f);
            }
            default -> { // Soft
                if (sh) {
                    Draw.shadow(vg, x, y, w, h, r, 14f, 3f, 0xFF000000, 0.5f);
                    Draw.glow(vg, x, y, w, h, r, 10f, acc, 0.10f);
                }
                if (bg) Draw.rect(vg, x, y, w, h, r, Theme.BG, 0.88f);
                Draw.topHighlight(vg, x, y, w, h, r, 0.04f);
                Draw.stroke(vg, x + 0.5f, y + 0.5f, w - 1f, h - 1f, r, 1f, acc, 0.22f);
            }
        }
    }

    /** Внутренний отступ слева с учётом полоски стиля «Минимал». */
    public static float padLeft(HudModule m) {
        return m.style() == 2 ? 12f : 10f;
    }
}
