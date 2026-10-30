package com.swordfish.lemuroid.app.shared.library

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.android.LocaleHelper
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import java.io.File
import java.io.InputStream
import kotlin.concurrent.thread

/** Copies games from the chosen USB folder into the TV storage so they stay after the USB is removed. */
class ImportGamesActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var closeButton: Button

    @Volatile
    private var cancelled = false

    private class Source(val name: String, val size: Long, val open: () -> InputStream?)

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(32), dp(32), dp(32), dp(32))
                setBackgroundColor(Color.parseColor("#0B1A30"))
            }
        root.addView(
            TextView(this).apply {
                text = getString(R.string.import_title)
                textSize = 26f
                setTextColor(Color.parseColor("#FFD23F"))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            },
        )
        status =
            TextView(this).apply {
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, dp(20))
            }
        root.addView(status)
        progress =
            ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
            }
        root.addView(progress, LinearLayout.LayoutParams(dp(360), LinearLayout.LayoutParams.WRAP_CONTENT))
        closeButton =
            Button(this).apply {
                text = getString(R.string.import_close)
                textSize = 18f
                setOnClickListener {
                    cancelled = true
                    finish()
                }
            }
        root.addView(
            closeButton,
            LinearLayout.LayoutParams(dp(240), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24) },
        )
        setContentView(root)
        closeButton.requestFocus()

        status.text = getString(R.string.import_searching)
        thread { runImport() }
    }

    private fun ui(block: () -> Unit) = runOnUiThread { if (!isFinishing) block() }

    private fun runImport() {
        val target = File(getExternalFilesDir(null), "roms").apply { mkdirs() }
        val existing = target.listFiles()?.map { it.name }?.toSet() ?: emptySet()

        val sources = listSources()
        if (sources == null) {
            ui { status.text = getString(R.string.import_no_folder) }
            return
        }

        val newFiles = sources.filter { it.name !in existing && !it.name.startsWith(".") }
        if (newFiles.isEmpty()) {
            ui {
                status.text = getString(R.string.import_nothing)
                progress.progress = 100
            }
            return
        }

        var copied = 0
        for ((index, file) in newFiles.withIndex()) {
            if (cancelled) return
            ui {
                status.text = getString(R.string.import_copying, index + 1, newFiles.size, file.name)
                progress.progress = index * 100 / newFiles.size
            }
            if (target.usableSpace < file.size + 50L * 1024 * 1024) {
                ui { status.text = getString(R.string.import_no_space, copied) }
                LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
                return
            }
            val out = File(target, file.name)
            val ok =
                runCatching {
                    file.open()?.use { input -> out.outputStream().use { input.copyTo(it) } } != null
                }.getOrDefault(false)
            if (ok) copied++ else out.delete()
        }

        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
        ui {
            status.text = getString(R.string.import_done, copied)
            progress.progress = 100
        }
    }

    @Suppress("DEPRECATION")
    private fun listSources(): List<Source>? {
        val prefs = SharedPreferencesHelper.getLegacySharedPreferences(this)
        val treeUri = prefs.getString(getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder), null)
        val legacyPath = prefs.getString(getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder), null)

        if (treeUri != null) {
            val root = DocumentFile.fromTreeUri(this, Uri.parse(treeUri)) ?: return null
            if (!root.exists()) return null
            val result = mutableListOf<Source>()
            val queue = ArrayDeque(listOf(root))
            while (queue.isNotEmpty()) {
                queue.removeFirst().listFiles().forEach { doc ->
                    when {
                        doc.isDirectory -> queue.add(doc)
                        doc.isFile && doc.name != null ->
                            result.add(Source(doc.name!!, doc.length()) { contentResolver.openInputStream(doc.uri) })
                    }
                }
            }
            return result
        }

        if (legacyPath != null) {
            val root = File(legacyPath)
            if (!root.exists()) return null
            return root.walkTopDown().filter { it.isFile }.map { f -> Source(f.name, f.length()) { f.inputStream() } }.toList()
        }
        return null
    }
}
