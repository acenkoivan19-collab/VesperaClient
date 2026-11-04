package dev.vespera.client.module.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.HudStyle;
import dev.vespera.client.render.Icons;
import dev.vespera.client.render.NvgRenderer;
import dev.vespera.client.ui.Theme;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Музыка: что играет в плеере лаунчера. Данные — gameDir/terra-nowplaying.json
 * (title, artist, playing, dur, pos в мс, artKey, art — обложка base64), файл читается
 * не чаще раза в 500 мс и только если изменился. Позиция между чтениями
 * досчитывается по часам. Обложка декодируется только при смене artKey.
 * Кнопки — индикаторы состояния (управление — клавишами F7–F9 TerraHUD).
 */
public final class MusicModule extends HudModule {

    private static final float W = 250f, H = 72f, ART = 52f;

    private final BoolSetting cover = setting(new BoolSetting("cover", "Обложка", true));

    private String title = "", artist = "", artKey = "";
    private boolean playing, hasData;
    private long durMs, posMs, posStamp, lastRead, lastMtime = -1;
    private int artImage;
    private String pendingArt; // base64 ждёт декодирования внутри кадра (нужен NanoVG)

    public MusicModule() {
        super("Music", "Что играет в плеере лаунчера", Category.INTERFACE);
        about("Что сейчас играет в плеере лаунчера: обложка, название, исполнитель и прогресс трека.");
    }

    @Override
    public float defaultX() {
        return 0.37f;
    }

    @Override
    public float defaultY() {
        return 0.86f;
    }

    @Override
    protected void onDisable() {
        long vg = NvgRenderer.context();
        if (artImage > 0 && vg != 0L) nvgDeleteImage(vg, artImage);
        artImage = 0;
        artKey = "";
        lastMtime = -1;
        hasData = false;
    }

    private void read() {
        long now = System.currentTimeMillis();
        if (now - lastRead < 500) return;
        lastRead = now;
        try {
            Path p = FabricLoader.getInstance().getGameDir().resolve("terra-nowplaying.json");
            if (!Files.exists(p)) { hasData = false; return; }
            long mt = Files.getLastModifiedTime(p).toMillis();
            if (mt == lastMtime) return;
            lastMtime = mt;
            JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            title = str(o, "title");
            artist = str(o, "artist");
            playing = "true".equalsIgnoreCase(str(o, "playing"));
            durMs = Math.max(0, num(o, "dur"));
            posMs = Math.max(0, num(o, "pos"));
            posStamp = now;
            hasData = !title.isEmpty();
            String ak = str(o, "artKey");
            if (hasData && !ak.isEmpty() && !ak.equals(artKey)) {
                artKey = ak;
                String art = str(o, "art");
                pendingArt = art.isEmpty() || art.length() > 1_500_000 ? null : art;
            }
        } catch (Exception e) {
            hasData = false;
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static long num(JsonObject o, String k) {
        try {
            JsonElement e = o.get(k);
            return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    private void decodeArt(long vg) {
        if (pendingArt == null) return;
        String art = pendingArt;
        pendingArt = null;
        try {
            byte[] bytes = Base64.getDecoder().decode(art);
            ByteBuffer buf = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            try {
                int img = nvgCreateImageMem(vg, 0, buf); // stb_image: PNG/JPEG
                if (img > 0) {
                    if (artImage > 0) nvgDeleteImage(vg, artImage);
                    artImage = img;
                }
            } finally {
                MemoryUtil.memFree(buf);
            }
        } catch (Exception ignored) {
        }
    }

    private long position() {
        long p = posMs + (playing ? System.currentTimeMillis() - posStamp : 0);
        return durMs > 0 ? Math.max(0, Math.min(durMs, p)) : Math.max(0, p);
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        read();
        decodeArt(vg);
        size(W, H);
        boolean show = hasData || isEditing();
        if (!show) return;
        String t = hasData ? title : "Название трека", a = hasData ? artist : "Исполнитель";

        HudStyle.panel(vg, this, 0, 0, W, H, 12f);
        int acc = accent();
        float x = 10f;
        if (cover.on()) {
            if (artImage > 0) {
                Draw.image(vg, artImage, 10f, (H - ART) / 2f, ART, ART, 8f, 1f);
            } else {
                Draw.linear(vg, 10f, (H - ART) / 2f, ART, ART, 8f, 10f, 10f, 10f + ART, 10f + ART, acc, 0.5f, Theme.ACCENT_2, 0.3f);
                Icons.glyph(vg, Icons.Glyph.MUSIC, 10f + ART / 2f, H / 2f, 20f, 0xFFFFFFFF, 0.9f);
            }
            x = 10f + ART + 10f;
        }
        float tw = W - x - 60f;
        Draw.text(vg, Draw.ellipsize(vg, t, Fonts.SEMIBOLD, 12.5f, tw), x, 17f, Fonts.SEMIBOLD, 12.5f, Draw.LEFT_MID, Theme.TEXT, 1f);
        Draw.text(vg, Draw.ellipsize(vg, a, Fonts.REGULAR, 11f, tw), x, 32f, Fonts.REGULAR, 11f, Draw.LEFT_MID, Theme.TEXT_DIM, 1f);

        // кнопки-индикаторы: ⏮ ⏯ ⏭
        float bx = W - 54f, by = 22f;
        tri(vg, bx, by, -1, Theme.TEXT_DIM);
        if (playing) {
            Draw.rect(vg, bx + 19f, by - 5f, 2.5f, 10f, 1f, Theme.TEXT);
            Draw.rect(vg, bx + 24f, by - 5f, 2.5f, 10f, 1f, Theme.TEXT);
        } else {
            nvgBeginPath(vg);
            nvgMoveTo(vg, bx + 19f, by - 5.5f);
            nvgLineTo(vg, bx + 27f, by);
            nvgLineTo(vg, bx + 19f, by + 5.5f);
            nvgClosePath(vg);
            nvgFillColor(vg, Draw.c1(Theme.TEXT, 1f));
            nvgFill(vg);
        }
        tri(vg, bx + 40f, by, 1, Theme.TEXT_DIM);

        // прогресс
        long pos = hasData ? position() : 42_000, dur = hasData ? durMs : 180_000;
        float py = H - 18f, pw = W - x - 10f;
        float p = dur > 0 ? pos / (float) dur : 0f;
        Draw.rect(vg, x, py, pw, 3f, 1.5f, 0xFF000000, 0.45f);
        Draw.linear(vg, x, py, Math.max(3f, pw * p), 3f, 1.5f, x, py, x + pw, py, acc, 1f, Theme.ACCENT_2, 1f);
        Draw.textTabular(vg, fmt(pos), x, py + 9f, Fonts.MEDIUM, 9.5f, Draw.LEFT_MID, Theme.TEXT_DIM, 1f);
        if (dur > 0) Draw.textTabular(vg, fmt(dur), x + pw, py + 9f, Fonts.MEDIUM, 9.5f, Draw.RIGHT_MID, Theme.TEXT_DIM, 1f);
    }

    /** Двойной треугольник «назад/вперёд». dir = −1 назад, 1 вперёд. */
    private static void tri(long vg, float x, float y, int dir, int color) {
        for (int k = 0; k < 2; k++) {
            float ox = x + k * 6f * dir + (dir < 0 ? 10f : 0f);
            nvgBeginPath(vg);
            nvgMoveTo(vg, ox, y - 4.5f);
            nvgLineTo(vg, ox + 6f * dir, y);
            nvgLineTo(vg, ox, y + 4.5f);
            nvgClosePath(vg);
            nvgFillColor(vg, Draw.c1(color, 1f));
            nvgFill(vg);
        }
    }
}
