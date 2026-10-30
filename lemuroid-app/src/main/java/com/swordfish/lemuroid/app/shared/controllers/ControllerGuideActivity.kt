package com.swordfish.lemuroid.app.shared.controllers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.android.LocaleHelper

/** Simple screen that explains how to pair the most common controllers. Works with touch and D-pad. */
class ControllerGuideActivity : Activity() {
    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(24), dp(20), dp(32))
            }

        content.addView(
            text(getString(R.string.controller_guide_title), 26f, Color.parseColor("#FFD23F"), bold = true),
        )
        content.addView(text(getString(R.string.controller_guide_intro), 16f, Color.parseColor("#DDE6F0")))

        val openBluetooth =
            Button(this).apply {
                text = getString(R.string.controller_guide_open_bluetooth)
                textSize = 18f
                setOnClickListener { openBluetoothSettings() }
            }
        content.addView(openBluetooth, spaced())

        listOf(
            R.string.controller_guide_xbox_name to R.string.controller_guide_xbox_steps,
            R.string.controller_guide_ps4_name to R.string.controller_guide_ps4_steps,
            R.string.controller_guide_ps5_name to R.string.controller_guide_ps5_steps,
            R.string.controller_guide_switch_name to R.string.controller_guide_switch_steps,
            R.string.controller_guide_8bitdo_name to R.string.controller_guide_8bitdo_steps,
            R.string.controller_guide_other_name to R.string.controller_guide_other_steps,
            R.string.controller_guide_after_name to R.string.controller_guide_after_steps,
        ).forEach { (name, steps) ->
            content.addView(card(getString(name), getString(steps)), spaced())
        }

        val scroll =
            ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#0B1A30"))
                isFillViewport = true
                addView(content)
            }
        setContentView(scroll)
        openBluetooth.requestFocus()
    }

    private fun spaced() =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(14) }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        textDirection = View.TEXT_DIRECTION_LOCALE
        gravity = Gravity.START
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun card(
        title: String,
        steps: String,
    ): View {
        val normal =
            GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#14284A"))
                setStroke(dp(2), Color.parseColor("#14284A"))
            }
        val focused =
            GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#1C3866"))
                setStroke(dp(3), Color.parseColor("#FFD23F"))
            }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = normal
            isFocusable = true
            setOnFocusChangeListener { v, hasFocus -> v.background = if (hasFocus) focused else normal }
            addView(text(title, 19f, Color.WHITE, bold = true))
            addView(text(steps, 16f, Color.parseColor("#DDE6F0")))
        }
    }

    private fun openBluetoothSettings() {
        val intents = listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                // Try the next option
            }
        }
    }
}
