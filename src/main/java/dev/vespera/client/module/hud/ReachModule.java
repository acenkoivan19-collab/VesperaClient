package dev.vespera.client.module.hud;

import dev.vespera.client.render.Icons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;

import java.util.List;
import java.util.Locale;

/** Дистанция от глаз до точки под прицелом (сущность или блок). */
public final class ReachModule extends InfoHudModule {

    public ReachModule() {
        super("Reach", "Дистанция до точки под прицелом", Icons.Glyph.CROSSHAIR);
        about("Расстояние от глаз до блока или сущности под прицелом.");
    }

    @Override
    public float defaultY() {
        return 0.57f;
    }

    @Override
    protected void collect(List<Row> out) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HitResult hit = mc.crosshairTarget;
        if (mc.player == null || hit == null || hit.getType() == HitResult.Type.MISS) {
            out.add(new Row("ДИСТ.", "—"));
            return;
        }
        double d = mc.player.getEyePos().distanceTo(hit.getPos());
        int c = hit.getType() == HitResult.Type.ENTITY ? accent() : 0;
        out.add(new Row(hit.getType() == HitResult.Type.ENTITY ? "ЦЕЛЬ" : "БЛОК", String.format(Locale.ROOT, "%.2f m", d), c));
    }
}
