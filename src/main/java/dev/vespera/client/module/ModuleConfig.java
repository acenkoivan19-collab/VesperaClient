package dev.vespera.client.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.vespera.client.VesperaClient;
import dev.vespera.client.module.impl.WatermarkModule;
import dev.vespera.client.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * config/vespera-client.json:
 * <pre>
 * {
 *   "modules": { "Watermark": {"enabled":true, "style":0, "color":"theme", ...}, ... },  // вкл/выкл + настройки
 *   "layout":  { "Watermark": {"x":0.01,"y":0.01,"scale":1.0,"alpha":1.0}, ... }  // Редактор HUD
 * }
 * </pre>
 * Любое изменение помечает конфиг «грязным», запись — с задержкой 2 сек и при выходе.
 * Неизвестные/удалённые модули в файле просто игнорируются.
 */
public final class ModuleConfig {

    /** Раскладка одного HUD-модуля: позиция (доля экрана, левый верхний угол), масштаб, прозрачность. */
    public static final class Layout {
        public float x, y, scale = 1f, alpha = 1f;

        Layout(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public static final float MIN_SCALE = 0.5f, MAX_SCALE = 2f, MIN_ALPHA = 0.1f, MAX_ALPHA = 1f;

        public void clamp() {
            x = Math.max(0f, Math.min(1f, x));
            y = Math.max(0f, Math.min(1f, y));
            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
            alpha = Math.max(MIN_ALPHA, Math.min(MAX_ALPHA, alpha));
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SAVE_DELAY_TICKS = 40;

    private static final Map<String, Layout> LAYOUT = new HashMap<>();
    private static boolean dirty;
    private static int ticksSinceChange;
    private static boolean loading;

    private ModuleConfig() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("vespera-client.json");
    }

    public static void init() {
        ModuleManager.addListener((module, enabled) -> {
            if (!loading) markDirty();
        });
        load();
    }

    public static void markDirty() {
        dirty = true;
        ticksSinceChange = 0;
    }

    // ---------------------------------------------------------------- layout

    /** Раскладка модуля (создаётся из его значений по умолчанию, если её ещё нет). */
    public static Layout layout(HudModule m) {
        return LAYOUT.computeIfAbsent(m.name(), k -> new Layout(m.defaultX(), m.defaultY()));
    }

    /** Сбросить раскладку модуля на значения по умолчанию. */
    public static void resetLayout(HudModule m) {
        Layout old = LAYOUT.get(m.name());
        Layout l = new Layout(m.defaultX(), m.defaultY());
        if (old != null) { l.scale = old.scale; l.alpha = old.alpha; } // «сбросить позицию» — только позицию
        LAYOUT.put(m.name(), l);
        markDirty();
    }

    /** Полный сброс раскладки: позиция, масштаб и прозрачность. */
    public static void resetLayoutFull(HudModule m) {
        LAYOUT.put(m.name(), new Layout(m.defaultX(), m.defaultY()));
        markDirty();
    }

    // ---------------------------------------------------------------- IO

    private static void load() {
        Path path = file();
        loading = true;
        try {
            if (!Files.exists(path)) {
                ModuleManager.get(WatermarkModule.class).setEnabled(true); // первый запуск
                return;
            }
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(r, JsonObject.class);
                if (root == null) return;
                if (root.has("modules") && root.get("modules").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("modules").entrySet()) {
                        Module m = ModuleManager.byName(e.getKey());
                        if (m == null) continue;
                        JsonElement v = e.getValue();
                        if (v.isJsonPrimitive()) { // старый формат: "Имя": true
                            m.setEnabled(v.getAsBoolean());
                        } else if (v.isJsonObject()) { // новый: "Имя": {"enabled":true, "style":1, ...}
                            JsonObject o = v.getAsJsonObject();
                            for (Setting<?> st : m.settings()) if (o.has(st.key())) st.read(o.get(st.key()));
                            if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
                        }
                    }
                }
                if (root.has("layout") && root.get("layout").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("layout").entrySet()) {
                        if (!(ModuleManager.byName(e.getKey()) instanceof HudModule hud) || !e.getValue().isJsonObject()) continue;
                        JsonObject o = e.getValue().getAsJsonObject();
                        Layout l = new Layout(num(o, "x", hud.defaultX()), num(o, "y", hud.defaultY()));
                        l.scale = num(o, "scale", 1f);
                        l.alpha = num(o, "alpha", 1f);
                        l.clamp();
                        LAYOUT.put(hud.name(), l);
                    }
                }
            }
        } catch (Exception e) {
            VesperaClient.LOGGER.error("vespera: failed to load {}", path, e);
        } finally {
            loading = false;
        }
    }

    private static float num(JsonObject o, String key, float def) {
        try {
            return o.has(key) ? o.get(key).getAsFloat() : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static void save() {
        JsonObject modules = new JsonObject();
        for (Module m : ModuleManager.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            for (Setting<?> st : m.settings()) st.write(o);
            modules.add(m.name(), o);
        }

        JsonObject layout = new JsonObject();
        for (Map.Entry<String, Layout> e : LAYOUT.entrySet()) {
            Layout l = e.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("x", round(l.x));
            o.addProperty("y", round(l.y));
            o.addProperty("scale", round(l.scale));
            o.addProperty("alpha", round(l.alpha));
            layout.add(e.getKey(), o);
        }

        JsonObject root = new JsonObject();
        root.add("modules", modules);
        root.add("layout", layout);

        Path path = file();
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (Exception e) {
            VesperaClient.LOGGER.error("vespera: failed to save {}", path, e);
        }
    }

    private static float round(float v) {
        return Math.round(v * 10000f) / 10000f;
    }

    public static void saveIfDirty() {
        if (dirty) save();
    }

    /** Вызывать каждый клиентский тик. */
    public static void tick() {
        if (dirty && ++ticksSinceChange >= SAVE_DELAY_TICKS) save();
    }
}
