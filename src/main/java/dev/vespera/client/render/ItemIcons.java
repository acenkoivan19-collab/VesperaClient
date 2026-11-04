package dev.vespera.client.render;

import dev.vespera.client.VesperaClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Иконки предметов и эффектов для NanoVG — без DrawContext.
 * Берём PNG прямо из ресурсов игры (учитываются ресурспаки):
 *   предмет → textures/item/<id>.png, если нет — textures/block/<id>.png;
 *   эффект  → textures/mob_effect/<id>.png.
 * Загружаются лениво (на render-потоке внутри кадра), с NEAREST-фильтром для пиксель-арта.
 * У предметов с 3D-моделью (сундук, щит…) плоской текстуры может не быть — тогда 0,
 * и модуль рисует запасной значок.
 */
public final class ItemIcons {

    private static final Map<Identifier, Integer> CACHE = new HashMap<>();
    private static final int MAX = 384;

    private ItemIcons() {}

    public static int item(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        Integer cached = CACHE.get(id);
        if (cached != null) return cached;
        int img = load(Identifier.of(id.getNamespace(), "textures/item/" + id.getPath() + ".png"));
        if (img == 0) img = load(Identifier.of(id.getNamespace(), "textures/block/" + id.getPath() + ".png"));
        put(id, img);
        return img;
    }

    public static int effect(StatusEffect effect) {
        Identifier id = Registries.STATUS_EFFECT.getId(effect);
        if (id == null) return 0;
        Identifier key = Identifier.of(id.getNamespace(), "effect/" + id.getPath());
        Integer cached = CACHE.get(key);
        if (cached != null) return cached;
        int img = load(Identifier.of(id.getNamespace(), "textures/mob_effect/" + id.getPath() + ".png"));
        put(key, img);
        return img;
    }

    private static void put(Identifier id, int img) {
        if (CACHE.size() >= MAX) clear(NvgRenderer.context());
        CACHE.put(id, img);
    }

    private static int load(Identifier tex) {
        long vg = NvgRenderer.context();
        if (vg == 0L) return 0;
        try {
            Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(tex);
            if (res.isEmpty()) return 0;
            byte[] bytes;
            try (InputStream in = res.get().getInputStream()) {
                bytes = in.readAllBytes();
            }
            ByteBuffer buf = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            try {
                return Math.max(0, nvgCreateImageMem(vg, NVG_IMAGE_NEAREST, buf));
            } finally {
                MemoryUtil.memFree(buf);
            }
        } catch (Exception e) {
            VesperaClient.LOGGER.debug("vespera: no icon {}", tex, e);
            return 0;
        }
    }

    /** Нарисовать иконку в квадрат (x, y, size). Анимированные текстуры (полосой) — первый кадр. */
    public static void draw(long vg, int img, float x, float y, float size, float alpha) {
        if (img <= 0) return;
        int[] w = new int[1], h = new int[1];
        nvgImageSize(vg, img, w, h);
        float frames = w[0] > 0 && h[0] > w[0] ? h[0] / (float) w[0] : 1f; // вертикальная лента кадров
        org.lwjgl.nanovg.NVGPaint p = PAINT;
        nvgImagePattern(vg, x, y, size, size * frames, 0f, img, alpha, p);
        nvgBeginPath(vg);
        nvgRect(vg, x, y, size, size);
        nvgFillPaint(vg, p);
        nvgFill(vg);
    }

    private static final org.lwjgl.nanovg.NVGPaint PAINT = org.lwjgl.nanovg.NVGPaint.malloc();

    /** Удалить все картинки (смена ресурспака/выход). */
    public static void clear(long vg) {
        if (vg != 0L) for (int img : CACHE.values()) if (img > 0) nvgDeleteImage(vg, img);
        CACHE.clear();
    }
}
