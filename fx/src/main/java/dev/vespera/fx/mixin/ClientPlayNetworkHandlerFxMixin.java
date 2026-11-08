package dev.vespera.fx.mixin;

import dev.vespera.client.module.ModuleManager;
import dev.vespera.fx.module.BlockEffectsModule;
import dev.vespera.fx.module.TotemPopModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Пакеты сервера для визуалов (обработчики вызываются на главном потоке):
 *  - onEntityStatus: статус 35 — сработал тотем (Totem Pop);
 *  - onExplosion: взрыв — ударная волна (Block Effects).
 * Yarn 1.21.11: public void onEntityStatus(EntityStatusS2CPacket), public void onExplosion(ExplosionS2CPacket)
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerFxMixin {

    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void vesperaFx$status(EntityStatusS2CPacket packet, CallbackInfo ci) {
        TotemPopModule m = ModuleManager.get(TotemPopModule.class);
        if (m == null || !m.isEnabled()) return;
        try {
            TotemPopModule.onStatus(packet);
        } catch (Throwable ignored) {
            // визуал не должен ломать обработку пакетов
        }
    }

    @Inject(method = "onExplosion", at = @At("TAIL"))
    private void vesperaFx$explosion(ExplosionS2CPacket packet, CallbackInfo ci) {
        BlockEffectsModule m = ModuleManager.get(BlockEffectsModule.class);
        if (m == null || !m.isEnabled()) return;
        try {
            BlockEffectsModule.onExplosion(packet.center(), packet.radius());
        } catch (Throwable ignored) {
            // визуал не должен ломать обработку пакетов
        }
    }
}
