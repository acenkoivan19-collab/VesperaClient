package dev.vespera.client.module.hud;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.HudStyle;
import dev.vespera.client.render.Icons;
import dev.vespera.client.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Основа текстовых HUD-панелей: [значок] ПОДПИСЬ   значение (табличные цифры).
 * Строк может быть несколько (координаты, FPS+Ping). Ширина подстраивается под текст,
 * но не «дёргается»: растёт сразу, а сужается только при заметной разнице.
 * Позиция/масштаб/прозрачность — общий WorldDraw.applyLayout (ModuleManager).
 */
public abstract class InfoHudModule extends HudModule {

    /** Одна строка панели. */
    public record Row(String label, String value, int valueColor) {
        public Row(String label, String value) {
            this(label, value, 0);
        }
    }

    protected static final float ROW_H = 18f, PAD_Y = 6f, FONT = 12f, LABEL_FONT = 10.5f;

    private final Icons.Glyph glyph;
    private final BoolSetting showIcon, showLabels;
    private final List<Row> rows = new ArrayList<>();
    private float width = 0f;

    protected InfoHudModule(String name, String description, Icons.Glyph glyph) {
        super(name, description, Category.INTERFACE);
        this.glyph = glyph;
        showIcon = setting(new BoolSetting("icon", "Значок", true));
        showLabels = setting(new BoolSetting("labels", "Подписи", true));
    }

    /** Заполнить строки для текущего кадра. */
    protected abstract void collect(List<Row> out);

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        rows.clear();
        collect(rows);
        if (rows.isEmpty()) {
            size(width > 0 ? width : 80f, ROW_H + PAD_Y * 2);
            return;
        }
        boolean icon = showIcon.on() && glyph != null, labels = showLabels.on();
        float pad = HudStyle.padLeft(this);
        float iconW = icon ? 22f : 0f;

        float labelW = 0f, valueW = 0f;
        for (Row r : rows) {
            if (labels && !r.label().isEmpty()) labelW = Math.max(labelW, Draw.textWidth(vg, r.label(), Fonts.SEMIBOLD, LABEL_FONT));
            valueW = Math.max(valueW, Draw.tabularWidth(vg, r.value(), Fonts.MEDIUM, FONT));
        }
        float target = pad + iconW + (labelW > 0 ? labelW + 10f : 0f) + valueW + 10f;
        if (target > width || width - target > 14f) width = target; // без «дрожания» ширины
        float bh = rows.size() * ROW_H + PAD_Y * 2;
        size(width, bh);

        HudStyle.panel(vg, this, 0, 0, width, bh);
        int acc = accent();
        if (icon) Icons.glyph(vg, glyph, pad + 7f, bh / 2f, 13f, acc, 1f);

        float lx = pad + iconW, vx = width - 10f;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float cy = PAD_Y + ROW_H * i + ROW_H / 2f;
            if (labels && !r.label().isEmpty()) {
                Draw.text(vg, r.label(), lx, cy + 0.5f, Fonts.SEMIBOLD, LABEL_FONT, Draw.LEFT_MID, Theme.TEXT_DIM, 1f);
            }
            int vc = r.valueColor() != 0 ? r.valueColor() : Theme.TEXT;
            Draw.textTabular(vg, r.value(), vx, cy, Fonts.MEDIUM, FONT, Draw.RIGHT_MID, vc, 1f);
        }
    }
}
