package com.ahosola.scalelock

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ahosola.scalelock.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AppListAdapter
    private var allApps: List<AppItem> = emptyList()

    private val requestHealthPermission = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (HealthConnectHelper.WEIGHT_PERMISSION in granted) {
            Toast.makeText(this, "Health Connect permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission needed to read Withings weight.", Toast.LENGTH_SHORT).show()
        }
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.switchEnabled.isChecked = ScaleLockPrefs.isEnabled(this)
        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            ScaleLockPrefs.setEnabled(this, isChecked)
            refreshStatus()
        }

        binding.btnHealthConnect.setOnClickListener {
            if (!HealthConnectHelper.isAvailable(this)) {
                Toast.makeText(this, "Health Connect is not available on this device.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            requestHealthPermission.launch(setOf(HealthConnectHelper.WEIGHT_PERMISSION))
        }

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnTestLock.setOnClickListener {
            val intent = Intent(this, LockActivity::class.java).apply {
                putExtra(LockActivity.EXTRA_IS_TEST, true)
            }
            startActivity(intent)
        }

        binding.btnCheckNow.setOnClickListener {
            checkWeightAndUpdate()
        }

        adapter = AppListAdapter { pkg, checked ->
            val current = ScaleLockPrefs.getBlockedPackages(this).toMutableSet()
            if (checked) current.add(pkg) else current.remove(pkg)
            ScaleLockPrefs.setBlockedPackages(this, current)
            updateBlockedHeader(current.size)
        }
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter

        binding.etSearchApps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            val hcAvailable = HealthConnectHelper.isAvailable(this@MainActivity)
            val hcGranted = HealthConnectHelper.hasWeightPermission(this@MainActivity)
            val accEnabled = isAccessibilityEnabled()
            val unlockedToday = ScaleLockPrefs.isUnlockedToday(this@MainActivity)
            val lastKg = ScaleLockPrefs.getLastWeightKg(this@MainActivity)
            val windowActive = ScaleLockPrefs.isWithinLockWindow()
            val enabled = ScaleLockPrefs.isEnabled(this@MainActivity)

            val hcText = when {
                !hcAvailable -> "Unavailable"
                hcGranted -> "Granted ✓"
                else -> "Missing ✗"
            }
            val accText = if (accEnabled) "Enabled ✓" else "Disabled ✗"
            val todayText = if (unlockedToday) {
                val kgStr = lastKg?.let { String.format(Locale.US, " (%.1f kg)", it) } ?: ""
                "Unlocked today$kgStr ✓"
            } else if (!enabled) {
                "Paused"
            } else if (windowActive) {
                "LOCKED (waiting for weigh-in)"
            } else {
                "Waiting for 05:00 window"
            }

            binding.tvStatus.text = buildString {
                appendLine("Today: $todayText")
                appendLine("Health Connect: $hcText")
                append("Accessibility Service: $accText")
            }
        }
    }

    private fun checkWeightAndUpdate() {
        lifecycleScope.launch {
            val record = HealthConnectHelper.getTodayWeightRecord(this@MainActivity)
            if (record != null) {
                val kg = record.weight.inKilograms
                ScaleLockPrefs.markUnlockedToday(this@MainActivity, kg)
                Toast.makeText(
                    this@MainActivity,
                    String.format(Locale.US, "Found today's weight: %.1f kg", kg),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "No weight record found since 04:00 today.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            refreshStatus()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                it.resolveInfo.serviceInfo.name == AppBlockerService::class.java.name
        }
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch {
            val blocked = ScaleLockPrefs.getBlockedPackages(this@MainActivity)
            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                    .map { it.activityInfo.packageName }
                    .distinct()
                    .filter { it !in ScaleLockPrefs.NEVER_BLOCK_PACKAGES }
                    .mapNotNull { pkg ->
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            val label = pm.getApplicationLabel(appInfo).toString()
                            AppItem(pkg, label, pkg in blocked)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    .sortedWith(compareByDescending<AppItem> { it.isBlocked }.thenBy { it.label.lowercase() })
            }
            allApps = apps
            updateBlockedHeader(blocked.size)
            filterApps(binding.etSearchApps.text?.toString().orEmpty())
        }
    }

    private fun filterApps(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
        adapter.submitList(filtered)
    }

    private fun updateBlockedHeader(count: Int) {
        binding.tvBlockedHeader.text = "Blocked Apps ($count selected)"
    }

    data class AppItem(
        val packageName: String,
        val label: String,
        var isBlocked: Boolean
    )

    private class AppListAdapter(
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.VH>() {

        private var items: List<AppItem> = emptyList()

        fun submitList(newItems: List<AppItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        class VH(val checkBox: CheckBox) : RecyclerView.ViewHolder(checkBox)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val cb = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false) as? CheckBox
                ?: CheckBox(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(16, 20, 16, 20)
                }
            return VH(cb)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.text = "${item.label}\n${item.packageName}"
            holder.checkBox.isChecked = item.isBlocked
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                item.isBlocked = isChecked
                onToggle(item.packageName, isChecked)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
