package dev.vespera.fx.module;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.setting.ColorSetting;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.Fonts;
import dev.vespera.fx.render.Fx;
import dev.vespera.fx.render.Occlusion;
import dev.vespera.client.render.Projection;
import dev.vespera.fx.render.Quality;
import dev.vespera.client.render.WorldDraw;
import dev.vespera.client.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Эффект «сработал тотем»: золотисто-зелёный взрыв частиц, кольца и счётчик ×N над головой.
 * Срабатывает от пакета статуса 35 (ваниль сама показывает анимацию тотема) — только для
 * тех, кого игрок видит напрямую (правило «не читы»).
 */
public final class TotemPopModule extends HudModule {

    private final ChoiceSetting style = setting(new ChoiceSetting("style", "Вид", 0, "Взрыв и кольца", "Только частицы", "Только кольца")
            .hints("Частицы разлетаются, по земле и вокруг тела идут кольца", "Облако частиц", "Два кольца — по земле и вверх по телу"));
    private final ChoiceSetting palette = setting(new ChoiceSetting("palette", "Цвета", 0, "Тотем", "Тема", "Свой")
            .hints("Золотой и зелёный, как у тотема", "Цвет темы клиента", "Цвет ниже"));
    private final ColorSetting color = setting(new ColorSetting("color", "Свой цвет", 0xFFFFD34D));
    private final BoolSetting counter = setting(new BoolSetting("counter", "Счётчик тотемов ×N", true));
    private final BoolSetting self = setting(new BoolSetting("self", "Показывать и на себе", true));

    private static final long LIFE = 1600;
    /** {x, y, z, рост, время мс, id сущности, номер срабатывания, яркость кадра} */
    private static final ArrayDeque<double[]> POPS = new ArrayDeque<>();
    private static final Map<Integer, int[]> COUNT = new HashMap<>(); // id → {сколько, время последнего (сек)}

    private final float[] p = new float[3];
    private final Fx.Dots dots = new Fx.Dots();
    private final Occlusion.Cache sight = new Occlusion.Cache(6);

    public TotemPopModule() {
        super("Totem Pop", "Взрыв частиц и счётчик, когда у кого-то срабатывает тотем", Category.COMBAT);
        about("Когда у игрока срабатывает тотем бессмертия, на нём вспыхивает золотисто-зелёный "
                + "взрыв частиц и кольца, а над головой появляется счётчик ×N — сколько тотемов он уже "
                + "потратил. Только для тех, кого ты видишь.");
    }

    /** Из миксина ClientPlayNetworkHandler.onEntityStatus (главный поток). */
    public static void onStatus(EntityStatusS2CPacket packet) {
        TotemPopModule m = ModuleManager.get(TotemPopModule.class);
        if (m == null || !m.isEnabled() || packet.getStatus() != 35) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        Entity e = packet.getEntity(mc.world);
        if (e == null) return;
        boolean me = e == mc.player;
        if (me && !m.self.on()) return;
        if (!me && !mc.player.canSee(e)) return; // за стеной — не показываем
        int now = (int) (System.currentTimeMillis() / 1000);
        int[] c = COUNT.computeIfAbsent(e.getId(), k -> new int[2]);
        if (now - c[1] > 120) c[0] = 0; // через 2 минуты без тотемов счёт начинается заново
        c[0]++;
        c[1] = now;
        POPS.add(new double[]{e.getX(), e.getY(), e.getZ(), e.getHeight(), System.currentTimeMillis(), e.getId(), c[0], 0});
        while (POPS.size() > 12) POPS.pollFirst();
    }

    @Override
    protected void onDisable() {
        POPS.clear();
        COUNT.clear();
        sight.clear();
    }

    @Override
    public int layer() {
        return LAYER_WORLD;
    }

    private int[] colors() {
        return switch (palette.index()) {
            case 1 -> new int[]{Theme.ACCENT, Theme.ACCENT_2};
            case 2 -> new int[]{color.argb(), Draw.lerpColor(color.argb(), 0xFFFFFFFF, 0.4f)};
            default -> new int[]{0xFFFFD34D, 0xFF7CF06A};
        };
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        if (!Projection.isReady() || POPS.isEmpty()) return;
        long now = System.currentTimeMillis();
        int[] cl = colors();
        int st = style.index();
        boolean glow = Quality.glow();
        if (glow) Fx.additive(vg);
        try {
            for (Iterator<double[]> it = POPS.iterator(); it.hasNext(); ) {
                double[] e = it.next();
                long age = now - (long) e[4];
                if (age > LIFE) { it.remove(); continue; }
                if (age < 0) continue;
                float t = age / (float) LIFE;
                float vis = sight.get((long) e[5], e[0], e[1] + e[3] * 0.5, e[2], dt);
                e[7] = 0;
                if (vis <= 0.02f) continue;
                float fade = (1f - Fx.smooth(0.5f, 1f, t)) * vis;
                e[7] = fade;
                if (st != 2) burst(e, t, fade, cl, glow);
                if (st != 1) rings(vg, e, t, fade, cl, glow);
            }
            dots.flush(vg);
        } finally {
            if (glow) Fx.normal(vg);
        }
        // подписи — обычным смешиванием, чтобы текст не «выгорал» на светлом фоне
        if (counter.on()) {
            for (double[] e : POPS) {
                long age = now - (long) e[4];
                if (age >= 0 && age <= LIFE && e[7] > 0.02) label(vg, e, age, (float) e[7]);
            }
        }
    }

    /** Частицы: вылетают во все стороны и медленно всплывают (как у тотема). */
    private void burst(double[] e, float t, float fade, int[] cl, boolean glow) {
        int n = Quality.count(46);
        double sec = t * LIFE / 1000.0;
        double seed = e[4] * 0.001;
        for (int i = 0; i < n; i++) {
            double r1 = Fx.rnd(seed + i), r2 = Fx.rnd(seed + i * 1.37 + 5), r3 = Fx.rnd(seed + i * 2.11 + 9);
            double ang = r1 * Math.PI * 2;
            double spd = 1.6 + 2.6 * r2;                       // блоков/с в стороны, с торможением
            double out = spd * (1 - Math.exp(-sec * 3)) / 3;
            double up = (0.6 + 1.6 * r3) * sec - 0.25 * sec * sec;
            double x = e[0] + Math.cos(ang) * out, z = e[2] + Math.sin(ang) * out;
            double y = e[1] + e[3] * (0.3 + 0.5 * r3) + up;
            if (!Projection.project(x, y, z, p)) continue;
            float px = Projection.pixelsPerBlock(p[2]);
            float sz = Math.max(0.8f, px * (0.035f + 0.03f * (float) r2) * (1f - t * 0.6f));
            int c = (i & 1) == 0 ? cl[0] : cl[1];
            float tw = 0.7f + 0.3f * (float) Math.sin(sec * 14 + i);
            if (glow) dots.add(p[0], p[1], sz * 3.2f, c, 0.16f * fade * tw);
            dots.add(p[0], p[1], sz, Draw.lerpColor(c, 0xFFFFFFFF, 0.35f), fade * tw);
        }
    }

    /** Кольцо по земле расходится, второе поднимается от ног к голове. */
    private void rings(long vg, double[] e, float t, float fade, int[] cl, boolean glow) {
        int seg = Math.max(16, Quality.count(40));
        float e1 = Fx.easeOutCubic(Math.min(1f, t * 1.6f));
        double rGround = 0.3 + 1.4 * e1;
        WorldDraw.ring(vg, e[0], e[1] + 0.05, e[2], rGround, seg, 2.2f, cl[0], fade * 0.9f, glow);
        float rise = Fx.easeOutCubic(Math.min(1f, t * 1.3f));
        double y = e[1] + e[3] * (0.05 + 1.0 * rise);
        WorldDraw.ring(vg, e[0], y, e[2], 0.55 - 0.15 * rise, seg, 2f, cl[1], fade * (1f - rise * 0.6f), glow);
    }

    /** ×N над головой: всплывает и гаснет. */
    private void label(long vg, double[] e, long age, float fade) {
        if (!Projection.project(e[0], e[1] + e[3] + 0.45 + age / 3000.0, e[2], p)) return;
        float pop = Fx.easeOutCubic(Math.min(1f, age / 160f));
        float sz = Math.max(9f, Math.min(22f, Projection.pixelsPerBlock(p[2]) * 0.32f)) * (0.7f + 0.3f * pop);
        String s = "×" + (int) e[6];
        Draw.text(vg, s, p[0] + 1, p[1] + 1, Fonts.SEMIBOLD, sz, Draw.CENTER_MID, 0xFF000000, fade * 0.5f);
        Draw.text(vg, s, p[0], p[1], Fonts.SEMIBOLD, sz, Draw.CENTER_MID, 0xFFFFE27A, fade);
    }
}
