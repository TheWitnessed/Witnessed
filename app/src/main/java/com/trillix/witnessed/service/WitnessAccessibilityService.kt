package com.trillix.witnessed.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.trillix.witnessed.Graph
import com.trillix.witnessed.InterventionActivity
import com.trillix.witnessed.data.DetectionTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Tier 2 detection. Watches only for the chosen package coming to the
 * foreground (never window content) and launches the wall before the user
 * scrolls. It fires only on a fresh entry into the watched app (you came from a
 * different app), which — with a short re-entry cooldown — prevents the
 * resolve -> app-returns -> retrigger loop.
 */
class WitnessAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var watchedPackage: String? = null
    @Volatile private var enabled = false

    // The last real foreground app (our own wall is never recorded here). We only
    // intervene on a fresh entry (prev != watched). A tiny dedupe window collapses
    // duplicate window events fired for the same single app open.
    @Volatile private var lastForeground: String? = null
    private var lastTriggerAt = 0L
    private val dedupeMs = 1_500L

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            combine(Graph.repository.watchedApp, Graph.repository.userSettings) { app, settings ->
                val pkg = app?.packageName
                val on = settings.detection == DetectionTier.ACCESSIBILITY &&
                    app?.enabled == true && !pkg.isNullOrBlank()
                Pair(pkg, on)
            }.collect { (pkg, on) ->
                watchedPackage = pkg
                enabled = on
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!enabled) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Our own wall must never count as a foreground change — otherwise the
        // bounce when it closes would look like "left then re-entered the app".
        if (pkg == applicationContext.packageName) return
        val watched = watchedPackage ?: return

        val prev = lastForeground
        lastForeground = pkg

        // Only intervene on a *fresh entry* into the watched app: you came from a
        // different app and landed here. Staying in it keeps prev == watched.
        if (pkg != watched) return
        if (prev == watched) return

        // The wall sets a short process-level mute when it resolves. This is what
        // actually kills the Continue -> app-returns loop, and unlike the old
        // per-instance cooldown it survives the OS recycling this service.
        if (InterventionGate.isMuted()) return

        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < dedupeMs) return
        lastTriggerAt = now

        val intent = Intent(this, InterventionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(InterventionActivity.EXTRA_FROM_DETECTION, true)
        startActivity(intent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

/**
 * Process-level gate between the wall and the detector. When the wall resolves it
 * mutes detection briefly, so the watched app returning to the foreground can't
 * immediately re-trigger. Kept in-process (not in the service instance) so it
 * survives the OS tearing down and recreating the AccessibilityService — the
 * failure mode that defeated the previous in-service cooldown.
 */
object InterventionGate {
    @Volatile private var muteUntil: Long = 0L
    fun mute(durationMs: Long = 12_000L) { muteUntil = System.currentTimeMillis() + durationMs }
    fun isMuted(): Boolean = System.currentTimeMillis() < muteUntil
}
