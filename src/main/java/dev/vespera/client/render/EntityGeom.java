package dev.vespera.client.render;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Интерполированная геометрия сущности для плавной отрисовки между тиками. */
public final class EntityGeom {

    private EntityGeom() {}

    /** Хитбокс сущности в интерполированной позиции. */
    public static Box box(Entity e, float tickProgress, double expand) {
        Vec3d p = e.getLerpedPos(tickProgress);
        double hw = e.getWidth() / 2.0 + expand;
        return new Box(p.x - hw, p.y - expand, p.z - hw, p.x + hw, p.y + e.getHeight() + expand, p.z + hw);
    }
}
