# MYO / ONYX — audit technique et plan de validation

Date : 14 novembre 2026. Branche : `myo/onyx-provider-architecture`.

## 1. Portée et intégrité des pièces examinées

- Projet modifié : `app-tv/` de MovixOpenSource, avec maintien de la branche `main`.
- Référence ONYX : `warbix-prog/ONYX`, dont le dépôt public ne fournit ni moteur source ni SDK public.
- APK fournie : `onyx-v1.8.15-universal.apk`, 65 565 852 octets ; SHA-256 `949050149e311c31ea86b32c49eefdb0f250653c4d1070606996fa509d94b4b8`.
- **Attention** : la release GitHub v1.8.15 publie actuellement un artefact universal de 65 598 677 octets avec le digest `72fbc8307558c3724c6f39e5df743443c0e22b419d0bce330dba3d753d3c6331`. Le binaire reçu **ne correspond donc pas** à celui de la release actuellement consultée. Aucune équivalence ni innocuité n'est attestée.

Constats vérifiés par lecture statique du manifeste binaire Android de l'APK reçue :

- package : `com.streamfr.app` ;
- versionName : `1.8.15` ; versionCode : `108015` ;
- minSdk : `23`, targetSdk : `35` ;
- `MainTvActivity`, `ProfilePickerTvActivity`, `SplashActivity`, `LocalVideoPlayerActivity` ;
- catégories de lancement `LEANBACK_LAUNCHER` ;
- 3 DEX ; 20 bibliothèques natives réparties entre les ABI ARM 32 et 64 bits ;
- présence de FFmpeg (`libavcodec`, `libavutil`, `libswresample`, `libswscale`), `libmedia3ext` et de composants liés aux sous-titres et au multimédia ;
- inventaire des définitions de classes applicatives, sans décompilation de méthodes : environ 3 415 classes déclarées sous le préfixe `com.streamflixreborn.streamflix` (classes internes et classes générées comprises), dont de nombreuses familles `providers` et `extractors`.

Ces faits démontrent la présence de composants utiles, **pas** la disponibilité ni la permission de réutiliser les serveurs, connecteurs ou secrets d'ONYX. Aucun identifiant interne, clé, cookie, schéma de contournement ou DRM n'a été extrait.

Une recherche d'anciennes sources StreamFlix Reborn a trouvé une référence à un dépôt Apache-2.0, mais la récupération directe de ce dépôt a échoué avec un blocage GitHub 451/DMCA. Aucune reprise de son code n'a été effectuée.

## 2. Architecture MYO existante

- UI : Kotlin, Compose pour TV, navigation D-pad et rangées paresseuses ;
- métadonnées : `data/tmdb` via TMDB avec recommandations IMDb ;
- sources actuelles : `SourceRepository` appelant les routes de la Main API Movix ;
- sélection : `SourceSelector` (langue, qualité, préférences) ;
- lecteur : AndroidX Media3 / ExoPlayer, HLS/fichier, buffers et sous-titres ;
- profils : `AccountManager` et stockage local pour certaines informations ;
- manifest Android TV : `LEANBACK_LAUNCHER`, `minSdk 25`, `targetSdk 36`.

**Écart majeur** : aucun connecteur officiellement autorisé vers l'API ONYX n'est actuellement documenté ou implémenté. Le moteur ONYX n'est pas intégré. La clé TMDB peut encore être nécessaire pour afficher le catalogue distant : l'exigence d'absence de configuration bloquante n'est pas satisfaite.

## 3. Modification implémentée sur la branche

- `SourceProvider` : interface indépendante d'un backend.
- `SourceGateway` : recherche concurrente, déduplication, gestion d'erreur isolée par fournisseur, annulation sur retour à l'UI.
- `MovixCompatibilityProvider` : ancien moteur encapsulé, **conservé provisoirement** pour éviter les régressions.
- `AuthorizedSourceProvider` : véritable second connecteur pour des flux HTTPS faisant l'objet d'une autorisation vérifiable et explicitement déclarée ; filtrage par identifiant TMDB, type, saison et épisode.
- `app/src/main/assets/myo-authorized-sources.json` : registre embarqué, vide intentionnellement tant qu'aucune source et licence ne sont vérifiées. Aucun flux fictif.
- `PlayerController` utilise `sourceGateway.load()` et ne contacte plus directement `container.sources.load()`.
- `SourceSelector` reconnaît les flux autorisés, sans supprimer ses mécanismes précédents.

**Limite importante :** le connecteur autorisé a pour l'instant zéro enregistrement. Les sources actuellement trouvées viennent encore de Movix. Un connecteur ONYX n'a pas été inventé. La couche de catalogue n'est pas encore abstraite.

### Format d'une entrée de source autorisée

```json
[
  {
    "tmdbId": 12345,
    "type": "movie",
    "url": "https://exemple-autorise.invalid/film.m3u8",
    "title": "Titre autorisé",
    "license": "Référence du contrat ou de la licence de distribution",
    "format": "hls",
    "language": "vf",
    "quality": "1080p"
  }
]
```

Ce JSON est uniquement **un exemple de schéma**, avec une URL `.invalid` non fonctionnelle et un identifiant fictif ; il ne doit pas être pris pour une source réelle. Pour les séries, préciser `type: "tv"`, `season` et `episode`. Ne renseigner que des flux HTTPS pour lesquels les droits et l'accès ont été vérifiés. Ne pas stocker de jetons ou clés privés dans l'APK.

## 4. Comparaison des voies d'intégration

| Stratégie | Situation | Décision |
| --- | --- | --- |
| Réutilisation directe du moteur ONYX | Code source / SDK / licence du moteur non fournis dans le dépôt public ONYX | Ne pas prétendre l'avoir intégré |
| Analyse statique de l'APK | Manifeste, classes et dépendances vérifiés ; provenance binaire non identique à la release | Conserver les résultats techniques, pas de réutilisation de code propriétaire |
| Réécriture indépendante d'une architecture de fournisseurs | Compatible avec le lecteur et les types existants | **Implémentée partiellement** via SourceGateway |
| Suppression immédiate de Movix | Risque de régression : aucun remplaçant prouvé | Refusée avant disponibilité de nouveaux connecteurs |
| Bases open source telles que Media3 | Media3 déjà intégré et maintenu | Réutiliser le moteur vidéo actuel |
| Intégration de fournisseurs officiels ou licenciés | Accès à obtenir et droits à vérifier | Ajouter des connecteurs distincts dès que les interfaces sont documentées |

## 5. Validation : résultats réels et non vérifiés

- **Réussi** : lecture structurale du manifeste APK, inventaire des bibliothèques, création de branche, écriture des interfaces et des adaptateurs, test de cohérence des chemins modifiés dans GitHub.
- **Tests ajoutés, pas encore attestés comme réussis** : `SourceGatewayTest`, `AuthorizedSourceProviderTest` (déduplication, erreur isolée, annulation, correspondance stricte épisode, refus des URL HTTP et des sources sans licence).
- **Non exécuté localement** : Gradle Android, compilation de l'APK, tests instrumentés, émulation Android TV, Logcat, test de source en lecture prolongée.
- **GitHub Actions** : workflow `.github/workflows/myo-tv-verify.yml` ajouté pour compiler et publier l'APK debug. Un statut vert doit être obtenu avant d'annoncer une APK construite.
- **Sony Bravia** : pas d'accès ADB au téléviseur de l'utilisateur ; installation et navigation réelles non testées.

### Protocole Sony Bravia après compilation

```bash
adb connect ADRESSE_TV:5555
adb install -r chemin/vers/myo-debug.apk
adb logcat -c
adb shell am start -n com.movix.tv/.MainActivity
# Sur une autre console, en naviguant à la télécommande :
adb logcat -v time -s AndroidRuntime:E MovixPlayer:V MovixSources:V MyoSources:V
```

Sur une source autorisée et fonctionnelle, vérifier successivement : préparation, 2 minutes de lecture, 15 minutes de lecture, pause, retour arrière, reprise, qualité, pistes audio, sous-titres, sortie, retour à la fiche, redémarrage, profil distinct, mémoire et réseau. Ne pas confondre test sur émulateur et test réel sur téléviseur.

## 6. Autorisation ONYX — message à transmettre au mainteneur

Bonjour,

Je développe MYO, une application Android TV indépendante. ONYX est ma référence fonctionnelle. Je souhaite savoir si vous proposez une API documentée, un SDK ou une licence permettant une intégration interopérable de certaines fonctionnalités de catalogue et de sélection des sources, exclusivement pour des contenus accessibles légalement.

Je ne demande ni clé privée, ni contournement d'authentification, ni accès à un service réservé. Si une intégration est possible, pourriez-vous préciser les conditions de licence, d'attribution, de débit et de distribution ?

Merci pour votre retour.

## 7. Prochaines contraintes bloquantes

1. Obtenir une interface officielle ONYX réutilisable **ou** construire un catalogue indépendant contenant des œuvres et flux dont les droits d'accès sont établis.
2. Remplacer la dépendance obligatoirement visible à une clé TMDB personnelle par une configuration serveur autorisée avec cache et mode de repli.
3. Concevoir l'identité MYO complète ; elle ne doit pas être présentée comme déjà intégrée.
4. Obtenir une compilation et des résultats de tests réels avant livraison d'APK.
5. Exécuter les essais longs et les tests D-pad sur une Sony Bravia.

Aucune intégration ONYX, aucune liste de serveurs ONYX et aucune lecture longue ne sont attestées par ce rapport.
