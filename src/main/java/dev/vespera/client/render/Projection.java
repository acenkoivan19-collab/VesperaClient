package dev.vespera.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/**
 * Проекция мировых координат в координаты HUD (3D → 2D) для эффектов,
 * которые рисуются NanoVG поверх кадра: кольца, след, шляпа, обводка блока…
 *
 * Камера берётся из Camera (позиция + yaw/pitch), FOV — тот, что реально
 * использовал GameRenderer в этом кадре (перехватывается GameRendererMixin,
 * поэтому учитываются спринт и Zoom). Покачивание камеры при ходьбе
 * (View Bobbing) не учитывается — при включённом bobbing эффекты чуть «плавают».
 */
public final class Projection {

    private static boolean ready;
    private static double camX, camY, camZ;
    private static double rX, rZ, uX, uY, uZ, fX, fY, fZ;
    private static double focal, aspect;
    private static float width, height;

    /** FOV последнего кадра мира, градусы; ≤ 0 — ещё не известен. */
    private static float capturedFov = -1f;

    private Projection() {}

    public static void captureFov(float fov) {
        capturedFov = fov;
    }

    /** Вызывается раз в кадр перед отрисовкой HUD. */
    public static void update(MinecraftClient mc, float hudW, float hudH) {
        Camera cam = mc.gameRenderer.getCamera();
        if (cam == null || !cam.isReady() || hudW <= 0 || hudH <= 0) {
            ready = false;
            return;
        }
        Vec3d p = cam.getCameraPos();
        camX = p.x;
        camY = p.y;
        camZ = p.z;

        double yaw = Math.toRadians(cam.getYaw());
        double pitch = Math.toRadians(cam.getPitch());
        double sy = Math.sin(yaw), cy = Math.cos(yaw);
        double sp = Math.sin(pitch), cp = Math.cos(pitch);

        // базис камеры Minecraft: yaw 0 = взгляд на +Z, pitch > 0 = вниз
        rX = -cy;            rZ = -sy;                         // вправо
        uX = -sy * sp;       uY = cp;        uZ = cy * sp;     // вверх
        fX = -sy * cp;       fY = -sp;       fZ = cy * cp;     // вперёд

        float fov = capturedFov > 0f ? capturedFov : mc.options.getFov().getValue();
        focal = 1.0 / Math.tan(Math.toRadians(fov) / 2.0);
        width = hudW;
        height = hudH;
        aspect = hudW / (double) hudH;
        ready = true;
    }

    public static boolean isReady() {
        return ready;
    }

    /**
     * @param out out[0], out[1] — точка на экране (HUD-координаты), out[2] — дистанция по оси взгляда
     * @return false, если точка позади камеры
     */
    public static boolean project(double x, double y, double z, float[] out) {
        if (!ready) return false;
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        double vz = dx * fX + dy * fY + dz * fZ;
        if (vz < 0.05) return false;
        double vx = dx * rX + dz * rZ;
        double vy = dx * uX + dy * uY + dz * uZ;
        out[0] = (float) ((1.0 + vx * focal / (aspect * vz)) * 0.5 * width);
        out[1] = (float) ((1.0 - vy * focal / vz) * 0.5 * height);
        out[2] = (float) vz;
        return true;
    }

    /** Сколько HUD-пикселей занимает 1 блок на глубине depth (для билбордов и толщины линий). */
    public static float pixelsPerBlock(float depth) {
        return depth <= 0.05f ? 0f : (float) (focal * height * 0.5 / depth);
    }

    /**
     * Как project(), но точку за кадром или позади камеры прижимает к краю экрана
     * (с отступом margin) по направлению на неё — для меток мира.
     * @return true, если точка реально в кадре
     */
    public static boolean projectClamped(double x, double y, double z, float margin, float[] out) {
        if (!ready) return false;
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        double vz = dx * fX + dy * fY + dz * fZ;
        double vx = dx * rX + dz * rZ;
        double vy = dx * uX + dy * uY + dz * uZ;
        if (vz > 0.05) {
            float sx = (float) ((1.0 + vx * focal / (aspect * vz)) * 0.5 * width);
            float sy = (float) ((1.0 - vy * focal / vz) * 0.5 * height);
            out[2] = (float) vz;
            if (sx >= margin && sx <= width - margin && sy >= margin && sy <= height - margin) {
                out[0] = sx;
                out[1] = sy;
                return true;
            }
        }
        // направление от центра экрана; для точек позади — то же «влево/вправо»
        double ex = vx, ey = -vy;
        if (Math.abs(ex) < 1e-6 && Math.abs(ey) < 1e-6) ey = 1;
        double hw = width / 2.0 - margin, hh = height / 2.0 - margin;
        double k = Math.min(hw / Math.max(1e-6, Math.abs(ex)), hh / Math.max(1e-6, Math.abs(ey)));
        out[0] = (float) (width / 2.0 + ex * k);
        out[1] = (float) (height / 2.0 + ey * k);
        out[2] = (float) Math.max(vz, 0.05);
        return false;
    }

    public static double distanceTo(double x, double y, double z) {
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Доля тика для плавной интерполяции позиций сущностей. */
    public static float tickProgress(MinecraftClient mc) {
        return mc.getRenderTickCounter().getTickProgress(true);
    }
}
