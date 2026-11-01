# Vitesse GPS

Application Android (Kotlin + Jetpack Compose) qui affiche la vitesse GPS en direct,
simultanément en **nœuds** et en **km/h**.

## Fonctionnalités

- Vitesse en très grands chiffres : nœuds (blanc) et km/h (jaune), 1 décimale.
- Source : `FusedLocationProviderClient`, `PRIORITY_HIGH_ACCURACY`, une mesure par seconde.
- Si `hasSpeed()` est faux : vitesse calculée par distance / temps entre deux positions.
- Lissage par moyenne mobile sur 3 mesures ; vitesse forcée à 0 sous 0,5 nœud.
- Vitesse max de la session (bouton « RAZ max »), cap en degrés + point cardinal,
  précision GPS en mètres, indicateur « Recherche GPS… » / « GPS OK ».
- Écran maintenu allumé, thème sombre à fort contraste, portrait et paysage.
- GPS arrêté automatiquement quand l'application passe en arrière-plan.

## Structure

```
app/src/main/java/com/vitessegps/app/
├── MainActivity.kt      # Permissions, état du GPS, cycle de vie, écran allumé
├── SpeedViewModel.kt    # Abonnement GPS, état de l'écran (StateFlow)
├── SpeedCalculator.kt   # Calcul / lissage / conversions de vitesse
└── ui/
    ├── SpeedScreen.kt   # Interface Compose (vitesse, infos, écrans d'erreur)
    └── Theme.kt         # Thème sombre et couleurs
```

## Compilation automatique

Le workflow `.github/workflows/build.yml` compile l'APK (`./gradlew assembleDebug`, JDK 17)
à chaque push sur `main` (et sur les branches `claude/**`), ou manuellement
(onglet **Actions** > **Build APK** > **Run workflow**). L'APK est publié comme artefact
nommé **`vitesse-gps-apk`**.

## Récupérer l'APK depuis le téléphone

1. Dans le navigateur (Chrome), ouvrir la page du dépôt GitHub et se connecter.
   Astuce : menu ⋮ > cocher **« Version pour ordinateur »** si l'onglet Actions n'apparaît pas.
2. Ouvrir l'onglet **Actions**.
3. Toucher le dernier build **« Build APK »** avec une coche verte ✅.
4. Descendre jusqu'à la section **Artifacts** et toucher **vitesse-gps-apk**.
   Un fichier `vitesse-gps-apk.zip` est téléchargé.
5. Décompresser le ZIP :
   - ouvrir l'application **Fichiers** (Files by Google ou « Mes fichiers » sur Samsung) ;
   - aller dans **Téléchargements** ;
   - toucher `vitesse-gps-apk.zip` puis **Extraire** ;
   - le fichier `app-debug.apk` apparaît.

## Installer l'APK

1. Toucher `app-debug.apk` dans l'application Fichiers.
2. Si Android affiche « Pour votre sécurité, votre téléphone n'est pas autorisé à installer
   des applications inconnues de cette source » : toucher **Paramètres**, activer
   **« Autoriser cette source »**, puis revenir en arrière.
3. Toucher **Installer** (si Play Protect avertit : **Plus de détails** > **Installer quand même**).
4. Ouvrir **Vitesse GPS**, accepter la localisation (**Position exacte**,
   **Pendant l'utilisation de l'appli**), puis attendre « GPS OK » — idéalement en extérieur.

Les APK sont tous signés avec la même clé de debug (`keystore/debug.keystore`) :
une nouvelle version s'installe par-dessus l'ancienne sans désinstallation.
