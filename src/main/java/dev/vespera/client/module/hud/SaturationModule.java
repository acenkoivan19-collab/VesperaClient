package dev.vespera.client.module.hud;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.HudStyle;
import dev.vespera.client.render.Icons;
import dev.vespera.client.ui.Anim;
import dev.vespera.client.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.HungerManager;

import java.util.Locale;

/** Голод и насыщение: две плавные полосы с цифрами. */
public final class SaturationModule extends HudModule {

    private static final float W = 150f, H = 42f;
    private final Anim food = new Anim(1f, 10f), sat = new Anim(0f, 10f);

    public SaturationModule() {
        super("Saturation", "Голод и насыщение цифрой", Category.INTERFACE);
        about("Голод и скрытое насыщение двумя полосами с точными цифрами.");
    }

    @Override
    public float defaultY() {
        return 0.62f;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        size(W, H);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        HungerManager hm = mc.player.getHungerManager();
        food.target = hm.getFoodLevel() / 20f;
        sat.target = hm.getSaturationLevel() / 20f;
        step(food, dt);
        step(sat, dt);

        HudStyle.panel(vg, this, 0, 0, W, H);
        float pad = HudStyle.padLeft(this);
        Icons.glyph(vg, Icons.Glyph.FOOD, pad + 7f, H / 2f, 13f, 0xFFFFB86B, 1f);
        float bx = pad + 22f, bw = W - bx - 10f;
        bar(vg, bx, 9f, bw, food.value, 0xFFFFB86B, String.valueOf(hm.getFoodLevel()));
        bar(vg, bx, 25f, bw, sat.value, accent(), String.format(Locale.ROOT, "%.1f", hm.getSaturationLevel()));
    }

    private static void bar(long vg, float x, float y, float w, float t, int color, String value) {
        float vw = 26f, bw = w - vw;
        Draw.rect(vg, x, y + 2f, bw, 5f, 2.5f, 0xFF000000, 0.45f);
        Draw.linear(vg, x, y + 2f, Math.max(5f, bw * Anim.clamp01(t)), 5f, 2.5f, x, y, x + bw, y,
                color, 0.7f, color, 1f);
        Draw.textTabular(vg, value, x + w, y + 4.5f, Fonts.MEDIUM, 10.5f, Draw.RIGHT_MID, Theme.TEXT, 1f);
    }
}
