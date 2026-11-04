package dev.vespera.client.module.hud;

import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.render.Icons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;

/** Координаты X Y Z (+ пересчёт в Незер/Верхний мир). */
public final class CoordsModule extends InfoHudModule {

    private final BoolSetting decimals = setting(new BoolSetting("decimals", "Дробные", false));
    private final BoolSetting other = setting(new BoolSetting("nether", "Пересчёт Незер", true));

    public CoordsModule() {
        super("Coordinates", "X Y Z текущего положения", Icons.Glyph.PIN);
        about("Координаты X Y Z и, по желанию, пересчёт в координаты Незера или Верхнего мира для порталов.");
    }

    @Override
    public float defaultY() {
        return 0.14f;
    }

    private String fmt(double v) {
        return decimals.on() ? String.format(Locale.ROOT, "%.1f", v) : String.valueOf((int) Math.floor(v));
    }

    @Override
    protected void collect(List<Row> out) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        out.add(new Row("X", fmt(x)));
        out.add(new Row("Y", fmt(y)));
        out.add(new Row("Z", fmt(z)));
        if (other.on()) {
            var dim = mc.world.getRegistryKey();
            if (dim == World.NETHER) out.add(new Row("МИР", (int) Math.floor(x * 8) + " " + (int) Math.floor(z * 8), 0xFF9FC39A));
            else if (dim == World.OVERWORLD) out.add(new Row("НЕЗЕР", (int) Math.floor(x / 8) + " " + (int) Math.floor(z / 8), 0xFFFF7A59));
        }
    }
}
