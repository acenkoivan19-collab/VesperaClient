package dev.vespera.client.module.hud;

import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.render.Icons;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Клики в секунду. Свой счётчик: каждый кадр опрашивает кнопки мыши (GLFW) и ловит
 * нажатия — ванильные бинды не «съедаются». Окно — последняя секунда.
 */
public final class CpsModule extends InfoHudModule {

    private final BoolSetting right = setting(new BoolSetting("right", "Показывать ПКМ", true));
    private final ArrayDeque<Long> left = new ArrayDeque<>(), rightClicks = new ArrayDeque<>();
    private boolean wasL, wasR;

    public CpsModule() {
        super("CPS", "Клики ЛКМ/ПКМ в секунду", Icons.Glyph.MOUSE);
        about("Считает клики левой и правой кнопкой мыши за последнюю секунду.");
    }

    @Override
    public float defaultY() {
        return 0.26f;
    }

    @Override
    protected void onDisable() {
        left.clear();
        rightClicks.clear();
    }

    @Override
    protected void collect(List<Row> out) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long win = mc.getWindow().getHandle(), now = System.currentTimeMillis();
        boolean l = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (mc.currentScreen == null) {
            if (l && !wasL) left.add(now);
            if (r && !wasR) rightClicks.add(now);
        }
        wasL = l;
        wasR = r;
        while (!left.isEmpty() && now - left.peekFirst() > 1000) left.pollFirst();
        while (!rightClicks.isEmpty() && now - rightClicks.peekFirst() > 1000) rightClicks.pollFirst();

        out.add(new Row("ЛКМ", String.valueOf(left.size())));
        if (right.on()) out.add(new Row("ПКМ", String.valueOf(rightClicks.size())));
    }
}
