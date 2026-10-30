package com.swordfish.lemuroid.app.shared.mugen

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.android.LocaleHelper

/** Opens Ikemen GO (the open source MUGEN engine) if installed, otherwise explains how to install it. */
class MugenActivity : Activity() {
    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launch = packageManager.getLaunchIntentForPackage(IKEMEN_PACKAGE)
        if (launch != null) {
            runCatching { startActivity(launch) }
            finish()
            return
        }

        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(24), dp(24), dp(32))
            }
        content.addView(
            TextView(this).apply {
                text = getString(R.string.mugen_title)
                textSize = 26f
                setTextColor(Color.parseColor("#FFD23F"))
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        content.addView(
            TextView(this).apply {
                text = getString(R.string.mugen_not_installed, RELEASES_URL)
                textSize = 17f
                setTextColor(Color.parseColor("#DDE6F0"))
                setTextIsSelectable(false)
                setPadding(0, dp(16), 0, dp(16))
            },
        )
        val close =
            Button(this).apply {
                text = getString(R.string.import_close)
                textSize = 18f
                setOnClickListener { finish() }
            }
        content.addView(close)
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#0B1A30"))
                addView(content)
            },
        )
        close.requestFocus()
    }

    companion object {
        const val IKEMEN_PACKAGE = "org.ikemen_engine.ikemen_go"
        const val RELEASES_URL = "github.com/ikemen-engine/Ikemen-GO/releases/latest"
    }
}
