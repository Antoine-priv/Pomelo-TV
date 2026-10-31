# Pomelo TV

Écran d'accueil pour Android TV (Android 7 ou plus récent), léger et sans publicité, pensé pour la télécommande.

## Fonctionnalités

- **Dock de 6 apps maximum** et grille de toutes les autres apps, accessible avec la flèche du bas.
- **Mode giggle** : maintenir OK sur une app, puis la déplacer avec les flèches dans le dock ou la grille.
- **Panneau Sources** : choix et ordre des entrées, sources débranchées grisées, touche Source de la télécommande.
- **15 paysages en 8 lumières**, choisis selon la hauteur réelle du soleil en France, changement d'heure compris.
- **Économiseur d'écran** dans le launcher et **extinction après inactivité**.
- **Réglages** : langue (téléviseur, français, anglais, espagnol, allemand, italien), fond du dock flou, son de navigation, délais.

## Compiler

Il faut un JDK 17 et le SDK Android (build-tools et une plateforme 30 ou plus). Le script cherche le JDK et le SDK dans `outils/jdk` et `outils/sdk`, un dossier local non versionné, sauf si `JAVA_HOME` ou `ANDROID_HOME` sont définis.

```bash
./compiler.sh
```

Le script crée une clé de signature locale dans `signature/` au premier lancement, puis produit `apk/pomelo-tv.apk`. Garde cette clé : sans elle, une nouvelle version ne peut pas s'installer par-dessus l'ancienne.

## Installer

```bash
adb connect <adresse-de-la-tele>:5555
adb install -r apk/pomelo-tv.apk
adb shell cmd package set-home-activity com.pomelo.tv/.HomeActivity
```

Pour la touche Source et l'extinction après inactivité, active ensuite le service dans les paramètres Android : **Préférences relatives à l'appareil → Accessibilité → Pomelo TV**.

`restaurer-tv.sh` retire Pomelo TV et remet l'accueil Google. Modifie d'abord l'adresse de la télé dans le script.

## Origine et licences

Pomelo TV est dérivé de [Zen TV](https://github.com/Hoax017/zen-tv) de Hoax017, sous licence MIT (voir `LICENSE`). La police [Inter](https://github.com/rsms/inter) est sous licence SIL OFL (voir `launcher/assets/fonts/LICENSE.txt`).
