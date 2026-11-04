package dev.vespera.client.util;

import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.impl.ChinaHatModule;
import dev.vespera.client.module.impl.HaloModule;
import dev.vespera.client.module.impl.HitboxesModule;
import dev.vespera.client.module.impl.WingsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.math.Box;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Сущности рядом (порт TerraHudClient.tickHitrange: 24 блока, ≤12 живых, ≤12 кристаллов,
 * раз в 3 тика) + ПРЯМАЯ ВИДИМОСТЬ.
 *
 * Правило «не читы»: Hitboxes и косметика на ЧУЖИХ игроках рисуются
 * только для тех, кого игрок реально видит — рейкаст от глаз игрока до сущности
 * (LivingEntity#canSee). Сущность за блоком в {@link #visible} не попадает.
 * Скан работает, только если включён хоть один модуль-потребитель.
 */
public final class EntityScan {

    private static final double RADIUS = 24.0;
    private static final int MAX = 12;

    private static List<LivingEntity> living = List.of();
    private static List<Entity> crystals = List.of();
    private static final IntOpenHashSet VISIBLE = new IntOpenHashSet();
    private static int tick;

    private EntityScan() {}

    private static boolean needed() {
        return ModuleManager.get(HitboxesModule.class).isEnabled()
                || ModuleManager.get(WingsModule.class).isEnabled()
                || ModuleManager.get(HaloModule.class).isEnabled()
                || ModuleManager.get(ChinaHatModule.class).isEnabled();
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || !needed()) {
            if (!living.isEmpty() || !crystals.isEmpty()) clear();
            return;
        }
        if ((tick++ % 3) != 0) return;

        List<LivingEntity> l = new ArrayList<>();
        List<Entity> c = new ArrayList<>();
        VISIBLE.clear();
        Box area = new Box(mc.player.getX() - RADIUS, mc.player.getY() - 8, mc.player.getZ() - RADIUS,
                mc.player.getX() + RADIUS, mc.player.getY() + 8, mc.player.getZ() + RADIUS);
        for (Entity e : mc.world.getOtherEntities(mc.player, area)) {
            if (Math.hypot(e.getX() - mc.player.getX(), e.getZ() - mc.player.getZ()) > RADIUS) continue;
            if (e instanceof LivingEntity le) {
                if (!le.isAlive() || l.size() >= MAX) continue;
                l.add(le);
            } else if (e instanceof EndCrystalEntity && c.size() < MAX) {
                c.add(e);
            } else {
                continue;
            }
            if (mc.player.canSee(e)) VISIBLE.add(e.getId());
        }
        living = l;
        crystals = c;
    }

    private static void clear() {
        living = List.of();
        crystals = List.of();
        VISIBLE.clear();
    }

    public static List<LivingEntity> living() {
        return Collections.unmodifiableList(living);
    }

    public static List<Entity> crystals() {
        return Collections.unmodifiableList(crystals);
    }

    /** true — игрок видит сущность напрямую (не за блоками). */
    public static boolean isVisible(Entity e) {
        return VISIBLE.contains(e.getId());
    }
}
