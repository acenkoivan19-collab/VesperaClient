package dev.vespera.client.mixin.feature;

import dev.vespera.client.util.ItemPhysicsState;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Хранит для Item Physics: лежит ли предмет, в воде ли он, блок ли это. */
@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemPhysicsState {

    @Unique private boolean vespera$ground, vespera$water, vespera$block;

    @Override
    public boolean vespera$onGround() {
        return vespera$ground;
    }

    @Override
    public boolean vespera$inWater() {
        return vespera$water;
    }

    @Override
    public boolean vespera$isBlock() {
        return vespera$block;
    }

    @Override
    public void vespera$set(boolean onGround, boolean inWater, boolean isBlock) {
        vespera$ground = onGround;
        vespera$water = inWater;
        vespera$block = isBlock;
    }
}
