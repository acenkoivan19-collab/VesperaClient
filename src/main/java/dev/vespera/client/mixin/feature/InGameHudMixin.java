package dev.vespera.client.mixin.feature;

import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.impl.CrosshairModule;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Пока включён Crosshair — ванильный прицел не рисуется.
 * Yarn 1.21.11: private void renderCrosshair(DrawContext, RenderTickCounter)
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void vespera$hideVanillaCrosshair(CallbackInfo ci) {
        if (ModuleManager.get(CrosshairModule.class).isEnabled()) ci.cancel();
    }
}
