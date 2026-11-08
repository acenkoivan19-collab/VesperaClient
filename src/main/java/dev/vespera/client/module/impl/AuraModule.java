package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fx;
import dev.vespera.client.render.Projection;
import dev.vespera.client.render.Quality;
import dev.vespera.client.render.WorldDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Аура вокруг твоего персонажа: пламя, неоновые кольца, вихрь или энергетический щит.
 * Видно от 3-го лица (F5). Части ауры за спиной персонажа приглушены — так она «обнимает»
 * модель, а не лежит плоской картинкой поверх неё.
 */
public final class AuraModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Вид", 0, "Пламя", "Кольца", "Вихрь", "Щит")
            .hints("Языки пламени поднимаются от ног", "Три наклонённых неоновых кольца вращаются вокруг тела",
                    "Потоки ветра закручиваются спиралью вверх", "Прозрачная энергетическая сфера с ярким краем"));
    private final ChoiceSetting palette = setting(new ChoiceSetting("palette", "Палитра", 1, "Один цвет", "Градиент", "Радуга")
            .hints("Цвет 1", "Переход от цвета 1 к цвету 2", "Переливается всеми цветами"));
    private final ColorSetting color = setting(new ColorSetting("color", "Цвет 1", 0));
    private final ColorSetting color2 = setting(new ColorSetting("color2", "Цвет 2", 0xFFB45CFF));
    private final NumberSetting size = setting(new NumberSetting("size", "Размер", 1f, 0.6f, 1.6f, 0.05f));
    private final NumberSetting bright = setting(new NumberSetting("bright", "Яркость", 0.9f, 0.2f, 1f, 0.05f));
    private final BoolSetting firstPerson = setting(new BoolSetting("fp", "Показывать от 1-го лица", false));

    private final float[] p = new float[3], q = new float[3], c0 = new float[3];
    private final Fx.Dots dots = new Fx.Dots();
    private final Fx.Lines lines = new Fx.Lines();
    private float show; // плавное появление/скрытие

    public AuraModule() {
        super("Aura", "Пламя, кольца, вихрь или щит вокруг тебя", Category.VISUALS);
        about("Вокруг твоего персонажа появляется аура: пламя от ног, вращающиеся неоновые кольца, "
                + "закрученный вихрь или энергетический щит. Цвета и размер настраиваются. "
                + "Лучше всего видно от 3-го лица (F5).");
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    private int col(float u, double time) {
        u = Math.round(u * 16f) / 16f; // 16 ступенек цвета — так отрезки рисуются пачками
        return switch (palette.index()) {
            case 1 -> Draw.lerpColor(color.argb(), color2.argb(), Math.max(0f, Math.min(1f, u)));
            case 2 -> Draw.hue((float) (u * 0.5 + time * 0.15));
            default -> color.argb();
        };
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity pl = mc.player;
        if (pl == null || !Projection.isReady()) return;
        boolean fp = mc.options.getPerspective().isFirstPerson();
        boolean want = !(fp && !firstPerson.on()) && !pl.isInvisible() && !pl.isSpectator();
        show += ((want ? 1f : 0f) - show) * Math.min(1f, dt * 6f);
        if (show <= 0.02f) return;
        Vec3d pos = pl.getLerpedPos(Projection.tickProgress(mc));
        double hgt = pl.getHeight();
        if (!Projection.project(pos.x, pos.y + hgt * 0.5, pos.z, c0)) return;
        double time = System.nanoTime() / 1e9;
        float k = bright.get() * show;
        double s = size.get();
        boolean glow = Quality.glow();
        if (glow) Fx.additive(vg);
        try {
            switch (style.index()) {
                case 1 -> rings(vg, pos, hgt, s, time, k, glow);
                case 2 -> vortex(pos, hgt, s, time, k, glow);
                case 3 -> shield(vg, pos, hgt, s, time, k, glow);
                default -> flames(vg, pos, s, hgt, time, k, glow);
            }
            lines.flush(vg);
            dots.flush(vg);
        } finally {
            if (glow) Fx.normal(vg);
        }
    }

    /** Множитель «за спиной»: точка дальше центра модели — приглушаем (её закрывает тело). */
    private float behind(float depth) {
        return depth > c0[2] + 0.05f ? 0.35f : 1f;
    }

    // ------------------------------------------------------------ пламя

    private void flames(long vg, Vec3d pos, double s, double hgt, double time, float k, boolean glow) {
        double r = 0.45 * s;
        // мягкое свечение на земле
        WorldDraw.disc(vg, pos.x, pos.y + 0.02, pos.z, r * 1.5, 24, col(0f, time), 0.16f * k);
        int n = Quality.count(70);
        for (int i = 0; i < n; i++) {
            double life = 0.7 + 0.5 * Fx.rnd(i * 1.3);
            double cyc = time / life + Fx.rnd(i * 3.7);
            long id = (long) Math.floor(cyc);
            float t = (float) (cyc - id);                                  // 0 рождение → 1 исчезла
            double ang = Fx.rnd(i * 5.1 + id * 0.37) * Math.PI * 2;
            double rr = r * (0.55 + 0.45 * Fx.rnd(i * 2.9 + id)) * (1 - 0.6 * t);
            double sway = Math.sin(time * 5 + i) * 0.04 * t;
            double x = pos.x + Math.cos(ang) * rr + sway, z = pos.z + Math.sin(ang) * rr;
            double y = pos.y + 0.05 + t * hgt * (0.5 + 0.35 * Fx.rnd(i * 7.7)) * s;
            if (!Projection.project(x, y, z, p)) continue;
            float px = Projection.pixelsPerBlock(p[2]);
            float sz = Math.max(0.8f, px * 0.075f * (float) s * (1f - t * 0.75f));
            float a = k * (float) Math.sin(Math.PI * Math.min(1f, t * 1.15f)) * behind(p[2]);
            int c = col(t, time);
            if (glow) dots.add(p[0], p[1], sz * 2.6f, c, 0.16f * a);
            dots.add(p[0], p[1], sz, Draw.lerpColor(c, 0xFFFFFFFF, 0.35f * (1f - t)), 0.8f * a);
        }
    }

    // ------------------------------------------------------------ кольца

    private void rings(long vg, Vec3d pos, double hgt, double s, double time, float k, boolean glow) {
        int seg = Math.max(24, Quality.count(64));
        for (int r = 0; r < 3; r++) {
            double rad = (0.55 + 0.08 * r) * s;
            double cy = pos.y + hgt * (0.25 + 0.3 * r);
            double tilt = Math.toRadians(18 + 10 * r) * Math.sin(time * 0.6 + r * 2.1);
            double spin = time * (0.8 + 0.35 * r) * (r % 2 == 0 ? 1 : -1);
            double tiltDir = time * 0.4 + r * 2.0;
            float prevX = 0, prevY = 0, prevD = 0;
            boolean has = false;
            for (int i = 0; i <= seg; i++) {
                double ang = Math.PI * 2 * i / seg;
                // точка окружности, наклонённой на tilt вокруг оси tiltDir
                double lx = Math.cos(ang) * rad, lz = Math.sin(ang) * rad;
                double along = lx * Math.cos(tiltDir) + lz * Math.sin(tiltDir);
                double ly = along * Math.sin(tilt);
                if (!Projection.project(pos.x + lx, cy + ly, pos.z + lz, p)) { has = false; continue; }
                if (has) {
                    float hl = (float) (0.35 + 0.65 * Math.pow(0.5 + 0.5 * Math.cos(ang - spin), 3)); // бегущий блик
                    float a = k * hl * behind((p[2] + prevD) / 2f);
                    int c = col((float) i / seg, time + r);
                    float px = Projection.pixelsPerBlock(p[2]);
                    float wdt = Math.max(1f, px * 0.022f * (float) s);
                    if (glow) lines.add(prevX, prevY, p[0], p[1], wdt * 4f, c, 0.14f * a);
                    lines.add(prevX, prevY, p[0], p[1], wdt, Draw.lerpColor(c, 0xFFFFFFFF, 0.3f * hl), a);
                }
                prevX = p[0];
                prevY = p[1];
                prevD = p[2];
                has = true;
            }
        }
    }

    // ------------------------------------------------------------ вихрь

    private void vortex(Vec3d pos, double hgt, double s, double time, float k, boolean glow) {
        int streams = Quality.count(14);
        int steps = 7;
        for (int i = 0; i < streams; i++) {
            double life = 1.1 + 0.6 * Fx.rnd(i * 2.3);
            double cyc = time / life + Fx.rnd(i * 4.4);
            long id = (long) Math.floor(cyc);
            float t = (float) (cyc - id);
            double a0 = Fx.rnd(i * 9.1 + id * 0.53) * Math.PI * 2 + time * 2.4;
            double rad = (0.5 + 0.25 * Fx.rnd(i * 6.6 + id)) * s;
            float alpha = k * (float) Math.sin(Math.PI * t);
            float px0 = 0, py0 = 0;
            boolean has = false;
            for (int j = 0; j < steps; j++) {
                double u = t - j * 0.045;                    // хвост штриха — чуть «раньше» по времени
                if (u < 0) break;
                double ang = a0 + u * 5.5;
                double y = pos.y + 0.1 + u * hgt * 1.05 * s;
                double rr = rad * (1 - 0.35 * u);
                if (!Projection.project(pos.x + Math.cos(ang) * rr, y, pos.z + Math.sin(ang) * rr, p)) { has = false; continue; }
                if (has) {
                    float fa = alpha * (1f - j / (float) steps) * behind(p[2]);
                    int c = col((float) u, time);
                    float wdt = Math.max(1f, Projection.pixelsPerBlock(p[2]) * 0.02f * (float) s);
                    if (glow) lines.add(px0, py0, p[0], p[1], wdt * 3.5f, c, 0.15f * fa);
                    lines.add(px0, py0, p[0], p[1], wdt, c, 0.85f * fa);
                }
                px0 = p[0];
                py0 = p[1];
                has = true;
            }
        }
    }

    // ------------------------------------------------------------ щит

    private void shield(long vg, Vec3d pos, double hgt, double s, double time, float k, boolean glow) {
        double R = Math.max(0.75, hgt * 0.62) * s;
        double cy = pos.y + hgt * 0.5;
        if (!Projection.project(pos.x, cy, pos.z, p)) return;
        float pr = Projection.pixelsPerBlock(p[2]) * (float) R;
        if (pr < 4) return;
        int c1 = col(0f, time), c2 = col(1f, time);
        float pulse = (float) (0.85 + 0.15 * Math.sin(time * 2.2));
        // «френель»: середина почти прозрачная, к краю — яркая кромка
        nvgRadialGradient(vg, p[0], p[1], pr * 0.55f, pr, Draw.c1(c1, 0.02f * k), Draw.c2(c2, 0.32f * k * pulse), WorldDraw.paint());
        nvgBeginPath(vg);
        nvgCircle(vg, p[0], p[1], pr);
        nvgFillPaint(vg, WorldDraw.paint());
        nvgFill(vg);
        nvgBeginPath(vg);
        nvgCircle(vg, p[0], p[1], pr);
        nvgStrokeWidth(vg, Math.max(1f, pr * 0.02f));
        nvgStrokeColor(vg, Draw.c1(Draw.lerpColor(c2, 0xFFFFFFFF, 0.4f), 0.7f * k * pulse));
        nvgStroke(vg);
        // медленно вращающиеся меридианы — сфера читается объёмной
        int seg = Math.max(20, Quality.count(40));
        for (int m = 0; m < 3; m++) {
            double rot = time * 0.5 + m * Math.PI / 3;
            float prevX = 0, prevY = 0;
            boolean has = false;
            for (int i = 0; i <= seg; i++) {
                double th = Math.PI * 2 * i / seg;
                double x = Math.cos(th) * R * Math.cos(rot), z = Math.cos(th) * R * Math.sin(rot), y = Math.sin(th) * R;
                if (!Projection.project(pos.x + x, cy + y, pos.z + z, q)) { has = false; continue; }
                if (has) lines.add(prevX, prevY, q[0], q[1], Math.max(1f, pr * 0.008f), c1, 0.22f * k * behind(q[2]));
                prevX = q[0];
                prevY = q[1];
                has = true;
            }
        }
        // искры, бегущие по поверхности
        int n = Quality.count(18);
        for (int i = 0; i < n; i++) {
            double th = Fx.rnd(i * 3.3) * Math.PI * 2 + time * (0.4 + 0.5 * Fx.rnd(i));
            double ph = Math.acos(2 * Fx.rnd(i * 7.9) - 1);
            double x = Math.sin(ph) * Math.cos(th) * R, y = Math.cos(ph) * R, z = Math.sin(ph) * Math.sin(th) * R;
            if (!Projection.project(pos.x + x, cy + y, pos.z + z, q)) continue;
            float sz = Math.max(0.8f, Projection.pixelsPerBlock(q[2]) * 0.03f);
            float a = k * behind(q[2]) * (float) (0.5 + 0.5 * Math.sin(time * 4 + i));
            if (glow) dots.add(q[0], q[1], sz * 3f, c2, 0.2f * a);
            dots.add(q[0], q[1], sz, 0xFFFFFFFF, a);
        }
    }
}
