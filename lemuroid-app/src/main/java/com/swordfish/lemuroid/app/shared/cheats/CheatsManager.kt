package com.swordfish.lemuroid.app.shared.cheats

import android.content.Context
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.libretrodroid.GLRetroView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** Downloads cheat lists from the libretro database and remembers which cheats are on for each game. */
object CheatsManager {
    data class Cheat(val index: Int, val description: String, val code: String)

    private const val BASE = "https://raw.githubusercontent.com/libretro/libretro-database/master/cht/"

    private fun cacheFile(
        context: Context,
        game: Game,
    ) = File(File(context.filesDir, "cheats").apply { mkdirs() }, "${game.id}.cht")

    /** Returns cached cheats, or null when they were never downloaded. */
    fun cached(
        context: Context,
        game: Game,
    ): List<Cheat>? {
        val file = cacheFile(context, game)
        return if (file.exists()) parse(file.readText()) else null
    }

    /** Downloads the cheat file for the game. Blocking, call it off the main thread. */
    fun download(
        context: Context,
        game: Game,
    ): List<Cheat> {
        val system = runCatching { GameSystem.findById(game.systemId) }.getOrNull() ?: return emptyList()
        val folder = system.libretroFullName
        for (name in candidateNames(game)) {
            val url = BASE + enc(folder) + "/" + enc(name) + ".cht"
            val text = fetch(url) ?: continue
            val cheats = parse(text)
            if (cheats.isNotEmpty()) {
                cacheFile(context, game).writeText(text)
                return cheats
            }
        }
        cacheFile(context, game).writeText("cheats = 0\n")
        return emptyList()
    }

    fun clearCache(
        context: Context,
        game: Game,
    ) {
        cacheFile(context, game).delete()
    }

    /** Names to try: the official title, the title used for the cover, then the file name. */
    private fun candidateNames(game: Game): List<String> {
        val fromCover =
            game.coverFrontUrl?.substringAfterLast("/")?.removeSuffix(".png")?.let {
                runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull()
            }
        return listOfNotNull(game.title, fromCover, game.fileName.substringBeforeLast('.'))
            .map { it.replace(Regex("[&*/:`<>?\\\\|]"), "_") }
            .distinct()
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun fetch(url: String): String? =
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            try {
                if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()

    fun parse(text: String): List<Cheat> {
        val values = mutableMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val i = line.indexOf('=')
            if (i > 0) values[line.substring(0, i).trim()] = line.substring(i + 1).trim().trim('"')
        }
        val count = values["cheats"]?.toIntOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { n ->
            val code = values["cheat${n}_code"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Cheat(n, values["cheat${n}_desc"]?.ifBlank { null } ?: "Cheat ${n + 1}", code)
        }
    }

    private fun prefKey(game: Game) = "cheats_enabled_${game.id}"

    fun enabled(
        context: Context,
        game: Game,
    ): Set<Int> =
        SharedPreferencesHelper.getSharedPreferences(context)
            .getStringSet(prefKey(game), emptySet())!!
            .mapNotNull { it.toIntOrNull() }.toSet()

    fun setEnabled(
        context: Context,
        game: Game,
        indexes: Set<Int>,
    ) {
        SharedPreferencesHelper.getSharedPreferences(context).edit()
            .putStringSet(prefKey(game), indexes.map { it.toString() }.toSet()).commit()
    }

    /** Sends the cheat state to the running game. Blocking, call it off the main thread. */
    fun apply(
        context: Context,
        game: Game,
        view: GLRetroView,
    ) {
        val cheats = cached(context, game) ?: return
        val on = enabled(context, game)
        cheats.forEachIndexed { position, cheat ->
            runCatching { view.setCheat(position, cheat.index in on, cheat.code) }
        }
    }
}
