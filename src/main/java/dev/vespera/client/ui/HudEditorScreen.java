package dev.vespera.client.ui;

import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.ModuleConfig;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.NvgRenderer;
import dev.vespera.client.render.WorldDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Редактор HUD — свободная раскладка всех включённых HUD-модулей.
 *
 *  ЛКМ по модулю — перетащить (позиция хранится долей экрана 0..1 в ModuleConfig.layout);
 *  колесо над модулем — масштаб 0.5–2.0 (шаг 0.05, центр модуля стоит на месте);
 *  Shift + колесо — прозрачность 0.1–1.0 (шаг 0.05);
 *  ПКМ по модулю — панель настроек (масштаб, прозрачность, стиль, цвет, подопции, сброс позиции);
 *  магнит к краям и центру экрана с подсветкой линии, Alt — без магнита;
 *  Esc / «Готово» — выход с сохранением.
 *
 * Рисует NvgRenderer: renderBackdrop (затемнение) → HUD-модули (с раскладкой) → renderOverlay
 * (рамки, подписи, линии магнита, тултипы, кнопки). Координаты — HUD-единицы.
 * Клики разбираются в renderOverlay по геометрии текущего кадра, перетаскивание — опрос GLFW.
 */
public final class HudEditorScreen extends Screen {

    private static final float PAD = 6f;         // отступ магнита от края
    private static final float SNAP = 8f;        // радиус прилипания
    private static final float STEP = 0.05f;     // шаг колеса

    private final @Nullable Screen parent;
    private final ArrayDeque<double[]> pendingClicks = new ArrayDeque<>(); // {x, y, button}

    private HudModule dragging;
    private float offX, offY;
    private HudModule tooltipFor;
    private long tooltipUntil;
    private boolean snapLeft, snapRight, snapCenterX, snapTop, snapBottom, snapCenterY;
    private final Anim guideAlpha = new Anim(0f, 18f);
    private final Anim open = new Anim(0f, 10f);
    private float[] doneBtn = new float[4], resetBtn = new float[4];
    private boolean closing;
    private final ModuleSettingsPanel settings = new ModuleSettingsPanel(); // ПКМ по модулю

    public HudEditorScreen(@Nullable Screen parent) {
        super(Text.literal("Редактор HUD"));
        this.parent = parent;
        open.target = 1f;
    }

    // =====================================================================
    //  Кадр (вызывается из NvgRenderer)
    // =====================================================================

    private static float mouseX() {
        return (float) (MinecraftClient.getInstance().mouse.getX() / NvgRenderer.hudScale());
    }

    private static float mouseY() {
        return (float) (MinecraftClient.getInstance().mouse.getY() / NvgRenderer.hudScale());
    }

    /** Модули, которые редактор показывает и двигает. */
    private static List<HudModule> editable() {
        List<HudModule> out = new ArrayList<>();
        for (HudModule m : ModuleManager.hudModules()) if (m.isEnabled() && m.layoutable()) out.add(m);
        return out;
    }

    /** Верхний модуль под курсором (последний в порядке отрисовки). */
    private static @Nullable HudModule moduleAt(float mx, float my, float w, float h) {
        List<HudModule> list = editable();
        for (int i = list.size() - 1; i >= 0; i--) {
            float[] r = WorldDraw.layoutRect(list.get(i), w, h);
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) return list.get(i);
        }
        return null;
    }

    /** Затемнение и сетка — ПОД HUD-модулями. */
    public void renderBackdrop(long vg, float w, float h) {
        float e = Anim.easeOutCubic(open.value);
        nvgBeginPath(vg);
        nvgRect(vg, 0, 0, w, h);
        nvgFillColor(vg, Draw.c1(0xFF05060A, 0.6f * e));
        nvgFill(vg);
        for (int i = 1; i < 12; i++) { // лёгкая сетка
            Draw.rect(vg, w * i / 12f, 0, 1f, h, 0f, 0xFFFFFFFF, 0.025f * e);
            Draw.rect(vg, 0, h * i / 12f, w, 1f, 0f, 0xFFFFFFFF, 0.025f * e);
        }
    }

    /** Рамки, подписи, магнит, тултипы, кнопки — ПОВЕРХ HUD-модулей. */
    public void renderOverlay(long vg, float w, float h, float dt) {
        open.update(dt);
        float mx = mouseX(), my = mouseY();
        updateDrag(mx, my, w, h);

        List<HudModule> list = editable();
        HudModule hover = dragging != null ? dragging : moduleAt(mx, my, w, h);

        // ---- линии магнита
        guideAlpha.target = dragging != null && (snapLeft || snapRight || snapCenterX || snapTop || snapBottom || snapCenterY) ? 1f : 0f;
        guideAlpha.update(dt);
        if (guideAlpha.value > 0.01f) {
            float a = guideAlpha.value;
            if (snapLeft) guideV(vg, PAD, h, a);
            if (snapRight) guideV(vg, w - PAD, h, a);
            if (snapCenterX) guideV(vg, w / 2f, h, a);
            if (snapTop) guideH(vg, PAD, w, a);
            if (snapBottom) guideH(vg, h - PAD, w, a);
            if (snapCenterY) guideH(vg, h / 2f, w, a);
        }

        // ---- неоновые рамки и подписи
        for (HudModule m : list) {
            float[] r = WorldDraw.layoutRect(m, w, h);
            boolean hot = m == hover, drag = m == dragging;
            float rr = 8f;
            Draw.glow(vg, r[0] - 3f, r[1] - 3f, r[2] + 6f, r[3] + 6f, rr, drag ? 18f : 12f, Theme.ACCENT, drag ? 0.45f : hot ? 0.3f : 0.14f);
            Draw.rect(vg, r[0] - 3f, r[1] - 3f, r[2] + 6f, r[3] + 6f, rr, Theme.ACCENT, hot ? 0.08f : 0.03f);
            Draw.stroke(vg, r[0] - 3f, r[1] - 3f, r[2] + 6f, r[3] + 6f, rr, hot ? 1.6f : 1f, Theme.ACCENT, hot ? 0.95f : 0.5f);

            String name = m.name();
            float tw = Draw.textWidth(vg, name, Fonts.SEMIBOLD, 11f);
            float lx = r[0] - 3f, ly = r[1] - 3f - 20f;
            if (ly < 4f) ly = r[1] + r[3] + 6f; // у верхнего края — подпись снизу
            Draw.rect(vg, lx, ly, tw + 16f, 17f, 6f, hot ? Theme.ACCENT : 0xFF0E1016, hot ? 0.9f : 0.85f);
            Draw.text(vg, name, lx + 8f, ly + 9f, Fonts.SEMIBOLD, 11f, Draw.LEFT_MID, hot ? 0xFF061412 : Theme.TEXT, 1f);
        }

        // ---- тултип с цифрами (наведение или только что крутили колесо)
        HudModule tip = System.currentTimeMillis() < tooltipUntil && tooltipFor != null && tooltipFor.isEnabled() ? tooltipFor : hover;
        if (tip != null) {
            ModuleConfig.Layout l = ModuleConfig.layout(tip);
            float[] r = WorldDraw.layoutRect(tip, w, h);
            String txt = String.format(Locale.ROOT, "×%.2f  ·  %d%%  ·  %.2f, %.2f", l.scale, Math.round(l.alpha * 100), l.x, l.y);
            float tw = Draw.textWidth(vg, txt, Fonts.MEDIUM, 11f);
            float tx = Math.min(w - tw - 22f, r[0] + r[2] + 10f), ty = r[1];
            if (tx < r[0] + r[2]) tx = Math.max(4f, r[0] - tw - 26f); // не влезает справа — слева
            Draw.shadow(vg, tx, ty, tw + 18f, 22f, 7f, 10f, 2f, 0xFF000000, 0.5f);
            Draw.rect(vg, tx, ty, tw + 18f, 22f, 7f, 0xFF0E1016, 0.95f);
            Draw.stroke(vg, tx + 0.5f, ty + 0.5f, tw + 17f, 21f, 7f, 1f, Theme.ACCENT, 0.4f);
            Draw.text(vg, txt, tx + 9f, ty + 11.5f, Fonts.MEDIUM, 11f, Draw.LEFT_MID, Theme.TEXT, 1f);
        }

        drawTopBar(vg, w, mx, my);
        settings.render(vg, w, h, mx, my, dt);
        if (list.isEmpty()) {
            Draw.text(vg, "Включи HUD-модули в меню (Right Shift) — они появятся здесь", w / 2f, h / 2f,
                    Fonts.MEDIUM, 14f, Draw.CENTER_MID, Theme.TEXT_DIM, 1f);
        }

        // ---- клики по геометрии этого кадра
        while (!pendingClicks.isEmpty()) {
            double[] c = pendingClicks.poll();
            handleClick((float) c[0], (float) c[1], (int) c[2], w, h);
        }
    }

    private static void guideV(long vg, float x, float h, float a) {
        Draw.rect(vg, x - 3f, 0, 6f, h, 3f, Theme.ACCENT, 0.12f * a);
        Draw.rect(vg, x - 0.75f, 0, 1.5f, h, 0.75f, Theme.ACCENT, 0.9f * a);
    }

    private static void guideH(long vg, float y, float w, float a) {
        Draw.rect(vg, 0, y - 3f, w, 6f, 3f, Theme.ACCENT, 0.12f * a);
        Draw.rect(vg, 0, y - 0.75f, w, 1.5f, 0.75f, Theme.ACCENT, 0.9f * a);
    }

    private void drawTopBar(long vg, float w, float mx, float my) {
        String hint = "ЛКМ — двигать  ·  колесо — размер  ·  Shift+колесо — прозрачность  ·  ПКМ — настройки  ·  Alt — без магнита";
        float hw = Draw.textWidth(vg, hint, Fonts.REGULAR, 11f);
        float barW = Math.max(hw + 32f, 520f), barH = 64f, bx = (w - barW) / 2f, by = 12f;
        Draw.shadow(vg, bx, by, barW, barH, 14f, 20f, 4f, 0xFF000000, 0.55f);
        Draw.glow(vg, bx, by, barW, barH, 14f, 14f, Theme.ACCENT, 0.12f);
        Draw.rect(vg, bx, by, barW, barH, 14f, Theme.BG, 0.94f);
        Draw.topHighlight(vg, bx, by, barW, barH, 14f, 0.04f);
        Draw.stroke(vg, bx + 0.5f, by + 0.5f, barW - 1f, barH - 1f, 14f, 1f, Theme.ACCENT, 0.3f);

        Draw.text(vg, "Редактор HUD", bx + 16f, by + 21f, Fonts.SEMIBOLD, 15f, Draw.LEFT_MID, Theme.TEXT, 1f);
        Draw.text(vg, hint, bx + 16f, by + 45f, Fonts.REGULAR, 11f, Draw.LEFT_MID, Theme.TEXT_DIM, 1f);

        doneBtn = button(vg, bx + barW - 16f - 86f, by + 9f, 86f, 26f, "Готово", true, mx, my);
        resetBtn = button(vg, doneBtn[0] - 8f - 92f, by + 9f, 92f, 26f, "Сбросить всё", false, mx, my);
    }

    private static float[] button(long vg, float x, float y, float w, float h, String label, boolean primary,
                                  float mx, float my) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (primary) {
            Draw.glow(vg, x, y, w, h, 8f, 10f, Theme.ACCENT, hov ? 0.45f : 0.25f);
            Draw.linear(vg, x, y, w, h, 8f, x, y, x + w, y, Theme.ACCENT, 1f, Theme.ACCENT_2, 1f);
        } else {
            Draw.rect(vg, x, y, w, h, 8f, hov ? Theme.CARD_HOVER : Theme.CARD);
            Draw.stroke(vg, x + 0.5f, y + 0.5f, w - 1f, h - 1f, 8f, 1f, Theme.BORDER, 1f);
        }
        Draw.text(vg, label, x + w / 2f, y + h / 2f + 0.5f, Fonts.SEMIBOLD, 12f, Draw.CENTER_MID,
                primary ? 0xFF061412 : Theme.TEXT, 1f);
        return new float[]{x, y, w, h};
    }

    private static boolean in(float[] r, float x, float y) {
        return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
    }

    // =====================================================================
    //  Перетаскивание и магнит
    // =====================================================================

    private void handleClick(float mx, float my, int button, float w, float h) {
        if (closing) return;
        if (button == GLFW_MOUSE_BUTTON_LEFT && !settings.contains(mx, my) && in(doneBtn, mx, my)) { done(); return; }
        if (button == GLFW_MOUSE_BUTTON_LEFT && !settings.contains(mx, my) && in(resetBtn, mx, my)) {
            for (HudModule m : editable()) ModuleConfig.resetLayoutFull(m);
            return;
        }
        if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            if (settings.contains(mx, my)) return;
            HudModule target = moduleAt(mx, my, w, h);
            if (target == null) { settings.close(); return; }
            settings.toggle(target, mx, my); // повторный ПКМ по тому же модулю — закрыть
            return;
        }
        if (settings.isOpen()) { settings.click(mx, my, button); return; } // клик мимо закрывает
        HudModule m = moduleAt(mx, my, w, h);
        if (m == null) return;
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            float[] r = WorldDraw.layoutRect(m, w, h);
            dragging = m;
            offX = mx - r[0];
            offY = my - r[1];
        }
    }

    private static boolean keyDown(int a, int b) {
        long win = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(win, a) == GLFW_PRESS || GLFW.glfwGetKey(win, b) == GLFW_PRESS;
    }

    private void updateDrag(float mx, float my, float w, float h) {
        if (dragging == null) return;
        long win = MinecraftClient.getInstance().getWindow().getHandle();
        if (GLFW.glfwGetMouseButton(win, GLFW_MOUSE_BUTTON_LEFT) != GLFW_PRESS || !dragging.isEnabled()) {
            dragging = null; // отпустили — позиция уже в layout
            clearSnap();
            ModuleConfig.markDirty();
            return;
        }
        ModuleConfig.Layout l = ModuleConfig.layout(dragging);
        float bw = dragging.boundsW() * l.scale, bh = dragging.boundsH() * l.scale;
        float x = mx - offX, y = my - offY;

        clearSnap();
        if (!keyDown(GLFW_KEY_LEFT_ALT, GLFW_KEY_RIGHT_ALT)) { // магнит
            if (Math.abs(x - PAD) < SNAP) { x = PAD; snapLeft = true; }
            else if (Math.abs(x + bw - (w - PAD)) < SNAP) { x = w - PAD - bw; snapRight = true; }
            else if (Math.abs(x + bw / 2f - w / 2f) < SNAP) { x = w / 2f - bw / 2f; snapCenterX = true; }
            if (Math.abs(y - PAD) < SNAP) { y = PAD; snapTop = true; }
            else if (Math.abs(y + bh - (h - PAD)) < SNAP) { y = h - PAD - bh; snapBottom = true; }
            else if (Math.abs(y + bh / 2f - h / 2f) < SNAP) { y = h / 2f - bh / 2f; snapCenterY = true; }
        }
        x = Math.max(0f, Math.min(w - bw, x));
        y = Math.max(0f, Math.min(h - bh, y));
        l.x = w > 0 ? x / w : 0f;
        l.y = h > 0 ? y / h : 0f;
        l.clamp();
        ModuleConfig.markDirty();
    }

    private void clearSnap() {
        snapLeft = snapRight = snapCenterX = snapTop = snapBottom = snapCenterY = false;
    }

    private void showTooltip(HudModule m) {
        tooltipFor = m;
        tooltipUntil = System.currentTimeMillis() + 1400;
    }

    private void done() {
        if (closing) return;
        closing = true;
        dragging = null;
        ModuleConfig.save(); // автосейв при выходе
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> mc.setScreen(parent));
    }

    // =====================================================================
    //  Screen
    // =====================================================================

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        pendingClicks.add(new double[]{mouseX(), mouseY(), click.button()});
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0) return true;
        if (settings.scroll(mouseX(), mouseY(), vertical)) return true;
        MinecraftClient mc = MinecraftClient.getInstance();
        float s = NvgRenderer.hudScale();
        float w = mc.getWindow().getWidth() / s, h = mc.getWindow().getHeight() / s;
        HudModule m = moduleAt(mouseX(), mouseY(), w, h);
        if (m == null) return true;
        ModuleConfig.Layout l = ModuleConfig.layout(m);
        float dir = vertical > 0 ? 1f : -1f;
        if (keyDown(GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT)) {
            l.alpha = Math.round((l.alpha + dir * STEP) * 20f) / 20f;
        } else {
            // масштаб вокруг центра модуля
            float[] r = WorldDraw.layoutRect(m, w, h);
            float cx = r[0] + r[2] / 2f, cy = r[1] + r[3] / 2f;
            l.scale = Math.round((l.scale + dir * STEP) * 20f) / 20f;
            l.clamp();
            float nw = m.boundsW() * l.scale, nh = m.boundsH() * l.scale;
            l.x = (cx - nw / 2f) / w;
            l.y = (cy - nh / 2f) / h;
        }
        l.clamp();
        ModuleConfig.markDirty();
        showTooltip(m);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (settings.keyPressed(input.key())) return true; // Esc сначала закрывает панель
        if (input.key() == GLFW_KEY_ESCAPE) {
            done();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Esc обрабатываем сами — с сохранением
    }

    @Override
    public void removed() {
        ModuleConfig.saveIfDirty(); // на случай закрытия не через «Готово»
    }

    /** Намеренно пусто: рисует NvgRenderer. */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
