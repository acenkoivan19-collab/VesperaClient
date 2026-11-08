package dev.vespera.client.mixin.feature;

import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.impl.TimeChangerModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Time Changer: сервер раз в секунду присылает своё время суток. Сразу после пакета
 * возвращаем выбранное время — чтобы небо не успело мигнуть серверным днём.
 * Yarn 1.21.11: public void onWorldTimeUpdate(WorldTimeUpdateS2CPacket)
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onWorldTimeUpdate", at = @At("TAIL"))
    private void vespera$time(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        if (!ModuleManager.get(TimeChangerModule.class).isEnabled()) return;
        TimeChangerModule.reapply();
    }
}
