# Mikhmon Android

Mikhmon v3 tout-en-un sur Android, sans Termux, avec un tableau de contrôle natif et des tickets premium.

## Pour l'utilisateur
1. Connectez le téléphone au Wi-Fi du MikroTik et ouvrez l'application : l'adresse du routeur est détectée.
2. Tapez seulement l'utilisateur et le mot de passe du routeur (ceux de Winbox) → **Se connecter**.
3. Connectez votre compte MPro (Google ou e-mail) : si le navigateur du téléphone est déjà connecté à la boutique,
   il est reconnu tout seul. **7 jours d'essai**, puis **accès à vie** payé une fois (lié au compte, tous appareils).

## Ce que fait l'application
- **Accueil** : état du routeur en direct (clients connectés, débit, processeur, mémoire, ventes du jour), raccourcis.
- **Clients** : connectés en direct (appareil, IP, MAC, débit, consommation, temps restant), déconnexion ;
  tickets (recherche, filtres, désactiver, supprimer, imprimer).
- **Trafic** : courbe en direct par interface, plus gros consommateurs.
- **Journal** : connexions, échecs, système, ventes Mikhmon du jour et du mois.
- **Tickets** : génération en quelques touches, au format Mikhmon (lots `vc-…`/`up-…`, profils, prix, validité) ;
  **stock par profil** (tickets restant à vendre) et impression d'un lot ou de tout un profil.
- **Impression des tickets** : formats **Mikhmon Default, QR et Small** (gratuits), Standard et Mini (gratuits) et
  46 modèles premium ; **nombre de tickets par feuille au choix** (taille réelle, 8, 24, 100…, la grille s'adapte),
  **aperçu de chaque page**, choix des pages, **impression Android** (imprimantes, « Enregistrer en PDF ») ou
  **PDF natif** dans Téléchargements/Mikhmon. Couleurs, textes, motif ou logo, QR de connexion automatique,
  papier A4 / A5 / Lettre / rouleaux 58 et 80 mm. **Taille du code réglable** (70 à 200 %, pour chaque modèle).
  Chaque modèle premium s'achète une fois (prix réglé sur la boutique).
- **Achats** : le bouton de paiement reste toujours disponible (solde du compte, sinon page de paiement) ; un paiement
  commencé et non terminé n'affiche « J'ai payé : vérifier » et « Annuler » que sous l'article concerné.
- **Mikhmon** : l'interface d'origine complète, déjà connectée. Ses boutons d'impression (Default, QR, Small, tickets
  à l'unité, après « Générer ») ouvrent l'impression de l'application avec aperçu ; un bouton **Premium** y est ajouté.
- **Devise** : déduite du pays du téléphone (réseau, carte SIM, fuseau horaire), FCFA dans la zone UEMOA/CEMAC.
- **Accès à distance** (option payante) : le routeur depuis la 4G ou n'importe quel Wi-Fi, grâce à « Back To Home »
  de MikroTik (RouterOS 7.12+, la plupart des modèles). L'offre n'apparaît que si le routeur en est capable (sinon
  « non disponible » dans Compte). 500 F/mois, payé par périodes de 2 mois : article « distance-AAAAMMJJ » de la
  boutique au prix des articles (1 000 F), sans rien changer au serveur. Activation en un geste depuis le Wi-Fi du
  routeur (utilisateur Back To Home créé par l'application), puis tunnel WireGuard intégré (bibliothèque officielle,
  Apache 2.0) utilisé seulement par l'application et seulement vers le routeur ; bascule automatique Wi-Fi / distance
  pour les écrans natifs et pour Mikhmon. **Activation automatique** dès l'achat (sur le Wi-Fi du routeur, ou au premier
  passage dessus), puis l'autorisation VPN d'Android proposée une fois. Réglage « Actualisation à distance »
  (Automatique, Rapide, Économe) ; diagnostic étape par étape avec rapport à copier.
- Raccourcis sur l'icône (appui long) : générer des tickets, clients, trafic, journal.

## Comment ça marche
- Android est un Linux : l'APK contient un **PHP compilé pour Android** (`libphp.so`) et **Mikhmon**. Le service
  `MikhmonService` lance `php -S 127.0.0.1:<port>` ; le pont `RouterBridge` force la connexion au routeur sur le Wi-Fi.
- Les écrans natifs parlent directement au routeur par l'API RouterOS (`core/RouterOs.kt`).
- **Tickets** (`core/tickets`) : dessin vectoriel indépendant de la plateforme → aperçu, PDF (PdfDocument) et tests
  (Java2D). Polices libres embarquées (Inter, JetBrains Mono, Caladea).
- **Compte** (`core/account/MproCore.kt`) : connexion par la page `/connexion-app/` de la boutique MPro (PKCE),
  jeton de l'application, essai, accès à vie, achats « en un geste » (le solde paie ; sinon recharge Chariow et l'achat
  se termine tout seul). L'adresse de la boutique est découverte (`mprodigital.shop`, puis l'adresse Cloudflare) et suit
  l'« adresse principale » réglée dans l'administration de la boutique.

## Structure
- `core/` : logique pure Kotlin testée sur ordinateur (Mikhmon, RouterOS, tickets, compte).
- `app/` : application Compose, service PHP, export PDF, tests sur émulateur (écrans, PDF, serveur Mikhmon).
- `scripts/build-php-android.sh` : PHP pour Android (NDK) · `scripts/fetch-mikhmon.sh` : Mikhmon v3 (GPL v2).

## Compiler
La CI (`.github/workflows/build.yml`) compile PHP, construit l'APK signée, la teste sur un émulateur Android 14 et
publie l'artefact **mikhmon-apk**. Signature stable : secrets `MIKHMON_KEYSTORE_B64` et `MIKHMON_KEYSTORE_PASSWORD`.
