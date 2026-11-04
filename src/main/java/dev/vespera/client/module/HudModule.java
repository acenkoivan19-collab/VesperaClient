package dev.vespera.client.module;

import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.Setting;
import dev.vespera.client.ui.Anim;
import dev.vespera.client.ui.Theme;

import java.util.List;

/**
 * Модуль, который рисует что-то поверх игры через NanoVG.
 * {@link #renderHud} вызывается только пока модуль включён —
 * выключил тумблер в меню → элемент пропадает в тот же кадр.
 *
 * <h3>Свободная раскладка (Редактор HUD)</h3>
 * Если {@link #layoutable()} == true, ModuleManager ПЕРЕД renderHud сам вызывает
 * {@link dev.vespera.client.render.WorldDraw#applyLayout}: translate в позицию из layout,
 * scale и глобальная прозрачность. Поэтому модуль рисует себя в ЛОКАЛЬНЫХ координатах
 * от (0,0) и сообщает свой размер через {@link #size(float, float)} — по нему работают
 * перетаскивание, магнит и рамки редактора. Новый модуль получает редактор бесплатно.
 *
 * Эффекты в мире (кольца, крылья…) привязаны к точкам мира, а не экрана —
 * для них layoutable() == false, редактор их не двигает.
 */
public abstract class HudModule extends Module {

    /** Стили панели по умолчанию (модуль может задать свои через {@link #styles}). */
    public static final String[] PANEL_STYLES = {"Soft", "Glass", "Минимал", "Контур"};
    public static final String[] PANEL_HINTS = {
            "Тёмная плашка с мягкой тенью", "Полупрозрачное «стекло» со светлой кромкой",
            "Без подложки — только акцентная полоска слева", "Только тонкий цветной контур"};

    /** Слои отрисовки: чем меньше, тем раньше (ниже). */
    public static final int LAYER_TINT = 0, LAYER_WORLD = 100, LAYER_HUD = 200, LAYER_OVERLAY = 300;

    /** Идёт ли сейчас Редактор HUD (модули рисуют пример-данные, если своих нет). */
    private static boolean editing;

    private float boundsW = 120f, boundsH = 30f;

    // ---- общие настройки экранных модулей (ПКМ-панель): стиль, цвет, фон, тень, анимация
    private ChoiceSetting style;
    private ColorSetting color;
    private BoolSetting background, shadow, animation;
    private boolean commonReady;

    protected HudModule(String name, String description, Category category) {
        super(name, description, category);
    }

    /**
     * Общие настройки создаются лениво при первом обращении — к этому моменту подкласс
     * уже сконструирован и layoutable()/styles заданы корректно.
     */
    private void ensureCommon() {
        if (commonReady) return;
        commonReady = true;
        if (!layoutable()) return; // эффектам в мире стиль панели не нужен
        style = new ChoiceSetting("style", "Стиль", 0, pendingStyles != null ? pendingStyles : PANEL_STYLES);
        style.hints(pendingHints != null ? pendingHints : PANEL_HINTS);
        color = new ColorSetting("color", "Цвет", ColorSetting.THEME);
        background = new BoolSetting("background", "Фон", true);
        shadow = new BoolSetting("shadow", "Тень", true);
        animation = new BoolSetting("animation", "Анимация", true);
        prependSettings(List.of(style, color, background, shadow, animation));
    }

    private String[] pendingStyles, pendingHints;

    /** Свои варианты стиля (2–4). Вызывать в конструкторе подкласса. */
    protected final void styles(String... names) {
        pendingStyles = names;
        if (style != null) style.options(names);
    }

    /** Подписи «что меняет» для своих стилей (в том же порядке). */
    protected final void styleHints(String... hints) {
        pendingHints = hints;
        if (style != null) style.hints(hints);
    }

    @Override
    public List<Setting<?>> settings() {
        ensureCommon();
        return super.settings();
    }

    /** Индекс выбранного стиля. */
    public final int style() {
        ensureCommon();
        return style != null ? style.index() : 0;
    }

    /** Цвет акцента модуля (или цвет темы). */
    public final int accent() {
        ensureCommon();
        return color != null ? color.argb() : Theme.ACCENT;
    }

    public final boolean background() {
        ensureCommon();
        return background == null || background.on();
    }

    public final boolean shadow() {
        ensureCommon();
        return shadow == null || shadow.on();
    }

    public final boolean animate() {
        ensureCommon();
        return animation == null || animation.on();
    }

    /** Шаг анимации с учётом тумблера «Анимация» (выкл — сразу к цели). */
    protected final void step(Anim a, float dt) {
        if (animate()) a.update(dt); else a.snap(a.target);
    }

    /**
     * @param vg NanoVG-контекст (кадр уже начат, GL-стейт сохранён)
     * @param w  ширина экрана в HUD-координатах
     * @param h  высота экрана в HUD-координатах
     * @param dt секунды с прошлого кадра — для анимаций
     */
    public abstract void renderHud(long vg, float w, float h, float dt);

    /** Слой: эффекты в мире рисуются под HUD-панелями. */
    public int layer() {
        return LAYER_HUD;
    }

    /** Двигается ли модуль Редактором HUD. По умолчанию — все модули слоя HUD. */
    public boolean layoutable() {
        return layer() == LAYER_HUD;
    }

    /** Позиция по умолчанию: левый верхний угол, доля экрана 0..1. */
    public float defaultX() {
        return 0.01f;
    }

    public float defaultY() {
        return 0.01f;
    }

    /** Сообщить размер содержимого (локальные HUD-единицы при scale 1). Вызывать в renderHud. */
    protected final void size(float w, float h) {
        boundsW = Math.max(8f, w);
        boundsH = Math.max(8f, h);
    }

    public final float boundsW() {
        return boundsW;
    }

    public final float boundsH() {
        return boundsH;
    }

    public static boolean isEditing() {
        return editing;
    }

    public static void setEditing(boolean value) {
        editing = value;
    }
}
