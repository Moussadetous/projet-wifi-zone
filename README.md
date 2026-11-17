# MikhmonDroid

Mikhmon (v3) tout-en-un sur Android, sans Termux. À l'ouverture : l'application démarre un serveur PHP local
(`127.0.0.1`, port rare 38000–39999), puis affiche Mikhmon déjà connecté dans une WebView.

## Fonctionnement
1. Premier lancement : formulaire (admin Mikhmon, IP/port API/identifiants du MikroTik, hotspot, devise).
2. L'app écrit `include/config.php` de Mikhmon avec ces valeurs (`ConfigWriter.kt`).
3. `MikhmonService` (service au premier plan) lance `libphp.so -S 127.0.0.1:PORT`.
4. `MainActivity` se connecte automatiquement puis ouvre le tableau de bord du routeur.

Le binaire PHP est livré sous le nom `libphp.so` car Android n'autorise l'exécution que depuis
le dossier des bibliothèques natives (`extractNativeLibs=true`).

## Compiler
```
./scripts/fetch-assets.sh        # télécharge PHP statique arm64 + sources Mikhmon
gradle :app:assembleDebug        # ou laissez GitHub Actions le faire (.github/workflows/build.yml)
```

## À valider sur un vrai téléphone (non testé ici : pas d'Android SDK dans l'environnement de dev)
- URL du binaire PHP (`PHP_VERSION` / `PHP_URL` dans le script) et extensions nécessaires (sockets/session/json).
- Format de `config.php` et champs du formulaire de login (`user`/`pass`) : écrits d'après Mikhmon v3 ;
  `encrypt()` est vérifié contre l'implémentation PHP par `ConfigWriterTest`, mais pas contre le code de Mikhmon lui-même.
- Licence de Mikhmon : vérifiez qu'elle autorise la redistribution dans un APK.
- Les mots de passe sont stockés en clair dans les préférences privées de l'app (à migrer vers Keystore).
