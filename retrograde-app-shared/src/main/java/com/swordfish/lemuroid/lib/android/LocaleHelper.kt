package com.swordfish.lemuroid.lib.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import java.util.Locale

/** Keeps the whole app in the language chosen inside the app (English by default). */
object LocaleHelper {
    const val PREF_KEY = "app_language"
    const val DEFAULT_LANGUAGE = "en"
    val SUPPORTED_LANGUAGES = listOf("en", "ar", "es")

    fun getLanguage(context: Context): String {
        val value =
            try {
                SharedPreferencesHelper.getSharedPreferences(context).getString(PREF_KEY, DEFAULT_LANGUAGE)
            } catch (e: Exception) {
                null
            }
        return value?.takeIf { it in SUPPORTED_LANGUAGES } ?: DEFAULT_LANGUAGE
    }

    fun setLanguage(
        context: Context,
        language: String,
    ) {
        SharedPreferencesHelper.getSharedPreferences(context).edit().putString(PREF_KEY, language).commit()
    }

    private fun toLocale(language: String): Locale =
        when (language) {
            "ar" -> Locale("ar", "SA")
            "es" -> Locale("es", "ES")
            else -> Locale("en")
        }

    fun wrap(base: Context): Context {
        val locale = toLocale(getLanguage(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    @Suppress("DEPRECATION")
    fun applyToResources(context: Context) {
        val locale = toLocale(getLanguage(context))
        Locale.setDefault(locale)
        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun restartApp(activity: Activity) {
        val pm = activity.packageManager
        val isTv = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val intent =
            (if (isTv) pm.getLeanbackLaunchIntentForPackage(activity.packageName) else null)
                ?: pm.getLaunchIntentForPackage(activity.packageName)
                ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finishAffinity()
    }
}
