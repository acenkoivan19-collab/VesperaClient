package dev.vespera.client.render;

import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.module.impl.RenderQualityModule;

/**
 * Качество визуалов + защита FPS.
 *
 * Уровень — дробное число 0..3 (0 низкое, 1 среднее, 2 высокое, 3 ультра). Эффекты берут из него
 * множитель количества частиц/сегментов. В режиме «Авто» уровень плавно опускается, когда FPS
 * ниже цели, и поднимается обратно, когда запас есть, — эффекты сами уступают игре.
 * Если модуль «Render Quality» выключен — работает «Авто» с целью 60 FPS.
 */
public final class Quality {

    private static float autoLevel = 2f;
    private static float fpsAvg = 60f;
    private static int frame;

    private Quality() {}

    /** Раз в кадр NanoVG (до рендера модулей). dt — секунды. */
    public static void frame(float dt) {
        frame++;
        if (dt <= 0f) return;
        float fps = 1f / dt;
        fpsAvg += (fps - fpsAvg) * Math.min(1f, dt * 2f); // сглаживание ~0.5 с
        float target = target();
        if (fpsAvg < target * 0.92f) autoLevel -= dt * 0.7f;        // быстро уступаем
        else if (fpsAvg > target * 1.3f) autoLevel += dt * 0.25f;   // медленно возвращаем
        autoLevel = Math.max(0f, Math.min(3f, autoLevel));
    }

    /** Номер кадра — для пересчётов «раз в N кадров». */
    public static int frameNo() {
        return frame;
    }

    private static RenderQualityModule cfg() {
        RenderQualityModule m = ModuleManager.get(RenderQualityModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    private static float target() {
        RenderQualityModule m = cfg();
        return m == null ? 60f : m.targetFps();
    }

    /** Текущий уровень 0..3 (дробный). */
    public static float level() {
        RenderQualityModule m = cfg();
        int mode = m == null ? 0 : m.mode();
        return mode == 0 ? autoLevel : mode - 1;
    }

    /** Множитель количества: 0.35 (низкое) … 1 (высокое) … 1.35 (ультра). */
    public static float scale() {
        float l = level();
        if (l <= 1f) return 0.35f + 0.25f * l;          // 0.35 → 0.6
        if (l <= 2f) return 0.6f + 0.4f * (l - 1f);      // 0.6 → 1
        return 1f + 0.35f * (l - 2f);                    // 1 → 1.35
    }

    /** Количество с учётом качества (минимум 1). */
    public static int count(int base) {
        return Math.max(1, Math.round(base * scale()));
    }

    /** Сколько соседних сегментов линии склеивать в один штрих (меньше вызовов отрисовки). */
    public static int chunk() {
        float l = level();
        if (l >= 2.6f) return 1;
        if (l >= 1.6f) return 2;
        if (l >= 0.7f) return 3;
        return 4; // больше 4 — кольца становятся заметно угловатыми
    }

    /** Дополнительные проходы свечения (ореолы) — только на среднем и выше. */
    public static boolean glow() {
        RenderQualityModule m = cfg();
        if (m != null && !m.glowOn()) return false;
        return level() >= 0.7f;
    }

    /** Прятать эффекты за блоками (лучи от камеры). */
    public static boolean occlusion() {
        RenderQualityModule m = cfg();
        return m == null || m.occlusionOn();
    }

    /** Сколько лучей видимости можно пустить за кадр (на все модули вместе). */
    public static int rayBudget() {
        return Math.round(60 + 80 * level());
    }

    /** Сглаженный FPS (для подписи в настройках). */
    public static int fps() {
        return Math.round(fpsAvg);
    }
}
