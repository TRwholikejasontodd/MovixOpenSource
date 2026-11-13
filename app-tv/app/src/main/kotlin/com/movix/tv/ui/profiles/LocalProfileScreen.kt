package com.movix.tv.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.movix.tv.container
import com.movix.tv.data.local.MyoLocalProfile
import com.movix.tv.ui.components.PillButton
import com.movix.tv.ui.theme.MovixColors
import com.movix.tv.ui.theme.MovixType
import kotlinx.coroutines.launch

private data class Avatar(val key: String, val glyph: String, val color: Long)

private val avatars = listOf(
    Avatar("astronaut", "✦", 0xFF4C3C87),
    Avatar("pirate", "⚓", 0xFF2A5B76),
    Avatar("fox", "◆", 0xFFB75B31),
    Avatar("cat", "●", 0xFF7B4D9D),
    Avatar("dragon", "♛", 0xFF46715F),
    Avatar("moon", "☾", 0xFF364C84),
    Avatar("knight", "♜", 0xFF67556B),
    Avatar("planet", "◎", 0xFF7E4A83),
)

/**
 * Mode hors compte : profils indépendants, avec avatars vectoriels locaux.
 * Aucune image distante : stockage et accès disponibles sans connexion réseau.
 * Les profils d'un compte relié restent gérés par le sélecteur serveur existant.
 */
@Composable
fun LocalProfileScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val values by container.store.values.collectAsStateWithLifecycle()
    val profiles = container.localProfiles.profiles(values)
    val active = container.localProfiles.activeId(values)
    var manage by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var avatarIndex by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    val inEditor = creating || editingId != null

    fun openEditor(p: MyoLocalProfile?) {
        editingId = p?.id
        creating = p == null
        name = p?.name.orEmpty()
        avatarIndex = avatars.indexOfFirst { it.key == p?.avatar }.coerceAtLeast(0)
    }
    fun closeEditor() { editingId = null; creating = false }

    BackHandler {
        when {
            busy -> Unit
            inEditor -> closeEditor()
            manage -> manage = false
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF09060F)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (inEditor) "Personnaliser le profil" else "Qui regarde ?", style = MovixType.Hero)
            Spacer(Modifier.height(10.dp))
            Text(
                if (inEditor) "Nom et avatar conservés sur cette télévision"
                else "Chaque profil possède sa propre liste et sa progression",
                style = MovixType.Body,
            )
            Spacer(Modifier.height(30.dp))
            if (inEditor) {
                val a = avatars[avatarIndex]
                AvatarTile(a, name.ifBlank { "MYO" }, selected = true, onClick = {}, modifier = Modifier)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("‹ Avatar", { avatarIndex = (avatarIndex + avatars.size - 1) % avatars.size })
                    PillButton("Avatar ›", { avatarIndex = (avatarIndex + 1) % avatars.size })
                }
                Spacer(Modifier.height(16.dp))
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
                    modifier = Modifier.width(320.dp)
                        .background(Color(0xFF251B31), RoundedCornerShape(10.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (name.isBlank()) Text("Nom du profil", style = MovixType.Meta)
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Annuler", ::closeEditor)
                    PillButton("Enregistrer", {
                        if (name.isBlank() || busy) return@PillButton
                        busy = true
                        scope.launch {
                            if (creating) container.localProfiles.create(name, a.key)
                            else editingId?.let { container.localProfiles.edit(it, name, a.key) }
                            busy = false
                            closeEditor()
                        }
                    })
                    if (!creating && profiles.size > 1) {
                        PillButton("Supprimer", {
                            val toDelete = editingId ?: return@PillButton
                            if (busy) return@PillButton
                            busy = true
                            scope.launch {
                                container.localProfiles.remove(toDelete)
                                busy = false
                                closeEditor()
                            }
                        })
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    profiles.forEachIndexed { index, profile ->
                        AvatarTile(
                            avatar = avatars.firstOrNull { it.key == profile.avatar } ?: avatars.first(),
                            name = profile.name,
                            selected = active == profile.id,
                            onClick = {
                                if (manage) openEditor(profile)
                                else if (!busy) {
                                    busy = true
                                    scope.launch {
                                        val ok = container.localProfiles.switch(profile.id)
                                        busy = false
                                        if (ok) onDone()
                                    }
                                }
                            },
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (profiles.size < 8) PillButton("Créer un profil", { openEditor(null) })
                    PillButton(if (manage) "Terminer" else "Modifier les profils", { manage = !manage })
                }
            }
        }
    }
    LaunchedEffect(profiles.size, inEditor) {
        if (!inEditor && profiles.isNotEmpty()) runCatching { first.requestFocus() }
    }
}

@Composable
private fun AvatarTile(
    avatar: Avatar,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.width(118.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(110.dp)
                .border(2.dp, if (selected) MovixColors.Red else Color.Transparent, RoundedCornerShape(24.dp))
                .padding(4.dp)
                .background(Color(avatar.color), RoundedCornerShape(21.dp))
                .clickable(onClick = onClick)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            Text(avatar.glyph, color = Color.White, style = MovixType.Hero.copy(fontSize = 60.sp))
        }
        Spacer(Modifier.height(10.dp))
        Text(name, style = MovixType.Body, maxLines = 1)
    }
}
