package dev.vespera.client.mixin.feature;

import dev.vespera.client.module.impl.ItemPhysicsModule;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item Physics: своя отрисовка выброшенных предметов.
 * Yarn 1.21.11: updateRenderState(ItemEntity, ItemEntityRenderState, float),
 *               render(ItemEntityRenderState, MatrixStack, OrderedRenderCommandQueue, CameraRenderState).
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {

    @Shadow @Final private Random random;

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"))
    private void vespera$capture(ItemEntity e, ItemEntityRenderState s, float tickProgress, CallbackInfo ci) {
        if (!ItemPhysicsModule.active()) return;
        ((ItemPhysicsState) s).vespera$set(e.isOnGround(), e.isTouchingWater(), e.getStack().getItem() instanceof BlockItem);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void vespera$render(ItemEntityRenderState s, MatrixStack m, OrderedRenderCommandQueue q,
                                CameraRenderState cam, CallbackInfo ci) {
        if (!ItemPhysicsModule.active()) return;
        if (s.itemRenderState.isEmpty()) return;
        ci.cancel();
        ItemPhysicsModule.render(s, m, q, random);
    }
}
