package dev.vespera.client.module;

import dev.vespera.client.VesperaClient;
import dev.vespera.client.module.hud.*;
import dev.vespera.client.module.impl.*;
import dev.vespera.client.render.WorldDraw;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static dev.vespera.client.module.Category.*;
import static org.lwjgl.nanovg.NanoVG.nvgRestore;
import static org.lwjgl.nanovg.NanoVG.nvgSave;

/**
 * Реестр и менеджер состояний модулей.
 *
 *  GUI-тумблер ──► Module.setEnabled() ──► onEnable()/onDisable()
 *                                     └──► ModuleManager.dispatchStateChanged()
 *                                              └──► StateListener'ы (конфиг, уведомления…)
 *
 *  Тик клиента ──► ModuleManager.tick()       ──► onTick() только у ВКЛЮЧЁННЫХ
 *  Кадр        ──► ModuleManager.renderHud()  ──► renderHud() только у ВКЛЮЧЁННЫХ HudModule
 */
public final class ModuleManager {

    private static final List<Module> MODULES = new ArrayList<>();
    private static final Map<Class<? extends Module>, Module> BY_CLASS = new HashMap<>();
    private static final Map<String, Module> BY_NAME = new LinkedHashMap<>();
    private static final List<StateListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static List<HudModule> hudOrder; // HUD-модули по слоям, строится лениво

    static {
        // ---- Combat (визуальные)
        register(new HitColorModule());
        register(new TargetHudModule());
        register(new CrosshairModule());
        register(new HitBurstModule());
        // ---- Visuals
        register(new JumpCirclesModule());
        register(new TrailsModule());
        register(new ChinaHatModule());
        register(new SwarmModule());
        register(new HitboxesModule());
        register(new TargetEspModule());
        register(new DamageNumbersModule());
        register(new KillEffectModule());
        register(new WorldParticlesModule());
        register(new NightSkyModule());
        register(new RenderQualityModule());
        register(new AuraModule());
        register(new ItemGlowModule());
        register(new ProjectileTrailsModule());
        register(new BlockEffectsModule());
        register(new ScreenEffectsModule());
        register(new TotemPopModule());
        register(new HitMarkerModule());
        register(new BlockOverlayModule());
        register(new HandViewModule());
        register(new ItemPhysicsModule());
        // ---- World
        register(new TimeChangerModule());
        register(new WeatherChangerModule());
        // ---- Interface
        register(new WatermarkModule());
        register(new KeystrokesModule());
        register(new NotificationsModule());
        // ---- HUD-панели (по умолчанию выключены; двигаются в Редакторе HUD, ПКМ — настройки)
        register(new FpsPingModule());
        register(new CoordsModule());
        register(new CpsModule());
        register(new ClockModule());
        register(new SessionModule());
        register(new RamModule());
        register(new SprintStatusModule());
        register(new DirectionModule());
        register(new ServerModule());
        register(new ReachModule());
        register(new SaturationModule());
        register(new CooldownsModule());
        register(new PickupModule());
        register(new EffectsModule());
        register(new ArmorModule());
        register(new MusicModule());
        register(new InventoryModule());
        register(new ComboModule());
        register(new ItemsCounterModule());
        register(new BpsModule());
        // ---- Utility
        register(new ZoomModule());
        register(new AutoSprintModule());
        register(new FpsBoostModule());
        // ---- Cosmetics
        register(new WingsModule());
        register(new HaloModule());
        register(new BackpackModule());
        register(new PetModule());
        // ---- Appearance
        register(new ThemeModule());
    }

    private ModuleManager() {}

    // ---------------------------------------------------------------- registry

    public static <T extends Module> T register(T module) {
        String key = key(module.name());
        if (BY_NAME.containsKey(key)) {
            throw new IllegalStateException("Duplicate module name: " + module.name());
        }
        MODULES.add(module);
        BY_NAME.put(key, module);
        hudOrder = null;
        BY_CLASS.put(module.getClass(), module);
        return module;
    }

    /** Типобезопасный доступ: {@code ModuleManager.get(WatermarkModule.class).isEnabled()}. */
    public static <T extends Module> T get(Class<T> type) {
        Module m = BY_CLASS.get(type);
        if (m == null) throw new IllegalArgumentException("Module not registered: " + type.getName());
        return type.cast(m);
    }

    public static Module byName(String name) {
        return BY_NAME.get(key(name));
    }

    /** HUD-модули в порядке отрисовки (по слоям). */
    public static List<HudModule> hudModules() {
        if (hudOrder == null) renderOrder();
        return Collections.unmodifiableList(hudOrder);
    }

    private static void renderOrder() {
        hudOrder = new ArrayList<>();
        for (Module m : MODULES) if (m instanceof HudModule hud) hudOrder.add(hud);
        hudOrder.sort(java.util.Comparator.comparingInt(HudModule::layer)); // стабильная сортировка
    }

    public static List<Module> all() {
        return Collections.unmodifiableList(MODULES);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- state

    public static void addListener(StateListener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(StateListener listener) {
        LISTENERS.remove(listener);
    }

    /** Вызывается только из Module.setEnabled — после того как фича переключилась. */
    static void dispatchStateChanged(Module module) {
        for (StateListener l : LISTENERS) {
            try {
                l.onStateChanged(module, module.isEnabled());
            } catch (Throwable t) {
                VesperaClient.LOGGER.error("State listener failed for '{}'", module.name(), t);
            }
        }
    }

    // ---------------------------------------------------------------- dispatch

    public static void tick(MinecraftClient mc) {
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try {
                m.onTick(mc);
            } catch (Throwable t) {
                VesperaClient.LOGGER.error("Module '{}' crashed in onTick — disabling", m.name(), t);
                m.setEnabled(false);
            }
        }
    }

    public static boolean hasEnabledHud() {
        for (Module m : MODULES) if (m instanceof HudModule && m.isEnabled()) return true;
        return false;
    }

    public static void renderHud(long vg, float w, float h, float dt) {
        if (hudOrder == null) renderOrder();
        for (HudModule hud : hudOrder) {
            if (!hud.isEnabled()) continue;
            boolean saved = false;
            try {
                if (hud.layoutable()) {
                    nvgSave(vg);
                    saved = true;
                    if (WorldDraw.applyLayout(vg, hud, w, h)) hud.renderHud(vg, w, h, dt);
                } else {
                    hud.renderHud(vg, w, h, dt);
                }
            } catch (Throwable t) {
                VesperaClient.LOGGER.error("Module '{}' crashed in renderHud — disabling", hud.name(), t);
                hud.setEnabled(false);
            } finally {
                if (saved) nvgRestore(vg);
            }
        }
    }
}
