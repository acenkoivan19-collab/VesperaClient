package dev.vespera.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * На ком рисовать косметику (крылья, нимб, шляпа) — как в TerraHUD:
 * на себе в виде от 3-го лица + на других игроках рядом.
 * Чужих игроков берём ТОЛЬКО в прямой видимости (EntityScan), чтобы косметика
 * сквозь стены не выдавала, где стоит игрок. Свою — всегда.
 */
public final class CosmeticTargets {

    private CosmeticTargets() {}

    public static List<LivingEntity> get(MinecraftClient mc) {
        return get(mc, true);
    }

    /** @param others рисовать ли и на других игроках (в прямой видимости) */
    public static List<LivingEntity> get(MinecraftClient mc, boolean others) {
        List<LivingEntity> out = new ArrayList<>();
        if (mc.player == null) return out;
        if (!mc.options.getPerspective().isFirstPerson()) out.add(mc.player);
        if (!others) return out;
        for (LivingEntity e : EntityScan.living()) {
            if (e instanceof PlayerEntity && e != mc.player && !e.isRemoved() && EntityScan.isVisible(e)) out.add(e);
        }
        return out;
    }
}
