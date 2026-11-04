package dev.vespera.client.module;

/** Слушатель смены состояния любого модуля. */
@FunctionalInterface
public interface StateListener {
    void onStateChanged(Module module, boolean enabled);
}
