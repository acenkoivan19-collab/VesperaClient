package dev.vespera.client.render;

import dev.vespera.client.VesperaClient;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.ui.ClickGuiScreen;
import dev.vespera.client.ui.HudEditorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * Владелец NanoVG-контекста. Каждый кадр (из WindowMixin, перед swap):
 *  1) HUD — renderHud() у включённых HudModule (Watermark, ArrayList…);
 *  2) ClickGUI — если открыт.
 * GL-стейт Minecraft сохраняется до и полностью восстанавливается после.
 */
public final class NvgRenderer {

    private static long vg = 0L;
    private static boolean initFailed = false;
    private static int logoImage = 0;
    private static long lastFrameNs = -1L;
    private static final GlStateBackup STATE = new GlStateBackup();
    private static final List<ByteBuffer> FONT_DATA = new ArrayList<>();

    private NvgRenderer() {}

    /** Во сколько раз HUD-координаты меньше координат окна (следует за GUI Scale игры). */
    public static float hudScale() {
        double f = MinecraftClient.getInstance().getWindow().getScaleFactor();
        return Math.max(0.75f, Math.min(2f, (float) f / 2f));
    }

    /** Текущий NanoVG-контекст (0, если ещё не создан). Использовать только внутри кадра. */
    public static long context() {
        return vg;
    }

    /** id картинки логотипа или 0, если не загрузилась. */
    public static int logoImage() {
        return logoImage;
    }

    /** Вызывается из WindowMixin перед glfwSwapBuffers. */
    public static void onFrameEnd() {
        if (initFailed) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        ClickGuiScreen gui = mc.currentScreen instanceof ClickGuiScreen s ? s : null;
        HudEditorScreen editor = mc.currentScreen instanceof HudEditorScreen e ? e : null;
        HudModule.setEditing(editor != null);
        boolean hud = mc.world != null && mc.player != null
                && (!mc.options.hudHidden || editor != null)
                && (mc.currentScreen == null || gui != null || editor != null)
                && (ModuleManager.hasEnabledHud() || editor != null);
        if (gui == null && !hud) {
            lastFrameNs = -1L;
            return;
        }

        Window window = mc.getWindow();
        int fbW = window.getFramebufferWidth(), fbH = window.getFramebufferHeight();
        int winW = window.getWidth(), winH = window.getHeight();
        if (fbW <= 0 || fbH <= 0 || winW <= 0 || winH <= 0) return;

        long now = System.nanoTime();
        float dt = lastFrameNs < 0 ? 0f : Math.min((now - lastFrameNs) / 1_000_000_000f, 0.1f);
        lastFrameNs = now;

        STATE.capture();
        try {
            if (vg == 0L && !init()) return;

            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, fbW, fbH);
            glBindSampler(0, 0);
            glDisable(GL_SCISSOR_TEST);
            glStencilMask(0xFF);
            glClear(GL_STENCIL_BUFFER_BIT);

            nvgBeginFrame(vg, winW, winH, fbW / (float) winW);

            if (hud) {
                // HUD масштабируется вместе с GUI Scale игры (2 = базовый размер)
                float s = hudScale();
                nvgSave(vg);
                nvgScale(vg, s, s);
                Projection.update(mc, winW / s, winH / s); // камера этого кадра для 3D-эффектов
                Quality.frame(dt);      // авто-качество по FPS
                Occlusion.newFrame();   // бюджет лучей видимости на кадр
                if (editor != null) editor.renderBackdrop(vg, winW / s, winH / s);
                ModuleManager.renderHud(vg, winW / s, winH / s, dt);
                if (editor != null) editor.renderOverlay(vg, winW / s, winH / s, dt);
                nvgRestore(vg);
            }
            if (gui != null) {
                gui.renderNvg(vg, winW, winH);
            }

            nvgEndFrame(vg);
        } catch (Throwable t) {
            VesperaClient.LOGGER.error("NanoVG frame failed", t);
        } finally {
            STATE.restore();
        }
    }

    private static boolean init() {
        vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (vg == 0L) {
            initFailed = true;
            VesperaClient.LOGGER.error("Could not create NanoVG GL3 context");
            return false;
        }
        int regular = loadFont(Fonts.REGULAR, "/assets/vespera/fonts/Inter-Regular.ttf");
        int medium = loadFont(Fonts.MEDIUM, "/assets/vespera/fonts/Inter-Medium.ttf");
        int semibold = loadFont(Fonts.SEMIBOLD, "/assets/vespera/fonts/Inter-SemiBold.ttf");

        // нет какого-то начертания — регистрируем под его именем любой загруженный файл
        if (FONT_DATA.isEmpty()) {
            VesperaClient.LOGGER.error("No fonts found in assets/vespera/fonts — text will be invisible");
        } else {
            if (regular < 0) nvgCreateFontMem(vg, Fonts.REGULAR, FONT_DATA.get(0), false);
            if (medium < 0) nvgCreateFontMem(vg, Fonts.MEDIUM, FONT_DATA.get(0), false);
            if (semibold < 0) nvgCreateFontMem(vg, Fonts.SEMIBOLD, FONT_DATA.get(0), false);
        }

        logoImage = loadImage("/assets/vespera/textures/logo.png");
        return true;
    }

    private static byte[] readResource(String path) throws java.io.IOException {
        try (InputStream in = VesperaClient.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static int loadFont(String name, String path) {
        try {
            byte[] bytes = readResource(path);
            if (bytes == null) {
                VesperaClient.LOGGER.warn("Font not found: {}", path);
                return -1;
            }
            ByteBuffer data = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            int id = nvgCreateFontMem(vg, name, data, false);
            if (id < 0) {
                MemoryUtil.memFree(data);
                return -1;
            }
            FONT_DATA.add(data); // NanoVG читает буфер, пока жив контекст
            return id;
        } catch (Exception e) {
            VesperaClient.LOGGER.warn("Failed to load font {}", path, e);
            return -1;
        }
    }

    private static int loadImage(String path) {
        try {
            byte[] bytes = readResource(path);
            if (bytes == null) {
                VesperaClient.LOGGER.warn("Image not found: {}", path);
                return 0;
            }
            ByteBuffer data = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            try {
                // NanoVG декодирует PNG сразу, буфер можно освободить
                return nvgCreateImageMem(vg, NVG_IMAGE_GENERATE_MIPMAPS, data);
            } finally {
                MemoryUtil.memFree(data);
            }
        } catch (Exception e) {
            VesperaClient.LOGGER.warn("Failed to load image {}", path, e);
            return 0;
        }
    }

    public static void destroy() {
        if (vg != 0L) {
            ItemIcons.clear(vg);
            if (logoImage > 0) nvgDeleteImage(vg, logoImage);
            nvgDelete(vg);
            vg = 0L;
            logoImage = 0;
        }
        FONT_DATA.forEach(MemoryUtil::memFree);
        FONT_DATA.clear();
    }
}
