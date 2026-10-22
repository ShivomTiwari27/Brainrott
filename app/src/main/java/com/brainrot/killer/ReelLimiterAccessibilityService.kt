package com.brainrot.killer

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat

class ReelLimiterAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelAccessibility"
        private const val NOTIFICATION_ID = 1001
        private const val MIN_SCROLL_INTERVAL_MS = 1600L // Minimum delay between reel counts

        const val ACTION_REEL_COUNT_UPDATED = "com.brainrot.killer.ACTION_REEL_UPDATED"
        const val EXTRA_SESSION_COUNT = "extra_session_count"
        const val EXTRA_LIMIT = "extra_limit"

        var isServiceRunning = false
            private set
    }

    private lateinit var preferencesManager: PreferencesManager
    private var lastScrollTimestamp = 0L
    private var lastCountedPackage = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        preferencesManager = PreferencesManager(this)
        Log.d(TAG, "ReelLimiterAccessibilityService connected and armed.")
        updateNotification(preferencesManager.sessionReelCount, preferencesManager.reelLimit)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // 1. Check if the app is one of our monitored targets
        if (!isMonitoredApp(packageName)) return

        // 2. If user is currently in a Detox Lockout, prevent them from accessing short-form apps!
        if (preferencesManager.isCurrentlyLockedOut) {
            enforceLockout(packageName)
            return
        }

        // 3. Detect Reel / Short scroll event
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                handleScrollEvent(packageName, event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // If floating pill is enabled, ensure overlay is active for target apps
                if (preferencesManager.showFloatingPill) {
                    notifyPillUpdate(preferencesManager.sessionReelCount, preferencesManager.reelLimit)
                }
            }
        }
    }

    private fun isMonitoredApp(packageName: String): Boolean {
        return when {
            packageName == "com.instagram.android" && preferencesManager.isInstagramEnabled -> true
            packageName == "com.google.android.youtube" && preferencesManager.isYoutubeEnabled -> true
            (packageName == "com.zhiliaoapp.musically" || packageName == "com.ss.android.ugc.trill") && preferencesManager.isTikTokEnabled -> true
            packageName == "com.facebook.katana" && preferencesManager.isFacebookEnabled -> true
            else -> false
        }
    }

    private fun handleScrollEvent(packageName: String, event: AccessibilityEvent) {
        val currentTime = System.currentTimeMillis()

        // Debounce: prevent registering multiple scroll events for a single swipe gesture
        if (currentTime - lastScrollTimestamp < MIN_SCROLL_INTERVAL_MS) {
            return
        }

        // Validate if this scroll is happening in a short-form video view
        if (!isShortVideoContext(packageName, event)) {
            return
        }

        lastScrollTimestamp = currentTime
        lastCountedPackage = packageName

        // Increment count
        val (sessionCount, todayTotal) = preferencesManager.incrementReel()
        val limit = preferencesManager.reelLimit

        Log.d(TAG, "Reel scrolled! Session: $sessionCount / Limit: $limit (Today: $todayTotal) in $packageName")

        // Update notification & floating HUD
        updateNotification(sessionCount, limit)
        notifyPillUpdate(sessionCount, limit)

        // Check if user has hit or exceeded the limit!
        if (sessionCount >= limit) {
            executeLimitAction()
        }
    }

    private fun isShortVideoContext(packageName: String, event: AccessibilityEvent): Boolean {
        // TikTok is 100% short videos
        if (packageName.contains("musically") || packageName.contains("ugc.trill")) {
            return true
        }

        // Inspect node hierarchy or event class names
        val className = event.className?.toString() ?: ""
        val source: AccessibilityNodeInfo? = event.source

        // Check common identifiers for Reels / Shorts
        val isReelsOrShorts = className.contains("ViewPager", ignoreCase = true) ||
                className.contains("RecyclerView", ignoreCase = true) ||
                containsKeyword(source, "reel") ||
                containsKeyword(source, "short") ||
                containsKeyword(source, "clips")

        source?.recycle()
        return isReelsOrShorts || (event.scrollDeltaY != 0) // Fallback to vertical swipe
    }

    private fun containsKeyword(node: AccessibilityNodeInfo?, keyword: String): Boolean {
        if (node == null) return false
        try {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val viewId = node.viewIdResourceName ?: ""

            if (text.contains(keyword, ignoreCase = true) ||
                desc.contains(keyword, ignoreCase = true) ||
                viewId.contains(keyword, ignoreCase = true)
            ) {
                return true
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (containsKeyword(child, keyword)) {
                    child?.recycle()
                    return true
                }
                child?.recycle()
            }
        } catch (e: Exception) {
            // Ignore hierarchy recycling exceptions
        }
        return false
    }

    private fun executeLimitAction() {
        triggerHapticWarning()

        val action = preferencesManager.actionType
        if (action == PreferencesManager.ACTION_LOCK_PHONE) {
            // Lock phone & turn off screen via Android 9+ global action!
            preferencesManager.triggerLockout()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        } else {
            // App Blocker / Detox countdown mode
            preferencesManager.triggerLockout()
            performGlobalAction(GLOBAL_ACTION_HOME)
            launchLockoutScreen()
        }
    }

    private fun enforceLockout(packageName: String) {
        // If locked out and user tries opening target app, kick them out immediately!
        Log.w(TAG, "User attempted to open $packageName during lockout. Enforcing detox!")
        performGlobalAction(GLOBAL_ACTION_HOME)
        launchLockoutScreen()
    }

    private fun launchLockoutScreen() {
        val intent = Intent(this, LockoutActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun triggerHapticWarning() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 300), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 200, 100, 300), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate: ${e.message}")
        }
    }

    private fun updateNotification(sessionCount: Int, limit: Int) {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, BrainRotKillerApp.CHANNEL_ID_MONITOR)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("BrainRot Shield Active")
            .setContentText("Session: $sessionCount / $limit reels scrolled")
            .setProgress(limit, sessionCount, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun notifyPillUpdate(sessionCount: Int, limit: Int) {
        val intent = Intent(ACTION_REEL_COUNT_UPDATED).apply {
            putExtra(EXTRA_SESSION_COUNT, sessionCount)
            putExtra(EXTRA_LIMIT, limit)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.")
    }

    override fun onDestroy() {
        isServiceRunning = false
        super.onDestroy()
    }
}
