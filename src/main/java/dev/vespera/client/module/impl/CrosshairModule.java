package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.ui.Anim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Свой прицел (NanoVG). Ванильный скрывает InGameHudMixin, пока модуль включён.
 *
 * Настройки (ПКМ по карточке): форма (крест / точка / круг / T), цвет, длина, зазор,
 * толщина, прозрачность, точка в центре, обводка, «раскрытие» при ударе,
 * подсветка на цели + её цвет. В ПКМ-панели есть живое превью.
 */
public final class CrosshairModule extends HudModule {

    private final ChoiceSetting shape = setting(new ChoiceSetting("shape", "Форма", 0, "Крест", "Точка", "Круг", "T"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет", 0xFFFFFFFF));
    private final NumberSetting length = setting(new NumberSetting("length", "Длина", 5f, 1f, 15f, 0.5f));
    private final NumberSetting gap = setting(new NumberSetting("gap", "Зазор", 3f, 0f, 12f, 0.5f));
    private final NumberSetting thickness = setting(new NumberSetting("thickness", "Толщина", 1.6f, 0.5f, 5f, 0.1f));
    private final NumberSetting opacity = setting(new NumberSetting("opacity", "Прозрачность", 1f, 0.1f, 1f, 0.05f));
    private final BoolSetting dot = setting(new BoolSetting("dot", "Точка в центре", true));
    private final BoolSetting outline = setting(new BoolSetting("outline", "Обводка", true));
    private final BoolSetting dynamic = setting(new BoolSetting("dynamic", "Раскрытие при ударе", true));
    private final BoolSetting targetHighlight = setting(new BoolSetting("target", "Подсветка на цели", true));
    private final ColorSetting targetColor = setting(new ColorSetting("targetColor", "Цвет на цели", ColorSetting.THEME));

    private final Anim spread = new Anim(0f, 18f);
    private final Anim onTarget = new Anim(0f, 14f);
    private boolean wasAttacking;

    public CrosshairModule() {
        super("Crosshair", "[Дубль TerraHUD: Crosshair] Свой прицел, ванильный скрыт", Category.COMBAT);
        about("Заменяет ванильный прицел своим: форма, цвет, размер, зазор и подсветка на цели. Превью сверху показывает результат.");
    }

    @Override
    public int layer() {
        return LAYER_OVERLAY;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null || !mc.options.getPerspective().isFirstPerson()) return;

        boolean attacking = mc.options.attackKey.isPressed();
        if (dynamic.on() && attacking && !wasAttacking) spread.value = 1f; // «толчок» при клике
        wasAttacking = attacking;
        spread.target = 0f;
        spread.update(dt);
        onTarget.target = targetHighlight.on() && mc.targetedEntity instanceof LivingEntity ? 1f : 0f;
        onTarget.update(dt);

        draw(vg, w / 2f, h / 2f, dynamic.on() ? spread.value : 0f, onTarget.value);
    }

    @Override
    public boolean hasPreview() {
        return true;
    }

    /** Превью в ПКМ-панели: обычный прицел и (если включено) он же на цели. */
    @Override
    public void renderPreview(long vg, float x, float y, float w, float h) {
        float t = (System.currentTimeMillis() % 2400) / 2400f;
        float pulse = dynamic.on() && t < 0.25f ? 1f - t / 0.25f : 0f;
        if (targetHighlight.on()) {
            draw(vg, x + w * 0.3f, y + h / 2f, pulse, 0f);
            draw(vg, x + w * 0.7f, y + h / 2f, pulse, 1f);
        } else {
            draw(vg, x + w / 2f, y + h / 2f, pulse, 0f);
        }
    }

    /** Нарисовать прицел с центром (cx, cy). spread и hit — 0..1. */
    private void draw(long vg, float cx, float cy, float spreadT, float hit) {
        int col = Draw.lerpColor(color.argb(), targetColor.argb(), hit);
        float a = opacity.get();
        float g = gap.get() + 4f * spreadT, len = length.get(), th = thickness.get();
        int sh = shape.index();

        // два прохода: тёмная обводка, затем сам прицел
        for (int pass = outline.on() ? 0 : 1; pass < 2; pass++) {
            int c = pass == 0 ? 0xFF000000 : col;
            float pa = pass == 0 ? 0.55f * a : a, o = pass == 0 ? 0.9f : 0f;
            switch (sh) {
                case 1 -> { /* только точка — ниже */ }
                case 2 -> { // круг
                    nvgBeginPath(vg);
                    nvgCircle(vg, cx, cy, g + len * 0.5f);
                    nvgStrokeWidth(vg, th + o * 2f);
                    nvgStrokeColor(vg, Draw.c1(c, pa));
                    nvgStroke(vg);
                }
                default -> { // крест (0) или T (3 — без верхнего штриха)
                    if (sh != 3) bar(vg, cx - th / 2f - o, cy - g - len - o, th + 2 * o, len + 2 * o, c, pa);
                    bar(vg, cx - th / 2f - o, cy + g - o, th + 2 * o, len + 2 * o, c, pa);
                    bar(vg, cx - g - len - o, cy - th / 2f - o, len + 2 * o, th + 2 * o, c, pa);
                    bar(vg, cx + g - o, cy - th / 2f - o, len + 2 * o, th + 2 * o, c, pa);
                }
            }
            if (dot.on() || sh == 1) {
                float r = (sh == 1 ? Math.max(1.5f, th) : Math.max(0.8f, th * 0.7f)) + o;
                Draw.circle(vg, cx, cy, r, c, pa);
            }
        }
        if (hit > 0.01f) Draw.glow(vg, cx - 2f, cy - 2f, 4f, 4f, 2f, 8f, targetColor.argb(), 0.45f * hit * a);
    }

    private static void bar(long vg, float x, float y, float w, float h, int c, float a) {
        Draw.rect(vg, x, y, w, h, Math.min(w, h) / 2f, c, a);
    }
}
