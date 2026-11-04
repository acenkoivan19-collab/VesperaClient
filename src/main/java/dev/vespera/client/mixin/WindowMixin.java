package dev.vespera.client.mixin;

import dev.vespera.client.render.NvgRenderer;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Рисуем меню в самый последний момент кадра — прямо перед swap.
 *
 * Почему здесь, а не в Screen#render:
 *  - с 1.21.6 GUI Minecraft рендерится отложенно (GuiRenderer в конце кадра),
 *    поэтому всё, что мы нарисовали бы в Screen#render, перекрылось бы HUD'ом;
 *  - к этому моменту main framebuffer уже скопирован в дефолтный (FBO 0),
 *    у которого есть stencil-буфер, нужный NanoVG.
 */
@Mixin(Window.class)
public abstract class WindowMixin {

    @Inject(method = "swapBuffers", at = @At("HEAD"))
    private void vespera$renderNanoVG(CallbackInfo ci) {
        NvgRenderer.onFrameEnd();
    }
}
