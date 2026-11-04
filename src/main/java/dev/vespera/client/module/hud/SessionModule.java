package dev.vespera.client.module.hud;

import dev.vespera.client.render.Icons;
import dev.vespera.client.util.SessionTracker;

import java.util.List;

/** Время текущей сессии (свой счётчик от входа в мир — SessionTracker). */
public final class SessionModule extends InfoHudModule {

    public SessionModule() {
        super("Session", "Время игры с входа в мир", Icons.Glyph.TIMER);
        about("Сколько ты уже играешь с момента входа в мир или на сервер.");
    }

    @Override
    public float defaultY() {
        return 0.37f;
    }

    @Override
    protected void collect(List<Row> out) {
        out.add(new Row("СЕССИЯ", SessionTracker.format(SessionTracker.millis())));
    }
}
