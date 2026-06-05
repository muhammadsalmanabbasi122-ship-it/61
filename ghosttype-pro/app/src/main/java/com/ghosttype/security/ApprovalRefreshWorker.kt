package com.ghosttype.security

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import com.ghosttype.utils.SettingsStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * v1.10 — Periodic background approval re-check.
 *
 * The original [ApprovalGate] only re-validated on app launch (or after the
 * 6-hour cache expired *and* a full IME open happened). That meant a user
 * whose approval was revoked from the GitHub Users.json could keep typing
 * for up to 6 h plus however long they avoided opening the keyboard
 * settings — far too lenient.
 *
 * This worker:
 *   1. Runs every [INTERVAL_MIN] minutes when the device has internet.
 *      (Android's [PeriodicWorkRequestBuilder] minimum is 15 min — going
 *      lower silently rounds up.)
 *   2. Calls [ApprovalGate.evaluate] with `force = true`, which bypasses
 *      the 6-h cache and hits GitHub.
 *   3. If the new state is anything other than [ApprovalGate.State.Approved]
 *      it broadcasts [ACTION_APPROVAL_REVOKED]. The IME service listens
 *      for that broadcast and immediately swaps its visible view to the
 *      lock screen, so the next character the user types is blocked even
 *      while the keyboard is open.
 *
 * Network constraint means the worker doesn't waste battery polling when
 * the device is offline — WorkManager waits for connectivity, then runs
 * the deferred check.
 *
 * Stealthy by design: no notification, no toast. The only sign of a
 * revoked approval is the keyboard switching to the lock view.
 */
class ApprovalRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // Refresh all three gates in the background
            runCatching { CrashGate.check(applicationContext) }
            runCatching { UpdateGate.check(applicationContext) }
            val state = ApprovalGate.evaluate(applicationContext, force = true)
            val prefs = SettingsStore.prefs(applicationContext)
            val updateDisabled = prefs.getBoolean("update_gate_disabled", false)
            val crashTriggered = prefs.getBoolean("crash_app_triggered", false)

            // If crash was just triggered while the app was open, brick immediately
            if (crashTriggered) {
                val intent = android.content.Intent(
                    applicationContext,
                    com.ghosttype.ui.BrickedActivity::class.java
                ).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                applicationContext.startActivity(intent)
                return Result.success()
            }

            if (state is ApprovalGate.State.Blocked       ||
                state is ApprovalGate.State.NotApproved   ||
                updateDisabled) {
                applicationContext.sendBroadcast(
                    Intent(ACTION_APPROVAL_REVOKED).setPackage(applicationContext.packageName)
                )
            }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        /** Broadcast action the IME listens for to force a lock-screen
         *  re-render mid-session. Package-scoped so other apps can't fake
         *  the signal — see the `setPackage(...)` call above. */
        const val ACTION_APPROVAL_REVOKED = "com.ghosttype.action.APPROVAL_REVOKED"

        /** Minimum WorkManager periodic interval. Anything < 15 min is
         *  silently clamped to 15 min by the framework. */
        private const val INTERVAL_MIN = 15L

        private const val UNIQUE_NAME = "ghosttype_approval_refresh"

        /**
         * Schedules the periodic check. Safe to call repeatedly — the KEEP
         * policy means an existing schedule is preserved, so we don't reset
         * the timer every app launch.
         */
        fun schedule(ctx: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<ApprovalRefreshWorker>(
                INTERVAL_MIN, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        /**
         * Enqueues a single immediate one-time check (also network-constrained).
         * Called on every app/process start so a revoked key is detected within
         * seconds of the next launch instead of waiting for the 15-min periodic
         * window to expire.
         */
        fun checkNow(ctx: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = OneTimeWorkRequestBuilder<ApprovalRefreshWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(ctx).enqueue(req)
        }
    }
}
