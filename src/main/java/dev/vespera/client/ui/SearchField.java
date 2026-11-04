package dev.vespera.client.ui;

import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.Icons;

import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.nanovg.NanoVG.*;

/** Поле поиска в шапке. Не скроллится — рисуется вне области сетки. */
public final class SearchField {

    private static final String PLACEHOLDER = "Search modules...";
    private static final int MAX_LEN = 32;
    private static final float FONT_SIZE = 13f;

    private final StringBuilder text = new StringBuilder();
    private final Anim focusAnim = new Anim(0f, 16f);
    private final Anim hoverAnim = new Anim(0f, 16f);
    private boolean focused;
    private float caretTime;

    private float x, y, w, h;

    public String query() {
        return text.toString().trim().toLowerCase(Locale.ROOT);
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean value) {
        focused = value;
        caretTime = 0f;
    }

    private boolean contains(float mx, float my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private boolean overClear(float mx, float my) {
        float cx = x + w - 18f, cy = y + h / 2f;
        return text.length() > 0 && Math.abs(mx - cx) <= 9f && Math.abs(my - cy) <= 9f;
    }

    public void render(long vg, float x, float y, float w, float h, float mx, float my, float dt) {
        this.x = x; this.y = y; this.w = w; this.h = h;

        focusAnim.target = focused ? 1f : 0f;
        hoverAnim.target = contains(mx, my) ? 1f : 0f;
        focusAnim.update(dt);
        hoverAnim.update(dt);
        caretTime += dt;
        float f = focusAnim.value, hv = hoverAnim.value;

        Draw.rect(vg, x, y, w, h, 12f, Theme.INPUT);
        int border = Draw.lerpColor(Theme.BORDER, 0xFF5A5A66, Math.max(f, hv * 0.5f));
        Draw.stroke(vg, x + 0.5f, y + 0.5f, w - 1f, h - 1f, 12f, 1f, border, 1f);

        Icons.search(vg, x + 20f, y + h / 2f, 14f, Draw.lerpColor(Theme.TEXT_MUTED, Theme.TEXT, f), 1f);

        float tx = x + 38f;
        float right = x + w - (text.length() > 0 ? 32f : 14f);
        float avail = right - tx;

        nvgSave(vg);
        nvgIntersectScissor(vg, tx, y, avail + 2f, h);
        if (text.length() == 0) {
            Draw.text(vg, PLACEHOLDER, tx, y + h / 2f, Fonts.REGULAR, FONT_SIZE,
                    Draw.LEFT_MID, Theme.TEXT_MUTED, 1f);
            if (focused && blink()) caret(vg, tx, y, h);
        } else {
            String s = text.toString();
            float tw = Draw.textWidth(vg, s, Fonts.MEDIUM, FONT_SIZE);
            float off = Math.max(0f, tw + 3f - avail); // держим конец текста в поле зрения
            Draw.text(vg, s, tx - off, y + h / 2f, Fonts.MEDIUM, FONT_SIZE,
                    Draw.LEFT_MID, Theme.TEXT, 1f);
            if (focused && blink()) caret(vg, tx - off + tw + 1f, y, h);
        }
        nvgRestore(vg);

        if (text.length() > 0) {
            boolean over = overClear(mx, my);
            Icons.cross(vg, x + w - 18f, y + h / 2f, 11f, over ? Theme.TEXT : Theme.TEXT_DIM, 1f);
        }
    }

    private boolean blink() {
        return ((int) (caretTime * 2f)) % 2 == 0;
    }

    private void caret(long vg, float cx, float y, float h) {
        Draw.rect(vg, cx, y + h / 2f - 8f, 1.5f, 16f, 0.75f, Theme.TEXT);
    }

    // ---------------------------------------------------------------- input

    /** @return true, если клик пришёлся на поле. */
    public boolean click(float mx, float my) {
        if (overClear(mx, my)) {
            text.setLength(0);
            setFocused(true);
            return true;
        }
        setFocused(contains(mx, my));
        return focused;
    }

    public boolean charTyped(int codepoint) {
        if (!focused || Character.isISOControl(codepoint)) return false;
        if (text.length() < MAX_LEN) text.appendCodePoint(codepoint);
        caretTime = 0f;
        return true;
    }

    /** Пока поле в фокусе — съедает все клавиши, чтобы не срабатывали бинды. */
    public boolean keyPressed(int key, int modifiers) {
        if (!focused) return false;
        switch (key) {
            case GLFW_KEY_BACKSPACE -> {
                if ((modifiers & GLFW_MOD_CONTROL) != 0) {
                    text.setLength(0);
                } else if (text.length() > 0) {
                    int cut = text.offsetByCodePoints(text.length(), -1);
                    text.setLength(cut);
                }
                caretTime = 0f;
            }
            case GLFW_KEY_ESCAPE, GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> setFocused(false);
            default -> { }
        }
        return true;
    }
}
