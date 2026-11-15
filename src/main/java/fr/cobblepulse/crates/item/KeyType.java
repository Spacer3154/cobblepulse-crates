package fr.cobblepulse.crates.item;

import java.util.Locale;

/** Les 10 cles fantasy : identifiant d'objet, nom court (commandes), couleur et ambiance par defaut de la caisse associee. */
public enum KeyType {
    OBSIDIENNE("obsidienne", 0xB48CF0, "minecraft:falling_obsidian_tear", "minecraft:reverse_portal"),
    SONGES("songes", 0xF4B6E8, "minecraft:spore_blossom_air", "minecraft:cherry_leaves"),
    RACINE("racine", 0x8CCB5E, "minecraft:happy_villager", "minecraft:composter"),
    GIVRE_ETOILE("givre_etoile", 0xA8E6FF, "minecraft:snowflake", "minecraft:snowflake"),
    SOLAIRE("solaire", 0xFFC93C, "minecraft:small_flame", "minecraft:flame"),
    PROFONDEURS("profondeurs", 0x3DBFAF, "minecraft:nautilus", "minecraft:glow_squid_ink"),
    OMBRE("ombre", 0x8C8CA8, "minecraft:smoke", "minecraft:squid_ink"),
    TEMPS("temps", 0xD9925F, "minecraft:electric_spark", "minecraft:wax_off"),
    CELESTE("celeste", 0xE3EAF5, "minecraft:end_rod", "minecraft:firework"),
    COEUR_DRAGON("coeur_dragon", 0xFF6A3D, "minecraft:dragon_breath", "minecraft:lava");

    /** Nom court (argument de /lootbox givekey, nom de la caisse par defaut). */
    public final String shortName;
    public final int color;
    public final String idleParticle;
    public final String openParticle;

    KeyType(String shortName, int color, String idleParticle, String openParticle) {
        this.shortName = shortName;
        this.color = color;
        this.idleParticle = idleParticle;
        this.openParticle = openParticle;
    }

    /** Chemin de l'objet (cobblepulse_crates:clef_xxx). */
    public String itemPath() {
        return "clef_" + shortName;
    }

    public String itemId() {
        return "cobblepulse_crates:" + itemPath();
    }

    public String hex() {
        return String.format(Locale.ROOT, "#%06X", color);
    }

    public static KeyType byShortName(String s) {
        if (s == null) return null;
        String v = s.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (v.startsWith("cobblepulse_crates:")) v = v.substring("cobblepulse_crates:".length());
        if (v.startsWith("clef_")) v = v.substring(5);
        if (v.startsWith("cle_")) v = v.substring(4);
        for (KeyType k : values()) if (k.shortName.equals(v)) return k;
        return null;
    }
}
