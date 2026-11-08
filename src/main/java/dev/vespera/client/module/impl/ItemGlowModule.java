package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.Fx;
import dev.vespera.client.render.Occlusion;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.Quality;
import dev.vespera.client.render.WorldDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Подсветка выпавших предметов: мягкое свечение, световой столб или кольцо на земле.
 * Цвет — по редкости предмета (как цвет названия в игре). Только то, что видно напрямую.
 */
public final class ItemGlowModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Вид", 0, "Свечение", "Луч", "Кольцо")
            .hints("Мягкий светящийся ореол вокруг предмета", "Столб света вверх, видно издалека",
                    "Вращающееся кольцо на земле"));
    private final ChoiceSetting palette = setting(new ChoiceSetting("palette", "Цвет", 0, "По редкости", "Свой")
            .hints("Белый, жёлтый, голубой, фиолетовый — как название предмета", "Цвет ниже для всех"));
    private final ColorSetting color = setting(new ColorSetting("color", "Свой цвет", 0));
    private final NumberSetting range = setting(new NumberSetting("range", "Дальность (блоков)", 20f, 6f, 40f, 1f));
    private final BoolSetting labels = setting(new BoolSetting("labels", "Название и количество вблизи", false));

    private final List<ItemEntity> items = new ArrayList<>();
    private final float[] p = new float[3], q = new float[3];
    private final Fx.Dots dots = new Fx.Dots();
    private final Occlusion.Cache sight = new Occlusion.Cache(6);
    private int ticks;

    public ItemGlowModule() {
        super("Item Glow", "Свечение и лучи у выпавших предметов", Category.VISUALS);
        about("Выпавшие предметы мягко светятся цветом своей редкости — их легко заметить в траве "
                + "и в темноте. Можно включить столб света или кольцо на земле и подписи вблизи. "
                + "За стенами подсветка не видна.");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    @Override
    protected void onDisable() {
        items.clear();
        sight.clear();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { items.clear(); return; }
        if (ticks++ % 4 != 0) return; // список обновляем 5 раз в секунду
        items.clear();
        double r = range.get(), r2 = r * r;
        int max = Quality.count(48);
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemEntity ie) || ie.getStack().isEmpty()) continue;
            if (e.squaredDistanceTo(mc.player) > r2) continue;
            items.add(ie);
        }
        if (items.size() > max) {
            items.sort((a, b) -> Double.compare(a.squaredDistanceTo(mc.player), b.squaredDistanceTo(mc.player)));
            items.subList(max, items.size()).clear();
        }
        if (ticks % 80 == 0) sight.prune();
    }

    private int colorOf(ItemStack s) {
        if (palette.index() == 1) return color.argb();
        return switch (s.getRarity()) {
            case UNCOMMON -> 0xFFFFE15A;
            case RARE -> 0xFF5AF0FF;
            case EPIC -> 0xFFE070FF;
            default -> s.hasGlint() ? 0xFFC9A6FF : 0xFFE6EEFF;
        };
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Projection.isReady() || items.isEmpty()) return;
        float tp = Projection.tickProgress(mc);
        double time = System.nanoTime() / 1e9;
        int st = style.index();
        boolean glow = Quality.glow();
        double range = this.range.get();
        if (glow) Fx.additive(vg);
        try {
            for (ItemEntity ie : items) {
                if (ie.isRemoved()) continue;
                Vec3d pos = ie.getLerpedPos(tp);
                double d = Projection.distanceTo(pos.x, pos.y, pos.z);
                float far = (float) Math.max(0, Math.min(1, (range - d) / 4)); // гаснут у края дальности
                if (far <= 0.02f) continue;
                float vis = sight.get(ie.getId(), pos.x, pos.y + 0.25, pos.z, dt);
                float a = far * vis;
                if (a <= 0.02f) continue;
                int c = colorOf(ie.getStack());
                double ph = time * 2 + ie.getId() * 0.7;
                float pulse = (float) (0.8 + 0.2 * Math.sin(ph));
                switch (st) {
                    case 1 -> beam(vg, pos, c, a * pulse, glow);
                    case 2 -> {
                        double rr = 0.32 + 0.04 * Math.sin(ph);
                        WorldDraw.ring(vg, pos.x, pos.y + 0.03, pos.z, rr, Math.max(16, Quality.count(28)), 2f, c, a * 0.9f, glow);
                        orbit(pos, rr, time + ie.getId(), c, a);
                    }
                    default -> {
                        if (!Projection.project(pos.x, pos.y + 0.22, pos.z, p)) continue;
                        float px = Projection.pixelsPerBlock(p[2]);
                        if (glow) Fx.glow(vg, p[0], p[1], px * 0.55f, c, 0.35f * a * pulse);
                        else dots.add(p[0], p[1], px * 0.3f, c, 0.25f * a * pulse);
                        WorldDraw.disc(vg, pos.x, pos.y + 0.02, pos.z, 0.28, 18, c, 0.22f * a);
                    }
                }
            }
            dots.flush(vg);
        } finally {
            if (glow) Fx.normal(vg);
        }
        if (labels.on()) drawLabels(vg, mc, tp);
    }

    /** Световой столб: яркий у земли, растворяется вверху. */
    private void beam(long vg, Vec3d pos, int c, float a, boolean glow) {
        if (!Projection.project(pos.x, pos.y + 0.05, pos.z, p) || !Projection.project(pos.x, pos.y + 2.2, pos.z, q)) return;
        float px = Projection.pixelsPerBlock(p[2]);
        float wdt = Math.max(1.5f, px * 0.09f);
        if (glow) {
            nvgLinearGradient(vg, p[0], p[1], q[0], q[1], Draw.c1(c, 0.3f * a), Draw.c2(c, 0f), WorldDraw.paint());
            nvgBeginPath(vg);
            nvgMoveTo(vg, p[0], p[1]);
            nvgLineTo(vg, q[0], q[1]);
            nvgStrokeWidth(vg, wdt * 4f);
            nvgLineCap(vg, NVG_ROUND);
            nvgStrokePaint(vg, WorldDraw.paint());
            nvgStroke(vg);
        }
        nvgLinearGradient(vg, p[0], p[1], q[0], q[1], Draw.c1(Draw.lerpColor(c, 0xFFFFFFFF, 0.4f), 0.95f * a),
                Draw.c2(c, 0f), WorldDraw.paint());
        nvgBeginPath(vg);
        nvgMoveTo(vg, p[0], p[1]);
        nvgLineTo(vg, q[0], q[1]);
        nvgStrokeWidth(vg, wdt);
        nvgStrokePaint(vg, WorldDraw.paint());
        nvgStroke(vg);
        dots.add(p[0], p[1], wdt * 1.6f, c, 0.6f * a);
    }

    /** Две искры бегут по кольцу. */
    private void orbit(Vec3d pos, double rr, double time, int c, float a) {
        for (int i = 0; i < 2; i++) {
            double ang = time * 2.2 + Math.PI * i;
            if (!Projection.project(pos.x + Math.cos(ang) * rr, pos.y + 0.03, pos.z + Math.sin(ang) * rr, p)) continue;
            float s = Math.max(1f, Projection.pixelsPerBlock(p[2]) * 0.035f);
            dots.add(p[0], p[1], s * 3f, c, 0.2f * a);
            dots.add(p[0], p[1], s, 0xFFFFFFFF, a);
        }
    }

    /** Подписи «Название ×N» для предметов ближе 7 блоков. */
    private void drawLabels(long vg, MinecraftClient mc, float tp) {
        for (ItemEntity ie : items) {
            if (ie.isRemoved()) continue;
            Vec3d pos = ie.getLerpedPos(tp);
            double d = Projection.distanceTo(pos.x, pos.y, pos.z);
            if (d > 7) continue;
            float vis = sight.get(ie.getId(), pos.x, pos.y + 0.25, pos.z, 0f);
            float a = vis * (float) Math.min(1, (7 - d) / 2);
            if (a <= 0.05f || !Projection.project(pos.x, pos.y + 0.75, pos.z, p)) continue;
            ItemStack s = ie.getStack();
            String text = s.getName().getString() + (s.getCount() > 1 ? " ×" + s.getCount() : "");
            float size = Math.max(8f, Math.min(13f, Projection.pixelsPerBlock(p[2]) * 0.16f));
            float tw = Draw.textWidth(vg, text, Fonts.MEDIUM, size);
            Draw.rect(vg, p[0] - tw / 2 - 4, p[1] - size * 0.75f, tw + 8, size * 1.5f, size * 0.4f, 0xFF101014, 0.55f * a);
            Draw.text(vg, text, p[0], p[1], Fonts.MEDIUM, size, Draw.CENTER_MID, colorOf(s), a);
        }
    }
}
