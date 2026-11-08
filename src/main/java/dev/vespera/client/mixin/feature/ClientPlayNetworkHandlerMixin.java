package dev.vespera.client.mixin.feature;

import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.impl.BlockEffectsModule;
import dev.vespera.client.module.impl.TimeChangerModule;
import dev.vespera.client.module.impl.TotemPopModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Пакеты сервера, на которые реагируют визуалы (все — только на главном потоке):
 *  - onWorldTimeUpdate: Time Changer сразу возвращает своё время, чтобы небо не мигало днём;
 *  - onEntityStatus: статус 35 — сработал тотем (Totem Pop);
 *  - onExplosion: взрыв — ударная волна (Block Effects).
 * Yarn 1.21.11: public void onWorldTimeUpdate(WorldTimeUpdateS2CPacket),
 *               public void onEntityStatus(EntityStatusS2CPacket), public void onExplosion(ExplosionS2CPacket)
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onWorldTimeUpdate", at = @At("TAIL"))
    private void vespera$time(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        if (!ModuleManager.get(TimeChangerModule.class).isEnabled()) return;
        TimeChangerModule.reapply();
    }

    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void vespera$status(EntityStatusS2CPacket packet, CallbackInfo ci) {
        if (!ModuleManager.get(TotemPopModule.class).isEnabled()) return;
        try {
            TotemPopModule.onStatus(packet);
        } catch (Throwable ignored) {
            // визуал не должен ломать обработку пакетов
        }
    }

    @Inject(method = "onExplosion", at = @At("TAIL"))
    private void vespera$explosion(ExplosionS2CPacket packet, CallbackInfo ci) {
        if (!ModuleManager.get(BlockEffectsModule.class).isEnabled()) return;
        try {
            BlockEffectsModule.onExplosion(packet.center(), packet.radius());
        } catch (Throwable ignored) {
            // визуал не должен ломать обработку пакетов
        }
    }
}
