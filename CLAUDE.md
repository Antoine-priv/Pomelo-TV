# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Pomelo TV est un écran d'accueil pour Android TV (`com.pomelo.tv`), en Java pur, dérivé de Zen TV (Hoax017, MIT). L'utilisateur parle français : réponses, commentaires de code et messages en français.

## Compiler et installer

Pas de Gradle : `compiler.sh` appelle `launcher/build.sh` (aapt2 → javac → d8 → zipalign → apksigner). Le JDK 17 et le SDK Android sont dans `outils/` (non versionné).

```bash
./compiler.sh
```

Le résultat est `apk/pomelo-tv.apk`. La version se règle dans `compiler.sh` (`POMELO_VERSION`, `POMELO_VERSION_CODE`) : à incrémenter à chaque livraison.

```bash
adb connect <adresse-de-la-tele>:5555
adb install -r apk/pomelo-tv.apk
adb shell cmd package set-home-activity com.pomelo.tv/.HomeActivity
```

Il n'y a ni tests automatisés ni linter : on vérifie sur la TV, par `adb` (touches, captures, `logcat`).

## Règles à respecter

- **Ne jamais lancer `adb uninstall com.pomelo.tv`**, même pour revenir à une version antérieure : cela efface les préférences et l'activation du service d'accessibilité. Toujours `install -r`. `restaurer-tv.sh` désinstalle : ne le lancer que sur demande.
- **`signature/` ne doit jamais être publié** (clé et mot de passe). Sans cette clé, une nouvelle version ne s'installe plus par-dessus l'ancienne.
- Ne pas modifier les réglages système ou de sécurité de la TV (accessibilité Android, fuseau horaire) : c'est à l'utilisateur de le faire. Relever les réglages de l'application avant un test qui les change, puis les remettre.
- Ne pas pousser sur GitHub sans demande explicite.

## Architecture

Le code est dans `launcher/src/com/pomelo/tv/`.

- **`HomeActivity`** contient presque toute l'application, sans fichiers de mise en page : les vues sont créées par le code. Dock, grille, panneau Sources, Réglages, économiseur et suggestions sont des calques superposés dans la même activité. Les constantes de mise en page et les clés de préférences (fichier `pomelo`) sont en tête de classe.
- **`HomeKeyService`** est un service d'accessibilité : il intercepte les touches Home et Source, et compte l'inactivité pour éteindre la TV, car lui seul voit les touches dans toutes les applis. Sans ce service activé, ces fonctions ne marchent pas ; `HomeActivity` adapte ses réglages en conséquence.
- **`TvPrograms`** lit les recommandations publiées par les applis (fournisseur `android.media.tv`). À appeler hors du fil principal.
- **`DayPhase`** choisit la lumière des fonds d'écran selon la hauteur du soleil à Paris.
- **`TileArt`** et **`Icons`** dessinent au Canvas les tuiles Sources, Réglages, Éteindre et les icônes. **`Squircle`** fournit le masque arrondi, **`Spring`** les interpolateurs de ressort.

Les durées d'animation passent par `slow()`, et l'échelle de focus par `animateFocus`. Le texte visible va dans `res/values{,-fr,-es,-de,-it}/strings.xml` : toujours renseigner les cinq langues.

Les fonds d'écran (`assets/wallpapers`) sont versionnés : c'est leur seule source. `apk/` ne l'est pas.

## Pièges sur la TV

- `uiautomator dump` déconnecte temporairement les services d'accessibilité et échoue pendant les animations sans fin (mode giggle).
- `adb shell input keyevent` contourne le filtre de touches du service : cela ne teste pas `HomeKeyService`.
- `FocusFinder` ne voit pas les translations du dock : la navigation y est explicite. Les `ScrollView` doivent rester non focalisables, sinon elles volent le focus.
- Les surfaces vidéo sont protégées et `screencap` est trop lent pour capter une animation.
