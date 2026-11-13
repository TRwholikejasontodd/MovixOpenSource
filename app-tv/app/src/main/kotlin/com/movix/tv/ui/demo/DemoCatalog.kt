package com.movix.tv.ui.demo

import androidx.compose.runtime.Immutable

/**
 * An intentionally small, legally reusable, independent first-launch catalogue.
 *
 * These URLs are public demonstration MP4s, not TMDB catalogue videos or Movix sources.
 * Their availability on the external sample CDN is not guaranteed. A failure must be
 * shown as a playback error rather than fabricated as a valid source.
 */
@Immutable
data class DemoFilm(
    val id: String,
    val title: String,
    val synopsis: String,
    val year: Int,
    val videoUrl: String,
    val attribution: String,
    val license: String,
)

object DemoCatalog {
    val films: List<DemoFilm> = listOf(
        DemoFilm(
            id = "big-buck-bunny",
            title = "Big Buck Bunny",
            synopsis = "Un lapin paisible va donner une leçon à trois petits fauteurs de troubles. Court-métrage d'animation.",
            year = 2008,
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            attribution = "© 2008 Blender Foundation — www.bigbuckbunny.org. Générique original conservé.",
            license = "CC BY 3.0",
        ),
        DemoFilm(
            id = "elephants-dream",
            title = "Elephants Dream",
            synopsis = "Deux voyageurs explorent un monde mécanique mystérieux. Premier film ouvert de la Blender Foundation.",
            year = 2006,
            videoUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            attribution = "© 2006 Blender Foundation / Netherlands Media Art Institute — www.elephantsdream.org. Générique original conservé.",
            license = "CC BY 2.5",
        ),
    )

    fun find(id: String): DemoFilm? = films.firstOrNull { it.id == id }
}
