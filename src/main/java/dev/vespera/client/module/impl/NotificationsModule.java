package dev.vespera.client.module.impl;

import dev.vespera.client.module.Category;
import dev.vespera.client.module.HudModule;
import dev.vespera.client.module.setting.BoolSetting;
import dev.vespera.client.module.setting.NumberSetting;
import dev.vespera.client.module.setting.ChoiceSetting;
import dev.vespera.client.module.Module;
import dev.vespera.client.module.ModuleManager;
import dev.vespera.client.render.Draw;
import dev.vespera.client.render.HudStyle;
import dev.vespera.client.render.Fonts;
import dev.vespera.client.render.Icons;
import dev.vespera.client.ui.Anim;
import dev.vespera.client.ui.Theme;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Тосты справа снизу при включении/выключении модулей.
 * Пример подписки на StateListener: менеджер оповещает о КАЖДОМ переключении.
 */
public final class NotificationsModule extends HudModule {

    private static final float W = 210f, H = 44f;
    private static final int MAX = 5;

    private static final class Toast {
        final Module module;
        final boolean enabled;
        final long bornNs = System.nanoTime();
        final Anim slide = new Anim(0f, 14f);
        final Anim y = new Anim(-1f, 16f);

        Toast(Module module, boolean enabled) {
            this.module = module;
            this.enabled = enabled;
        }
    }

    private final List<Toast> toasts = new ArrayList<>();
    private final ChoiceSetting look = setting(new ChoiceSetting("look", "Вид", 0, "Карточка", "Строка")
            .hints("Значок, название и статус в две строки", "Компактно: «Модуль — вкл/выкл» одной строкой"));
    private final NumberSetting duration = setting(new NumberSetting("duration", "Сколько висит (сек)", 2.4f, 1f, 6f, 0.2f));
    private final BoolSetting timerBar = setting(new BoolSetting("timerBar", "Полоса времени", true));

    public NotificationsModule() {
        super("Notifications", "Уведомления о включении модулей", Category.INTERFACE);
        about("Когда включаешь или выключаешь модуль, в углу появляется короткое уведомление.");
        ModuleManager.addListener((module, enabled) -> {
            if (!isEnabled()) return;
            toasts.removeIf(t -> t.module == module); // один тост на модуль
            toasts.add(new Toast(module, enabled));
            if (toasts.size() > MAX) toasts.remove(0);
        });
    }

    @Override
    protected void onDisable() {
        toasts.clear();
    }

    @Override
    public int layer() {
        return LAYER_OVERLAY;
    }

    @Override
    public boolean layoutable() {
        return true; // поверх всего, но двигается редактором
    }

    @Override
    public float defaultX() {
        return 0.80f;
    }

    @Override
    public float defaultY() {
        return 0.62f;
    }

    @Override
    public void renderHud(long vg, float w, float h, float dt) {
        boolean line = look.index() == 1;
        float th = line ? 28f : H, life = duration.get();
        float boxH = MAX * (th + 8f);
        size(W, boxH); // для Редактора HUD: стопка тостов растёт снизу вверх внутри рамки
        if (toasts.isEmpty() && isEditing()) toasts.add(new Toast(this, true)); // превью
        long now = System.nanoTime();
        int index = 0;
        for (int i = toasts.size() - 1; i >= 0; i--) { // новые снизу
            Toast t = toasts.get(i);
            float age = (now - t.bornNs) / 1e9f;
            t.slide.target = age < life || (isEditing() && t.module == this) ? 1f : 0f;
            step(t.slide, dt);
            float targetY = boxH - (index + 1) * (th + 8f);
            if (t.y.value < 0f) t.y.snap(targetY);
            t.y.target = targetY;
            step(t.y, dt);
            index++;

            float s = Anim.easeOutCubic(t.slide.value);
            float x = W * (1f - s); // въезд справа внутри рамки
            float y = t.y.value;
            int c = t.enabled ? accent() : 0xFFFF5C7A;

            HudStyle.panel(vg, this, x, y, W, th, line ? 8f : 11f);
            if (line) {
                Draw.circle(vg, x + 14f, y + th / 2f, 3.5f, c, s);
                String txt = t.module.name() + (t.enabled ? " — вкл" : " — выкл");
                Draw.text(vg, Draw.ellipsize(vg, txt, Fonts.MEDIUM, 12f, W - 34f), x + 26f, y + th / 2f,
                        Fonts.MEDIUM, 12f, Draw.LEFT_MID, Theme.TEXT, s);
            } else {
                float is = 28f, ix = x + 8f, iy = y + (th - is) / 2f;
                Draw.rect(vg, ix, iy, is, is, 8f, c, 0.15f * s);
                Icons.draw(vg, t.module.category(), ix + is / 2f, iy + is / 2f, 14f, c, s);
                float tx = ix + is + 10f, tw = x + W - 10f - tx;
                Draw.text(vg, Draw.ellipsize(vg, t.module.name(), Fonts.SEMIBOLD, 12.5f, tw), tx, y + 15f,
                        Fonts.SEMIBOLD, 12.5f, Draw.LEFT_MID, Theme.TEXT, s);
                Draw.text(vg, t.enabled ? "Включён" : "Выключен", tx, y + 30f,
                        Fonts.REGULAR, 11f, Draw.LEFT_MID, c, s);
            }
            if (timerBar.on()) {
                float left = Math.max(0f, 1f - age / life);
                Draw.rect(vg, x + 10f, y + th - 3f, (W - 20f) * left, 1.5f, 0.75f, c, 0.6f * s);
            }
        }
        for (Iterator<Toast> it = toasts.iterator(); it.hasNext(); ) {
            Toast t = it.next();
            if ((now - t.bornNs) / 1e9f > life && t.slide.value < 0.01f && !isEditing()) it.remove();
        }
    }
}
