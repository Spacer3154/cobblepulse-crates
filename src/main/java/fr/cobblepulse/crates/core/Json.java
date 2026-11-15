package fr.cobblepulse.crates.core;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class Json {
    /** Donnees joueurs / etat (noms de champs Java). */
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();
    /** Fichiers de configuration et echanges avec l'editeur (champs en snake_case). */
    public static final Gson CFG = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    public static final Gson CFG_COMPACT = new GsonBuilder().disableHtmlEscaping()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    private Json() {}

    public static <T> T copy(T obj, Class<T> type) {
        return GSON.fromJson(GSON.toJson(obj), type);
    }

    public static <T> T copyCfg(T obj, Class<T> type) {
        return CFG.fromJson(CFG.toJson(obj), type);
    }
}
