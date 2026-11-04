package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.EntityGeom;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.WorldDraw;
import dev.vespera.client.util.EntityScan;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Хитбоксы — порт TerraHUD «Хитбоксы» (WorldRings, блок «Хитбоксы»).
 *
 * Рамка (или рамка + заливка) хитбокса и луч взгляда 2.5 блока у сущностей рядом
 * (24 блока). Игроки — зелёные, кристаллы — розовые, остальные — белые.
 *
 * НЕ ЧИТ: рисуется только для сущностей в прямой видимости (EntityScan, рейкаст
 * от глаз игрока). За блоком — не рисуется. Свой хитбокс — в виде от 3-го лица.
 */
public final class HitboxesModule extends HudModule {

    private final ChoiceSetting mode = setting(new ChoiceSetting("mode", "Вид", 0, "Рамка", "Рамка + заливка")
            .hints("Только светящиеся рёбра хитбокса", "Рёбра и полупрозрачные грани"));
    private final ChoiceSetting who = setting(new ChoiceSetting("who", "Кого показывать", 0, "Всех", "Только игроков")
            .hints("Мобы, игроки и кристаллы рядом (в прямой видимости)", "Только других игроков"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет мобов", 0xFFFFFFFF));
    private final ColorSetting playerColor = setting(new ColorSetting("playerColor", "Цвет игроков", 0xFF39FF14));
    private final BoolSetting eyes = setting(new BoolSetting("eyes", "Линия взгляда", true));
    private static final int CRYSTAL_COLOR = 0xFFE56BD6;

    public HitboxesModule() {
        super("Hitboxes", "[Дубль TerraHUD: Хитбоксы] Рамки сущностей и линия взгляда", Category.VISUALS);
        about("Показывает хитбоксы сущностей рядом и куда они смотрят. Только для тех, "
                + "кого ты видишь напрямую — за блоками рамки не рисуются.");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !Projection.isReady()) return;
        float tp = Projection.tickProgress(mc);

        List<Entity> all = new ArrayList<>();
        boolean onlyPlayers = who.index() == 1;
        for (LivingEntity e : EntityScan.living()) if (EntityScan.isVisible(e) && (!onlyPlayers || e instanceof PlayerEntity)) all.add(e);
        if (!onlyPlayers) for (Entity e : EntityScan.crystals()) if (EntityScan.isVisible(e)) all.add(e);
        if (!mc.options.getPerspective().isFirstPerson()) all.add(mc.player);

        for (Entity e : all) {
            if (e.isRemoved()) continue;
            int col = e instanceof PlayerEntity ? playerColor.argb()
                    : e instanceof EndCrystalEntity ? CRYSTAL_COLOR : color.argb();
            Box b = EntityGeom.box(e, tp, 0.0);
            WorldDraw.box(vg, b, col, 1f, col, mode.index() == 1 ? 45f / 255f : 0f);

            if (eyes.on() && e instanceof LivingEntity le) {
                Vec3d p = le.getLerpedPos(tp);
                double ey = p.y + le.getStandingEyeHeight();
                Vec3d look = le.getRotationVec(tp);
                WorldDraw.beam(vg, p.x, ey, p.z, p.x + look.x * 2.5, ey + look.y * 2.5, p.z + look.z * 2.5,
                        0.02, col, 1f);
            }
        }
    }
}
