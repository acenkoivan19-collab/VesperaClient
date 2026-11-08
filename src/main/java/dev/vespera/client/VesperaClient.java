package dev.vespera.client;

import dev.vespera.client.module.ModuleConfig;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.impl.HitBurstModule;
import dev.vespera.client.module.impl.TargetHudModule;
import dev.vespera.client.module.impl.ZoomModule;
import dev.vespera.client.render.NvgRenderer;
import dev.vespera.client.ui.ClickGuiScreen;
import dev.vespera.client.ui.HudEditorScreen;
import dev.vespera.client.util.EntityScan;
import dev.vespera.client.util.MotionTracker;
import dev.vespera.client.util.SessionTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vespera Client — точка входа.
 *  - Right Shift открывает меню Vespera (единственное меню клиента, меню TerraHUD удалено);
 *  - C — зум (модуль Zoom);
 *  - H — Редактор HUD (свободная раскладка);
 *  - каждый тик: onTick() у включённых модулей + автосохранение конфига;
 *  - при выходе: сохранить конфиг, освободить NanoVG.
 */
public final class VesperaClient implements ClientModInitializer {

    public static final String MOD_ID = "vespera";
    public static final Logger LOGGER = LoggerFactory.getLogger("Vespera");

    // 1.21.9+: категории биндов — объекты, а не строки.
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

    private static KeyBinding openGui;
    private static KeyBinding openHudEditor;

    @Override
    public void onInitializeClient() {
        // автообновление из GitHub Releases (фоновый поток, игру не задерживает)
        dev.vespera.client.update.Updater.start();

        openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vespera.clickgui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                CATEGORY
        ));

        openHudEditor = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vespera.hudeditor",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                CATEGORY
        ));

        ZoomModule.setKey(KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vespera.zoom",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                CATEGORY
        )));

        // Target HUD: запоминаем, кого ударил локальный игрок
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player == MinecraftClient.getInstance().player) {
                TargetHudModule.onAttack(entity);
                HitBurstModule.onAttack(entity);
                dev.vespera.client.module.impl.TargetEspModule.onAttack(entity);
                dev.vespera.client.module.impl.KillEffectModule.onAttack(entity);
                dev.vespera.client.module.hud.ComboModule.onAttack();
                try {
                    dev.vespera.client.module.impl.HitMarkerModule.onAttack(entity);
                } catch (Throwable t) {
                    LOGGER.warn("Hit Marker failed", t);
                }
            }
            return ActionResult.PASS;
        });

        // Block Effects: осколки при ломании и вспышка при установке (только свой игрок, только клиент)
        // (ошибка внутри визуала не должна ронять игру — ловим всё)
        ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
            try {
                dev.vespera.client.module.impl.BlockEffectsModule.onBreak(world, pos, state);
            } catch (Throwable t) {
                LOGGER.warn("Block Effects (break) failed", t);
            }
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            try {
                if (world.isClient() && player == MinecraftClient.getInstance().player)
                    dev.vespera.client.module.impl.BlockEffectsModule.onUse(player, hand, hit);
            } catch (Throwable t) {
                LOGGER.warn("Block Effects (place) failed", t);
            }
            return ActionResult.PASS;
        });

        // Состояния модулей грузим, когда клиент полностью запущен: onEnable() некоторых
        // модулей (FPS Boost) трогают настройки игры, которых ещё нет в момент init.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> ModuleConfig.init());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGui.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ClickGuiScreen());
            }
            while (openHudEditor.wasPressed()) {
                if (client.currentScreen == null && client.world != null) client.setScreen(new HudEditorScreen(null));
            }
            SessionTracker.tick(client);  // время сессии (модуль Session)
            MotionTracker.tick(client);   // скорость/движение — для Swarm и Wings
            EntityScan.tick(client);      // сущности рядом + прямая видимость
            ModuleManager.tick(client);
            ModuleConfig.tick();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ModuleConfig.saveIfDirty();
            NvgRenderer.destroy();
            dev.vespera.client.update.Updater.applyOnExit(); // подменить jar новым после выхода
        });
    }
}
