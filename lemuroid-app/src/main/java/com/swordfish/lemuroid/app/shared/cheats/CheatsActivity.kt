package com.swordfish.lemuroid.app.shared.cheats

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.android.LocaleHelper
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlin.concurrent.thread

/** Lists the cheats for one game. Works with touch and D-pad. */
class CheatsActivity : Activity() {
    private lateinit var game: Game
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val selected = mutableSetOf<Int>()

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = intent.getSerializableExtra(EXTRA_GAME) as Game
        selected.addAll(CheatsManager.enabled(this, game))

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(24), dp(24), dp(24))
            }
        root.addView(
            TextView(this).apply {
                text = getString(R.string.cheats_title)
                textSize = 26f
                setTextColor(Color.parseColor("#FFD23F"))
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        root.addView(
            TextView(this).apply {
                text = game.title
                textSize = 16f
                setTextColor(Color.parseColor("#DDE6F0"))
                setPadding(0, dp(4), 0, dp(8))
            },
        )
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val done =
            Button(this).apply {
                text = getString(R.string.cheats_done)
                textSize = 18f
                setOnClickListener { finishWithResult() }
            }
        val refresh =
            Button(this).apply {
                text = getString(R.string.cheats_refresh)
                textSize = 16f
                setOnClickListener {
                    CheatsManager.clearCache(this@CheatsActivity, game)
                    load(forceDownload = true)
                }
            }
        buttons.addView(done)
        buttons.addView(refresh, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(12) })
        root.addView(buttons)
        status =
            TextView(this).apply {
                textSize = 17f
                setTextColor(Color.WHITE)
                setPadding(0, dp(14), 0, dp(8))
            }
        root.addView(status)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#0B1A30"))
                addView(root)
            },
        )
        done.requestFocus()
        load(forceDownload = false)
    }

    private fun load(forceDownload: Boolean) {
        list.removeAllViews()
        val cached = if (forceDownload) null else CheatsManager.cached(this, game)
        if (cached != null) {
            show(cached)
            return
        }
        status.text = getString(R.string.cheats_loading)
        thread {
            val cheats = CheatsManager.download(applicationContext, game)
            runOnUiThread { if (!isFinishing) show(cheats) }
        }
    }

    private fun show(cheats: List<CheatsManager.Cheat>) {
        list.removeAllViews()
        if (cheats.isEmpty()) {
            status.text = getString(R.string.cheats_none)
            return
        }
        status.text = getString(R.string.cheats_hint)
        val normal =
            GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#14284A"))
            }
        cheats.forEach { cheat ->
            val row =
                CheckBox(this).apply {
                    text = cheat.description
                    textSize = 17f
                    setTextColor(Color.WHITE)
                    isChecked = cheat.index in selected
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background =
                        GradientDrawable().apply {
                            cornerRadius = dp(12).toFloat()
                            setColor(Color.parseColor("#14284A"))
                        }
                    setOnFocusChangeListener { v, hasFocus ->
                        v.background =
                            if (hasFocus) {
                                GradientDrawable().apply {
                                    cornerRadius = dp(12).toFloat()
                                    setColor(Color.parseColor("#1C3866"))
                                    setStroke(dp(3), Color.parseColor("#FFD23F"))
                                }
                            } else {
                                normal.constantState!!.newDrawable()
                            }
                    }
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selected.add(cheat.index) else selected.remove(cheat.index)
                        CheatsManager.setEnabled(this@CheatsActivity, game, selected)
                    }
                }
            list.addView(
                row,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                },
            )
        }
    }

    private fun finishWithResult() {
        CheatsManager.setEnabled(this, game, selected)
        setResult(RESULT_OK)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishWithResult()
    }

    companion object {
        const val EXTRA_GAME = "EXTRA_GAME"
    }
}
