package dev.vespera.client.module.impl;

import dev.vespera.client.mixin.feature.ItemPhysicsState;
import dev.vespera.client.module.Category;
import dev.vespera.client.module.Module;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.NumberSetting;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;

/**
 * Item Physics — выброшенные предметы ведут себя «по-настоящему»:
 *  - на земле ложатся плашмя (без парения и вращения на месте), каждый под своим углом;
 *  - в полёте кувыркаются, в воде медленно покачиваются;
 *  - блоки по желанию стоят ровно, как поставленные.
 * Чисто визуально (меняется только отрисовка), на сервере ничего не трогает.
 * Подключается через ItemEntityRendererMixin.
 */
public final class ItemPhysicsModule extends Module {

    private final ChoiceSetting mode = setting(new ChoiceSetting("mode", "Режим", 1, "Только лёжа", "Лёжа + кувырок")
            .hints("На земле лежат плашмя, в воздухе — как обычно", "На земле лежат плашмя, в полёте кувыркаются"));
    private final NumberSetting spin = setting(new NumberSetting("spin", "Скорость кувырка", 1f, 0.2f, 3f, 0.1f));
    private final BoolSetting blocksUpright = setting(new BoolSetting("blocks", "Блоки стоят ровно", true));
    private final BoolSetting randomYaw = setting(new BoolSetting("yaw", "Случайный поворот на земле", true));

    public ItemPhysicsModule() {
        super("Item Physics", "Выброшенные предметы лежат на земле и кувыркаются в полёте", Category.VISUALS);
        about("Выброшенные предметы ложатся на землю плашмя, без парения и вращения, в полёте "
                + "кувыркаются, в воде мягко покачиваются. Блоки могут стоять ровно. Меняется только картинка.");
    }

    public static boolean active() {
        ItemPhysicsModule m = ModuleManager.get(ItemPhysicsModule.class);
        return m != null && m.isEnabled();
    }

    /** Отрисовка вместо ванильной (вызывается из миксина, когда модуль включён). */
    public static void render(ItemEntityRenderState s, MatrixStack m, OrderedRenderCommandQueue q, Random random) {
        ItemPhysicsModule mod = ModuleManager.get(ItemPhysicsModule.class);
        ItemPhysicsState ps = (ItemPhysicsState) s;
        Box box = s.itemRenderState.getModelBoundingBox();
        float yaw = mod.randomYaw.on() ? (s.uniqueOffset * 57.2958f) % 360f : 0f;

        m.push();
        if (ps.vespera$onGround()) {
            if (ps.vespera$isBlock() && mod.blocksUpright.on()) {
                // блок стоит на земле ровно, как поставленный
                m.translate(0f, (float) -box.minY, 0f);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
            } else {
                // лежит плашмя: поворачиваем «лицом вверх» и приподнимаем на половину толщины
                float half = (float) Math.max(0.015, (box.maxZ - box.minZ) / 2.0);
                m.translate(0f, half + 0.005f, 0f);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
            }
        } else {
            float lift = (float) (-box.minY + 0.0625);
            m.translate(0f, lift, 0f);
            float t = s.age + s.uniqueOffset * 20f;
            if (ps.vespera$inWater()) {
                // в воде — медленное покачивание
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw + t * 0.8f));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.sin(t * 0.05) * 18f));
            } else if (mod.mode.index() == 1) {
                // в полёте — кувырок
                float sp = mod.spin.get();
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t * 9f * sp));
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(t * 5f * sp));
            } else {
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
            }
        }
        ItemEntityRenderer.render(m, q, s.light, s, random, box);
        m.pop();
    }
}
