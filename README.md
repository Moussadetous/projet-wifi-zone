# MikhmonDroid

Mikhmon v3 tout-en-un sur Android, sans Termux.

## Pour l'utilisateur
1. Connectez le téléphone au Wi-Fi du MikroTik.
2. Ouvrez l'application : l'adresse du routeur est détectée automatiquement.
3. Entrez l'utilisateur et le mot de passe du routeur (ceux de Winbox) → **Se connecter**.
4. Mikhmon s'ouvre, déjà connecté. Aux lancements suivants, il s'ouvre directement.

Le nom du routeur, le hotspot et son nom DNS sont lus sur le routeur ; la devise vient de la langue du téléphone.
Le compte administrateur Mikhmon est généré et utilisé automatiquement.

## Comment ça marche
Android est un système Linux : l'application contient un **PHP compilé pour Android** (`libphp.so`,
seul emplacement d'où Android autorise l'exécution) et les fichiers de **Mikhmon**. Au démarrage, le service
`MikhmonService` lance `php -S 127.0.0.1:<port>` comme un processus normal — exactement ce que fait Termux, sans Termux.
Le navigateur intégré (WebView) affiche alors Mikhmon et s'y connecte tout seul.

Mikhmon joint le routeur via un **pont interne** (`RouterBridge`) dont les connexions sont forcées sur le Wi-Fi :
ça marche même si le Wi-Fi du MikroTik n'a pas internet et que les données mobiles sont actives.

En cas de problème, l'application affiche la cause (ex. « service API désactivé », « mot de passe incorrect »,
« PHP s'est arrêté : SIGSYS ») et un rapport de diagnostic copiable.

## Structure
- `core/` : logique pure Kotlin testée sur JVM (format `config.php` de Mikhmon, client API RouterOS, pont, patch Mikhmon).
- `app/` : application Android (écrans, service, exécution de PHP) + test sur émulateur (`ServerTest`).
- `scripts/build-php-android.sh` : compile PHP avec le NDK Android (bionic).
- `scripts/fetch-mikhmon.sh` : récupère Mikhmon v3 (GPL v2) à une version figée.

## Compiler
La CI GitHub Actions (`.github/workflows/build.yml`) compile PHP, construit l'APK et le teste dans un émulateur.
En local : `scripts/build-php-android.sh arm64-v8a out/php/arm64-v8a`, copier `libphp.so` dans
`app/src/main/jniLibs/arm64-v8a/`, puis `scripts/fetch-mikhmon.sh` et `gradle :app:assembleDebug`.
