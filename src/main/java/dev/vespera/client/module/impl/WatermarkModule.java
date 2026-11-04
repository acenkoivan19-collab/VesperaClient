package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.render.Brand;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.HudStyle;
import dev.vespera.client.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;

import java.util.ArrayList;
import java.util.List;

/** Водяной знак клиента: логотип + имя, по желанию FPS, пинг и сервер. */
public final class WatermarkModule extends HudModule {

    private final ChoiceSetting content = setting(new ChoiceSetting("content", "Содержимое", 1, "Логотип", "С FPS", "Полный")
            .hints("Только логотип и «Vespera Client»", "Логотип, имя и FPS", "Логотип, имя, FPS, пинг и сервер"));

    public WatermarkModule() {
        super("Watermark", "[Дубль TerraHUD: Watermark] Логотип клиента и FPS", Category.INTERFACE);
        about("Плашка с логотипом Vespera Client. Можно добавить FPS, пинг и адрес сервера.");
    }

    @Override
    public float defaultX() {
        return 0.006f;
    }

    @Override
    public float defaultY() {
        return 0.01f;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<String> parts = new ArrayList<>();
        int mode = content.index();
        if (mode >= 1) parts.add(mc.getCurrentFps() + " fps");
        if (mode == 2) {
            if (mc.isInSingleplayer() || mc.getNetworkHandler() == null || mc.player == null) {
                parts.add("SP");
            } else {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                parts.add((e != null ? e.getLatency() : 0) + " ms");
                ServerInfo info = mc.getCurrentServerEntry();
                if (info != null) parts.add(info.address);
            }
        }

        float bh = 28f, logo = 20f;
        float nameW = Draw.textWidth(vg, Brand.NAME, Fonts.SEMIBOLD, 12.5f);
        float bw = 6f + logo + 8f + nameW + 10f;
        for (String p : parts) bw += 11f + Draw.tabularWidth(vg, p, Fonts.MEDIUM, 11.5f) + 10f;
        size(bw, bh);
        HudStyle.panel(vg, this, 0, 0, bw, bh);

        float cy = bh / 2f;
        Brand.logo(vg, 6f, cy - logo / 2f, logo, 6f, 1f);
        float tx = 6f + logo + 8f;
        Draw.text(vg, Brand.NAME, tx, cy, Fonts.SEMIBOLD, 12.5f, Draw.LEFT_MID, Theme.TEXT, 1f);
        tx += nameW + 10f;
        for (int i = 0; i < parts.size(); i++) {
            Draw.rect(vg, tx, cy - 7f, 1f, 14f, 0.5f, 0xFFFFFFFF, 0.12f);
            tx += 11f;
            String p = parts.get(i);
            Draw.textTabular(vg, p, tx, cy, Fonts.MEDIUM, 11.5f, Draw.LEFT_MID, i == 0 ? accent() : Theme.TEXT_DIM, 1f);
            tx += Draw.tabularWidth(vg, p, Fonts.MEDIUM, 11.5f) + 10f;
        }
    }
}
