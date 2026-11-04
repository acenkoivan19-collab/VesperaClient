package dev.vespera.client.ui;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.Module;
import dev.vespera.client.module.ModuleConfig;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.render.Brand;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.Icons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.nanovg.NanoVG.*;

/**
 * ClickGUI Vespera Client (Dark Soft UI, NanoVG).
 *
 * Отрисовка — в {@link #renderNvg} (вызывает NvgRenderer в конце кадра).
 * Screen используется только для ввода; DrawContext не используется.
 *
 * <h3>Как обрабатываются клики</h3>
 * Раньше hit-test делался прямо в mouseClicked() по геометрии ПРОШЛОГО кадра,
 * а позиция мыши переводилась трансформацией, которая к этому моменту уже
 * могла измениться (анимация открытия, плавный скролл). Отсюда промахи.
 *
 * Теперь mouseClicked() только кладёт клик в очередь вместе с экранными
 * координатами курсора В МОМЕНТ НАЖАТИЯ. Очередь разбирается в renderNvg()
 * ПОСЛЕ раскладки текущего кадра — тем же преобразованием и по тем же
 * прямоугольникам, которые сейчас на экране. Клик попадает ровно туда,
 * куда смотрит пользователь.
 */
public final class ClickGuiScreen extends Screen {

    // ---- раскладка (логические px при uiScale = 1)
    private static final float W = 920f, H = 600f, PANEL_R = 18f;
    private static final float SIDEBAR_W = 232f;
    private static final float NAV_X = 20f, NAV_Y = 112f, NAV_H = 42f, NAV_GAP = 6f;
    private static final float HEADER_Y = 24f, SEARCH_W = 230f, SEARCH_H = 40f;
    private static final float CONTENT_X = SIDEBAR_W + 12f, CONTENT_R = 24f;
    private static final float TITLE_Y = 94f, GRID_Y = 120f, GRID_BOTTOM = 20f;
    private static final float CARD_H = 70f, GAP = 12f;
    private static final float PROFILE_H = 64f;

    private static final float GRID_X = CONTENT_X + 8f;
    private static final float GRID_W = W - CONTENT_R - GRID_X;
    private static final float GRID_H = H - GRID_Y - GRID_BOTTOM;
    private static final float NAV_W = SIDEBAR_W - NAV_X * 2f, NAV_ITEM_H = NAV_H - NAV_GAP;

    // ---- анимации
    private final Anim open = new Anim(0f, 12f);
    private final Anim navSelection = new Anim(NAV_Y, 16f);
    private final Anim scroll = new Anim(0f, 14f);
    private final Map<Category, Anim> navHover = new EnumMap<>(Category.class);

    // ---- данные
    private final List<ModuleCard> cards = new ArrayList<>();
    private final List<ModuleCard> visible = new ArrayList<>();
    private final Map<Category, Integer> counts = new EnumMap<>(Category.class);
    private final SearchField search = new SearchField();
    private Category selected = Category.ALL;
    private String appliedQuery = null;
    private Category appliedCategory = null;

    // ---- ввод: клики в экранных координатах на момент нажатия
    private final ArrayDeque<double[]> pendingClicks = new ArrayDeque<>();
    private final float[] hudEditorBtn = new float[4];
    private final ModuleSettingsPanel settings = new ModuleSettingsPanel(); // ПКМ по карточке

    // ---- состояние кадра
    private long lastFrameNs = -1L;
    private boolean closing, closeScheduled;
    private float originX, originY, scaleEff = 1f;

    public ClickGuiScreen() {
        super(Text.literal(Brand.NAME));
        for (Category c : Category.values()) {
            navHover.put(c, new Anim(0f, 18f));
            counts.put(c, 0);
        }
        for (Module m : ModuleManager.all()) {
            cards.add(new ModuleCard(m));
            counts.merge(m.category(), 1, Integer::sum);
            counts.merge(Category.ALL, 1, Integer::sum);
        }
        open.target = 1f;
    }

    // =====================================================================
    //  Кадр
    // =====================================================================

    public void renderNvg(long vg, float winW, float winH) {
        long now = System.nanoTime();
        float dt = lastFrameNs < 0 ? 0f : Math.min((now - lastFrameNs) / 1_000_000_000f, 0.1f);
        lastFrameNs = now;

        open.update(dt);
        if (closing && open.value < 0.02f && !closeScheduled) {
            closeScheduled = true;
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> { if (mc.currentScreen == this) mc.setScreen(null); });
        }

        // Адаптивный масштаб: панель занимает ≤ 92% окна, но не крупнее 115%.
        float fit = Math.min(winW * 0.92f / W, winH * 0.92f / H);
        float uiScale = Math.max(0.3f, Math.min(fit, 1.15f));
        float e = Anim.easeOutCubic(open.value);
        scaleEff = uiScale * (0.94f + 0.06f * e);
        originX = (winW - W * scaleEff) / 2f;
        originY = (winH - H * scaleEff) / 2f;

        MinecraftClient mc = MinecraftClient.getInstance();
        float mx = toLocalX(mc.mouse.getX()), my = toLocalY(mc.mouse.getY());
        refreshVisible();

        // затемнение мира
        nvgBeginPath(vg);
        nvgRect(vg, 0, 0, winW, winH);
        nvgFillColor(vg, Draw.c1(0xFF000000, 0.5f * e));
        nvgFill(vg);

        nvgSave(vg);
        nvgGlobalAlpha(vg, e);
        nvgTranslate(vg, originX, originY);
        nvgScale(vg, scaleEff, scaleEff);

        drawPanel(vg);
        drawSidebar(vg, mx, my, dt);
        drawHeader(vg, mx, my, dt);
        drawGrid(vg, mx, my, dt);
        settings.render(vg, W, H, mx, my, dt);

        nvgRestore(vg);

        // Раскладка кадра готова → разбираем клики по актуальной геометрии.
        while (!pendingClicks.isEmpty()) {
            double[] c = pendingClicks.poll();
            handleClick(toLocalX(c[0]), toLocalY(c[1]), (int) c[2]);
        }
    }

    private float toLocalX(double screenX) {
        return (float) ((screenX - originX) / scaleEff);
    }

    private float toLocalY(double screenY) {
        return (float) ((screenY - originY) / scaleEff);
    }

    private void drawPanel(long vg) {
        Draw.shadow(vg, 0, 0, W, H, PANEL_R, 40f, 12f, 0xFF000000, 0.6f);
        Draw.rect(vg, 0, 0, W, H, PANEL_R, Theme.BG);
        Draw.stroke(vg, 0.5f, 0.5f, W - 1f, H - 1f, PANEL_R, 1f, Theme.BORDER, 1f);
    }

    // ---------------------------------------------------------------- sidebar

    private void drawSidebar(long vg, float mx, float my, float dt) {
        float sx = 10f, sy = 10f, sw = SIDEBAR_W - 10f, sh = H - 20f;
        Draw.rect(vg, sx, sy, sw, sh, 14f, Theme.SIDEBAR);
        Draw.stroke(vg, sx + 0.5f, sy + 0.5f, sw - 1f, sh - 1f, 14f, 1f, 0xFFFFFFFF, 0.03f);

        // бренд
        float lx = 30f, ly = 54f, ls = 36f;
        Brand.logo(vg, lx, ly - ls / 2f, ls, 10f, 1f);
        Draw.text(vg, Brand.NAME, lx + ls + 12f, ly - 7f, Fonts.SEMIBOLD, 16f, Draw.LEFT_MID, Theme.TEXT, 1f);
        Draw.text(vg, "visual client", lx + ls + 12f, ly + 10f, Fonts.REGULAR, 11f, Draw.LEFT_MID, Theme.TEXT_DIM, 1f);

        nvgTextLetterSpacing(vg, 1.4f);
        Draw.text(vg, "NAVIGATION", NAV_X + 14f, NAV_Y - 16f, Fonts.SEMIBOLD, 10f,
                Draw.LEFT_MID, Theme.TEXT_MUTED, 1f);
        nvgTextLetterSpacing(vg, 0f);

        // скользящая подсветка активного раздела
        navSelection.target = navY(selected);
        navSelection.update(dt);
        float py = navSelection.value;
        // активная категория: плоская светлая плашка + акцентная полоска слева (единственный акцент сайдбара)
        Draw.rect(vg, NAV_X, py, NAV_W, NAV_ITEM_H, 9f, 0xFFFFFFFF, 0.06f);
        Draw.rect(vg, NAV_X + 6f, py + NAV_ITEM_H / 2f - 8f, 3f, 16f, 1.5f, Theme.ACCENT);

        for (Category cat : Category.values()) {
            float iy = navY(cat);
            boolean isSel = cat == selected;
            Anim hv = navHover.get(cat);
            hv.target = inside(mx, my, NAV_X, iy, NAV_W, NAV_ITEM_H) ? 1f : 0f;
            hv.update(dt);
            float h = hv.value;

            if (!isSel && h > 0.01f) Draw.rect(vg, NAV_X, iy, NAV_W, NAV_ITEM_H, 9f, 0xFFFFFFFF, 0.03f * h);

            int textCol = isSel ? Theme.TEXT : Draw.lerpColor(Theme.TEXT_DIM, Theme.TEXT, h * 0.7f);
            int iconCol = isSel ? Theme.ACCENT : textCol;
            float slide = isSel ? 2f : 2f * h;

            Icons.draw(vg, cat, NAV_X + 26f + slide, iy + NAV_ITEM_H / 2f, 15f, iconCol, 1f);
            Draw.text(vg, cat.displayName(), NAV_X + 46f + slide, iy + NAV_ITEM_H / 2f,
                    Fonts.MEDIUM, 13.5f, Draw.LEFT_MID, textCol, 1f);
            Draw.text(vg, String.valueOf(counts.get(cat)), NAV_X + NAV_W - 14f, iy + NAV_ITEM_H / 2f,
                    Fonts.MEDIUM, 11f, Draw.RIGHT_MID, isSel ? Theme.TEXT_DIM : Theme.TEXT_MUTED, 1f);
        }

        drawProfile(vg);
    }

    private void drawProfile(long vg) {
        float bx = NAV_X, bw = NAV_W;
        float by = H - 10f - 12f - PROFILE_H;
        float cy = by + PROFILE_H / 2f;

        Draw.rect(vg, bx, by, bw, PROFILE_H, 10f, Theme.CARD);
        Draw.stroke(vg, bx + 0.5f, by + 0.5f, bw - 1f, PROFILE_H - 1f, 10f, 1f, Theme.BORDER, 0.8f);

        // квадратная скруглённая аватарка-логотип
        float as = 40f, ax = bx + 12f, ay = cy - as / 2f;
        Brand.logo(vg, ax, ay, as, 11f, 1f);
        Draw.circle(vg, ax + as - 2f, ay + as - 2f, 5.5f, Theme.CARD, 1f);
        Draw.circle(vg, ax + as - 2f, ay + as - 2f, 3.8f, Theme.ONLINE, 1f);

        float tx = ax + as + 12f, maxW = bx + bw - 10f - tx;
        Draw.text(vg, Draw.ellipsize(vg, Brand.NAME, Fonts.SEMIBOLD, 13.5f, maxW),
                tx, cy - 8f, Fonts.SEMIBOLD, 13.5f, Draw.LEFT_MID, Theme.TEXT, 1f);
        Draw.text(vg, Draw.ellipsize(vg, playerName(), Fonts.REGULAR, 11.5f, maxW),
                tx, cy + 9f, Fonts.REGULAR, 11.5f, Draw.LEFT_MID, Theme.TEXT_DIM, 1f);
    }

    private static String playerName() {
        try {
            return MinecraftClient.getInstance().getSession().getUsername();
        } catch (Throwable t) {
            return "Player";
        }
    }

    // ---------------------------------------------------------------- header

    private void drawHeader(long vg, float mx, float my, float dt) {
        float cy = HEADER_Y + SEARCH_H / 2f;
        float x = GRID_X;
        Draw.text(vg, Brand.NAME, x, cy, Fonts.SEMIBOLD, 15f, Draw.LEFT_MID, Theme.TEXT, 1f);
        x += Draw.textWidth(vg, Brand.NAME, Fonts.SEMIBOLD, 15f) + 14f;

        float pillY = cy - 13f;
        x = pill(vg, x, pillY, "1.21.11", Theme.TEXT_DIM) + 8f;
        x = pill(vg, x, pillY, "Fabric", Theme.TEXT_DIM) + 12f;

        // кнопка «Редактор HUD» (то же делает бинд H)
        String lbl = "Редактор HUD";
        float bw = Draw.textWidth(vg, lbl, Fonts.SEMIBOLD, 11.5f) + 36f, bh = 26f;
        boolean hov = inside(mx, my, x, pillY, bw, bh);
        hudEditorBtn[0] = x; hudEditorBtn[1] = pillY; hudEditorBtn[2] = bw; hudEditorBtn[3] = bh;
        Draw.rect(vg, x, pillY, bw, bh, 8f, hov ? Theme.CARD_HOVER : Theme.CARD);
        Draw.stroke(vg, x + 0.5f, pillY + 0.5f, bw - 1f, bh - 1f, 8f, 1f, Theme.BORDER, 1f);
        // иконка: рамка с «перекрестием перемещения»
        float ix = x + 14f, iy = pillY + bh / 2f;
        Draw.stroke(vg, ix - 5f, iy - 5f, 10f, 10f, 2f, 1.3f, Theme.TEXT_DIM, 1f);
        Draw.rect(vg, ix - 0.6f, iy - 3f, 1.2f, 6f, 0.6f, Theme.TEXT_DIM);
        Draw.rect(vg, ix - 3f, iy - 0.6f, 6f, 1.2f, 0.6f, Theme.TEXT_DIM);
        Draw.text(vg, lbl, x + 25f, iy + 0.5f, Fonts.SEMIBOLD, 11.5f, Draw.LEFT_MID, Theme.TEXT, 1f);

        // поиск закреплён справа и не участвует в скролле
        search.render(vg, W - CONTENT_R - SEARCH_W, HEADER_Y, SEARCH_W, SEARCH_H, mx, my, dt);

        boolean searching = !search.query().isEmpty();
        String title = searching ? "Search results" : selected.displayName();
        Draw.text(vg, title, GRID_X, TITLE_Y, Fonts.SEMIBOLD, 20f, Draw.LEFT_MID, Theme.TEXT, 1f);
        float tw = Draw.textWidth(vg, title, Fonts.SEMIBOLD, 20f);
        long on = visible.stream().filter(c -> c.module().isEnabled()).count();
        String sub = visible.size() + " modules · " + on + " enabled";
        Draw.text(vg, sub, GRID_X + tw + 12f, TITLE_Y + 1f, Fonts.REGULAR, 12f, Draw.LEFT_MID, Theme.TEXT_DIM, 1f);
        Draw.rect(vg, GRID_X, HEADER_Y + SEARCH_H + 10f, W - CONTENT_R - GRID_X, 1f, 0f, 0xFFFFFFFF, 0.05f); // тонкий разделитель
    }

    private float pill(long vg, float x, float y, String label, int color) {
        float tw = Draw.textWidth(vg, label, Fonts.SEMIBOLD, 11.5f);
        float w = tw + 30f, h = 26f;
        Draw.rect(vg, x, y, w, h, 8f, 0xFFFFFFFF, 0.04f);
        Draw.stroke(vg, x + 0.5f, y + 0.5f, w - 1f, h - 1f, 8f, 1f, Theme.BORDER, 1f);
        Draw.circle(vg, x + 12f, y + h / 2f, 2.6f, color, 1f);
        Draw.text(vg, label, x + 20f, y + h / 2f + 0.5f, Fonts.SEMIBOLD, 11.5f, Draw.LEFT_MID, color, 1f);
        return x + w;
    }

    // ---------------------------------------------------------------- grid

    private void drawGrid(long vg, float mx, float my, float dt) {
        float cardW = (GRID_W - GAP) / 2f;

        int rows = (visible.size() + 1) / 2;
        float contentH = rows == 0 ? 0f : rows * CARD_H + (rows - 1) * GAP;
        float maxScroll = Math.max(0f, contentH - GRID_H + 12f);
        scroll.target = Anim.clamp(scroll.target, 0f, maxScroll);
        scroll.update(dt);

        boolean mouseInGrid = inside(mx, my, GRID_X, GRID_Y, GRID_W, GRID_H);
        for (ModuleCard c : cards) c.hide(); // невидимые карточки не ловят клики

        nvgSave(vg);
        nvgIntersectScissor(vg, GRID_X - 10f, GRID_Y, GRID_W + 20f, GRID_H);
        for (int i = 0; i < visible.size(); i++) {
            float x = GRID_X + (i % 2) * (cardW + GAP);
            float y = GRID_Y + 6f + (i / 2) * (CARD_H + GAP) - scroll.value;
            if (y + CARD_H < GRID_Y || y > GRID_Y + GRID_H) continue;
            visible.get(i).render(vg, x, y, cardW, CARD_H, mx, my, mouseInGrid, dt);
        }
        nvgRestore(vg);

        // мягкие края области скролла
        Draw.linear(vg, GRID_X - 10f, GRID_Y, GRID_W + 20f, 14f, 0f, 0, GRID_Y, 0, GRID_Y + 14f,
                Theme.BG, 1f, Theme.BG, 0f);
        float by = GRID_Y + GRID_H - 14f;
        Draw.linear(vg, GRID_X - 10f, by, GRID_W + 20f, 14f, 0f, 0, by, 0, by + 14f,
                Theme.BG, 0f, Theme.BG, 1f);

        if (maxScroll > 0f) {
            float trackX = W - 12f, thumbH = Math.max(36f, GRID_H * GRID_H / (contentH + 12f));
            float thumbY = GRID_Y + (GRID_H - thumbH) * (scroll.value / maxScroll);
            Draw.rect(vg, trackX, GRID_Y, 3f, GRID_H, 1.5f, 0xFFFFFFFF, 0.04f);
            Draw.rect(vg, trackX, thumbY, 3f, thumbH, 1.5f, 0xFFFFFFFF, 0.18f);
        }

        if (visible.isEmpty()) {
            Icons.search(vg, GRID_X + GRID_W / 2f, GRID_Y + GRID_H / 2f - 22f, 26f, Theme.TEXT_MUTED, 1f);
            Draw.text(vg, "No modules found", GRID_X + GRID_W / 2f, GRID_Y + GRID_H / 2f + 12f,
                    Fonts.MEDIUM, 14f, Draw.CENTER_MID, Theme.TEXT_DIM, 1f);
        }
    }

    /** Пересобирает список видимых карточек только при смене раздела/запроса. */
    private void refreshVisible() {
        String q = search.query();
        if (q.equals(appliedQuery) && selected == appliedCategory) return;
        appliedQuery = q;
        appliedCategory = selected;
        visible.clear();
        for (ModuleCard c : cards) {
            Module m = c.module();
            boolean match = q.isEmpty()
                    ? (selected == Category.ALL || m.category() == selected)
                    : (m.name().toLowerCase(Locale.ROOT).contains(q)
                       || m.description().toLowerCase(Locale.ROOT).contains(q)); // поиск — глобальный
            if (match) visible.add(c);
        }
        scroll.target = 0f;
    }

    // =====================================================================
    //  Ввод
    // =====================================================================

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static float navY(Category c) {
        return NAV_Y + c.ordinal() * NAV_H;
    }

    /** Hit-test по геометрии ТЕКУЩЕГО кадра. Порядок = порядок слоёв сверху вниз. */
    private void handleClick(float mx, float my, int button) {
        if (closing) return;

        // 0. ПКМ-панель настроек модуля
        if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            if (settings.contains(mx, my)) return;
            ModuleCard card = cardAt(mx, my);
            if (card == null) { settings.close(); return; }
            settings.toggle(card.module(), mx, my); // повторный ПКМ по той же карточке — закрыть
            return;
        }
        if (button != GLFW_MOUSE_BUTTON_LEFT) return;
        if (settings.isOpen()) { settings.click(mx, my, button); return; } // клик мимо закрывает

        // 1. поле поиска (клик мимо — снимает фокус)
        if (search.click(mx, my)) return;

        // 2. кнопка «Редактор HUD»
        if (inside(mx, my, hudEditorBtn[0], hudEditorBtn[1], hudEditorBtn[2], hudEditorBtn[3])) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> mc.setScreen(new HudEditorScreen(this)));
            return;
        }

        // 2. навигация
        for (Category cat : Category.values()) {
            if (inside(mx, my, NAV_X, navY(cat), NAV_W, NAV_ITEM_H)) {
                if (cat != selected) {
                    selected = cat;
                    scroll.target = 0f;
                }
                return;
            }
        }

        // 3. карточки — только внутри видимой области сетки (не под фейдами/за scissor)
        if (!inside(mx, my, GRID_X, GRID_Y, GRID_W, GRID_H)) return;
        for (ModuleCard card : visible) {
            if (card.hit(mx, my)) {
                card.module().toggle(); // ← связка: GUI → Module.setEnabled → onEnable/onDisable
                card.pulse();
                return;
            }
        }
    }

    /** Карточка под курсором (только внутри видимой области сетки). */
    private ModuleCard cardAt(float mx, float my) {
        if (!inside(mx, my, GRID_X, GRID_Y, GRID_W, GRID_H)) return null;
        for (ModuleCard card : visible) if (card.hit(mx, my)) return card;
        return null;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int b = click.button();
        if ((b == GLFW_MOUSE_BUTTON_LEFT || b == GLFW_MOUSE_BUTTON_RIGHT) && !closing) {
            MinecraftClient mc = MinecraftClient.getInstance();
            // экранные координаты окна (те же, что у NanoVG), а не GUI-scaled click.x()
            pendingClicks.add(new double[]{mc.mouse.getX(), mc.mouse.getY(), b});
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (settings.scroll(toLocalX(mc.mouse.getX()), toLocalY(mc.mouse.getY()), vertical)) return true;
        scroll.target -= (float) vertical * 56f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (settings.keyPressed(input.key())) return true; // Esc закрывает панель, а не меню
        if (search.keyPressed(input.key(), input.modifiers())) return true;
        int key = input.key();
        if (key == GLFW_KEY_ESCAPE || key == GLFW_KEY_RIGHT_SHIFT) {
            requestClose();
            return true;
        }
        if (key == GLFW_KEY_F && (input.modifiers() & GLFW_MOD_CONTROL) != 0) {
            search.setFocused(true);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        return search.charTyped(input.codepoint()) || super.charTyped(input);
    }

    private void requestClose() {
        closing = true;
        open.target = 0f;
        search.setFocused(false);
        pendingClicks.clear();
    }

    // =====================================================================
    //  Screen
    // =====================================================================

    /** Намеренно пусто: рисует NvgRenderer. */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void removed() {
        ModuleConfig.saveIfDirty(); // меню закрыли — сразу сохраняем состояния
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Esc обрабатываем сами — с анимацией закрытия
    }
}
