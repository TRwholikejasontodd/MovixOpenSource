# Audit d'interopérabilité Onyx → Myo (Android TV)

Mise à jour : 13 novembre 2026. Référence reçue : `onyx-v1.8.15-tv.apk`.

## Limites de preuve

Cet APK a été inspecté statiquement (conteneur, DEX, tables de chaînes et classes). **Il n'a pas été intégralement décompilé en sources Java/Kotlin ni compilé sous une autre identité.** Les noms de classes indiquent des fonctionnalités possibles, pas leur activation ou leur fiabilité en production. Les performances et la sélection automatique propres à Onyx ne sont pas encore mesurées sur un appareil.

Le dépôt public `warbix-prog/ONYX` consultable à cette date ne contient que le README et un guide PDF : il n'expose pas le code source du moteur de l'APK. Un dépôt apparenté StreamFlix Reborn a retourné HTTP 451 lors de la tentative d'accès aux sources ; ne présumer ni de l'accessibilité ni des droits sur les modifications propriétaires d'Onyx. Réimplémenter les comportements utiles à partir des interfaces documentées et de sources autorisées ; ne pas embarquer du bytecode tiers sans licence applicable.

## Architecture constatée dans l'APK

- 2 595 entrées ZIP, trois DEX (`classes.dex`, `classes2.dex`, `classes3.dex`).
- Une ABI native : `armeabi-v7a`, compatible avec la cible ARMv7 de Myo.
- Espace de noms significatif : `com.streamflixreborn.streamflix`.
- Classes de type `Provider` et implémentations de fournisseurs ; classes de type `Extractor` par hébergeur.
- Traces de lecteurs Media3/ExoPlayer, persistance locale des films, saisons et épisodes.
- La classe `PlayerTvFragment` expose 105 noms de méthodes dans la table DEX, dont une grande majorité obscurcie (`A`, `B0`, etc.). La simple présence des noms ne permet pas de reconstituer l'algorithme de choix de source.
- Bibliothèques vidéo natives (avcodec/avutil et modules complémentaires).

## Correspondances et travaux dans Myo

| Principe visé | Composant Myo existant | État |
| --- | --- | --- |
| Charger plusieurs sources | `SourceRepository` | Routes parallèles avec délais individuels |
| Sélectionner une source | `SourceSelector` | Priorité langue, qualité mesurée et codecs |
| Éviter les doublons | `SourceSelector.playable` | Correction d'un cas de doublon qui masquait un autre flux |
| Lire HLS, DASH et MP4 | `PlayerEngine`, `SourceParsers` | DASH ajouté au pipeline, non validé sur appareil |
| Basculer si source indisponible | `PlayerController` | Repli sur erreur et délai initial |
| Réagir aux gels après démarrage | `PlayerController` | Détecteur de rebuffering prolongé (30 s), non validé sur appareil |
| Reprendre à la même position | `PlayerController` / `Library` | Position transférée au repli |
| Économiser les ressources | `PlayerController`, `StreamProbe` | Annulation des tâches et suppression des sondes MP4 sur DASH |
| Refonte graphique | `ui/theme`, `ui/home`, `ui/details` | À finaliser, sans changer le parcours simple |

## Prérequis de validation

1. Obtenir une compilation verte des tests JVM puis de `:app:assembleDebug` pour `armeabi-v7a` via GitHub Actions.
2. Tester HLS, MP4 et DASH avec des flux autorisés et vérifier lecture > 2 minutes, seek, pause, reprise et changement de qualité.
3. Injecter un rebuffering long dans une source de test, vérifier la bascule sans retour à zéro, et empêcher les réessais infinis.
4. Mesurer un lancement à froid, une session prolongée, l'empreinte mémoire sur TCL Android TV et les octets réseau.
5. Comparer sur appareil le parcours de sélection de film et des épisodes à l'APK Onyx, sans en présumer le classement effectif à partir des seuls noms de classe.

## État CI connu

- Run n°80 : échec du test `SourceSelectorTest.routeOrderKeptAmongHdAndWithoutProbe` à la suite du nouveau critère de qualité. Assertion mise à jour pour refléter le comportement voulu.
- Run n°97 : configuration Gradle interrompue avant les tests par l'impossibilité de télécharger plusieurs dépendances, dont `kotlin-stdlib`. Miroir Maven Central ajouté en première source avec Maven Central en secours ; résultat de la nouvelle exécution à vérifier.

**Ne pas qualifier l'APK de « terminé » avant compilation et tests sur matériel.**
