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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Броня: 4 слота (+ предмет в руке) — иконка, полоса прочности (зелёный → красный)
 * и число. Стиль «Колонка» — вертикально, «Ряд» — горизонтально.
 */
public final class ArmorModule extends HudModule {

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final float CELL = 26f;

    private final BoolSetting hand = setting(new BoolSetting("hand", "Предмет в руке", true));
    private final BoolSetting percent = setting(new BoolSetting("percent", "В процентах", false));

    public ArmorModule() {
        super("Armor", "Слоты брони и прочность", Category.INTERFACE);
        about("Надетая броня и предмет в руке с полосами прочности и числом оставшейся прочности.");
        styles("Колонка", "Ряд");
    }

    @Override
    public float defaultX() {
        return 0.01f;
    }

    @Override
    public float defaultY() {
        return 0.50f;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ItemStack[] stacks = new ItemStack[hand.on() ? 5 : 4];
        for (int i = 0; i < 4; i++) stacks[i] = mc.player != null ? mc.player.getEquippedStack(SLOTS[i]) : ItemStack.EMPTY;
        if (hand.on()) stacks[4] = mc.player != null ? mc.player.getMainHandStack() : ItemStack.EMPTY;
        if (isEditing() && stacks[1].isEmpty()) stacks[1] = new ItemStack(Items.DIAMOND_CHESTPLATE); // превью

        boolean column = style() == 0;
        float cellW = column ? 92f : 34f, cellH = column ? CELL : 44f;
        int n = stacks.length;
        float bw = column ? cellW + 12f : n * cellW + 12f, bh = column ? n * cellH + 12f : cellH + 12f;
        size(bw, bh);
        HudStyle.panel(vg, this, 0, 0, bw, bh);

        for (int i = 0; i < n; i++) {
            float x = column ? 6f : 6f + i * cellW, y = column ? 6f + i * cellH : 6f;
            ItemStack s = stacks[i];
            int img = s.isEmpty() ? 0 : ItemIcons.item(s.getItem());
            float ix = column ? x + 2f : x + (cellW - 18f) / 2f, iy = column ? y + (cellH - 18f) / 2f : y + 2f;
            if (s.isEmpty()) Draw.rect(vg, ix + 2f, iy + 2f, 14f, 14f, 4f, 0xFFFFFFFF, 0.05f);
            else if (img > 0) ItemIcons.draw(vg, img, ix, iy, 18f, 1f);
            else Draw.rect(vg, ix + 2f, iy + 2f, 14f, 14f, 4f, accent(), 0.6f);
            if (s.isEmpty() || !s.isDamageable()) {
                if (!s.isEmpty() && s.getCount() > 1) {
                    Draw.textTabular(vg, String.valueOf(s.getCount()), column ? x + cellW - 4f : x + cellW / 2f,
                            column ? y + cellH / 2f : y + 34f, Fonts.MEDIUM, 10.5f,
                            column ? Draw.RIGHT_MID : Draw.CENTER_MID, Theme.TEXT, 1f);
                }
                continue;
            }
            int max = s.getMaxDamage(), left = max - s.getDamage();
            float t = max > 0 ? left / (float) max : 1f;
            int col = Draw.lerpColor(0xFFFF5C7A, Draw.lerpColor(0xFFFFD166, 0xFF5EE6CF, Math.max(0f, t * 2f - 1f)), Math.min(1f, t * 2f));
            String num = percent.on() ? Math.round(t * 100) + "%" : String.valueOf(left);
            if (column) {
                float bx = x + 26f, bwid = cellW - 26f - 4f;
                Draw.textTabular(vg, num, x + cellW - 4f, y + 9f, Fonts.MEDIUM, 10.5f, Draw.RIGHT_MID, Theme.TEXT, 1f);
                Draw.rect(vg, bx, y + 17f, bwid, 3f, 1.5f, 0xFF000000, 0.45f);
                Draw.rect(vg, bx, y + 17f, Math.max(3f, bwid * t), 3f, 1.5f, col);
            } else {
                Draw.rect(vg, x + 4f, y + 23f, cellW - 8f, 3f, 1.5f, 0xFF000000, 0.45f);
                Draw.rect(vg, x + 4f, y + 23f, Math.max(3f, (cellW - 8f) * t), 3f, 1.5f, col);
                Draw.textTabular(vg, num, x + cellW / 2f, y + 34f, Fonts.MEDIUM, 9.5f, Draw.CENTER_MID, Theme.TEXT, 1f);
            }
        }
    }
}
