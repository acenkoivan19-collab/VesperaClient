package dev.vespera.fx.render;

import dev.vespera.client.render.Projection;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Видимость точек из камеры: эффекты NanoVG рисуются поверх кадра, поэтому «за стеной»
 * их надо прятать самим. Лучи дорогие — на все модули за кадр есть общий бюджет
 * ({@link Quality#rayBudget()}), а результат для каждой точки кэшируется и меняется плавно.
 */
public final class Occlusion {

    private static int budget;

    private Occlusion() {}

    /** Раз в кадр, до рендера модулей. */
    public static void newFrame() {
        budget = Quality.rayBudget();
    }

    /** Можно ли пустить ещё один луч в этом кадре (и списать его). */
    public static boolean spend() {
        if (budget <= 0) return false;
        budget--;
        return true;
    }

    /** Луч от камеры до точки: true — по пути нет блоков. Без учёта бюджета. */
    public static boolean clear(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return true;
        Vec3d cam = new Vec3d(Projection.camX(), Projection.camY(), Projection.camZ());
        Vec3d end = new Vec3d(x, y, z);
        if (cam.squaredDistanceTo(end) < 0.04) return true;
        HitResult hr = mc.world.raycast(new RaycastContext(cam, end, RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE, mc.player));
        return hr.getType() == HitResult.Type.MISS;
    }

    /**
     * Кэш видимости по ключу (id сущности, номер частицы…). {@link #get} возвращает 0..1:
     * плавно, чтобы эффект не мигал, когда луч то попадает в блок, то нет.
     */
    public static final class Cache {
        private static final class Entry {
            float vis, target;
            int checked, used;
        }

        private final Map<Long, Entry> map = new HashMap<>();
        private final int recheck;

        /** recheck — раз во сколько кадров перепроверять одну точку. */
        public Cache(int recheck) {
            this.recheck = Math.max(1, recheck);
        }

        public float get(long key, double x, double y, double z, float dt) {
            if (!Quality.occlusion()) return 1f;
            int f = Quality.frameNo();
            Entry e = map.get(key);
            if (e == null) {
                // первое появление — проверяем сразу (даже сверх бюджета), иначе вспыхнет за стеной
                e = new Entry();
                e.vis = e.target = Occlusion.clear(x, y, z) ? 1f : 0f;
                e.checked = e.used = f;
                map.put(key, e);
                return e.vis;
            }
            e.used = f;
            if (f - e.checked >= recheck && spend()) {
                e.target = Occlusion.clear(x, y, z) ? 1f : 0f;
                e.checked = f;
            }
            e.vis += (e.target - e.vis) * Math.min(1f, dt * 10f);
            return e.vis;
        }

        /** Убрать ключи, к которым давно не обращались. Вызывать иногда (раз в секунду). */
        public void prune() {
            int f = Quality.frameNo();
            for (Iterator<Entry> it = map.values().iterator(); it.hasNext(); ) {
                if (f - it.next().used > 120) it.remove();
            }
        }

        public void clear() {
            map.clear();
        }
    }
}
