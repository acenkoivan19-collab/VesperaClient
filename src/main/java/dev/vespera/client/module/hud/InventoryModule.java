package dev.vespera.client.module.hud;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.HudStyle;
import dev.vespera.client.render.ItemIcons;
import dev.vespera.client.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/**
 * Инвентарь на экране: хотбар (слоты 0–8) и/или рюкзак (слоты 9–35, 3×9).
 * Иконки — ItemIcons (PNG из ресурсов игры), количество — табличными цифрами,
 * у повреждаемых предметов — тонкая полоса прочности. Выбранный слот хотбара подсвечен.
 */
public final class InventoryModule extends HudModule {

    private static final float CELL = 22f, GAP = 2f, PAD = 6f;

    private final ChoiceSetting show = setting(new ChoiceSetting("show", "Что показывать", 1, "Хотбар", "Рюкзак", "Всё")
            .hints("Только 9 слотов хотбара", "Рюкзак 3×9 — то, чего не видно на хотбаре",
                    "Рюкзак и под ним хотбар"));
    private final BoolSetting durability = setting(new BoolSetting("durability", "Полосы прочности", true));
    private final BoolSetting emptySlots = setting(new BoolSetting("emptySlots", "Пустые ячейки", true));

    public InventoryModule() {
        super("Inventory", "Хотбар и рюкзак на экране", Category.INTERFACE);
        about("Показывает содержимое инвентаря поверх игры — рюкзак, хотбар или всё сразу, "
                + "с количеством и прочностью предметов.");
    }

    @Override
    public float defaultX() {
        return 0.36f;
    }

    @Override
    public float defaultY() {
        return 0.70f;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        int mode = show.index();
        int rows = mode == 0 ? 1 : mode == 1 ? 3 : 4;
        float bw = PAD * 2 + 9 * CELL + 8 * GAP;
        float bh = PAD * 2 + rows * CELL + (rows - 1) * GAP + (mode == 2 ? 4f : 0f);
        size(bw, bh);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        PlayerInventory inv = mc.player.getInventory();
        HudStyle.panel(vg, this, 0, 0, bw, bh);

        int row = 0;
        if (mode >= 1) {
            for (int r = 0; r < 3; r++, row++) for (int c = 0; c < 9; c++) cell(vg, inv.getStack(9 + r * 9 + c), c, row, 0f, false);
        }
        if (mode != 1) {
            int sel = inv.getSelectedSlot();
            float extra = mode == 2 ? 4f : 0f;
            for (int c = 0; c < 9; c++) cell(vg, inv.getStack(c), c, row, extra, c == sel);
        }
    }

    private void cell(long vg, ItemStack s, int col, int row, float extraY, boolean selected) {
        float x = PAD + col * (CELL + GAP), y = PAD + row * (CELL + GAP) + extraY;
        if (emptySlots.on() || !s.isEmpty() || selected) {
            Draw.rect(vg, x, y, CELL, CELL, 5f, 0xFFFFFFFF, selected ? 0.10f : 0.035f);
            if (selected) Draw.stroke(vg, x + 0.5f, y + 0.5f, CELL - 1f, CELL - 1f, 5f, 1f, accent(), 0.9f);
        }
        if (s.isEmpty()) return;
        int img = ItemIcons.item(s.getItem());
        if (img > 0) ItemIcons.draw(vg, img, x + 3f, y + 3f, 16f, 1f);
        else Draw.rect(vg, x + 5f, y + 5f, 12f, 12f, 3f, accent(), 0.6f);
        if (durability.on() && s.isDamageable() && s.getDamage() > 0) {
            float t = 1f - s.getDamage() / (float) Math.max(1, s.getMaxDamage());
            int c = Draw.lerpColor(0xFFFF5C7A, 0xFF5EE6CF, t);
            Draw.rect(vg, x + 3f, y + CELL - 4f, (CELL - 6f) * t, 1.8f, 0.9f, c);
        }
        if (s.getCount() > 1) {
            Draw.textTabular(vg, String.valueOf(s.getCount()), x + CELL - 2f, y + CELL - 5f, Fonts.SEMIBOLD, 9.5f,
                    Draw.RIGHT_MID, Theme.TEXT, 1f);
        }
    }
}
