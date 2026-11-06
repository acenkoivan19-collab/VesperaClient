package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.Module;
import dev.vespera.client.module.setting.BoolSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Auto Sprint — бег включается сам, когда идёшь вперёд (как зажатый Ctrl).
 * Только по честным правилам ванилы: вперёд, хватает еды, не крадёшься, не ешь/не натягиваешь лук,
 * не упёрся в стену, нет слепоты. Во все стороны НЕ бежит — это уже читерство.
 */
public final class AutoSprintModule extends Module {

    private final BoolSetting inWater = setting(new BoolSetting("water", "Плыть быстро в воде", true));
    private final BoolSetting keepOnUse = setting(new BoolSetting("use", "Не сбрасывать при еде/луке", false));

    public AutoSprintModule() {
        super("Auto Sprint", "Бег включается сам, когда идёшь вперёд", Category.UTILITY);
        about("Не нужно зажимать Ctrl: когда идёшь вперёд, персонаж сам переходит на бег. "
                + "Работает по правилам игры — только вперёд, при достаточной сытости и без слепоты.");
    }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null) return;
        if (!mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()) return;
        if (p.isSneaking() || p.horizontalCollision || p.hasStatusEffect(StatusEffects.BLINDNESS)) return;
        if (p.isUsingItem() && !keepOnUse.on()) return;
        if (p.isTouchingWater() && !p.isSubmergedInWater() && !inWater.on()) return;
        boolean fed = p.getHungerManager().getFoodLevel() > 6 || p.getAbilities().allowFlying;
        if (!fed) return;
        if (!p.isSprinting()) p.setSprinting(true);
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        // бег сам сбросится, когда отпустишь «вперёд»; принудительно не трогаем
        if (mc.player != null && !mc.options.sprintKey.isPressed() && !mc.options.forwardKey.isPressed())
            mc.player.setSprinting(false);
    }
}
