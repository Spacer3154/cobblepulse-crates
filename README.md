# CobblePulse Crates

Caisses mystères ouvertes avec des clés thématiques, et **Vœu ADN** pour remodeler ses Pokémon, pour le serveur Cobble Pulse.

- Minecraft **1.21.1**, Fabric Loader ≥ 0.16, Fabric API, Fabric Language Kotlin, **Cobblemon 1.8.4**.
- Mod **client et serveur** (objets, écrans de caisse, éditeur, menu Vœu ADN) : il doit être sur le serveur **et** dans le modpack.
- Optionnel : **LuckPerms** (permissions). Sans LuckPerms : joueurs autorisés par défaut, administration réservée aux opérateurs (niveau 3).

## Objets

| Objet | Identifiant |
|---|---|
| Clef d'Obsidienne | `cobblepulse_crates:clef_obsidienne` |
| Clef des Songes | `cobblepulse_crates:clef_songes` |
| Clef-Racine | `cobblepulse_crates:clef_racine` |
| Clef de Givre-Étoile | `cobblepulse_crates:clef_givre_etoile` |
| Clef Solaire | `cobblepulse_crates:clef_solaire` |
| Clef des Profondeurs | `cobblepulse_crates:clef_profondeurs` |
| Clef de l'Ombre | `cobblepulse_crates:clef_ombre` |
| Clef du Temps | `cobblepulse_crates:clef_temps` |
| Clef Céleste | `cobblepulse_crates:clef_celeste` |
| Clef Cœur de Dragon | `cobblepulse_crates:clef_coeur_dragon` |
| Vœu ADN | `cobblepulse_crates:voeu_adn` |

Les clés n'ont aucun effet : elles servent uniquement à ouvrir la caisse associée. Onglet créatif « CobblePulse — Caisses ».

## Caisses

### Installer une caisse
1. Posez n'importe quel conteneur (coffre, tonneau, coffre de l'Ender, boîte de Shulker...).
2. Visez-le et tapez `/lootbox set <nom>` (ex. `/lootbox set obsidienne`). Plusieurs conteneurs, dans plusieurs mondes, peuvent être liés à la même caisse.
3. Pour le délier : visez-le et tapez `/lootbox unset <nom>` (sans viser : délie **tous** les conteneurs de la caisse).

Un conteneur lié ne peut plus être cassé ni ouvert comme un coffre : **clic gauche ou clic droit** ouvre l'aperçu de la caisse.

### Aperçu et ouverture
- Grille des récompenses, de la plus probable à la plus rare ; au survol : nom, rareté, **chance en %** et détails.
- Bouton d'ouverture : consomme la ou les clés (vérifiées et retirées par le serveur).
- Animations au choix par caisse : **roulette** façon CS:GO (défilement fluide, ralentissement progressif, « tic » à chaque lot, lot gagnant mis en avant), **roue**, ou **instantanée**. Bouton « Passer » si l'ouverture rapide est autorisée.
- Dans le monde : couvercle du conteneur qui s'ouvre, spirale de particules pendant l'animation, puis **hologramme** temporaire de la récompense au-dessus de la caisse, particules et son de la rareté.
- La récompense est tirée par le serveur dès l'ouverture et remise à la fin de l'animation — ou tout de suite si le joueur passe l'animation, se déconnecte ou si le serveur s'arrête.
- **Maintenance** (par caisse ou globale) : l'aperçu reste disponible, l'ouverture est bloquée sans consommer de clé.

### Fichiers (`config/cobblepulse_crates/`)
| Fichier | Contenu |
|---|---|
| `caisses/<nom>.json` | Une caisse par fichier : nom, clé, animation, récompenses... (10 caisses d'exemple, une par clé) |
| `pools/<nom>.json` | Pools d'espèces pour les Pokémon aléatoires : `all`, `legendary`, `ultra_beasts`, `fakemons` |
| `config.json` | Maintenance globale, distance d'ouverture, annonces, hologramme, particules d'ambiance, **raretés** |
| `voeu_adn.json` | Prix et restrictions du menu Vœu ADN |
| `emplacements.json` | Conteneurs liés (monde + coordonnées) |
| `messages.toml` | Tous les textes (codes couleur `&`, `&#RRGGBB`) |
| `journal/` | Journal des ouvertures et des remodelages, un fichier par mois |

Tout est modifiable **à la main** (puis `/lootbox reload`) ou **en jeu** avec `/lootbox editor`.

### Caisse (exemple)
```json
{
  "nom": "&#B48CF0Caisse d'Obsidienne",
  "description": "&7Forgée dans la roche volcanique.",
  "cle": "cobblepulse_crates:clef_obsidienne",
  "cles_requises": 1,
  "animation": "roulette",
  "duree_animation": 6.0,
  "ouverture_rapide": true,
  "maintenance": false,
  "couleur": "#B48CF0",
  "particule_attente": "minecraft:falling_obsidian_tear",
  "particule_ouverture": "minecraft:reverse_portal",
  "son_ouverture": "",
  "hologramme": true,
  "recompenses": [ ... ]
}
```
Particules : un identifiant (`minecraft:end_rod`) ou une poussière colorée (`dust #FFD24A 1.2`). Sons : tout son du jeu, des mods, ou d'un pack de ressources.

### Récompenses
Champs communs : `type`, `nom` (vide = automatique), `icone`, `poids` (chance = poids / somme des poids), `rarete`, `annonce` (message à tout le serveur), `description` (lignes au survol).

| `type` | Champs |
|---|---|
| `objet` | `objet` : `{"id": "cobblemon:rare_candy", "count": 3}` (composants complets acceptés ; « Prendre l'objet en main » dans l'éditeur) |
| `commande` | `commandes` : `["cobbledollars give %player% 500"]` — `%player%`, `%uuid%` (à adapter à l'économie installée : CobbleDollars, Impactor...) |
| `pokemon` | `pokemon` : espèce (fakemons acceptés), forme, niveau, chromatique, nature, talent, IV, EV, objet tenu, Poké Ball, capacités |
| `pokemon_aleatoire` | `aleatoire` : `pool`, `niveau_min`, `niveau_max`, `chance_shiny` (%), `ivs_parfaits` (0 à 6), `formes` (`base` ou `aleatoire`) — affiché sous forme de Poké Ball dans l'aperçu |

Une récompense impossible à préparer (pool vide, objet inconnu...) est ignorée et un autre lot est tiré ; si aucune ne convient, la clé n'est pas consommée.

### Pools (`pools/*.json`)
```json
{ "nom": "Légendaires et fabuleux", "toutes_especes": false, "etiquettes": ["legendary", "mythical"],
  "espaces_de_noms": [], "especes": [], "exclure": [], "exclure_etiquettes": [], "implementees_seulement": true }
```
`espaces_de_noms: ["*"]` = toutes les espèces hors `cobblemon` (fakemons de datapacks). Ajoutez autant de pools que nécessaire (ex. `starters.json`).

### Raretés (`config.json`)
Cinq raretés par défaut (commun, peu commun, rare, épique, légendaire), chacune avec couleur, son, volume, tonalité, particules et quantité, jouées à la caisse quand le lot est obtenu.

## Vœu ADN

Menu de remodelage : **touche N** (modifiable dans les contrôles) ou `/cpbuilder`. Le joueur choisit un Pokémon de son équipe ou de son PC.

| Modification | Coût par défaut |
|---|---|
| Chromatique (basculer) | 5 |
| Talent caché | 3 |
| Forme régionale / variante (hors Méga, Dynamax, formes de combat et formes à objet) | 2 |
| Autre talent standard | 1 |
| Nature | 1 |
| IV à 31 | 1 par statistique |
| EV (répartition libre, 252 max par stat, 510 au total) | 1 par session |

Coût minimum : 1 Vœu ADN par action. Tout est vérifié côté serveur (propriété du Pokémon, combat en cours, légalité). Prix, monnaie, PC autorisé, formes et espèces interdites : `voeu_adn.json` ou onglet « Vœu ADN » de l'éditeur.

## Commandes et permissions

| Commande | Permission |
|---|---|
| `/lootbox preview <nom>` — aperçu à distance (consultation seulement) | `cobblepulsecrates.command.preview` |
| `/cpbuilder` — menu Vœu ADN | `cobblepulsecrates.builder.open` |
| `/lootbox set <nom>` | `cobblepulsecrates.admin.set` |
| `/lootbox unset [nom]` | `cobblepulsecrates.admin.unset` |
| `/lootbox editor` | `cobblepulsecrates.admin.editor` |
| `/lootbox toggle <nom>` / `/lootbox toggleall` | `cobblepulsecrates.admin.toggle` |
| `/lootbox givekey <joueur> <clé> [quantité]` | `cobblepulsecrates.admin.givekey` |
| `/lootbox givewish <joueur> [quantité]` | `cobblepulsecrates.admin.givewish` |
| `/lootbox reload` | `cobblepulsecrates.admin.reload` |
| `/lootbox list` | `cobblepulsecrates.admin.editor` |

`<clé>` : `obsidienne`, `songes`, `racine`, `givre_etoile`, `solaire`, `profondeurs`, `ombre`, `temps`, `celeste`, `coeur_dragon` (ou le nom d'une caisse).

Nœuds spéciaux :
- `cobblepulsecrates.admin.*` — toute l'administration.
- `cobblepulsecrates.crate.open.<nom>` — ouvrir une caisse précise (autorisé par défaut ; mettre à `false` pour réserver une caisse).
- `cobblepulsecrates.bypass.maintenance` — ouvrir pendant une maintenance.
- `cobblepulsecrates.builder.use` — utiliser le menu Vœu ADN (autorisé par défaut).
- `cobblepulsecrates.builder.bypass` — remodeler sans consommer de Vœu ADN (accordé explicitement uniquement).

## Tests automatiques (GitHub Actions)
Chaque envoi sur `main` compile le mod, démarre un vrai serveur Fabric + Cobblemon 1.8.4 + LuckPerms (joueurs factices : liaison, aperçu, ouverture, clés, maintenance, permissions, chaque type de récompense, pools, hologrammes, déconnexion, éditeur, Vœu ADN complet, persistance après redémarrage) puis lance un vrai client sous xvfb pour capturer les écrans. Résultats et jar sur la branche `ci-output`.
