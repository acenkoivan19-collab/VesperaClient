package dev.vespera.client.module.hud;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.HudStyle;
import dev.vespera.client.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Компас: лента со сторонами света (С, СВ, В…) и делениями, едет вместе с поворотом.
 * Стиль «Лента» — широкая полоса с затуханием краёв, «Компакт» — буква + градусы.
 */
public final class DirectionModule extends HudModule {

    private static final String[] DIRS = {"Ю", "ЮЗ", "З", "СЗ", "С", "СВ", "В", "ЮВ"}; // yaw 0 = юг
    private static final float STRIP_W = 220f, STRIP_H = 34f, PX_PER_DEG = 1.6f;

    public DirectionModule() {
        super("Direction", "Сторона света и градусы", Category.INTERFACE);
        about("Компас: лента со сторонами света, которая едет при повороте, или компактная буква с градусами.");
        styles("Лента", "Компакт");
    }

    @Override
    public float defaultX() {
        return 0.5f - STRIP_W / 2f / 960f;
    }

    @Override
    public float defaultY() {
        return 0.01f;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { size(STRIP_W, STRIP_H); return; }
        float yaw = MathHelper.wrapDegrees(mc.player.getYaw());
        float deg = (yaw + 180f) % 360f; // 0 = север
        int acc = accent();
        if (style() == 1) {
            String dir = DIRS[Math.floorMod(Math.round(yaw / 45f), 8)];
            String txt = String.format("%s  %d°", dir, Math.round(deg) % 360);
            float tw = Draw.tabularWidth(vg, txt, Fonts.SEMIBOLD, 13f) + 24f;
            size(tw, 28f);
            HudStyle.panel(vg, this, 0, 0, tw, 28f);
            Draw.textTabular(vg, txt, tw / 2f, 14.5f, Fonts.SEMIBOLD, 13f, Draw.CENTER_MID, Theme.TEXT, 1f);
            return;
        }
        size(STRIP_W, STRIP_H);
        HudStyle.panel(vg, this, 0, 0, STRIP_W, STRIP_H);

        nvgSave(vg);
        nvgIntersectScissor(vg, 6f, 0, STRIP_W - 12f, STRIP_H);
        float cx = STRIP_W / 2f;
        for (int d = -75; d <= 75; d += 5) {
            float a = yaw + d;
            int mark = Math.round(a / 5f) * 5;
            float x = cx + (mark - yaw) * PX_PER_DEG;
            float fade = 1f - Math.min(1f, Math.abs(x - cx) / (STRIP_W / 2f - 8f));
            int m = Math.floorMod(mark, 360);
            if (m % 45 == 0) {
                String lbl = DIRS[Math.floorMod(Math.round(mark / 45f), 8)];
                boolean main = lbl.length() == 1;
                Draw.text(vg, lbl, x, 14f, Fonts.SEMIBOLD, main ? 13f : 10.5f, Draw.CENTER_MID,
                        "С".equals(lbl) ? acc : Theme.TEXT, fade);
            } else {
                Draw.rect(vg, x - 0.5f, m % 15 == 0 ? 9f : 11f, 1f, m % 15 == 0 ? 8f : 5f, 0.5f, Theme.TEXT_DIM, fade);
            }
        }
        nvgRestore(vg);

        // центральная метка + градусы
        Draw.rect(vg, cx - 1f, 3f, 2f, 6f, 1f, acc);
        Draw.textTabular(vg, String.valueOf(Math.round(deg) % 360), cx, STRIP_H - 7f, Fonts.MEDIUM, 9.5f,
                Draw.CENTER_MID, Theme.TEXT_DIM, 1f);
    }
}
