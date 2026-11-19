package com.ahosola.scalelock

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahosola.scalelock.databinding.ActivityLockBinding
import kotlinx.coroutines.launch
import java.util.Locale

class LockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        const val EXTRA_IS_TEST = "is_test"
    }

    private lateinit var binding: ActivityLockBinding
    private var isTestMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isTestMode = intent.getBooleanExtra(EXTRA_IS_TEST, false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })

        binding.btnOpenWithings.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.withings.wiscale2")
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Withings app not found", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCheckAgain.setOnClickListener {
            checkHealthConnect(manual = true)
        }

        binding.btnGoHome.setOnClickListener {
            goHome()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isTestMode) {
            checkHealthConnect(manual = false)
        } else {
            binding.tvLockSubtitle.text = "Test preview mode. Tap 'Check Weight Again' or 'Go to Home Screen'."
        }
    }

    private fun checkHealthConnect(manual: Boolean) {
        binding.tvLockSubtitle.text = "Checking Health Connect for today's weigh-in…"
        lifecycleScope.launch {
            val record = HealthConnectHelper.getTodayWeightRecord(this@LockActivity)
            if (record != null) {
                val kg = record.weight.inKilograms
                ScaleLockPrefs.markUnlockedToday(this@LockActivity, kg)
                val formatted = String.format(Locale.US, "%.1f kg", kg)
                Toast.makeText(
                    this@LockActivity,
                    "Weigh-in logged ($formatted)! Apps unlocked.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } else {
                binding.tvLockSubtitle.text = if (manual) {
                    "Still no weigh-in since 04:00 today. Step on the scale, then tap 'Open Withings to Sync'."
                } else {
                    "No weigh-in found since 04:00 today. Step on your scale to unlock."
                }
            }
        }
    }

    private fun goHome() {
        if (isTestMode) {
            finish()
            return
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
