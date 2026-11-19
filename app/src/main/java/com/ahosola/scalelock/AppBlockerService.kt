package com.ahosola.scalelock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppBlockerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg in ScaleLockPrefs.NEVER_BLOCK_PACKAGES) return
        if (!ScaleLockPrefs.isBlockerActiveNow(this)) return

        val blocked = ScaleLockPrefs.getBlockedPackages(this)
        if (pkg !in blocked) return

        val lockIntent = Intent(this, LockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(LockActivity.EXTRA_BLOCKED_PACKAGE, pkg)
        }
        startActivity(lockIntent)
    }

    override fun onInterrupt() {
        // Nothing to clean up
    }
}
