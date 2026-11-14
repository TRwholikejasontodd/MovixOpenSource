# MYO ↔ ONYX TV 1.8.16 — implémentation et limites vérifiables

## Artefact de référence

ONYX TV officiel (13 novembre 2026), `onyx-v1.8.16-tv.apk`, taille **38 330 573 octets**, SHA-256 `38e0b8cb3a85efc22afc1d777402fad54694e5a39a9c0a0be8ae98c3140bf741`.
Téléchargé et contrôlé sur GitHub Actions, puis examiné sur un runner éphémère avec JADX 1.5.6. L'APK et son code décompilé ne sont ni mis en dépôt ni redistribués par MYO. Rapport de signatures : workflow `myo-onyx-structure.yml`, exécution `43537322638`.

Résultat : **3 DEX**, **10 bibliothèques natives** pour `armeabi-v7a`, dont `libmedia3ext.so` et des bibliothèques FFmpeg. Le décompilateur a émis environ 11 887 classes Java (y compris des dépendances tiers, donc **pas** 11 887 classes exclusives d'ONYX). La recherche par nom a trouvé environ 179 classes `Provider/Source` et 160 `Extractor`, **avec** des classes Android/Media3 tierces : ces compteurs ne représentent pas exclusivement des fournisseurs de streaming.

## Composants observés et tâches dans le port MYO

| Composant ONYX | Signatures observées | Rôle technique probable | Usage dans MYO |
| --- | --- | --- | --- |
| `OnyxFilmsProvider` | `implements Provider, ProgressiveServersProvider`, `getHome`, `search`, `getVideo`, `getServersProgressive`, `getEpisodesBySeason`, `chargerSources`, `sourcesDirectes` | Catalogue, recherche, saisons et découverte progressive de serveurs, avec chargement de données externes | **Contrat fonctionnel inspirant `ProgressiveCatalogPort` ; aucun moteur ONYX repris** |
| `TmdbProvider` | `implements Provider, ProgressiveServersProvider`, `getHome`, `search`, `getVideo`, `getServersProgressive`, `getEpisodesBySeason` | Affichage et recherche de métadonnées et recherche de serveurs via une couche distincte | Métadonnées non confondues avec sources de lecture |
| `ArchiveOrgProvider` | `videosDe`, `getVideo`, `regexEpisode`, `fetchArchiveOrgBackupServers` | Recherche de vidéos et épisodes sur une archive et récupération de serveurs | Internet Archive dispose d'une API publique, mais le connecteur MYO ne prétend pas reproduire cette implémentation ONYX |
| `FilemoonExtractor` | `extract`, `extractOnDomain`, `extractViaApi`, `extractViaWebView`, `decryptPlayback`, `isNetworkError` | Plusieurs stratégies de résolution de serveur / erreurs, sans garantie de droits d'accès | **Non repris** : pas de SDK/licence/extracteur ONYX réutilisable vérifié |
| `Provider` | Interface de fournisseur, présence de `getProviders`, `getProvidersByGroup` dans l'APK | Registre de fournisseurs et regroupements | `SourceGateway` fournit l'isolation entre moteurs |
| Media3 / FFmpeg | Libs natives embarquées ; source Java Media3 dans l'APK | Décodage, buffering et prise en charge de conteneurs | MYO conserve son lecteur Media3 existant ; ne copie pas les binaires ONYX |

L'inspection des signatures et classes **ne permet pas** de vérifier la disponibilité actuelle d'un flux, les conditions contractuelles d'accès aux serveurs, ni l'équivalence avec les fonctions observées en usage réel.

## Droits et protocoles

Le dépôt officiel public `warbix-prog/ONYX` contient `README.md` et le guide `tuto/ONYX_Guide_complet.pdf` ; il ne publie pas de code source du moteur, SDK, spécification officielle de son API fournisseur ou licence autorisant la redistribution du code. L'APK disponible publiquement n'autorise pas à elle seule son empaquetage ou sa transformation en application MYO.

Aucun identifiant, jeton ou mécanisme DRM privé n'a été récupéré, aucune méthode propriétaire n'a été copiée, et aucun fournisseur ONYX n'est annoncé comme connecté. Le port `ProgressiveCatalogPort` est indépendant et ne doit être enregistré dans `SourceGateway` que lorsqu'une implémentation *et* les droits de réutilisation ont été établis.

## Code produit

- `app-tv/app/src/main/kotlin/com/movix/tv/data/sources/ProgressiveCatalogPort.kt` : contrat `home`, `search`, `episodesBySeason`, `servers` progressif, correspondance stricte `tmdbId`/saison/épisode.
- `ProgressiveCatalogSourceAdapter` : adaptation vers `SourceGateway` ; refuse les identifiants inconnus, épisodes incomplets et URLs HTTP, ne fabrique jamais d'adresse et s'arrête après 24 candidats par requête.
- `app-tv/app/src/test/kotlin/com/movix/tv/data/sources/ProgressiveCatalogPortTest.kt` : tests de correspondance d'identité, droits déclarés, sources HTTPS et épisodes.
- `.github/workflows/myo-onyx-structure.yml` et `scripts/onyx_interop_report.py` : analyse reproductible de l'APK authentifiée, sans sortie de code propriétaire.

## État réel

**Moteur ONYX intégré : non. Fournisseurs ONYX intégrés : aucun.** Le moteur compatible avec la distribution reste fourni par les fournisseurs existants de MYO/Movix et le catalogue libre public, comme précédemment ; l'utilisateur a explicitement refusé que ceux-ci soient présentés comme équivalents à ONYX. Ni un succès de compilation ni un test réseau d'Internet Archive n'impliquent une vidéo ONYX fonctionnelle.

Le port de compatibilité est fonctionnel pour une implémentation *autorisée* ultérieure ; sans fournisseur officiel/documenté ou permission du détenteur d'ONYX, il ne peut délivrer de flux ONYX. L'APK de ce développement reste une **version expérimentale**, pas une refonte validée du moteur d'ONYX.
