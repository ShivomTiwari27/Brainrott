package com.brainrot.killer

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.brainrot.killer.databinding.ActivityLockoutBinding
import java.util.Locale

class LockoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockoutBinding
    private lateinit var preferencesManager: PreferencesManager
    private var countDownTimer: CountDownTimer? = null

    private val mindfulTips = listOf(
        "Drink a full glass of cold water. Your brain is often dehydrated when seeking cheap dopamine.",
        "Take 5 slow, deep breaths. Inhale for 4 seconds, hold for 4, exhale for 6.",
        "Look out the window at the furthest object you can see for 20 seconds to relax your optic nerves.",
        "Stand up, stretch your spine, and shake out your hands.",
        "Remember: Endless scrolling doesn't relax you, it leaves you drained. Real peace is off-screen."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesManager = PreferencesManager(this)

        // Ensure activity wakes up screen and shows over lockscreen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Block back button from bypassing detox lock
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToHomeScreen()
            }
        })

        binding.btnExitToHome.setOnClickListener {
            goToHomeScreen()
        }

        binding.tvTipMessage.text = mindfulTips.random()

        startCountdown()
    }

    private fun startCountdown() {
        val remainingMillis = preferencesManager.remainingLockoutMillis
        if (remainingMillis <= 0) {
            finish()
            return
        }

        val totalDurationMillis = preferencesManager.cooldownMinutes * 60 * 1000L

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding.tvCountdownTimer.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

                val progress = ((millisUntilFinished.toFloat() / totalDurationMillis) * 100).toInt()
                binding.progressLockout.progress = progress
            }

            override fun onFinish() {
                binding.tvCountdownTimer.text = "00:00"
                binding.progressLockout.progress = 0
                binding.tvDetoxTitle.text = "Detox Complete! 🌟"
                binding.tvDetoxSubtitle.text = "You broke the loop. Choose your next task with intention."
                binding.btnExitToHome.text = "Done"
            }
        }.start()
    }

    private fun goToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }
}
