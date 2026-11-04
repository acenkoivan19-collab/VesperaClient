package dev.vespera.client.module.hud;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.HudStyle;
import dev.vespera.client.render.ItemIcons;
import dev.vespera.client.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Активные эффекты: иконка зелья в круговом прогрессе + таймер + уровень.
 * Полный круг = самая большая длительность, которую мы видели у этого эффекта
 * (сервер не сообщает исходную длительность). Последние 10 сек — круг мигает.
 * Стиль «Список» — строки с названием, «Иконки» — компактный ряд.
 */
public final class EffectsModule extends HudModule {

    private static final float ROW = 34f, ICON = 30f;
    private final BoolSetting names = setting(new BoolSetting("names", "Названия", true));
    private final Map<StatusEffect, Integer> maxSeen = new HashMap<>();

    public EffectsModule() {
        super("Effects", "Иконки зелий с таймерами", Category.INTERFACE);
        about("Активные эффекты зелий: иконка, оставшееся время и кольцо прогресса, которое мигает в последние 10 секунд.");
        styles("Список", "Иконки");
    }

    @Override
    public float defaultX() {
        return 0.82f;
    }

    @Override
    public float defaultY() {
        return 0.08f;
    }

    @Override
    protected void onDisable() {
        maxSeen.clear();
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<StatusEffectInstance> list = new ArrayList<>();
        if (mc.player != null) list.addAll(mc.player.getStatusEffects());
        if (list.isEmpty() && isEditing()) list.add(new StatusEffectInstance(StatusEffects.SPEED, 1200, 1)); // превью
        list.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));

        boolean row = style() == 0;
        if (row) {
            float bw = names.on() ? 170f : 96f;
            float bh = Math.max(1, list.size()) * (ROW + 4f) - 4f + 8f;
            size(bw, bh);
            if (list.isEmpty()) return;
            HudStyle.panel(vg, this, 0, 0, bw, bh);
            float y = 4f;
            for (StatusEffectInstance e : list) {
                drawEffect(vg, e, 8f, y + (ROW - ICON) / 2f);
                float tx = 8f + ICON + 8f;
                if (names.on()) {
                    String nm = e.getEffectType().value().getName().getString() + level(e);
                    Draw.text(vg, Draw.ellipsize(vg, nm, Fonts.SEMIBOLD, 11.5f, bw - tx - 8f), tx, y + 11f,
                            Fonts.SEMIBOLD, 11.5f, Draw.LEFT_MID, Theme.TEXT, 1f);
                    Draw.textTabular(vg, time(e), tx, y + 24f, Fonts.MEDIUM, 10.5f, Draw.LEFT_MID, Theme.TEXT_DIM, 1f);
                } else {
                    Draw.textTabular(vg, time(e), tx, y + ROW / 2f, Fonts.MEDIUM, 11.5f, Draw.LEFT_MID, Theme.TEXT, 1f);
                }
                y += ROW + 4f;
            }
        } else {
            int n = Math.max(1, list.size());
            float bw = n * (ICON + 8f) + 8f, bh = ICON + 30f;
            size(bw, bh);
            if (list.isEmpty()) return;
            HudStyle.panel(vg, this, 0, 0, bw, bh);
            float x = 8f;
            for (StatusEffectInstance e : list) {
                drawEffect(vg, e, x, 6f);
                Draw.textTabular(vg, time(e), x + ICON / 2f, ICON + 18f, Fonts.MEDIUM, 10f, Draw.CENTER_MID, Theme.TEXT, 1f);
                x += ICON + 8f;
            }
        }
    }

    private void drawEffect(long vg, StatusEffectInstance e, float x, float y) {
        RegistryEntry<StatusEffect> type = e.getEffectType();
        int dur = e.getDuration();
        int max = maxSeen.merge(type.value(), dur, Math::max);
        float p = e.isInfinite() ? 1f : max > 0 ? dur / (float) max : 1f;
        boolean blink = !e.isInfinite() && dur < 200 && (System.currentTimeMillis() / 250) % 2 == 0;
        int color = type.value().getColor() | 0xFF000000;
        float cx = x + ICON / 2f, cy = y + ICON / 2f;
        Draw.circle(vg, cx, cy, ICON / 2f - 1f, 0xFF000000, 0.35f);
        Draw.progressRing(vg, cx, cy, ICON / 2f - 1.5f, 2.5f, p, 0xFFFFFFFF, 0.08f, color, blink ? 0.4f : 1f);
        int img = ItemIcons.effect(type.value());
        if (img > 0) ItemIcons.draw(vg, img, cx - 9f, cy - 9f, 18f, 1f);
        else Draw.circle(vg, cx, cy, 5f, color, 1f);
    }

    private static String level(StatusEffectInstance e) {
        int a = e.getAmplifier();
        String[] roman = {"", " II", " III", " IV", " V"};
        return a < roman.length ? roman[a] : " " + (a + 1);
    }

    private static String time(StatusEffectInstance e) {
        if (e.isInfinite()) return "∞";
        int s = e.getDuration() / 20;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }
}
