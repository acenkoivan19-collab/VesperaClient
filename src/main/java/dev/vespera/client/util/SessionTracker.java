package dev.vespera.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

/**
 * Время текущей сессии: с момента входа в мир/на сервер. Смена измерения
 * (новый ClientWorld при том же подключении) сессию не сбрасывает — сброс только
 * при выходе в меню (world == null). Тикается из VesperaClient.
 */
public final class SessionTracker {

    private static long startMs = -1L;
    private static ClientWorld lastWorld;

    private SessionTracker() {}

    public static void tick(MinecraftClient mc) {
        if (mc.world == null) {
            startMs = -1L;
            lastWorld = null;
            return;
        }
        if (startMs < 0) startMs = System.currentTimeMillis();
        lastWorld = mc.world;
    }

    /** Длительность сессии в мс (0 вне мира). */
    public static long millis() {
        return startMs < 0 ? 0 : System.currentTimeMillis() - startMs;
    }

    public static String format(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }
}
