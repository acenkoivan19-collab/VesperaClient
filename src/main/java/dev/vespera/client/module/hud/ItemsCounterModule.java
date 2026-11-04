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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Счётчик важных предметов во всём инвентаре (включая вторую руку):
 * тотемы, золотые яблоки (обычные + зачарованные), эндер-жемчуг, стрелы.
 * Нулевые — тусклые или скрыты.
 */
public final class ItemsCounterModule extends HudModule {

    private static final Item[] ITEMS = {Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
            Items.ENDER_PEARL, Items.ARROW};

    private final ChoiceSetting look = setting(new ChoiceSetting("look", "Вид", 0, "Ряд", "Столбик")
            .hints("Иконки с числами в одну строку", "Иконка и число друг под другом"));
    private final BoolSetting hideZero = setting(new BoolSetting("hideZero", "Скрывать нулевые", false));

    public ItemsCounterModule() {
        super("Items Counter", "Тотемы, яблоки, перлы, стрелы", Category.COMBAT);
        about("Считает тотемы, золотые яблоки, эндер-жемчуг и стрелы во всём инвентаре, "
                + "чтобы не открывать его в бою.");
    }

    @Override
    public float defaultX() {
        return 0.01f;
    }

    @Override
    public float defaultY() {
        return 0.80f;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int[] counts = new int[ITEMS.length];
        if (mc.player != null) {
            PlayerInventory inv = mc.player.getInventory();
            for (int i = 0; i < inv.size(); i++) count(inv.getStack(i), counts);
            count(mc.player.getOffHandStack(), counts);
        }
        List<Integer> show = new ArrayList<>();
        for (int i = 0; i < ITEMS.length; i++) if (!hideZero.on() || counts[i] > 0 || isEditing()) show.add(i);

        boolean row = look.index() == 0;
        int n = Math.max(1, show.size());
        float cellW = row ? 44f : 70f, cellH = row ? 28f : 24f;
        float bw = row ? n * cellW + 8f : cellW + 12f, bh = row ? cellH + 8f : n * cellH + 8f;
        size(bw, bh);
        if (show.isEmpty()) return;
        HudStyle.panel(vg, this, 0, 0, bw, bh);
        for (int k = 0; k < show.size(); k++) {
            int i = show.get(k);
            float x = row ? 4f + k * cellW : 6f, y = row ? 4f : 4f + k * cellH;
            boolean zero = counts[i] == 0;
            int img = ItemIcons.item(ITEMS[i]);
            if (img > 0) ItemIcons.draw(vg, img, x + 4f, y + (cellH - 16f) / 2f, 16f, zero ? 0.35f : 1f);
            Draw.textTabular(vg, String.valueOf(counts[i]), x + 24f, y + cellH / 2f, Fonts.SEMIBOLD, 12f,
                    Draw.LEFT_MID, zero ? Theme.TEXT_MUTED : Theme.TEXT, 1f);
        }
    }

    private static void count(ItemStack s, int[] counts) {
        if (s.isEmpty()) return;
        for (int i = 0; i < ITEMS.length; i++) if (s.isOf(ITEMS[i])) counts[i] += s.getCount();
    }
}
