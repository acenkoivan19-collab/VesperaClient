package dev.vespera.client.mixin.feature;

import com.llamalad7.mixinextras.sugar.Local;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.impl.HandViewModule;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hand View: сдвиг и масштаб рук от первого лица.
 * Yarn 1.21.11: renderFirstPersonItem(AbstractClientPlayerEntity, float, float, Hand, float,
 *               ItemStack, float, MatrixStack, OrderedRenderCommandQueue, int)
 * MatrixStack и Hand берём через @Local(argsOnly) — не зависит от остальных параметров.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Unique
    private boolean vespera$pushed;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void vespera$handViewPush(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices,
                                      @Local(argsOnly = true) Hand hand) {
        HandViewModule m = ModuleManager.get(HandViewModule.class);
        vespera$pushed = m.isEnabled() && (!m.mainHandOnly() || hand == Hand.MAIN_HAND);
        if (!vespera$pushed) return;
        matrices.push();
        // для левой руки сдвиг вбок зеркалим, чтобы руки расходились симметрично
        float side = hand == Hand.MAIN_HAND ? 1f : -1f;
        matrices.translate(m.offsetX() * side, m.offsetY(), m.offsetZ());
        matrices.scale(m.scale(), m.scale(), m.scale());
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void vespera$handViewPop(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices) {
        if (vespera$pushed) {
            matrices.pop();
            vespera$pushed = false;
        }
    }
}
