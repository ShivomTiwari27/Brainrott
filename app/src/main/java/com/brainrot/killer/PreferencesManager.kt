package com.brainrot.killer

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("brainrot_killer_prefs", Context.MODE_PRIVATE)

    companion object {
        const val ACTION_LOCK_PHONE = 0
        const val ACTION_BLOCK_SCREEN = 1

        private const val KEY_REEL_LIMIT = "key_reel_limit"
        private const val KEY_COOLDOWN_MINUTES = "key_cooldown_minutes"
        private const val KEY_ACTION_TYPE = "key_action_type"
        private const val KEY_FLOATING_PILL = "key_floating_pill"

        private const val KEY_APP_INSTAGRAM = "key_app_instagram"
        private const val KEY_APP_YOUTUBE = "key_app_youtube"
        private const val KEY_APP_TIKTOK = "key_app_tiktok"
        private const val KEY_APP_FACEBOOK = "key_app_facebook"

        private const val KEY_SESSION_REEL_COUNT = "key_session_reel_count"
        private const val KEY_TODAY_TOTAL_REELS = "key_today_total_reels"
        private const val KEY_LOCKOUT_COUNT = "key_lockout_count"
        private const val KEY_LOCKOUT_UNTIL = "key_lockout_until"
        private const val KEY_LAST_DAY = "key_last_day"
        private const val KEY_LAST_SCROLL_TIME = "key_last_scroll_time"
    }

    var reelLimit: Int
        get() = prefs.getInt(KEY_REEL_LIMIT, 10)
        set(value) = prefs.edit().putInt(KEY_REEL_LIMIT, value).apply()

    var cooldownMinutes: Int
        get() = prefs.getInt(KEY_COOLDOWN_MINUTES, 10)
        set(value) = prefs.edit().putInt(KEY_COOLDOWN_MINUTES, value).apply()

    var actionType: Int
        get() = prefs.getInt(KEY_ACTION_TYPE, ACTION_LOCK_PHONE)
        set(value) = prefs.edit().putInt(KEY_ACTION_TYPE, value).apply()

    var showFloatingPill: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_PILL, true)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_PILL, value).apply()

    var isInstagramEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_INSTAGRAM, true)
        set(value) = prefs.edit().putBoolean(KEY_APP_INSTAGRAM, value).apply()

    var isYoutubeEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_YOUTUBE, true)
        set(value) = prefs.edit().putBoolean(KEY_APP_YOUTUBE, value).apply()

    var isTikTokEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_TIKTOK, true)
        set(value) = prefs.edit().putBoolean(KEY_APP_TIKTOK, value).apply()

    var isFacebookEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_FACEBOOK, true)
        set(value) = prefs.edit().putBoolean(KEY_APP_FACEBOOK, value).apply()

    var sessionReelCount: Int
        get() {
            checkAutoResetSession()
            return prefs.getInt(KEY_SESSION_REEL_COUNT, 0)
        }
        set(value) {
            prefs.edit()
                .putInt(KEY_SESSION_REEL_COUNT, value)
                .putLong(KEY_LAST_SCROLL_TIME, System.currentTimeMillis())
                .apply()
        }

    var todayTotalReels: Int
        get() {
            checkDailyReset()
            return prefs.getInt(KEY_TODAY_TOTAL_REELS, 0)
        }
        set(value) = prefs.edit().putInt(KEY_TODAY_TOTAL_REELS, value).apply()

    var lockoutCount: Int
        get() {
            checkDailyReset()
            return prefs.getInt(KEY_LOCKOUT_COUNT, 0)
        }
        set(value) = prefs.edit().putInt(KEY_LOCKOUT_COUNT, value).apply()

    var lockoutUntilTimestamp: Long
        get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCKOUT_UNTIL, value).apply()

    val isCurrentlyLockedOut: Boolean
        get() = System.currentTimeMillis() < lockoutUntilTimestamp

    val remainingLockoutMillis: Long
        get() = (lockoutUntilTimestamp - System.currentTimeMillis()).coerceAtLeast(0L)

    fun incrementReel(): Pair<Int, Int> {
        checkDailyReset()
        checkAutoResetSession()

        val currentSession = prefs.getInt(KEY_SESSION_REEL_COUNT, 0) + 1
        val todayTotal = prefs.getInt(KEY_TODAY_TOTAL_REELS, 0) + 1

        prefs.edit()
            .putInt(KEY_SESSION_REEL_COUNT, currentSession)
            .putInt(KEY_TODAY_TOTAL_REELS, todayTotal)
            .putLong(KEY_LAST_SCROLL_TIME, System.currentTimeMillis())
            .apply()

        return Pair(currentSession, todayTotal)
    }

    fun triggerLockout(): Long {
        val until = System.currentTimeMillis() + (cooldownMinutes * 60 * 1000L)
        val lockouts = lockoutCount + 1

        prefs.edit()
            .putLong(KEY_LOCKOUT_UNTIL, until)
            .putInt(KEY_SESSION_REEL_COUNT, 0) // Reset session count after lockout
            .putInt(KEY_LOCKOUT_COUNT, lockouts)
            .apply()

        return until
    }

    fun resetStats() {
        prefs.edit()
            .putInt(KEY_SESSION_REEL_COUNT, 0)
            .putInt(KEY_TODAY_TOTAL_REELS, 0)
            .putInt(KEY_LOCKOUT_COUNT, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    private fun checkAutoResetSession() {
        val lastScroll = prefs.getLong(KEY_LAST_SCROLL_TIME, 0L)
        // If user hasn't scrolled in 8 minutes, reset the session counter
        if (lastScroll > 0 && (System.currentTimeMillis() - lastScroll > 8 * 60 * 1000L)) {
            prefs.edit().putInt(KEY_SESSION_REEL_COUNT, 0).apply()
        }
    }

    private fun checkDailyReset() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val savedDay = prefs.getInt(KEY_LAST_DAY, -1)
        if (savedDay != today) {
            prefs.edit()
                .putInt(KEY_LAST_DAY, today)
                .putInt(KEY_TODAY_TOTAL_REELS, 0)
                .putInt(KEY_LOCKOUT_COUNT, 0)
                .putInt(KEY_SESSION_REEL_COUNT, 0)
                .apply()
        }
    }
}
