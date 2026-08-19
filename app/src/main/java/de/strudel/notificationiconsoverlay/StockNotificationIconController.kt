package de.strudel.notificationiconsoverlay

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.ArrayDeque
import java.util.concurrent.Executors

/**
 * Coordinates asynchronous calls to the Shizuku user service that controls stock notification
 * icons.
 *
 * Requests are serialized so a quick sequence of reapply and user-toggle operations cannot finish
 * out of order. Callbacks are always delivered on the main thread.
 */
object StockNotificationIconController {
    /** Outcome of one requested stock-icon visibility change. */
    data class Result(val successful: Boolean, val message: String? = null)

    private data class Request(
        val hidden: Boolean,
        val callback: (Result) -> Unit,
    )

    private val lock = Any()
    private val pending = ArrayDeque<Request>()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var service: IStatusBarControlService? = null
    private var binding = false
    private var draining = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            synchronized(lock) {
                binding = false
                service = binder
                    ?.takeIf { it.pingBinder() }
                    ?.let { IStatusBarControlService.Stub.asInterface(it) }
                if (service == null) {
                    failPendingLocked("Shizuku returned an invalid status-bar service.")
                } else {
                    startDrainLocked()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) {
                service = null
                binding = false
                failPendingLocked("The Shizuku status-bar service disconnected.")
            }
        }
    }

    /**
     * Requests that Android hide or restore its original notification icons.
     *
     * A Shizuku user service is bound lazily on the first request. The supplied callback receives
     * a failure without binding when Shizuku is stopped or permission has not been granted.
     */
    fun setHidden(context: Context, hidden: Boolean, callback: (Result) -> Unit = {}) {
        if (!ShizukuStatusBarReader.isAvailableAndGranted()) {
            mainHandler.post {
                callback(Result(false, "Start Shizuku and grant this app access first."))
            }
            return
        }

        synchronized(lock) {
            pending.addLast(Request(hidden, callback))
            if (service != null) {
                startDrainLocked()
                return
            }
            if (binding) return

            binding = true
            try {
                Shizuku.bindUserService(userServiceArgs(context.applicationContext), connection)
            } catch (error: RuntimeException) {
                Log.e(TAG, "Could not bind the Shizuku status-bar service", error)
                binding = false
                failPendingLocked(error.message ?: "Could not start the Shizuku status-bar service.")
            }
        }
    }

    /** Reapplies the persisted hidden state after the accessibility service or Shizuku reconnects. */
    fun reapplyIfEnabled(context: Context) {
        if (!OverlayConfig.hideStockNotificationIcons(OverlayConfig.preferences(context))) return
        setHidden(context, true) { result ->
            if (!result.successful) Log.w(TAG, result.message ?: "Could not reapply stock-icon hiding.")
        }
    }

    private fun userServiceArgs(context: Context) = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, StatusBarControlUserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("status_bar")
        .debuggable(false)
        .version(USER_SERVICE_VERSION)

    /** Starts a single background drain while holding [lock]. */
    private fun startDrainLocked() {
        if (draining || service == null || pending.isEmpty()) return
        draining = true
        executor.execute(::drainRequests)
    }

    /** Runs queued binder calls in order and posts their results to the UI thread. */
    private fun drainRequests() {
        while (true) {
            val next: Pair<Request, IStatusBarControlService> = synchronized(lock) {
                val request = pending.pollFirst()
                val currentService = service
                if (request == null || currentService == null) {
                    draining = false
                    return
                }
                request to currentService
            }

            val result = try {
                val error = next.second.setNotificationIconsHidden(next.first.hidden)
                if (error.isNullOrBlank()) Result(true) else Result(false, error)
            } catch (error: RemoteException) {
                val message = error.message ?: "The Shizuku status-bar service disconnected."
                synchronized(lock) {
                    service = null
                    failPendingLocked(message)
                }
                Result(false, message)
            } catch (error: RuntimeException) {
                Result(false, error.message ?: "The status-bar command failed.")
            }
            mainHandler.post { next.first.callback(result) }
        }
    }

    /** Removes and fails all queued requests while holding [lock]. */
    private fun failPendingLocked(message: String) {
        val callbacks = buildList {
            while (pending.isNotEmpty()) add(pending.removeFirst().callback)
        }
        callbacks.forEach { callback -> mainHandler.post { callback(Result(false, message)) } }
    }

    private const val TAG = "StockStatusBarIcons"
    private const val USER_SERVICE_VERSION = 1
}
