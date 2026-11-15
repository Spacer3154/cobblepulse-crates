package fr.cobblepulse.crates.config;

import fr.cobblepulse.crates.Crates;
import fr.cobblepulse.crates.core.Json;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Fichiers de config/cobblepulse_crates/ :
 * config.json (reglages, raretes), voeu_adn.json (prix du remodelage), caisses/*.json (une caisse par fichier),
 * pools/*.json (pools d'especes), emplacements.json (conteneurs lies) et messages.toml (textes).
 */
public final class Cfg {
    public static TomlFile messages;
    public static GeneralConfig general = new GeneralConfig();
    public static BuilderConfig builder = new BuilderConfig();
    public static final Map<String, CrateDef> CRATES = new LinkedHashMap<>();
    public static final Map<String, PoolDef> POOLS = new LinkedHashMap<>();
    public static Locations locations = new Locations();
    /** Incremente a chaque rechargement / modification (invalide les caches). */
    public static int revision;

    private Cfg() {}

    public static Path root() {
        return FabricLoader.getInstance().getConfigDir().resolve("cobblepulse_crates");
    }

    public static Path cratesDir() {
        return root().resolve("caisses");
    }

    public static Path poolsDir() {
        return root().resolve("pools");
    }

    public static void loadAll() {
        Path root = root();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            Crates.LOG.warn("[Crates] Creation de {} impossible : {}", root, e.toString());
        }
        messages = TomlFile.load(root, "messages.toml");
        general = load(root.resolve("config.json"), GeneralConfig.class, GeneralConfig::new);
        general.normalize();
        builder = load(root.resolve("voeu_adn.json"), BuilderConfig.class, BuilderConfig::new);
        builder.normalize();
        locations = load(root.resolve("emplacements.json"), Locations.class, Locations::new);
        if (locations.emplacements == null) locations.emplacements = new ArrayList<>();
        loadCrates();
        loadPools();
        revision++;
    }

    private static void loadCrates() {
        CRATES.clear();
        Path dir = cratesDir();
        boolean fresh = !Files.isDirectory(dir);
        try {
            Files.createDirectories(dir);
            if (fresh) {
                for (CrateDef c : Defaults.crates()) {
                    writeAtomic(dir.resolve(c.id + ".json"), Json.CFG.toJson(c));
                }
                Crates.LOG.info("[Crates] 10 caisses d'exemple creees dans {}", dir);
            }
        } catch (IOException e) {
            Crates.LOG.error("[Crates] Dossier des caisses illisible", e);
        }
        for (Path f : list(dir)) {
            String id = CrateDef.cleanId(f.getFileName().toString().replaceFirst("\\.json$", ""));
            if (id.isEmpty()) continue;
            CrateDef c = load(f, CrateDef.class, null);
            if (c == null) continue;
            c.id = id;
            c.normalize();
            CRATES.put(id, c);
        }
    }

    private static void loadPools() {
        POOLS.clear();
        Path dir = poolsDir();
        try {
            Files.createDirectories(dir);
            for (PoolDef p : Defaults.pools()) {
                Path f = dir.resolve(p.id + ".json");
                if (!Files.exists(f)) writeAtomic(f, Json.CFG.toJson(p));
            }
        } catch (IOException e) {
            Crates.LOG.error("[Crates] Dossier des pools illisible", e);
        }
        for (Path f : list(dir)) {
            String id = CrateDef.cleanId(f.getFileName().toString().replaceFirst("\\.json$", ""));
            if (id.isEmpty()) continue;
            PoolDef p = load(f, PoolDef.class, null);
            if (p == null) continue;
            p.id = id;
            p.normalize();
            POOLS.put(id, p);
        }
    }

    private static List<Path> list(Path dir) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : ds) out.add(p);
        } catch (IOException e) {
            Crates.LOG.warn("[Crates] Lecture de {} impossible : {}", dir, e.toString());
        }
        out.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        return out;
    }

    /** Lit un fichier JSON ; s'il n'existe pas, il est cree avec les valeurs par defaut (si fournies). */
    private static <T> T load(Path f, Class<T> type, Supplier<T> defaults) {
        try {
            if (!Files.exists(f)) {
                if (defaults == null) return null;
                T def = defaults.get();
                writeAtomic(f, Json.CFG.toJson(def));
                return def;
            }
            T v = Json.CFG.fromJson(Files.readString(f, StandardCharsets.UTF_8), type);
            if (v == null) throw new IllegalStateException("fichier vide");
            return v;
        } catch (Exception e) {
            Crates.LOG.error("[Crates] {} illisible ({}). Copie de sauvegarde {}.bak{}", f.getFileName(), e.getMessage(), f.getFileName(),
                    defaults == null ? " ; fichier ignore." : " ; valeurs par defaut utilisees en memoire.");
            try {
                Files.copy(f, f.resolveSibling(f.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
            return defaults == null ? null : defaults.get();
        }
    }

    public static void writeAtomic(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void write(Path file, Object value) {
        try {
            writeAtomic(file, Json.CFG.toJson(value));
        } catch (Exception e) {
            Crates.LOG.error("[Crates] Ecriture de {} impossible", file, e);
        }
        revision++;
    }

    public static void saveCrate(CrateDef c) {
        c.normalize();
        CRATES.put(c.id, c);
        write(cratesDir().resolve(c.id + ".json"), c);
    }

    public static boolean deleteCrate(String id) {
        CrateDef c = CRATES.remove(id);
        if (c == null) return false;
        try {
            Path f = cratesDir().resolve(id + ".json");
            if (Files.exists(f)) Files.move(f, f.resolveSibling(id + ".json.supprime"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Crates.LOG.warn("[Crates] Suppression de {} : {}", id, e.toString());
        }
        revision++;
        return true;
    }

    public static void saveGeneral() {
        general.normalize();
        write(root().resolve("config.json"), general);
    }

    public static void saveBuilder() {
        builder.normalize();
        write(root().resolve("voeu_adn.json"), builder);
    }

    public static void saveLocations() {
        write(root().resolve("emplacements.json"), locations);
    }
}
