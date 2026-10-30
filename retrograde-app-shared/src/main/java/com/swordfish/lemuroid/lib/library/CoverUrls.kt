package com.swordfish.lemuroid.lib.library

/** Builds libretro thumbnail URLs. Uses https because some TVs block plain http images. */
object CoverUrls {
    private val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|]")

    fun build(
        systemId: String,
        name: String,
    ): String? {
        val system = runCatching { GameSystem.findById(systemId) }.getOrNull() ?: return null
        val systemName = if (system.id == SystemID.MAME2003PLUS) "MAME" else system.libretroFullName
        val cleanName = name.substringBeforeLast('.').replace(THUMB_REPLACE, "_")
        if (cleanName.isBlank()) return null
        return "https://thumbnails.libretro.com/$systemName/Named_Boxarts/$cleanName.png"
    }

    fun upgrade(url: String?): String? = url?.replaceFirst("http://thumbnails.libretro.com", "https://thumbnails.libretro.com")
}
