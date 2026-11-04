package dev.vespera.client.module.hud;

import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.render.Icons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.List;

/** Где играешь: имя/адрес сервера или одиночная игра. */
public final class ServerModule extends InfoHudModule {

    private final BoolSetting address = setting(new BoolSetting("address", "Адрес", true));

    public ServerModule() {
        super("Server", "Имя и адрес сервера", Icons.Glyph.SERVER);
        about("Название и адрес сервера, на котором ты играешь, или «Одиночная игра».");
    }

    @Override
    public float defaultY() {
        return 0.52f;
    }

    @Override
    protected void collect(List<Row> out) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isInSingleplayer()) {
            out.add(new Row("МИР", "Одиночная игра"));
            return;
        }
        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null) {
            out.add(new Row("СЕРВЕР", "—"));
            return;
        }
        out.add(new Row("СЕРВЕР", info.name));
        if (address.on() && !info.address.equals(info.name)) out.add(new Row("IP", info.address));
    }
}
