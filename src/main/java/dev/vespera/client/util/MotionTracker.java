package dev.vespera.client.util;

import net.minecraft.client.MinecraftClient;

/**
 * Скорость игрока (блоков/сек, сглаженная) и время последнего движения.
 * Логика 1:1 из TerraHudClient.tickMovement. Тикается из VesperaClient.
 */
public final class MotionTracker {

    private static double lastX, lastZ, smoothBps;
    private static long lastT, lastMoveMs;

    private MotionTracker() {}

    public static void tick(MinecraftClient mc) {
        if (mc.player == null) {
            smoothBps = 0;
            lastT = 0;
            return;
        }
        long now = System.currentTimeMillis();
        double x = mc.player.getX(), z = mc.player.getZ();
        if (lastT > 0) {
            double dt = Math.max(1, now - lastT) / 1000.0;
            double inst = Math.min(60, Math.hypot(x - lastX, z - lastZ) / dt);
            smoothBps += (inst - smoothBps) * 0.15;
            if (smoothBps < 0.05 && inst < 0.05) smoothBps = 0;
            if (inst > 0.4) lastMoveMs = now;
        }
        lastX = x;
        lastZ = z;
        lastT = now;
    }

    /** Сглаженная горизонтальная скорость, блоков/сек. */
    public static double bps() {
        return smoothBps;
    }

    /** System.currentTimeMillis() последнего заметного движения. */
    public static long lastMoveMs() {
        return lastMoveMs;
    }
}
