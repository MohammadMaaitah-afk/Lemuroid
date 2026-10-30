package com.swordfish.lemuroid.app.shared.settings

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.android.LocaleHelper

/** Lets the player choose the app language. Works with touch and D-pad. */
class LanguagePickerActivity : Activity() {
    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val current = LocaleHelper.getLanguage(this)
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(32), dp(32), dp(32), dp(32))
                setBackgroundColor(Color.parseColor("#0B1A30"))
            }

        root.addView(
            TextView(this).apply {
                text = getString(R.string.settings_title_language)
                textSize = 26f
                setTextColor(Color.parseColor("#FFD23F"))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(20))
            },
        )

        val options = listOf("en" to "English", "ar" to "العربية", "es" to "Español")
        var focusTarget: Button? = null
        options.forEach { (code, label) ->
            val button =
                Button(this).apply {
                    text = if (code == current) "✓  $label" else label
                    textSize = 20f
                    setOnClickListener {
                        if (code != current) {
                            LocaleHelper.setLanguage(this@LanguagePickerActivity, code)
                            LocaleHelper.restartApp(this@LanguagePickerActivity)
                        } else {
                            finish()
                        }
                    }
                }
            if (code == current) focusTarget = button
            root.addView(
                button,
                LinearLayout.LayoutParams(dp(320), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(10)
                },
            )
        }
        setContentView(root)
        focusTarget?.requestFocus()
    }
}
