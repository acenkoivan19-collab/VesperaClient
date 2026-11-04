package dev.vespera.client.compat;

import dev.vespera.client.VesperaClient;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.invoke.MethodType.methodType;

/**
 * Null-safe обёртка над мостом TerraHUD (com.terra.hud.TerraHudBridge + Modules).
 *
 * Почему через MethodHandle, а не прямые вызовы: TerraHUD — НЕобязательная зависимость.
 * Прямая ссылка на com.terra.hud.* в коде Vespera без мода в сборке/игре дала бы
 * NoClassDefFoundError при загрузке класса меню. Хендлы ищутся один раз (если мод
 * загружен), вызовы — invokeExact, по скорости как прямые.
 *
 * Любая ошибка моста → безопасное значение по умолчанию + одно предупреждение в лог,
 * раздел меню просто скрывается. Меню Vespera не падает никогда.
 *
 * Состояние НЕ кэшируется: каждый геттер читает конфиг TerraHUD живьём.
 */
public final class TerraBridge {

    /** Описание рендер-модуля TerraHUD (метаданные из Modules.ALL — статичны). */
    public record ModInfo(String id, String name, String desc) {}

    private static boolean initDone, available;

    private static MethodHandle hudIds, hudName, hudEnabled, setHudEnabled, hudPos, setHudPos,
            hudScale, setHudScale, hudStyle, setHudStyle, hudColor, setHudColor,
            config, modulesIsOn, modulesSetOn, configSave;
    private static List<ModInfo> allModules = List.of();
    private static boolean warned;

    private TerraBridge() {}

    /** Мод terrahud загружен и мост найден. */
    public static boolean available() {
        if (!initDone) init();
        return available;
    }

    private static void init() {
        initDone = true;
        try {
            if (!FabricLoader.getInstance().isModLoaded("terrahud")) return;
            ClassLoader cl = TerraBridge.class.getClassLoader();
            Class<?> br = Class.forName("com.terra.hud.TerraHudBridge", true, cl);
            Class<?> mods = Class.forName("com.terra.hud.Modules", true, cl);
            Class<?> cfg = Class.forName("com.terra.hud.TerraHudConfig", true, cl);
            MethodHandles.Lookup lk = MethodHandles.publicLookup();

            hudIds = lk.findStatic(br, "hudIds", methodType(String[].class));
            hudName = lk.findStatic(br, "hudName", methodType(String.class, String.class));
            hudEnabled = lk.findStatic(br, "hudEnabled", methodType(boolean.class, String.class));
            setHudEnabled = lk.findStatic(br, "setHudEnabled", methodType(void.class, String.class, boolean.class));
            hudPos = lk.findStatic(br, "hudPos", methodType(float[].class, String.class));
            setHudPos = lk.findStatic(br, "setHudPos", methodType(void.class, String.class, float.class, float.class));
            hudScale = lk.findStatic(br, "hudScale", methodType(float.class, String.class));
            setHudScale = lk.findStatic(br, "setHudScale", methodType(void.class, String.class, float.class));
            hudStyle = lk.findStatic(br, "hudStyle", methodType(int.class, String.class));
            setHudStyle = lk.findStatic(br, "setHudStyle", methodType(void.class, String.class, int.class));
            hudColor = lk.findStatic(br, "hudColor", methodType(int.class, String.class));
            setHudColor = lk.findStatic(br, "setHudColor", methodType(void.class, String.class, int.class));

            config = lk.findStatic(br, "config", methodType(cfg)).asType(methodType(Object.class));
            modulesIsOn = lk.findStatic(mods, "isOn", methodType(boolean.class, cfg, String.class))
                    .asType(methodType(boolean.class, Object.class, String.class));
            modulesSetOn = lk.findStatic(mods, "setOn", methodType(void.class, cfg, String.class, boolean.class))
                    .asType(methodType(void.class, Object.class, String.class, boolean.class));
            configSave = lk.findVirtual(cfg, "save", methodType(void.class))
                    .asType(methodType(void.class, Object.class));

            allModules = readModules(mods);
            available = true;
            VesperaClient.LOGGER.info("vespera: мост TerraHUD подключён ({} модулей)", allModules.size());
        } catch (Throwable t) {
            available = false;
            VesperaClient.LOGGER.warn("vespera: мост TerraHUD недоступен — раздел меню скрыт", t);
        }
    }

    /** Modules.ALL: List<Mod(id, name, cat, desc)> — читаем аксессоры record'а. */
    private static List<ModInfo> readModules(Class<?> mods) throws Exception {
        Object list = mods.getField("ALL").get(null);
        List<ModInfo> out = new ArrayList<>();
        if (!(list instanceof List<?> l)) return out;
        for (Object m : l) {
            Method id = m.getClass().getMethod("id"), name = m.getClass().getMethod("name"),
                    desc = m.getClass().getMethod("desc");
            out.add(new ModInfo((String) id.invoke(m), (String) name.invoke(m), (String) desc.invoke(m)));
        }
        return Collections.unmodifiableList(out);
    }

    private static void fail(Throwable t) {
        if (!warned) {
            warned = true;
            VesperaClient.LOGGER.warn("vespera: ошибка вызова моста TerraHUD", t);
        }
    }

    // ---------------------------------------------------------------- HUD-элементы

    public static String[] hudIds() {
        if (!available()) return new String[0];
        try { String[] r = (String[]) hudIds.invokeExact(); return r != null ? r : new String[0]; }
        catch (Throwable t) { fail(t); return new String[0]; }
    }

    public static String hudName(String id) {
        if (!available()) return id;
        try { String r = (String) hudName.invokeExact(id); return r != null ? r : id; }
        catch (Throwable t) { fail(t); return id; }
    }

    public static boolean hudEnabled(String id) {
        if (!available()) return false;
        try { return (boolean) hudEnabled.invokeExact(id); } catch (Throwable t) { fail(t); return false; }
    }

    public static void setHudEnabled(String id, boolean v) {
        if (!available()) return;
        try { setHudEnabled.invokeExact(id, v); } catch (Throwable t) { fail(t); }
    }

    /** {x, y} доля экрана 0..1. */
    public static float[] hudPos(String id) {
        if (!available()) return new float[]{0f, 0f};
        try {
            float[] r = (float[]) hudPos.invokeExact(id);
            return r != null && r.length >= 2 ? r : new float[]{0f, 0f};
        } catch (Throwable t) { fail(t); return new float[]{0f, 0f}; }
    }

    public static void setHudPos(String id, float x, float y) {
        if (!available()) return;
        try { setHudPos.invokeExact(id, x, y); } catch (Throwable t) { fail(t); }
    }

    public static float hudScale(String id) {
        if (!available()) return 1f;
        try { return (float) hudScale.invokeExact(id); } catch (Throwable t) { fail(t); return 1f; }
    }

    public static void setHudScale(String id, float s) {
        if (!available()) return;
        try { setHudScale.invokeExact(id, s); } catch (Throwable t) { fail(t); }
    }

    public static int hudStyle(String id) {
        if (!available()) return 0;
        try { return (int) hudStyle.invokeExact(id); } catch (Throwable t) { fail(t); return 0; }
    }

    public static void setHudStyle(String id, int s) {
        if (!available()) return;
        try { setHudStyle.invokeExact(id, s); } catch (Throwable t) { fail(t); }
    }

    public static int hudColor(String id) {
        if (!available()) return 0xFF4F6F52;
        try { return (int) hudColor.invokeExact(id); } catch (Throwable t) { fail(t); return 0xFF4F6F52; }
    }

    public static void setHudColor(String id, int argb) {
        if (!available()) return;
        try { setHudColor.invokeExact(id, argb); } catch (Throwable t) { fail(t); }
    }

    // ---------------------------------------------------------------- рендер-модули

    /** Все модули TerraHUD, кроме HUD-элементов (их список — живой, из hudIds()). */
    public static List<ModInfo> renderModules() {
        if (!available()) return List.of();
        Set<String> hud = new HashSet<>(List.of(hudIds()));
        List<ModInfo> out = new ArrayList<>();
        for (ModInfo m : allModules) if (!hud.contains(m.id())) out.add(m);
        return out;
    }

    private static Object cfg() throws Throwable {
        return (Object) config.invokeExact();
    }

    public static boolean moduleOn(String id) {
        if (!available()) return false;
        try {
            Object c = cfg();
            return c != null && (boolean) modulesIsOn.invokeExact(c, id);
        } catch (Throwable t) { fail(t); return false; }
    }

    /** Modules.setOn (с побочными эффектами TerraHUD: фулбрайт, FPS Boost…) + config.save(). */
    public static void setModuleOn(String id, boolean v) {
        if (!available()) return;
        try {
            Object c = cfg();
            if (c == null) return;
            modulesSetOn.invokeExact(c, id, v);
            configSave.invokeExact(c);
        } catch (Throwable t) { fail(t); }
    }
}
