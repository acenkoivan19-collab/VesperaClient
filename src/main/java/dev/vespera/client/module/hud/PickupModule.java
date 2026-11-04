package dev.vespera.client.module.hud;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.HudStyle;
import dev.vespera.client.render.ItemIcons;
import dev.vespera.client.ui.Anim;
import dev.vespera.client.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Лог подбора: свой трекер изменения инвентаря. Раз в тик считаем количество каждого
 * предмета; прирост → строка «+N предмет» (одна строка на предмет, суммируется), TTL 6 сек.
 * Пока открыт экран (сундук, инвентарь) прирост не считается — это перекладывание.
 */
public final class PickupModule extends HudModule {

    private static final long TTL = 6000;
    private static final float W = 190f, ROW = 24f;

    private static final class Entry {
        final Item item;
        int count;
        long at;
        final Anim slide = new Anim(0f, 14f);

        Entry(Item item) { this.item = item; }
    }

    private final Map<Item, Integer> last = new HashMap<>();
    private final LinkedHashMap<Item, Entry> entries = new LinkedHashMap<>();
    private ClientWorld world;
    private boolean primed;

    public PickupModule() {
        super("Pickup Log", "Что подобрал за последние секунды", Category.INTERFACE);
        about("Список того, что ты только что подобрал, с количеством; каждая строка исчезает через 6 секунд.");
    }

    @Override
    public float defaultX() {
        return 0.80f;
    }

    @Override
    public float defaultY() {
        return 0.35f;
    }

    @Override
    protected void onDisable() {
        last.clear();
        entries.clear();
        primed = false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world != world) {
            world = mc.world;
            last.clear();
            primed = false;
            return;
        }
        Map<Item, Integer> now = new HashMap<>();
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty()) now.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        if (primed && mc.currentScreen == null) {
            long t = System.currentTimeMillis();
            for (Map.Entry<Item, Integer> e : now.entrySet()) {
                int diff = e.getValue() - last.getOrDefault(e.getKey(), 0);
                if (diff <= 0) continue;
                Entry en = entries.remove(e.getKey()); // переносим в конец (свежие снизу)
                if (en == null) en = new Entry(e.getKey());
                en.count += diff;
                en.at = t;
                entries.put(e.getKey(), en);
            }
        }
        last.clear();
        last.putAll(now);
        primed = true;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        long now = System.currentTimeMillis();
        for (Iterator<Entry> it = entries.values().iterator(); it.hasNext(); ) {
            Entry e = it.next();
            e.slide.target = now - e.at < TTL ? 1f : 0f;
            step(e.slide, dt);
            if (e.slide.target == 0f && e.slide.value < 0.01f) it.remove();
        }
        List<Entry> show = new ArrayList<>(entries.values());
        if (show.size() > 6) show = show.subList(show.size() - 6, show.size());
        if (show.isEmpty() && isEditing()) { // превью
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && !mc.player.getMainHandStack().isEmpty()) {
                Entry p = new Entry(mc.player.getMainHandStack().getItem());
                p.count = 1;
                p.slide.snap(1f);
                show = List.of(p);
            }
        }
        float bh = Math.max(1, show.size()) * (ROW + 4f) + 4f;
        size(W, bh);
        float y = 4f;
        for (Entry e : show) {
            float s = Anim.easeOutCubic(e.slide.value);
            float x = (1f - s) * 24f;
            HudStyle.panel(vg, this, x, y, W - x, ROW, 7f);
            int img = ItemIcons.item(e.item);
            if (img > 0) ItemIcons.draw(vg, img, x + 8f, y + 4f, 16f, s);
            String name = new ItemStack(e.item).getName().getString();
            Draw.textTabular(vg, "+" + e.count, x + 30f, y + ROW / 2f, Fonts.SEMIBOLD, 11.5f, Draw.LEFT_MID, accent(), s);
            float nx = x + 30f + Draw.tabularWidth(vg, "+" + e.count, Fonts.SEMIBOLD, 11.5f) + 6f;
            Draw.text(vg, Draw.ellipsize(vg, name, Fonts.MEDIUM, 11.5f, W - nx - 8f), nx, y + ROW / 2f,
                    Fonts.MEDIUM, 11.5f, Draw.LEFT_MID, Theme.TEXT, s);
            y += (ROW + 4f) * Math.max(0.2f, s);
        }
    }
}
