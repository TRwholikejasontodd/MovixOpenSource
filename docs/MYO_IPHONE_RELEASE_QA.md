# MYO Premium — iPhone : livraison et contrôle qualité

Date de référence : 13 novembre 2026.

## État vérifié

- Dépôt : `TRwholikejasontodd/MovixOpenSource` ; base : `feature/android-tv-premium`.
- Version dans `app/app.json` : `2.6.14` (build iOS `51`), nom visible `MYO Premium`.
- GitHub Actions : [run 43513207028](https://github.com/TRwholikejasontodd/MovixOpenSource/actions/runs/43513207028), terminé avec succès.
- Binaire iPhone `arm64` compilé en configuration Release, empaqueté dans une **IPA non signée**.
- Artefact : `Movix-unsigned-2.6.14-13`. La mention `-13` est le numéro de run, **pas** le build iOS.
- Le job simulateur a été ignoré sur ce run de pull request ; seuls les tags `ios-v*` lancent la compilation simulateur.
- La signature, le provisioning, l'installation et la validation sur un iPhone réel **ne sont pas couverts par ce run**.
- Aucune release GitHub n'était publiée sur ce fork lors de la vérification.

## Reproduire la récupération

Prérequis : compte GitHub autorisé et CLI `gh` installée.

```sh
gh run download 43513207028 \
  --repo TRwholikejasontodd/MovixOpenSource \
  --name Movix-unsigned-2.6.14-13 \
  --dir ./myo-iphone
cd myo-iphone
shasum -a 256 -c Movix-unsigned.ipa.sha256
```

Conserver le fichier `Movix-unsigned.ipa` et la somme fournie ensemble. **Ne pas** installer l'IPA brute : la signature iOS est nécessaire. Elle peut être signée avec l'identifiant Apple de l'utilisateur via un outil compatible (par ex. AltStore ou SideStore) ou par un processus de signature Apple approprié. Un certificat, un profil de provisioning et, selon les capacités, des entitlements compatibles restent nécessaires.

Les sources AltStore/SideStore et Scarlet seront générées avec le nom visible issu de `app/app.json` à la prochaine publication taggée. Les identifiants stables `com.movix.app` et `com.movix.source` doivent rester inchangés pour la compatibilité des mises à jour. **Le run de PR actuel ne publie pas automatiquement ces sources ni une release.**

## Contrôles indispensables sur un iPhone

Consigner pour chaque scénario : modèle iPhone, version iOS, méthode de signature, réseau, résultat attendu/observé et journaux nettoyés de tout identifiant ou secret.

| Scénario | Attendu | Statut au 13/11/2026 |
| --- | --- | --- |
| Signature / installation | L'IPA se signe et l'app démarre sans crash | Non testé sur appareil |
| Démarrage initial | Intro MYO lisible avant toute demande de configuration DNS | À vérifier sur appareil |
| Identité | Nom MYO Premium et icône correcte sur l'écran d'accueil | Compilation vérifiée ; affichage physique non testé |
| Intro | Assemblage vectoriel et voix 6, lecture unique, aucun son après sortie | MP3 intégré vérifié par CI ; rendu physique non testé |
| Navigation | Ouverture de la WebView, boutons retour/avance/rechargement | Non testé sur appareil |
| Lecteur | Lecture d'un flux HLS/MP4 **autorisé**, pause, reprise, seek | Non testé sur appareil |
| Image dans l'image | Passage en PiP, reprise et fermeture | Non testé sur appareil |
| Chromecast | Détection sur réseau local avec permission iOS et diffusion d'un flux autorisé | Non testé sur appareil |
| DNS Cloudflare | Activation, désactivation et état cohérent | Non testé ; nécessite les entitlements/profils Apple correspondants |
| Cycle de vie | Fond/retour, rotation, lecture en arrière-plan, arrêt propre de l'intro | Non testé sur appareil |

## Points de vigilance

1. Le workflow actuel construit avec `CODE_SIGN_ENTITLEMENTS=` et désactive la signature : aucune capacité DNS Network Extension n'est accordée par cette archive non signée. Ne pas présenter le DNS système comme fonctionnel avant de tester le profil réellement signé.
2. Le code iOS contient une introduction de 4,2 s et une voix 6 embarquée. La validation CI de la présence du fichier n'équivaut pas à une validation de synchronisation audiovisuelle sur iPhone.
3. La présence d'une IPA ne prouve pas qu'une installation sur iPhone ait réussi, ni que les flux vidéo et les fonctionnalités PiP/Cast fonctionnent.
4. Ne pas fusionner ni annoncer une release de production avant une vérification sur appareil et la validation du chemin de signature.
