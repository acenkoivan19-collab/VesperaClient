package dev.vespera.fx;

import dev.vespera.client.module.Module;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.fx.module.AuraModule;
import dev.vespera.fx.module.BlockEffectsModule;
import dev.vespera.fx.module.HitMarkerModule;
import dev.vespera.fx.module.ItemGlowModule;
import dev.vespera.fx.module.ProjectileTrailsModule;
import dev.vespera.fx.module.RenderQualityModule;
import dev.vespera.fx.module.ScreenEffectsModule;
import dev.vespera.fx.module.TotemPopModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vespera FX — дополнение к Vespera Client: новые визуалы как обычные модули клиента.
 *
 * Модули добавляются в общий ModuleManager клиента ДО того, как клиент загрузит конфиг
 * (это происходит в CLIENT_STARTED), поэтому они появляются в меню, сохраняют настройки
 * и рисуются его NanoVG-рендером. Работает с любой версией Vespera Client с тем же API
 * модулей. Если модуль с таким именем уже есть в клиенте — наш не добавляется (без конфликтов).
 */
public final class VesperaFx implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("Vespera FX");

    @Override
    public void onInitializeClient() {
        int added = 0;
        added += add(new RenderQualityModule());
        added += add(new AuraModule());
        added += add(new ItemGlowModule());
        added += add(new ProjectileTrailsModule());
        added += add(new BlockEffectsModule());
        added += add(new ScreenEffectsModule());
        added += add(new TotemPopModule());
        added += add(new HitMarkerModule());
        LOGGER.info("[Vespera FX] добавлено модулей: {}", added);

        // Hit Marker: удар локального игрока
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player == MinecraftClient.getInstance().player) {
                try {
                    HitMarkerModule.onAttack(entity);
                } catch (Throwable t) {
                    LOGGER.warn("Hit Marker failed", t);
                }
            }
            return ActionResult.PASS;
        });

        // Block Effects: осколки при ломании и вспышка при установке (ошибка визуала не роняет игру)
        ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
            try {
                BlockEffectsModule.onBreak(world, pos, state);
            } catch (Throwable t) {
                LOGGER.warn("Block Effects (break) failed", t);
            }
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            try {
                if (world.isClient() && player == MinecraftClient.getInstance().player)
                    BlockEffectsModule.onUse(player, hand, hit);
            } catch (Throwable t) {
                LOGGER.warn("Block Effects (place) failed", t);
            }
            return ActionResult.PASS;
        });
    }

    private static int add(Module m) {
        if (ModuleManager.byName(m.name()) != null) {
            LOGGER.info("[Vespera FX] модуль '{}' уже есть в клиенте — пропускаем", m.name());
            return 0;
        }
        try {
            ModuleManager.register(m);
            return 1;
        } catch (Throwable t) {
            LOGGER.warn("[Vespera FX] не удалось добавить модуль '{}'", m.name(), t);
            return 0;
        }
    }
}
