package dev.vespera.fx.mixin;

import dev.vespera.fx.render.Occlusion;
import dev.vespera.fx.render.Quality;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Начало каждого кадра: авто-качество по FPS и новый бюджет лучей видимости.
 * Это инфраструктура всех модулей Vespera FX, поэтому проверки «включён ли модуль» нет.
 * Yarn 1.21.11: public void render(RenderTickCounter, boolean)
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererFxMixin {

    @Unique
    private static long vesperaFx$lastNs = -1L;

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("HEAD"))
    private void vesperaFx$frame(CallbackInfo ci) {
        long now = System.nanoTime();
        float dt = vesperaFx$lastNs < 0 ? 0f : Math.min((now - vesperaFx$lastNs) / 1_000_000_000f, 0.1f);
        vesperaFx$lastNs = now;
        Quality.frame(dt);
        Occlusion.newFrame();
    }
}
