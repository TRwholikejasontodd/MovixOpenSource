# MYO — Choix vocal et demande de contre-audit

Date : 13 novembre 2026

## Décision de design
La seule version approuvée pour l'introduction est **la sixième**, « enfantine et aérienne » : fichier de comparaison `05_MYO_enfantine_aerienne.mp4` et WAV associé, dans les livrables de la conversation. Elle dérive d'une voix synthétique locale retravaillée, non d'une comédienne japonaise. Les autres variantes de la comparaison n'ont pas été retenues : elles étaient trop similaires.

L'introduction doit durer 4 secondes, avec la même vidéo de construction vectorielle, un son final original après le mot « Myo », et une transition unique vers l'écran des profils. La voix doit être **exactement la même** sur les appareils : une synthèse TTS Android à la volée n'est pas suffisante.

## Contrôle contradictoire demandé

**Rôle :** auditeur qualité indépendant (audio, Android TV, synchronisation). Ne modifie pas l'implémentation tant que les écarts ne sont pas isolés. Ton verdict doit être ACCEPTÉ, REFUSÉ ou NON TESTABLE.

1. Vérifie la filiation et le timbre de la version 6 ; ne remplace pas la voix retenue par une autre variante HeyGen ou eSpeak.
2. Vérifie **l'audio réellement livré dans l'APK**, et non celui d'une vidéo MP4 de démonstration. L'audio approuvé doit être embarqué dans les ressources Android.
3. Vérifie le début de voix vers 2,62 secondes et l'impact final vers 3,35 secondes ; mesurer les décalages effectivement audibles, idéalement sous 80 ms.
4. Mesure durée totale, crêtes, clics, saturation, équilibre voix/musique et niveau de l'impact.
5. Vérifie que l'audio est chargé sans réseau et joué une fois, que sa ressource est libérée au changement d'écran et qu'il ne s'entend pas lors d'une reprise de navigation.
6. Mesure sur TCL Android TV ARMv7 : latence audio, synchronisation avec les mouvements, mémoire, taille de l'APK et stabilité en sortie audio HDMI/ARC.
7. Vérifie que chargement initial et profils fonctionnent sans attendre la piste sonore et que le mode « réduire les animations » reste respecté.
8. Passe `:app:testDebugUnitTest` et `:app:assembleDebug`. Échec CI = refus.

**État au 13 novembre 2026 :** la version Android TV embarque désormais le MP3 `app/src/main/res/raw/myo_intro_voice6.mp3`, fichier de 14 564 octets contenant la voix 6 et l'impact final. `MyoSplash.kt` le lit via `MediaPlayer` au lieu de `TextToSpeech` et de l'ancien jingle séparé. Le hash SHA-256 `2f7344884618244b5b5ac185d0bf5887115638620a3d9433fe699821e7e03c39` est vérifié pendant `preBuild`.

**Vérification de livraison :** GitHub Actions n°226 (<https://github.com/TRwholikejasontodd/MovixOpenSource/actions/runs/43499887710>) a réussi tests JVM et compilation Android TV. L'APK `app-armeabi-v7a-debug.apk` téléchargé de l'artefact `myo-tv-armv7-apk` a été ouvert et contrôlé indépendamment : entrée `res/raw/myo_intro_voice6.mp3`, 14 564 octets, SHA-256 identique au MP3 approuvé, ABI native `armeabi-v7a`. **Verdict d'intégrité du fichier et de compilation : ACCEPTÉ.**

**Verdict sur TCL réelle : NON TESTABLE dans cet environnement.** Le rendu sonore, la synchronisation perçue (latence du circuit audio), l'utilisation mémoire et le parcours télécommande nécessitent une vérification sur appareil. Aucun sous-agent indépendant n'a effectué ces tests physiques.

## Preuves disponibles
- Vidéo de référence voix 6 : `05_MYO_enfantine_aerienne.mp4` (4 secondes).
- Export MP3 comparé au WAV référence ; corrélation du signal > 99,9 %, crête < 0,13 pleine échelle.
- Un script de vérification autonome `controle_qualite_myo_audio.py` a été exécuté hors de la fonction de montage.
- **Aucun sous-agent IA de revue de code indépendant n'était accessible directement dans cette session.** Ce document est une demande d'audit à transmettre à un agent compétent, pas la preuve qu'un agent a terminé ce travail.

## Livrable attendu de l'auditeur
Tableau de preuves par test, défauts Bloquant/Majeur/Mineur, patch ou correction minimale pour chacun, et séparation explicite entre preuves automatisées et test TV réel. Aucune validation tant que la voix fixe approuvée n'est pas intégrée.
