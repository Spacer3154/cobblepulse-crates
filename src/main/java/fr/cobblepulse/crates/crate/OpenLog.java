package fr.cobblepulse.crates.crate;

import fr.cobblepulse.crates.Crates;
import fr.cobblepulse.crates.config.Cfg;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Journal des ouvertures : config/cobblepulse_crates/journal/ouvertures-AAAA-MM.log (une ligne par ouverture). */
public final class OpenLog {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static ExecutorService io = newIo();

    private OpenLog() {}

    private static ExecutorService newIo() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CobblePulse-Crates-Journal");
            t.setDaemon(true);
            return t;
        });
    }

    public static void log(String player, String crate, String reward, String rarity, String detail) {
        if (!Cfg.general.journal) return;
        LocalDateTime now = LocalDateTime.now();
        String line = TS.format(now) + " | " + player + " | " + crate + " | " + reward + " | " + rarity + (detail == null || detail.isEmpty() ? "" : " | " + detail)
                + System.lineSeparator();
        Path f = Cfg.root().resolve("journal").resolve("ouvertures-" + MONTH.format(now) + ".log");
        if (io.isShutdown()) io = newIo();
        io.execute(() -> {
            try {
                Files.createDirectories(f.getParent());
                Files.writeString(f, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception e) {
                Crates.LOG.warn("[Crates] Journal : {}", e.toString());
            }
        });
    }

    public static void flush() {
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }
}
