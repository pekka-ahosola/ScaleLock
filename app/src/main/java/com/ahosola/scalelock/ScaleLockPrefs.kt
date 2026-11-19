package com.ahosola.scalelock

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

object ScaleLockPrefs {
    private const val PREFS_NAME = "scale_lock_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_UNLOCKED_DATE = "unlocked_date"
    private const val KEY_LAST_WEIGHT_KG = "last_weight_kg"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
    private const val KEY_INITIALIZED_DEFAULTS = "initialized_defaults"

    const val START_HOUR = 5
    const val START_MINUTE = 0
    const val END_HOUR = 11
    const val END_MINUTE = 0

    // Never allow blocking critical system, safety, or weigh-in apps
    val NEVER_BLOCK_PACKAGES = setOf(
        "com.ahosola.scalelock",
        "com.withings.wiscale2",
        "com.google.android.apps.healthdata",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.google.android.apps.messaging",
        "com.google.android.deskclock",
        "com.android.systemui",
        "com.google.android.apps.nexuslauncher",
        "com.google.android.inputmethod.latin"
    )

    val DEFAULT_DISTRACTING_PACKAGES = setOf(
        "com.android.chrome",
        "com.google.android.youtube",
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.facebook.katana",
        "com.snapchat.android"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isUnlockedToday(context: Context): Boolean {
        val savedDate = prefs(context).getString(KEY_UNLOCKED_DATE, null)
        return savedDate == LocalDate.now().toString()
    }

    fun markUnlockedToday(context: Context, weightKg: Double) {
        prefs(context).edit()
            .putString(KEY_UNLOCKED_DATE, LocalDate.now().toString())
            .putFloat(KEY_LAST_WEIGHT_KG, weightKg.toFloat())
            .apply()
    }

    fun clearUnlockedToday(context: Context) {
        prefs(context).edit().remove(KEY_UNLOCKED_DATE).apply()
    }

    fun getLastWeightKg(context: Context): Double? {
        val value = prefs(context).getFloat(KEY_LAST_WEIGHT_KG, -1f)
        return if (value > 0f) value.toDouble() else null
    }

    fun isWithinLockWindow(now: LocalTime = LocalTime.now()): Boolean {
        val start = LocalTime.of(START_HOUR, START_MINUTE)
        val end = LocalTime.of(END_HOUR, END_MINUTE)
        return !now.isBefore(start) && now.isBefore(end)
    }

    fun isBlockerActiveNow(context: Context): Boolean {
        return isEnabled(context) && isWithinLockWindow() && !isUnlockedToday(context)
    }

    fun getBlockedPackages(context: Context): Set<String> {
        val p = prefs(context)
        if (!p.getBoolean(KEY_INITIALIZED_DEFAULTS, false)) {
            val installed = context.packageManager.getInstalledApplications(0)
                .map { it.packageName }
                .toSet()
            val defaults = DEFAULT_DISTRACTING_PACKAGES.intersect(installed)
            p.edit()
                .putStringSet(KEY_BLOCKED_PACKAGES, defaults)
                .putBoolean(KEY_INITIALIZED_DEFAULTS, true)
                .apply()
            return defaults
        }
        return p.getStringSet(KEY_BLOCKED_PACKAGES, emptySet())?.toSet() ?: emptySet()
    }

    fun setBlockedPackages(context: Context, packages: Set<String>) {
        val safeSet = packages - NEVER_BLOCK_PACKAGES
        prefs(context).edit()
            .putStringSet(KEY_BLOCKED_PACKAGES, safeSet)
            .putBoolean(KEY_INITIALIZED_DEFAULTS, true)
            .apply()
    }
}
