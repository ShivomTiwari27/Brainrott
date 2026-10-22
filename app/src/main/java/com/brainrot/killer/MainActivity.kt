package com.brainrot.killer

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.brainrot.killer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = PreferencesManager(this)

        setupListeners()
        loadSavedPreferences()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateLiveStats()
    }

    private fun setupListeners() {
        // Reset statistics button
        binding.btnResetStats.setOnClickListener {
            preferencesManager.resetStats()
            updateLiveStats()
            Toast.makeText(this, "Scroll stats reset!", Toast.LENGTH_SHORT).show()
        }

        // Grant Accessibility Button
        binding.btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Find 'BrainRot Killer' and toggle it ON",
                Toast.LENGTH_LONG
            ).show()
        }

        // Grant Overlay Button
        binding.btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        // Disable Battery Optimization Button
        binding.btnBatteryOptimization.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        // Reel limit slider
        binding.sliderReelLimit.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val limit = value.toInt()
                preferencesManager.reelLimit = limit
                binding.tvReelLimitDisplay.text = "$limit reels"
            }
        }

        // Cooldown slider
        binding.sliderCooldown.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val minutes = value.toInt()
                preferencesManager.cooldownMinutes = minutes
                binding.tvCooldownDisplay.text = "$minutes mins"
            }
        }

        // Lock Action Selection
        binding.rgLockAction.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbLockPhone -> preferencesManager.actionType = PreferencesManager.ACTION_LOCK_PHONE
                R.id.rbBlockerScreen -> preferencesManager.actionType = PreferencesManager.ACTION_BLOCK_SCREEN
            }
        }

        // Floating Pill Toggle
        binding.switchFloatingPill.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.showFloatingPill = isChecked
            toggleFloatingPillService(isChecked)
        }

        // Social apps switches
        binding.switchInstagram.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.isInstagramEnabled = isChecked
        }
        binding.switchYoutube.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.isYoutubeEnabled = isChecked
        }
        binding.switchTikTok.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.isTikTokEnabled = isChecked
        }
        binding.switchFacebook.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.isFacebookEnabled = isChecked
        }
    }

    private fun loadSavedPreferences() {
        val limit = preferencesManager.reelLimit
        binding.sliderReelLimit.valueFrom = 3f
        binding.sliderReelLimit.valueTo = 30f
        binding.sliderReelLimit.stepSize = 1f
        binding.sliderReelLimit.value = limit.toFloat()
        binding.tvReelLimitDisplay.text = "$limit reels"

        val cooldown = preferencesManager.cooldownMinutes
        binding.sliderCooldown.valueFrom = 2f
        binding.sliderCooldown.valueTo = 60f
        binding.sliderCooldown.stepSize = 1f
        binding.sliderCooldown.value = cooldown.toFloat()
        binding.tvCooldownDisplay.text = "$cooldown mins"

        if (preferencesManager.actionType == PreferencesManager.ACTION_LOCK_PHONE) {
            binding.rbLockPhone.isChecked = true
        } else {
            binding.rbBlockerScreen.isChecked = true
        }

        binding.switchFloatingPill.isChecked = preferencesManager.showFloatingPill
        binding.switchInstagram.isChecked = preferencesManager.isInstagramEnabled
        binding.switchYoutube.isChecked = preferencesManager.isYoutubeEnabled
        binding.switchTikTok.isChecked = preferencesManager.isTikTokEnabled
        binding.switchFacebook.isChecked = preferencesManager.isFacebookEnabled
    }

    private fun updateLiveStats() {
        binding.tvStatCurrentSession.text = preferencesManager.sessionReelCount.toString()
        binding.tvStatTodayTotal.text = preferencesManager.todayTotalReels.toString()
        binding.tvStatLockouts.text = preferencesManager.lockoutCount.toString()
    }

    private fun updatePermissionStatus() {
        val isAccessibilityGranted = isAccessibilityServiceEnabled()
        val isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBatteryExempt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else true

        // 1. Accessibility Status
        if (isAccessibilityGranted) {
            binding.btnGrantAccessibility.text = "Granted"
            binding.btnGrantAccessibility.isEnabled = false
            binding.viewStatusDot.setBackgroundResource(R.drawable.status_dot_active)
            binding.tvStatusText.text = "Shield Active & Guarding"
            binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.primary_accent))
        } else {
            binding.btnGrantAccessibility.text = "Enable"
            binding.btnGrantAccessibility.isEnabled = true
            binding.viewStatusDot.setBackgroundResource(R.drawable.status_dot_inactive)
            binding.tvStatusText.text = "Accessibility Service Not Running"
            binding.tvStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_medium))
        }

        // 2. Overlay Status
        if (isOverlayGranted) {
            binding.btnGrantOverlay.text = "Granted"
            binding.btnGrantOverlay.isEnabled = false
        } else {
            binding.btnGrantOverlay.text = "Enable"
            binding.btnGrantOverlay.isEnabled = true
        }

        // 3. Battery Status
        if (isBatteryExempt) {
            binding.btnBatteryOptimization.text = "Allowed"
            binding.btnBatteryOptimization.isEnabled = false
        } else {
            binding.btnBatteryOptimization.text = "Allow"
            binding.btnBatteryOptimization.isEnabled = true
        }
    }

    private fun toggleFloatingPillService(enable: Boolean) {
        val serviceIntent = Intent(this, FloatingPillService::class.java)
        if (enable && Settings.canDrawOverlays(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            stopService(serviceIntent)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.id.contains(packageName)) {
                return true
            }
        }
        return false
    }
}
