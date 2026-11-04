package dev.vespera.client.module.hud;

import dev.vespera.client.render.Icons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.List;

/** FPS + пинг (латентность своей записи в списке игроков). Цвет — по качеству. */
public final class FpsPingModule extends InfoHudModule {

    public FpsPingModule() {
        super("FPS & Ping", "Кадры в секунду и задержка до сервера", Icons.Glyph.BOLT);
        about("Показывает FPS и пинг до сервера. Цифры желтеют и краснеют, когда становится хуже.");
    }

    @Override
    public float defaultY() {
        return 0.06f;
    }

    @Override
    protected void collect(List<Row> out) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int fps = mc.getCurrentFps();
        out.add(new Row("FPS", String.valueOf(fps), fps >= 60 ? 0 : fps >= 30 ? 0xFFFFD166 : 0xFFFF5C7A));
        if (mc.isInSingleplayer() || mc.getNetworkHandler() == null || mc.player == null) {
            out.add(new Row("PING", "SP"));
            return;
        }
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        int ping = e != null ? e.getLatency() : 0;
        out.add(new Row("PING", ping + " ms", ping <= 80 ? 0 : ping <= 160 ? 0xFFFFD166 : 0xFFFF5C7A));
    }
}
