package dev.vespera.client.update;

import dev.vespera.client.VesperaClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Автообновление мода из GitHub Releases (тег dev-build).
 *
 * Как работает:
 *  1. При запуске в фоне читаем build.txt из релиза (это SHA коммита, из которого собран jar)
 *     и сравниваем с build.txt внутри нашего jar.
 *  2. Если отличается — скачиваем новый vespera-client.jar рядом с текущим под именем
 *     «<имя>.jar.new». Fabric грузит только *.jar, так что .new игру не ломает.
 *  3. При выходе из игры подменяем старый jar новым. В Windows открытый jar заблокирован,
 *     поэтому подмену делает отдельный cmd, который ждёт пару секунд, пока игра закроется.
 *     Если подмена не удалась — старый jar остаётся рабочим, а .new будет перекачан.
 *
 * Локальные сборки (build.txt = «local») не обновляются, чтобы не затереть разработку.
 * Отключить: создать файл config/vespera-noupdate.
 */
public final class Updater {

    private static final String BASE = "https://github.com/acenkoivan19-collab/VesperaClient/releases/download/dev-build/";
    private static final String JAR_URL = BASE + "vespera-client.jar";
    private static final String BUILD_URL = BASE + "build.txt";

    public enum State { IDLE, CHECKING, UP_TO_DATE, DOWNLOADING, READY, FAILED, DISABLED }

    private static volatile State state = State.IDLE;
    private static Path ownJar, pendingJar;

    private Updater() {}

    public static State state() {
        return state;
    }

    /** Обновление скачано и будет применено при выходе из игры. */
    public static boolean ready() {
        return state == State.READY;
    }

    public static void start() {
        try {
            ownJar = findOwnJar();
            if (ownJar == null) { state = State.DISABLED; return; } // запуск из IDE
            pendingJar = ownJar.resolveSibling(ownJar.getFileName() + ".new");
            if (Files.exists(FabricLoader.getInstance().getConfigDir().resolve("vespera-noupdate"))) {
                state = State.DISABLED;
                return;
            }
            String local = localBuild();
            if ("local".equals(local)) { state = State.DISABLED; return; }
        } catch (Throwable t) {
            VesperaClient.LOGGER.warn("[Vespera] автообновление недоступно: {}", t.toString());
            state = State.DISABLED;
            return;
        }
        Thread th = new Thread(Updater::run, "Vespera-Updater");
        th.setDaemon(true);
        th.start();
    }

    private static void run() {
        state = State.CHECKING;
        try {
            HttpClient http = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            String remote = http.send(get(BUILD_URL), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .body().trim();
            String local = localBuild();
            if (remote.isEmpty() || remote.length() > 64 || remote.equals(local)) {
                state = State.UP_TO_DATE;
                Files.deleteIfExists(pendingJar); // остаток прошлой неудачной подмены
                return;
            }
            state = State.DOWNLOADING;
            VesperaClient.LOGGER.info("[Vespera] новая версия {} (у нас {}), качаю", remote, local);
            Path tmp = ownJar.resolveSibling(ownJar.getFileName() + ".part");
            HttpResponse<InputStream> r = http.send(get(JAR_URL), HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
            try (InputStream in = r.body()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(tmp) < 100_000 || !isZip(tmp)) {
                Files.deleteIfExists(tmp);
                throw new IllegalStateException("битый файл");
            }
            Files.move(tmp, pendingJar, StandardCopyOption.REPLACE_EXISTING);
            state = State.READY;
            VesperaClient.LOGGER.info("[Vespera] обновление скачано, применится после выхода из игры");
        } catch (Throwable t) {
            state = State.FAILED;
            VesperaClient.LOGGER.warn("[Vespera] проверка обновления не удалась: {}", t.toString());
        }
    }

    /** Вызывается при выходе из игры: подменить старый jar новым. */
    public static void applyOnExit() {
        if (state != State.READY || pendingJar == null || !Files.exists(pendingJar)) return;
        try {
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            if (windows) {
                // ждём ~4 с, пока JVM отпустит jar, затем move поверх старого
                String cmd = "ping -n 5 127.0.0.1 >nul & move /y \"" + pendingJar + "\" \"" + ownJar + "\" >nul";
                new ProcessBuilder("cmd.exe", "/c", cmd).start();
            } else {
                Files.move(pendingJar, ownJar, StandardCopyOption.REPLACE_EXISTING);
            }
            VesperaClient.LOGGER.info("[Vespera] обновление будет установлено: {}", ownJar);
        } catch (Throwable t) {
            VesperaClient.LOGGER.warn("[Vespera] не удалось установить обновление: {}", t.toString());
        }
    }

    // ------------------------------------------------------------------ helpers

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "Vespera-Client-Updater")
                .GET().build();
    }

    private static String localBuild() {
        try (InputStream in = Updater.class.getResourceAsStream("/assets/vespera/build.txt")) {
            return in == null ? "local" : new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "local";
        }
    }

    private static Path findOwnJar() {
        Optional<ModContainer> mc = FabricLoader.getInstance().getModContainer(VesperaClient.MOD_ID);
        if (mc.isEmpty()) return null;
        List<Path> paths = mc.get().getOrigin().getPaths();
        if (paths.isEmpty()) return null;
        Path p = paths.get(0).toAbsolutePath();
        return Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar") ? p : null;
    }

    private static boolean isZip(Path p) throws Exception {
        try (InputStream in = Files.newInputStream(p)) {
            byte[] h = in.readNBytes(2);
            return h.length == 2 && h[0] == 'P' && h[1] == 'K';
        }
    }
}
