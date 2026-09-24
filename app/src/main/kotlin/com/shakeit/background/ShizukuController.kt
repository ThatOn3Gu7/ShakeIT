package com.shakeit.background

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import java.util.Locale

/**
 * What ShakeIT can actually do with Shizuku, as opposed to what the user tapped.
 */
enum class ShizukuState {
    /** The Shizuku app is not installed on this device. */
    NOT_INSTALLED,

    /** Installed, but its service is not running — or is pre-v11, which this does not support. */
    NOT_RUNNING,

    /** Running, and ShakeIT has not been authorised yet. */
    PERMISSION_NEEDED,

    /** The user chose "deny and don't ask again"; only the Shizuku app can undo that. */
    DENIED,

    /** Authorised: privileged calls can be made right now. */
    READY,
}

/**
 * The live Shizuku state.
 *
 * Read, never remembered: a stored "connected" would be a lie the moment Shizuku
 * stopped, which is why the binder-death listener exists and why this carries no
 * history. What a privileged call actually changed is recorded separately by the
 * engine, next to the command that changed it.
 *
 * @param serverVersion Shizuku's own version, or -1 when unknown
 * @param uid the identity privileged calls run as: 2000 for shell (started with
 *   adb), 0 for root. Worth showing because it bounds what can be done — shell
 *   is *not* root, and this app never claims otherwise
 */
data class ShizukuStatus(
    val state: ShizukuState,
    val serverVersion: Int = -1,
    val uid: Int = -1,
) {
    /** Whether privileged calls can be made right now. */
    val authorised: Boolean
        get() = state == ShizukuState.READY

    /** Human-readable privilege level, or null when there is no connection. */
    val privilege: String?
        get() = when (uid) {
            UID_ROOT -> "root"
            UID_SHELL -> "shell (adb)"
            -1 -> null
            else -> "uid $uid"
        }

    private companion object {
        const val UID_ROOT = 0
        const val UID_SHELL = 2000
    }
}

/**
 * One privileged command's outcome.
 *
 * @param succeeded the command ran and exited 0
 * @param exitCode the remote exit code, or -1 when it never ran
 * @param output stdout, trimmed
 * @param errorText stderr, or why the call could not be made at all
 */
data class ShizukuCommandResult(
    val succeeded: Boolean,
    val exitCode: Int,
    val output: String,
    val errorText: String,
)

/**
 * Shizuku, honestly.
 *
 * Shizuku runs a process with shell (or root) identity and hands authorised apps
 * a binder to it. That makes a small set of otherwise-unavailable things
 * possible — among them the two settings that decide whether this app's
 * background work survives: the Doze/power-exemption allowlist, and the
 * `RUN_ANY_IN_BACKGROUND` app-op that the platform's own "restrict background
 * activity" action writes.
 *
 * What it is *not* is root, and not a way around OEM policy: a Transsion phone
 * manager that freezes the package will freeze it whatever the allowlist says.
 * Every state here is read from Shizuku rather than remembered — a button that
 * stored "connected" would be a lie the moment Shizuku stopped, which is why the
 * binder-death listener exists.
 *
 * Nothing is ever changed silently. Reading is free; writing happens only from an
 * explicit user action and only for this app's own package. Every result carries
 * the command's exit code and output, so the caller can record what actually
 * changed instead of reporting that a button was pressed.
 */
class ShizukuController(private val context: Context) {

    private val _status = MutableStateFlow(ShizukuStatus(state = ShizukuState.NOT_INSTALLED))

    /** The real connection state. Never persisted: it is read, not remembered. */
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    // Held as stable instances so they can be removed again: Shizuku compares
    // listeners by identity, and a leak here would outlive the engine.
    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                Log.i(TAG, "permission request answered: granted=$granted")
                refresh()
            }
        }

    private var listening = false

    /**
     * The binder of [ShizukuCommandService], running in Shizuku's own process as
     * shell or root. Bound on first use rather than on connect: starting a
     * process inside Shizuku is not free, and reading a setting nobody asked
     * about is not a reason to.
     */
    @Volatile
    private var commandBinder: IBinder? = null

    private val commandConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            commandBinder = service
            if (service != null) {
                // Shizuku dying takes the service process with it; without this
                // the next command would be sent to a dead binder and reported as
                // a failure of the command rather than of the connection.
                try {
                    service.linkToDeath({ commandBinder = null }, 0)
                } catch (error: Exception) {
                    // RemoteException, if the service is already gone: nothing to
                    // watch, and the next command rebinds instead.
                    Log.w(TAG, "could not watch the command service for death", error)
                }
            }
            Log.i(TAG, "command service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            commandBinder = null
            Log.i(TAG, "command service disconnected")
        }
    }

    /**
     * Starts observing Shizuku. Safe to call when Shizuku is not installed: every
     * API in the library throws `IllegalStateException` before the binder has
     * been received, so each call here is guarded rather than assumed.
     */
    fun start() {
        if (listening) return
        listening = true
        guard("addBinderReceivedListenerSticky") {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
        }
        guard("addBinderDeadListener") { Shizuku.addBinderDeadListener(binderDead) }
        guard("addRequestPermissionResultListener") {
            Shizuku.addRequestPermissionResultListener(permissionResult)
        }
        refresh()
    }

    /** Stops observing. Called when the process is done with Shizuku. */
    fun stop() {
        if (!listening) return
        listening = false
        guard("unbindUserService") {
            Shizuku.unbindUserService(userServiceArgs(), commandConnection, true)
        }
        commandBinder = null
        guard("removeBinderReceivedListener") {
            Shizuku.removeBinderReceivedListener(binderReceived)
        }
        guard("removeBinderDeadListener") { Shizuku.removeBinderDeadListener(binderDead) }
        guard("removeRequestPermissionResultListener") {
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
    }

    /** Re-reads the connection state. Called on every binder and permission event. */
    fun refresh() {
        val previous = _status.value
        val next = evaluate()
        if (next.state != previous.state) {
            Log.i(TAG, "Shizuku ${previous.state} -> ${next.state} (uid=${next.uid})")
        }
        _status.value = next
    }

    /**
     * Asks Shizuku for authorisation. Does nothing when there is nothing to ask —
     * no service, or already granted — and never invents a result: the answer
     * arrives on [permissionResult] and is read back by [refresh].
     */
    fun requestPermission() {
        if (!isRunning()) {
            refresh()
            return
        }
        if (isGranted()) {
            refresh()
            return
        }
        val deniedForGood = guard("shouldShowRequestPermissionRationale") {
            Shizuku.shouldShowRequestPermissionRationale()
        } ?: false
        if (deniedForGood) {
            Log.w(TAG, "permission was denied permanently; only the Shizuku app can grant it")
            refresh()
            return
        }
        guard("requestPermission") { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /**
     * An intent that opens the Shizuku app itself, for the states this cannot fix:
     * the service is not running, or the user denied the request for good.
     */
    fun shizukuLaunchIntent(): Intent? = try {
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
    } catch (error: RuntimeException) {
        Log.w(TAG, "could not resolve a launch intent for Shizuku", error)
        null
    }

    // ------------------------------------------------------- privileged reads

    /**
     * Whether ShakeIT is on the Doze/power-exemption allowlist, read from the
     * platform's own list rather than from `isIgnoringBatteryOptimizations`.
     *
     * The two should agree; when they do not, that disagreement is itself the
     * interesting finding — it means an OEM has a second list of its own.
     */
    fun readDozeAllowlist(): ShizukuCommandResult = run("cmd", "deviceidle", "whitelist")

    /** Reads the `RUN_ANY_IN_BACKGROUND` app-op as the platform records it. */
    fun readBackgroundAppOp(): ShizukuCommandResult =
        run("cmd", "appops", "get", context.packageName, OP_RUN_ANY_IN_BACKGROUND_SHORT)

    // ------------------------------------------------------ privileged writes

    /**
     * Adds *this package only* to the Doze/power-exemption allowlist — the same
     * thing the system's "Unrestricted" battery setting does, without asking the
     * user to find it.
     */
    fun addToDozeAllowlist(): ShizukuCommandResult =
        run("cmd", "deviceidle", "whitelist", "+${context.packageName}")

    /** Removes *this package only* from that allowlist. Undoes [addToDozeAllowlist]. */
    fun removeFromDozeAllowlist(): ShizukuCommandResult =
        run("cmd", "deviceidle", "whitelist", "-${context.packageName}")

    /** Clears a background restriction the platform wrote for *this package only*. */
    fun allowBackgroundAppOp(): ShizukuCommandResult = run(
        "cmd",
        "appops",
        "set",
        context.packageName,
        OP_RUN_ANY_IN_BACKGROUND_SHORT,
        "allow",
    )

    // ----------------------------------------------------------------- plumbing

    private fun evaluate(): ShizukuStatus {
        val running = isRunning()
        val installed = running || isInstalled()
        if (!running) {
            return ShizukuStatus(
                state = if (installed) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED,
            )
        }
        val granted = isGranted()
        val state = when {
            granted -> ShizukuState.READY
            // "Deny and don't ask again" — asking again would do nothing.
            guard("shouldShowRequestPermissionRationale") {
                Shizuku.shouldShowRequestPermissionRationale()
            } == true -> ShizukuState.DENIED
            else -> ShizukuState.PERMISSION_NEEDED
        }
        return ShizukuStatus(
            state = state,
            serverVersion = guard("getVersion") { Shizuku.getVersion() } ?: -1,
            uid = guard("getUid") { Shizuku.getUid() } ?: -1,
        )
    }

    private fun isInstalled(): Boolean = try {
        // The PackageInfoFlags overload is API 33; this one is not going anywhere.
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (error: PackageManager.NameNotFoundException) {
        false
    } catch (error: RuntimeException) {
        Log.w(TAG, "could not check whether Shizuku is installed", error)
        false
    }

    private fun isRunning(): Boolean {
        val alive = guard("pingBinder") { Shizuku.pingBinder() } ?: false
        // Pre-v11 uses a different permission model that this does not implement,
        // so it counts as unavailable rather than as a connection that lies.
        return alive && !isPreV11()
    }

    private fun isPreV11(): Boolean = guard("isPreV11") { Shizuku.isPreV11() } ?: true

    private fun isGranted(): Boolean {
        val result = guard("checkSelfPermission") { Shizuku.checkSelfPermission() }
        return result == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Runs one `cmd` line in Shizuku's process and reads the answer back.
     *
     * **Blocking**, twice over: binding the user service can take a moment the
     * first time, and the transaction itself waits for the command to finish.
     * Callers must be on a background dispatcher — the engine is.
     *
     * Shizuku 13.1.1 removed `Shizuku.newProcess` and points at its User Service
     * API instead, so the command runs inside
     * [ShizukuCommandService][com.shakeit.background.ShizukuCommandService],
     * which Shizuku loads from this APK into a process of its own. Nothing about
     * the result is invented here: the exit code, stdout and stderr come back
     * verbatim, and a failure to reach the service is reported as one rather than
     * as a command that failed.
     */
    private fun run(vararg command: String): ShizukuCommandResult {
        val label = command.joinToString(" ")
        if (!_status.value.authorised) {
            return ShizukuCommandResult(false, -1, "", "Shizuku is not authorised")
        }
        val service = commandService() ?: return ShizukuCommandResult(
            succeeded = false,
            exitCode = -1,
            output = "",
            errorText = "Shizuku's command service did not start",
        )
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(ShizukuCommandService.DESCRIPTOR)
            data.writeInt(command.size)
            command.forEach { data.writeString(it) }
            service.transact(ShizukuCommandService.TRANSACTION_RUN_COMMAND, data, reply, 0)
            reply.readException()
            val exitCode = reply.readInt()
            val output = reply.readString().orEmpty()
            val errorText = reply.readString().orEmpty()
            Log.i(TAG, "`$label` exited $exitCode")
            ShizukuCommandResult(exitCode == 0, exitCode, output, errorText)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            ShizukuCommandResult(false, -1, "", "interrupted while running `$label`")
        } catch (error: Throwable) {
            // A Shizuku that died mid-call lands here rather than taking the app
            // with it. The cached binder is dropped so the next call rebinds, and
            // the state is re-read because the connection is probably gone too.
            Log.w(TAG, "`$label` failed", error)
            commandBinder = null
            refresh()
            ShizukuCommandResult(false, -1, "", error.message ?: error.javaClass.simpleName)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * The command service's binder, binding it if this is the first call.
     *
     * @return null when Shizuku will not start it, which the caller reports as a
     *   connection failure rather than as a command that returned nothing
     */
    private fun commandService(): IBinder? {
        commandBinder?.takeIf { it.pingBinder() }?.let { return it }
        val args = userServiceArgs()
        guard("bindUserService") { Shizuku.bindUserService(args, commandConnection) }
            ?: return null
        val deadline = SystemClock.elapsedRealtime() + BIND_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            val binder = commandBinder
            if (binder != null && binder.pingBinder()) return binder
            try {
                Thread.sleep(POLL_MILLIS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
        Log.w(TAG, "the command service did not come up within ${BIND_TIMEOUT_MILLIS}ms")
        return null
    }

    /**
     * Which service to start, and how Shizuku should tell it apart next time.
     *
     * A fixed [tag][Shizuku.UserServiceArgs.tag] and [version] matter more than
     * they look: Shizuku keys the running service on them, so a build that
     * renames the class under R8 still finds its own service, and bumping the
     * version is what makes Shizuku replace a stale one after an app update.
     */
    private fun userServiceArgs(): Shizuku.UserServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context, ShizukuCommandService::class.java),
    )
        .daemon(false)
        .tag(SERVICE_TAG)
        .version(SERVICE_VERSION)
        .processNameSuffix("cmd")

    /**
     * Every Shizuku call goes through here. The library throws
     * `IllegalStateException` for anything attempted before its binder arrives,
     * and ShakeIT has to keep working on devices where Shizuku is not installed
     * at all — so "Shizuku is unavailable" is a state, never an exception.
     */
    private inline fun <T> guard(what: String, block: () -> T): T? = try {
        block()
    } catch (error: Throwable) {
        Log.w(TAG, "$what failed: ${error.message}")
        null
    }

    private companion object {
        const val TAG = "ShizukuController"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 4242

        /** Stable across renames and obfuscation, unlike the class name. */
        const val SERVICE_TAG = "shakeit-command"

        /** Bump when [ShizukuCommandService] changes, so Shizuku replaces it. */
        const val SERVICE_VERSION = 1

        /** How long to wait for Shizuku to start the service process. */
        const val BIND_TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 25L
    }
}

/** The app-op's short name, as `cmd appops` spells it on the command line. */
private const val OP_RUN_ANY_IN_BACKGROUND_SHORT = "RUN_ANY_IN_BACKGROUND"

/**
 * Reads the list printed by `cmd deviceidle whitelist` and answers whether
 * [packageName] is on it.
 *
 * Pure, because the output format is the fragile part and worth pinning down.
 * Across Android versions the list is headed "Whitelist packages" or "Allowlist
 * packages", and each entry appears bare (`com.shakeit`), prefixed
 * (`package:com.shakeit`), or with the owning user appended (`com.shakeit,10123`).
 *
 * @return null when the output cannot be recognised at all — an error message
 *   rather than a list — because "could not read it" must not be reported as
 *   "not on the list". The same applies in reverse: a parser that misses the
 *   device's own spelling would report a repair as failed when it worked.
 */
internal fun dozeAllowlistContains(output: String, packageName: String): Boolean? {
    if (!output.contains("hitelist") && !output.contains("llowlist")) return null
    return output.lineSequence().any { line ->
        // Tokens, not substrings: `contains` would call `com.shakeit.demo` a hit
        // for `com.shakeit`, and a false "it worked" is worse than no answer.
        line.trim()
            .removePrefix("package:")
            .split(',', ';', ' ', '\t')
            .any { token -> token.trim().removePrefix("package:") == packageName }
    }
}

/**
 * Reads the mode out of `cmd appops get <pkg> RUN_ANY_IN_BACKGROUND`.
 *
 * @return null when the op is not mentioned in the output, which happens on
 *   builds that do not know it — reported as unknown rather than as allowed
 */
internal fun parseAppOpMode(output: String): RestrictionState? {
    val line = output.lineSequence().firstOrNull { it.contains(OP_RUN_ANY_IN_BACKGROUND_SHORT) }
        ?: return null
    val mode = line.substringAfter(':', "")
        .substringBefore(';')
        .trim()
        .lowercase(Locale.ROOT)
    return when (mode) {
        "allow", "allowed", "default" -> RestrictionState.ALLOWED
        "ignore", "ignored", "deny", "denied",
        "foreground",
        -> RestrictionState.RESTRICTED
        // MODE_ERRORED is what appops answers for an op this Android version does
        // not know. That is "no answer", not "restricted", and saying otherwise
        // would report a restriction the platform never confirmed.
        else -> null
    }
}
