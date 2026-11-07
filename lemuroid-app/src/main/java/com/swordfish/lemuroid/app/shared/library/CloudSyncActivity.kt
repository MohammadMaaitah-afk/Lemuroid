package com.swordfish.lemuroid.app.shared.library

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.android.LocaleHelper
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Downloads the games from a Google Drive folder shared with "Anyone with the link"
 * into the device storage, then refreshes the library. Only new files are downloaded.
 */
class CloudSyncActivity : Activity() {
    private lateinit var linkInput: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var syncButton: Button

    @Volatile
    private var running = false

    @Volatile
    private var cancelled = false

    private data class CloudFile(val id: String, val name: String)

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = SharedPreferencesHelper.getSharedPreferences(this)

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(28), dp(28), dp(28), dp(28))
            }
        root.addView(
            TextView(this).apply {
                text = getString(R.string.cloud_title)
                textSize = 26f
                setTextColor(Color.parseColor("#FFD23F"))
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        root.addView(
            TextView(this).apply {
                text = getString(R.string.cloud_help)
                textSize = 16f
                setTextColor(Color.parseColor("#DDE6F0"))
                setPadding(0, dp(12), 0, dp(12))
            },
        )
        linkInput =
            EditText(this).apply {
                hint = getString(R.string.cloud_link_hint)
                setText(prefs.getString(PREF_LINK, ""))
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#7F8FA6"))
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_DONE
                textDirection = android.view.View.TEXT_DIRECTION_LTR
            }
        root.addView(linkInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        syncButton =
            Button(this).apply {
                text = getString(R.string.cloud_sync_now)
                textSize = 18f
                setOnClickListener { startSync() }
            }
        root.addView(
            syncButton,
            LinearLayout.LayoutParams(dp(280), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) },
        )
        status =
            TextView(this).apply {
                textSize = 17f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, dp(12))
            }
        root.addView(status)
        progress =
            ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                visibility = android.view.View.INVISIBLE
            }
        root.addView(progress, LinearLayout.LayoutParams(dp(360), LinearLayout.LayoutParams.WRAP_CONTENT))
        val close =
            Button(this).apply {
                text = getString(R.string.import_close)
                textSize = 18f
                setOnClickListener {
                    cancelled = true
                    finish()
                }
            }
        root.addView(
            close,
            LinearLayout.LayoutParams(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20) },
        )

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#0B1A30"))
                addView(root)
            },
        )

        // If a folder is already saved, sync straight away so new games show up without extra steps
        if (folderIdFrom(linkInput.text.toString()) != null) startSync() else linkInput.requestFocus()
    }

    private fun ui(block: () -> Unit) = runOnUiThread { if (!isFinishing) block() }

    private fun startSync() {
        if (running) return
        val folderId = folderIdFrom(linkInput.text.toString())
        if (folderId == null) {
            status.text = getString(R.string.cloud_bad_link)
            return
        }
        SharedPreferencesHelper.getSharedPreferences(this).edit()
            .putString(PREF_LINK, linkInput.text.toString().trim()).apply()
        running = true
        syncButton.isEnabled = false
        progress.visibility = android.view.View.VISIBLE
        progress.progress = 0
        status.text = getString(R.string.import_searching)
        thread {
            try {
                runSync(folderId)
            } finally {
                running = false
                ui { syncButton.isEnabled = true }
            }
        }
    }

    private fun runSync(folderId: String) {
        val target = File(getExternalFilesDir(null), "roms").apply { mkdirs() }
        val existing = target.listFiles()?.map { it.name }?.toSet() ?: emptySet()

        val files =
            runCatching { listFolder(folderId, depth = 0) }.getOrElse {
                ui { status.text = getString(R.string.cloud_cannot_open) }
                return
            }
        if (files.isEmpty()) {
            ui { status.text = getString(R.string.cloud_cannot_open) }
            return
        }

        val newFiles = files.filter { it.name !in existing }
        if (newFiles.isEmpty()) {
            LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
            ui {
                status.text = getString(R.string.cloud_up_to_date, files.size)
                progress.progress = 100
            }
            return
        }

        var downloaded = 0
        var failed = 0
        for ((index, file) in newFiles.withIndex()) {
            if (cancelled) return
            ui {
                status.text = getString(R.string.cloud_downloading, index + 1, newFiles.size, file.name)
                progress.progress = index * 100 / newFiles.size
            }
            if (target.usableSpace < 100L * 1024 * 1024) {
                ui { status.text = getString(R.string.import_no_space, downloaded) }
                break
            }
            if (download(file, File(target, file.name))) downloaded++ else failed++
        }

        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
        ui {
            progress.progress = 100
            status.text =
                if (failed == 0) {
                    getString(R.string.cloud_done, downloaded)
                } else {
                    getString(R.string.cloud_done_with_errors, downloaded, failed)
                }
        }
    }

    /** Reads the public folder page and returns its files, looking into sub folders too. */
    private fun listFolder(
        folderId: String,
        depth: Int,
    ): List<CloudFile> {
        val html = fetchText("https://drive.google.com/embeddedfolderview?id=$folderId")
        val result = mutableListOf<CloudFile>()
        html.split("class=\"flip-entry\"").drop(1).forEach { chunk ->
            val href = Regex("href=\"([^\"]+)\"").find(chunk)?.groupValues?.get(1) ?: return@forEach
            val rawTitle = Regex("class=\"flip-entry-title\">([^<]*)<").find(chunk)?.groupValues?.get(1) ?: return@forEach
            val title = Html.fromHtml(rawTitle, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            val fileId = Regex("/file/d/([A-Za-z0-9_-]+)").find(href)?.groupValues?.get(1)
            val subFolder = Regex("/folders/([A-Za-z0-9_-]+)").find(href)?.groupValues?.get(1)
            when {
                fileId != null && title.isNotBlank() -> result.add(CloudFile(fileId, title))
                subFolder != null && depth < 3 -> result.addAll(listFolder(subFolder, depth + 1))
            }
        }
        return result
    }

    private fun download(
        file: CloudFile,
        out: File,
    ): Boolean {
        val tmp = File(out.parentFile, ".${out.name}.part")
        return runCatching {
            val conn = open("https://drive.usercontent.google.com/download?id=${file.id}&export=download&confirm=t")
            try {
                // An HTML answer means Google refused the download (private file or daily limit)
                if (conn.responseCode != 200 || conn.contentType?.startsWith("text/html") == true) return false
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
            tmp.renameTo(out)
        }.getOrElse {
            tmp.delete()
            false
        }
    }

    private fun fetchText(url: String): String {
        val conn = open(url)
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(5) {
            val conn = current.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) 16BitLegends")
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: return conn
                conn.disconnect()
                current = URL(current, location)
            } else {
                return conn
            }
        }
        error("Too many redirects")
    }

    companion object {
        const val PREF_LINK = "cloud_drive_link"

        fun folderIdFrom(link: String): String? {
            val text = link.trim()
            Regex("folders/([A-Za-z0-9_-]{10,})").find(text)?.let { return it.groupValues[1] }
            Regex("[?&]id=([A-Za-z0-9_-]{10,})").find(text)?.let { return it.groupValues[1] }
            return Regex("^[A-Za-z0-9_-]{20,}$").find(text)?.value
        }
    }
}
