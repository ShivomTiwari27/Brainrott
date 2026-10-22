package com.brainrot.killer

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingPillService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1002
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var tvCount: TextView? = null
    private var preferencesManager: PreferencesManager? = null

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ReelLimiterAccessibilityService.ACTION_REEL_COUNT_UPDATED) {
                val count = intent.getIntExtra(ReelLimiterAccessibilityService.EXTRA_SESSION_COUNT, 0)
                val limit = intent.getIntExtra(ReelLimiterAccessibilityService.EXTRA_LIMIT, 10)
                updatePillUI(count, limit)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)

        startForeground(NOTIFICATION_ID, createForegroundNotification())

        val filter = IntentFilter(ReelLimiterAccessibilityService.ACTION_REEL_COUNT_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }

        if (Settings.canDrawOverlays(this)) {
            initFloatingView()
        }
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, BrainRotKillerApp.CHANNEL_ID_MONITOR)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Reel HUD Active")
            .setContentText("Overlay counter is visible")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun initFloatingView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.view_floating_pill, null)

        tvCount = floatingView?.findViewById(R.id.tvPillCount)
        val initialCount = preferencesManager?.sessionReelCount ?: 0
        val initialLimit = preferencesManager?.reelLimit ?: 10
        updatePillUI(initialCount, initialLimit)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 120
        }

        // Add touch listener to allow dragging the floating badge around the screen
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = Math.abs(event.rawX - initialTouchX)
                        val deltaY = Math.abs(event.rawY - initialTouchY)
                        // If it's a tap rather than a drag, open the dashboard
                        if (deltaX < 10 && deltaY < 10) {
                            val mainIntent = Intent(this@FloatingPillService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(mainIntent)
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updatePillUI(count: Int, limit: Int) {
        tvCount?.text = "$count / $limit"
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(updateReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }

        if (floatingView != null && windowManager != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                // View not attached
            }
        }
        super.onDestroy()
    }
}
