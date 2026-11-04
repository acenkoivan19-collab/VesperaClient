package dev.vespera.client.mixin.feature;

import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.impl.ZoomModule;
import dev.vespera.client.render.Projection;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FOV мира: Zoom уменьшает его (только пока модуль включён), а итоговое значение
 * всегда запоминается для Projection — иначе 3D-эффекты NanoVG разъедутся с картинкой.
 * Yarn 1.21.11: private float getFov(Camera camera, float tickProgress, boolean changingFov)
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void vespera$fov(Camera camera, float tickProgress, boolean changingFov,
                             CallbackInfoReturnable<Float> cir) {
        boolean zoom = ModuleManager.get(ZoomModule.class).isEnabled();
        if (!changingFov) return; // FOV руки от первого лица не трогаем
        float fov = cir.getReturnValueF();
        if (zoom) {
            fov = ZoomModule.applyZoom(fov);
            cir.setReturnValue(fov);
        }
        Projection.captureFov(fov); // инфраструктура, нужна всем 3D-модулям
    }
}
