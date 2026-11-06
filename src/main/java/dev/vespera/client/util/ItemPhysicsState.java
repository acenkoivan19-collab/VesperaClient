package dev.vespera.client.util;

/** Лежит вне пакета миксинов: классы из mixin-пакета нельзя использовать напрямую. Доп. данные для Item Physics в ItemEntityRenderState (заполняет ItemEntityRendererMixin). */
public interface ItemPhysicsState {
    boolean vespera$onGround();

    boolean vespera$inWater();

    boolean vespera$isBlock();

    void vespera$set(boolean onGround, boolean inWater, boolean isBlock);
}
