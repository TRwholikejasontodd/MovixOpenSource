package com.movix.tv.data.local

import com.movix.tv.data.account.SyncMerge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID

/** Profil créé sur la télévision ; ne dépend d'aucun compte ni service. */
@Serializable
data class MyoLocalProfile(
    val id: String,
    val name: String,
    val avatar: String = "astronaut",
)

/**
 * Une seule bibliothèque active dans les clés historiques de l'application.
 * Chaque bascule sauvegarde atomiquement les données de l'ancien profil puis
 * remplace celles-ci par la bibliothèque du nouveau. Ce modèle conserve
 * les écrans de l'application sans réécrire tous leurs accès au stockage.
 *
 * Un compte distant lié continue d'utiliser [SyncManager] et son sélecteur :
 * les profils locaux sont seulement pour les télés non connectées.
 */
class LocalProfiles(private val store: LocalStore) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val profilesSerializer = ListSerializer(MyoLocalProfile.serializer())
    private val shelfSerializer = MapSerializer(String.serializer(), String.serializer())

    fun profiles(values: Map<String, String> = store.values.value): List<MyoLocalProfile> =
        values[PROFILES]?.let { runCatching { json.decodeFromString(profilesSerializer, it) }.getOrNull() }.orEmpty()

    fun activeId(values: Map<String, String> = store.values.value): String? = values[ACTIVE]

    /** Premier profil créé uniquement si aucune liste n'existe déjà. */
    suspend fun ensureDefault() = mutex.withLock {
        val values = store.snapshot()
        if (profiles(values).isEmpty()) {
            val first = MyoLocalProfile(UUID.randomUUID().toString(), "Invité")
            store.writeAll(mapOf(PROFILES to json.encodeToString(profilesSerializer, listOf(first)), ACTIVE to first.id))
        }
    }

    suspend fun create(name: String, avatar: String): Boolean = mutex.withLock {
        val old = store.snapshot()
        val list = profiles(old)
        val trimmed = name.trim().take(24)
        if (trimmed.isBlank() || list.size >= 8) return@withLock false
        val added = MyoLocalProfile(UUID.randomUUID().toString(), trimmed, avatar)
        store.writeAll(mapOf(PROFILES to json.encodeToString(profilesSerializer, list + added)))
        true
    }

    suspend fun edit(id: String, name: String, avatar: String): Boolean = mutex.withLock {
        val list = profiles(store.snapshot())
        val trimmed = name.trim().take(24)
        if (trimmed.isBlank() || list.none { it.id == id }) return@withLock false
        store.writeAll(mapOf(PROFILES to json.encodeToString(
            profilesSerializer,
            list.map { if (it.id == id) it.copy(name = trimmed, avatar = avatar) else it },
        )))
        true
    }

    suspend fun switch(id: String): Boolean = mutex.withLock {
        switchLocked(id, store.snapshot())
    }

    private suspend fun switchLocked(id: String, values: Map<String, String>): Boolean {
        val list = profiles(values)
        if (list.none { it.id == id }) return false
        val oldId = values[ACTIVE] ?: list.firstOrNull()?.id ?: return false
        if (oldId == id) return true
        val present = values.filterKeys(ProfileLibraryKeys::belongsToProfile)
        val restored = values[SHELF + id]?.let {
            runCatching { json.decodeFromString(shelfSerializer, it) }.getOrNull()
        }.orEmpty().filterKeys(ProfileLibraryKeys::belongsToProfile)
        val cleared = present.keys.associateWith<String, String?> { null }
        // Ne jamais supprimer des clés appartenant au compte/TV.
        store.writeAll(
            cleared + restored + mapOf(
                ACTIVE to id,
                SHELF + oldId to json.encodeToString(shelfSerializer, present),
            ),
        )
        return true
    }

    suspend fun remove(id: String): Boolean = mutex.withLock {
        val snapshot = store.snapshot()
        val list = profiles(snapshot)
        if (list.size < 2 || list.none { it.id == id }) return@withLock false
        if (snapshot[ACTIVE] == id) {
            val next = list.first { it.id != id }.id
            if (!switchLocked(next, snapshot)) return@withLock false
        }
        store.writeAll(mapOf(
            PROFILES to json.encodeToString(profilesSerializer, list.filterNot { it.id == id }),
            SHELF + id to null,
        ))
        true
    }

    private companion object {
        const val PROFILES = "myo_local_profiles_v1"
        const val ACTIVE = "myo_local_active_v1"
        const val SHELF = "myo_local_library_v1_"
    }
}

/** Uniquement les données propres au spectateur ; réseau et préférences TV restent intacts. */
object ProfileLibraryKeys {
    private val ui = setOf(
        TvKeys.WATCH_META, TvKeys.SEARCH_RECENT, TvKeys.PLAYER_PREFS,
        UiKeys.SUBTITLE_STYLE, UiKeys.HOME_ROWS, UiKeys.RAIL_HIDDEN,
        UiKeys.AUTOPLAY_NEXT, UiKeys.BANNER_VIDEO, UiKeys.ACCENT,
    )
    fun belongsToProfile(key: String): Boolean = SyncMerge.isSynced(key) || key in ui
}
