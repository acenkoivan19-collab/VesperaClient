package dev.vespera.client.ui;

import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.Module;
import dev.vespera.client.module.ModuleConfig;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.module.setting.Setting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.Icons;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.nanovg.NanoVG.*;

/**
 * ПКМ-панель настроек модуля (ClickGuiScreen и HudEditorScreen).
 *
 * Панель НИКОГДА не пустая. Сверху всегда: название, полное описание (Module.about(),
 * с переносом строк) и тумблер «Включён». Дальше — превью (если есть), масштаб и
 * прозрачность (экранные модули), настройки модуля: ChoiceSetting → сетка вариантов +
 * подпись «что меняет», ColorSetting → палитра + свой оттенок, NumberSetting → слайдер,
 * BoolSetting → тумблер. Внизу — «Сбросить».
 *
 * Стиль — строгий минимализм: плоские тёмные блоки, нейтральные контуры; акцентный
 * цвет только у включённого тумблера. Закрывается Esc / кликом мимо / повторным ПКМ.
 */
public final class ModuleSettingsPanel {

    private static final float W = 310f, MAX_H = 460f, PAD = 14f;
    private static final int NEUTRAL = 0xFFB4B4BE;          // заливка слайдеров/выбора — без акцента
    private static final int[] PALETTE = {
            0xFF5EE6CF, 0xFF4C8DFF, 0xFFA78BFF, 0xFFFF6FD8, 0xFFFF4D6D, 0xFFFF9F43, 0xFFFFD166, 0xFF9FC39A, 0xFFFFFFFF
    };

    private record Hit(float x, float y, float w, float h, Runnable action) {}
    private record Slider(float x, float w, float min, float max, Consumer<Float> apply) {}

    private final List<Hit> hits = new ArrayList<>();
    private final Anim open = new Anim(0f, 16f);
    private final Anim scroll = new Anim(0f, 16f);
    private Module module;
    private float px, py, ph, contentH = 200f;
    private float anchorX, anchorY;
    private Slider dragging;

    public boolean isOpen() {
        return module != null;
    }

    public Module module() {
        return module;
    }

    /** Открыть рядом с точкой (координаты хоста). Повторно по тому же модулю — закрыть. */
    public void toggle(Module m, float x, float y) {
        if (module == m) { close(); return; }
        module = m;
        anchorX = x;
        anchorY = y;
        open.snap(0f);
        open.target = 1f;
        scroll.snap(0f);
        dragging = null;
    }

    public void close() {
        module = null;
        dragging = null;
    }

    // =====================================================================
    //  Кадр
    // =====================================================================

    public void render(long vg, float screenW, float screenH, float mx, float my, float dt) {
        if (module == null) return;
        open.update(dt);
        scroll.update(dt);
        updateDrag(mx);
        hits.clear();

        ph = Math.min(Math.min(MAX_H, screenH - 16f), contentH + 16f);
        px = Math.max(8f, Math.min(screenW - W - 8f, anchorX + 12f));
        py = Math.max(8f, Math.min(screenH - ph - 8f, anchorY - 20f));
        float e = Anim.easeOutCubic(open.value);

        nvgSave(vg);
        nvgGlobalAlpha(vg, e);
        nvgTranslate(vg, 0f, (1f - e) * 6f);

        Draw.shadow(vg, px, py, W, ph, 12f, 24f, 6f, 0xFF000000, 0.6f);
        Draw.rect(vg, px, py, W, ph, 12f, Theme.SIDEBAR);
        Draw.stroke(vg, px + 0.5f, py + 0.5f, W - 1f, ph - 1f, 12f, 1f, Theme.BORDER, 1f);

        float maxScroll = Math.max(0f, contentH + 16f - ph);
        scroll.target = Anim.clamp(scroll.target, 0f, maxScroll);
        nvgSave(vg);
        nvgIntersectScissor(vg, px, py, W, ph);
        float y0 = py + PAD - scroll.value;
        float y = content(vg, y0, mx, my, py, ph);
        contentH = y - y0 + PAD;
        nvgRestore(vg);
        if (maxScroll > 0f) {
            float th = Math.max(24f, ph * ph / (contentH + 16f));
            Draw.rect(vg, px + W - 5f, py + (ph - th) * (scroll.value / maxScroll), 2.5f, th, 1.25f, 0xFFFFFFFF, 0.18f);
        }
        nvgRestore(vg);
    }

    private float content(long vg, float y, float mx, float my, float clipY, float clipH) {
        float x = px + PAD, w = W - PAD * 2;

        // ---- шапка: имя, закрыть, полное описание, тумблер «Включён»
        Draw.text(vg, module.name(), x, y + 9f, Fonts.SEMIBOLD, 15f, Draw.LEFT_MID, Theme.TEXT, 1f);
        float cx = px + W - 22f, cy = y + 9f;
        boolean xh = Math.abs(mx - cx) < 10 && Math.abs(my - cy) < 10;
        Icons.cross(vg, cx, cy, 10f, xh ? Theme.TEXT : Theme.TEXT_MUTED, 1f);
        hit(cx - 10f, cy - 10f, 20f, 20f, clipY, clipH, this::close);
        y += 24f;
        y += Draw.textBox(vg, module.about(), x, y, w, Fonts.REGULAR, 11.5f, Theme.TEXT_DIM, 1f) + 12f;

        y = row(vg, x, y, w, "Включён", module.isEnabled(), module::toggle, clipY, clipH);
        y = divider(vg, x, y, w);

        // ---- превью
        if (module.hasPreview()) {
            float pvH = 76f;
            Draw.linear(vg, x, y, w, pvH, 8f, x, y, x, y + pvH, 0xFF6B8FB5, 1f, 0xFF3E5A3A, 1f);
            Draw.stroke(vg, x + 0.5f, y + 0.5f, w - 1f, pvH - 1f, 8f, 1f, Theme.BORDER, 1f);
            nvgSave(vg);
            nvgIntersectScissor(vg, x, y, w, pvH);
            module.renderPreview(vg, x, y, w, pvH);
            nvgRestore(vg);
            y += pvH + 12f;
        }

        // ---- раскладка экранных модулей
        boolean hud = module instanceof HudModule h && h.layoutable();
        if (hud) {
            ModuleConfig.Layout l = ModuleConfig.layout((HudModule) module);
            y = slider(vg, x, y, w, "Масштаб", String.format(Locale.ROOT, "×%.2f", l.scale), l.scale,
                    ModuleConfig.Layout.MIN_SCALE, ModuleConfig.Layout.MAX_SCALE, v -> {
                        l.scale = Math.round(v * 20f) / 20f; l.clamp(); ModuleConfig.markDirty();
                    }, clipY, clipH);
            y = slider(vg, x, y, w, "Прозрачность", Math.round(l.alpha * 100) + "%", l.alpha,
                    ModuleConfig.Layout.MIN_ALPHA, ModuleConfig.Layout.MAX_ALPHA, v -> {
                        l.alpha = Math.round(v * 20f) / 20f; l.clamp(); ModuleConfig.markDirty();
                    }, clipY, clipH);
        }

        // ---- настройки модуля: выборы, цвета, слайдеры, затем тумблеры
        List<BoolSetting> toggles = new ArrayList<>();
        for (Setting<?> s : module.settings()) {
            if (s instanceof ChoiceSetting c) y = choice(vg, x, y, w, c, mx, my, clipY, clipH);
            else if (s instanceof ColorSetting c) y = color(vg, x, y, w, c, clipY, clipH);
            else if (s instanceof NumberSetting n) {
                y = slider(vg, x, y, w, n.label(), String.format(Locale.ROOT, n.step() >= 1 ? "%.0f" : "%.2f", n.get()),
                        n.get(), n.min(), n.max(), n::set, clipY, clipH);
            } else if (s instanceof BoolSetting b) toggles.add(b);
        }
        if (!toggles.isEmpty()) y = divider(vg, x, y, w);
        for (BoolSetting b : toggles) y = row(vg, x, y, w, b.label(), b.on(), b::toggle, clipY, clipH);

        // ---- сброс
        y += 8f;
        if (hud) {
            float bw = (w - 8f) / 2f;
            button(vg, x, y, bw, "Сбросить позицию", mx, my, () -> ModuleConfig.resetLayout((HudModule) module), clipY, clipH);
            button(vg, x + bw + 8f, y, bw, "Сбросить", mx, my, this::resetSettings, clipY, clipH);
        } else {
            button(vg, x, y, w, "Сбросить", mx, my, this::resetSettings, clipY, clipH);
        }
        return y + 28f;
    }

    private void resetSettings() {
        for (Setting<?> s : module.settings()) s.reset();
        if (module instanceof HudModule hud && hud.layoutable()) ModuleConfig.resetLayoutFull(hud);
    }

    // ---------------------------------------------------------------- элементы

    private void hit(float x, float y, float w, float h, float clipY, float clipH, Runnable r) {
        float t = Math.max(y, clipY), b = Math.min(y + h, clipY + clipH);
        if (b > t) hits.add(new Hit(x, t, w, b - t, r));
    }

    private static float divider(long vg, float x, float y, float w) {
        Draw.rect(vg, x, y + 4f, w, 1f, 0f, 0xFFFFFFFF, 0.06f);
        return y + 14f;
    }

    private float label(long vg, float x, float y, float w, String label, String value) {
        Draw.text(vg, label, x, y + 7f, Fonts.MEDIUM, 11.5f, Draw.LEFT_MID, Theme.TEXT, 1f);
        if (value != null) Draw.textTabular(vg, value, x + w, y + 7f, Fonts.MEDIUM, 11f, Draw.RIGHT_MID, Theme.TEXT_DIM, 1f);
        return y + 19f;
    }

    private float slider(long vg, float x, float y, float w, String name, String value, float v, float min, float max,
                         Consumer<Float> apply, float clipY, float clipH) {
        y = label(vg, x, y, w, name, value);
        float t = (Anim.clamp(v, min, max) - min) / (max - min);
        float sy = y + 5f;
        Draw.rect(vg, x, sy - 2f, w, 4f, 2f, Theme.TOGGLE_OFF);
        Draw.rect(vg, x, sy - 2f, Math.max(4f, w * t), 4f, 2f, NEUTRAL, 0.85f);
        Draw.circle(vg, x + w * t, sy, 6.5f, 0xFFFFFFFF, 1f);
        Slider s = new Slider(x, w, min, max, apply);
        hit(x - 8f, sy - 11f, w + 16f, 22f, clipY, clipH, () -> dragging = s);
        return y + 20f;
    }

    /** Варианты сеткой (до 3 в ряд), под ними — подпись выбранного. */
    private float choice(long vg, float x, float y, float w, ChoiceSetting c, float mx, float my, float clipY, float clipH) {
        y = label(vg, x, y, w, c.label(), null);
        String[] opts = c.options();
        int perRow = opts.length == 4 ? 2 : Math.min(3, opts.length);
        float cw = (w - (perRow - 1) * 6f) / perRow, ch = 26f;
        for (int i = 0; i < opts.length; i++) {
            float cx = x + (i % perRow) * (cw + 6f), cy = y + (i / perRow) * (ch + 6f);
            boolean sel = c.index() == i;
            boolean hov = mx >= cx && mx <= cx + cw && my >= cy && my <= cy + ch;
            Draw.rect(vg, cx, cy, cw, ch, 7f, sel ? 0xFF2C2C34 : hov ? Theme.CARD_HOVER : Theme.CARD);
            Draw.stroke(vg, cx + 0.5f, cy + 0.5f, cw - 1f, ch - 1f, 7f, 1f, sel ? 0xFF5A5A66 : Theme.BORDER, 1f);
            Draw.text(vg, Draw.ellipsize(vg, opts[i], Fonts.MEDIUM, 11f, cw - 10f), cx + cw / 2f, cy + ch / 2f + 0.5f,
                    Fonts.MEDIUM, 11f, Draw.CENTER_MID, sel ? Theme.TEXT : Theme.TEXT_DIM, 1f);
            final int idx = i;
            hit(cx, cy, cw, ch, clipY, clipH, () -> c.set(idx));
        }
        int rows = (opts.length + perRow - 1) / perRow;
        y += rows * (ch + 6f);
        String hint = c.hint();
        if (hint != null && !hint.isEmpty()) {
            y += Draw.textBox(vg, hint, x, y, w, Fonts.REGULAR, 10.5f, Theme.TEXT_MUTED, 1f) + 4f;
        }
        return y + 8f;
    }

    private float color(long vg, float x, float y, float w, ColorSetting c, float clipY, float clipH) {
        int cur = c.argb();
        y = label(vg, x, y, w, c.label(), c.isTheme() ? "тема" : String.format("#%06X", cur & 0xFFFFFF));
        int n = PALETTE.length + 1;
        float sw = (w - (n - 1) * 5f) / n, sh = 20f;
        for (int i = 0; i < n; i++) {
            float cx = x + i * (sw + 5f);
            boolean theme = i == 0;
            int col = theme ? Theme.ACCENT : PALETTE[i - 1];
            boolean sel = theme ? c.isTheme() : !c.isTheme() && (c.value() | 0xFF000000) == col;
            Draw.rect(vg, cx, y, sw, sh, 5f, col);
            if (theme) Draw.text(vg, "T", cx + sw / 2f, y + sh / 2f + 0.5f, Fonts.SEMIBOLD, 10f, Draw.CENTER_MID, 0xFF0E0E12, 1f);
            Draw.stroke(vg, cx + 0.5f, y + 0.5f, sw - 1f, sh - 1f, 5f, sel ? 2f : 1f, sel ? 0xFFFFFFFF : 0xFF000000, sel ? 0.95f : 0.3f);
            hit(cx, y, sw, sh, clipY, clipH, () -> c.set(theme ? ColorSetting.THEME : col));
        }
        y += sh + 10f;
        // свой оттенок
        float hy = y + 4f;
        for (int i = 0; i < 24; i++) {
            float seg = w / 24f;
            Draw.linear(vg, x + i * seg, hy - 3f, seg + 0.6f, 6f, i == 0 || i == 23 ? 3f : 0f,
                    x + i * seg, hy, x + (i + 1) * seg, hy, hsv(i / 24f), 1f, hsv((i + 1) / 24f), 1f);
        }
        if (!c.isTheme()) {
            float kx = x + w * hueOf(cur);
            Draw.circle(vg, kx, hy, 6.5f, 0xFFFFFFFF, 1f);
            Draw.circle(vg, kx, hy, 4f, cur, 1f);
        }
        Slider s = new Slider(x, w, 0f, 1f, v -> c.set(hsv(Anim.clamp01(v))));
        hit(x - 6f, hy - 10f, w + 12f, 20f, clipY, clipH, () -> dragging = s);
        return hy + 16f;
    }

    private float row(long vg, float x, float y, float w, String label, boolean on, Runnable toggle,
                      float clipY, float clipH) {
        Draw.text(vg, label, x, y + 11f, Fonts.MEDIUM, 11.5f, Draw.LEFT_MID, Theme.TEXT, 1f);
        Toggle.draw(vg, x + w - 36f, y + 2f, 36f, 18f, on ? 1f : 0f, 0f);
        hit(x, y, w, 22f, clipY, clipH, toggle);
        return y + 27f;
    }

    private void button(long vg, float x, float y, float w, String label, float mx, float my, Runnable r,
                        float clipY, float clipH) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + 26f;
        Draw.rect(vg, x, y, w, 26f, 7f, hov ? Theme.CARD_HOVER : Theme.CARD);
        Draw.stroke(vg, x + 0.5f, y + 0.5f, w - 1f, 25f, 7f, 1f, Theme.BORDER, 1f);
        Draw.text(vg, label, x + w / 2f, y + 13.5f, Fonts.MEDIUM, 11f, Draw.CENTER_MID, Theme.TEXT, 1f);
        hit(x, y, w, 26f, clipY, clipH, r);
    }

    // ---------------------------------------------------------------- цвет

    private static int hsv(float h) {
        return java.awt.Color.HSBtoRGB(h, 0.62f, 1f) | 0xFF000000;
    }

    private static float hueOf(int argb) {
        return java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null)[0];
    }

    // =====================================================================
    //  Ввод
    // =====================================================================

    /** Курсор над панелью (координаты хоста). */
    public boolean contains(float mx, float my) {
        return module != null && mx >= px && mx <= px + W && my >= py && my <= py + ph;
    }

    /** @return true — событие поглощено панелью (в т.ч. клик мимо, который её закрыл). */
    public boolean click(float mx, float my, int button) {
        if (module == null) return false;
        if (!contains(mx, my)) {
            close();
            return button == GLFW_MOUSE_BUTTON_LEFT;
        }
        if (button != GLFW_MOUSE_BUTTON_LEFT) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x() && mx <= h.x() + h.w() && my >= h.y() && my <= h.y() + h.h()) {
                h.action().run();
                if (dragging != null) applyDrag(mx);
                return true;
            }
        }
        return true;
    }

    public boolean scroll(float mx, float my, double amount) {
        if (!contains(mx, my)) return false;
        scroll.target -= (float) amount * 40f;
        return true;
    }

    public boolean keyPressed(int key) {
        if (module == null) return false;
        if (key == GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    private void updateDrag(float mx) {
        if (dragging == null) return;
        long win = MinecraftClient.getInstance().getWindow().getHandle();
        if (GLFW.glfwGetMouseButton(win, GLFW_MOUSE_BUTTON_LEFT) != GLFW_PRESS) {
            dragging = null;
            return;
        }
        applyDrag(mx);
    }

    private void applyDrag(float mx) {
        Slider s = dragging;
        s.apply().accept(s.min() + (s.max() - s.min()) * Anim.clamp01((mx - s.x()) / s.w()));
    }
}
