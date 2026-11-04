package dev.vespera.client.module;

import dev.vespera.client.VesperaClient;
import dev.vespera.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Базовый модуль.
 *
 * Состояние меняется ТОЛЬКО через {@link #setEnabled(boolean)} / {@link #toggle()}.
 * Это единственная точка входа: GUI, бинды и конфиг вызывают её, а она
 *  1) вызывает onEnable()/onDisable() — здесь фича включает/выключает себя;
 *  2) оповещает ModuleManager → слушатели (автосохранение конфига, уведомления).
 *
 * Сама фича должна работать только пока модуль включён:
 *  - логика каждого тика → переопредели {@link #onTick(MinecraftClient)}
 *    (ModuleManager зовёт его только у включённых модулей);
 *  - отрисовка HUD → наследуйся от {@link HudModule}
 *    (рисуется только у включённых модулей);
 *  - в миксинах → проверяй состояние явно:
 *    {@code if (!ModuleManager.get(MyModule.class).isEnabled()) return;}
 */
public class Module {

    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled;
    private final List<Setting<?>> settings = new ArrayList<>();
    private String about;

    public Module(String name, String description, Category category) {
        if (category == Category.ALL) {
            throw new IllegalArgumentException("Category.ALL is a filter, not a module category");
        }
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    /** Вызывать на клиентском потоке (GUI, тики и бинды уже на нём). */
    public final void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        try {
            if (value) onEnable(); else onDisable();
        } catch (Throwable t) {
            VesperaClient.LOGGER.error("Module '{}' failed to {}", name, value ? "enable" : "disable", t);
            if (value) {
                enabled = false; // не оставляем модуль «включённым» с упавшей фичей
                return;
            }
        }
        ModuleManager.dispatchStateChanged(this);
    }

    /** Фича включается: подписки, подмена настроек, кэш и т.п. */
    protected void onEnable() {}

    /** Фича выключается: откатить ВСЁ, что сделал onEnable. */
    protected void onDisable() {}

    /** Каждый клиентский тик (20/сек), только пока модуль включён. */
    public void onTick(MinecraftClient mc) {}

    /** Зарегистрировать настройку (вызывать в конструкторе модуля). Появится в ПКМ-панели. */
    protected final <S extends Setting<?>> S setting(S s) {
        settings.add(s);
        return s;
    }

    /** Полное описание для ПКМ-панели (1–2 предложения). Вызывать в конструкторе. */
    protected final void about(String text) {
        this.about = text;
    }

    /** Полное описание; если не задано — короткое без пометки [Дубль TerraHUD: …]. */
    public String about() {
        if (about != null) return about;
        String d = description;
        if (d.startsWith("[") && d.contains("] ")) d = d.substring(d.indexOf("] ") + 2);
        return d;
    }

    /** Есть ли у модуля живое превью в ПКМ-панели (прицел и т.п.). */
    public boolean hasPreview() {
        return false;
    }

    /** Нарисовать превью в прямоугольнике панели (NanoVG, координаты панели). */
    public void renderPreview(long vg, float x, float y, float w, float h) {}

    /** Все настройки модуля в порядке показа в ПКМ-панели. */
    public List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    /** Внутреннее: добавить настройки в начало списка (общие настройки HudModule). */
    final void prependSettings(List<Setting<?>> common) {
        settings.addAll(0, common);
    }

    public String name() { return name; }
    public String description() { return description; }
    public Category category() { return category; }
    public boolean isEnabled() { return enabled; }
}
