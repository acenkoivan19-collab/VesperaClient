package dev.vespera.client.module.hud;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.HudStyle;
import dev.vespera.client.render.ItemIcons;
import dev.vespera.client.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Кулдауны эндер-жемчуга и хоруса (ItemCooldownManager): иконка + круговой прогресс
 * + оставшиеся секунды. Секунды считаются по скорости убывания прогресса —
 * поэтому верны и на серверах с изменённым кулдауном.
 */
public final class CooldownsModule extends HudModule {

    private static final Item[] TRACKED = {Items.ENDER_PEARL, Items.CHORUS_FRUIT, Items.WIND_CHARGE, Items.GOAT_HORN};
    private static final float SLOT = 40f, GAP = 6f;

    private final ItemStack[] stacks = new ItemStack[TRACKED.length];
    private final float[] startProgress = new float[TRACKED.length];
    private final long[] startAt = new long[TRACKED.length];

    public CooldownsModule() {
        super("Cooldowns", "Откат перла и хоруса", Category.COMBAT);
        about("Откат эндер-жемчуга, хоруса, заряда ветра и рога: круговой прогресс и оставшиеся секунды.");
        for (int i = 0; i < TRACKED.length; i++) stacks[i] = new ItemStack(TRACKED[i]);
    }

    @Override
    public float defaultX() {
        return 0.42f;
    }

    @Override
    public float defaultY() {
        return 0.72f;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<Integer> active = new ArrayList<>();
        float[] prog = new float[TRACKED.length];
        if (mc.player != null) {
            ItemCooldownManager cm = mc.player.getItemCooldownManager();
            long now = System.currentTimeMillis();
            for (int i = 0; i < TRACKED.length; i++) {
                float p = cm.getCooldownProgress(stacks[i], 0f);
                prog[i] = p;
                if (p > 0f) {
                    if (startAt[i] == 0 || p > startProgress[i] + 0.01f) { startAt[i] = now; startProgress[i] = p; }
                    active.add(i);
                } else {
                    startAt[i] = 0;
                }
            }
        }
        if (active.isEmpty() && isEditing()) { active.add(0); prog[0] = 0.6f; } // превью
        int n = Math.max(1, active.size());
        float bw = n * SLOT + (n - 1) * GAP + 16f, bh = SLOT + 16f;
        size(bw, bh);
        if (active.isEmpty()) return;

        HudStyle.panel(vg, this, 0, 0, bw, bh);
        long now = System.currentTimeMillis();
        for (int k = 0; k < active.size(); k++) {
            int i = active.get(k);
            float x = 8f + k * (SLOT + GAP), y = 8f, cx = x + SLOT / 2f, cy = y + SLOT / 2f;
            Draw.progressRing(vg, cx, cy, SLOT / 2f - 2f, 3f, prog[i], 0xFF000000, 0.45f, accent(), 1f);
            int img = ItemIcons.item(TRACKED[i]);
            if (img > 0) ItemIcons.draw(vg, img, cx - 10f, cy - 10f, 20f, 0.55f);
            String secs = remaining(i, prog[i], now);
            Draw.textTabular(vg, secs, cx, cy + 0.5f, Fonts.SEMIBOLD, 11.5f, Draw.CENTER_MID, Theme.TEXT, 1f);
        }
    }

    /** Оставшееся время по скорости убывания прогресса. */
    private String remaining(int i, float p, long now) {
        long el = now - startAt[i];
        float spent = startProgress[i] - p;
        if (startAt[i] == 0 || el < 100 || spent <= 0.0001f) return "…";
        double total = el / spent;               // мс на весь прогресс 1.0
        double left = p * total / 1000.0;
        return left >= 10 ? String.valueOf((int) Math.ceil(left)) : String.format(Locale.ROOT, "%.1f", left);
    }
}
