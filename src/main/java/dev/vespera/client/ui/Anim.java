package dev.vespera.client.ui;

/**
 * Lerp-анимация, не зависящая от FPS.
 * Каждый кадр: value = lerp(value, target, 1 - e^(-speed·dt)).
 * На 60 и на 1000 FPS анимация идёт с одинаковой скоростью.
 */
public final class Anim {

    public float value;
    public float target;
    private final float speed;

    public Anim(float initial, float speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
    }

    public void update(float dt) {
        if (value == target) return;
        float k = 1f - (float) Math.exp(-speed * dt);
        value += (target - value) * k;
        if (Math.abs(target - value) < 0.0005f) value = target;
    }

    public void snap(float v) {
        value = target = v;
    }

    public static float easeOutCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    public static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
