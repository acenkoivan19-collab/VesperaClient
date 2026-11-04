package dev.vespera.client.module.hud;

import dev.vespera.client.render.Icons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.List;

/** Состояние движения: бег, крадусь, полёт, элитры, плаваю, стою/иду. */
public final class SprintStatusModule extends InfoHudModule {

    public SprintStatusModule() {
        super("Sprint Status", "Бег, крадусь, лечу", Icons.Glyph.RUN);
        about("Показывает, как ты сейчас двигаешься: бег, крадёшься, плывёшь, летишь или на элитрах.");
    }

    @Override
    public float defaultY() {
        return 0.47f;
    }

    @Override
    protected void collect(List<Row> out) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        String s;
        int c = 0;
        if (p.isGliding()) { s = "Элитры"; c = 0xFF9FC3FF; }
        else if (p.getAbilities().flying) { s = "Полёт"; c = 0xFF9FC3FF; }
        else if (p.isSwimming()) { s = "Плаваю"; c = 0xFF5EB8FF; }
        else if (p.isSneaking()) { s = "Крадусь"; c = 0xFFFFD166; }
        else if (p.isSprinting()) { s = "Бег"; c = 0xFF5EE6CF; }
        else if (p.getVelocity().horizontalLengthSquared() > 1e-4) s = "Иду";
        else s = "Стою";
        out.add(new Row("ДВИЖЕНИЕ", s, c));
    }
}
