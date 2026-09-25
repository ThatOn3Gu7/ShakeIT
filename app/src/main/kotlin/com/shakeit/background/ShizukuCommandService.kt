package com.shakeit.background

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.Parcel

/**
 * The half of the Shizuku integration that runs **inside Shizuku's process**, as
 * shell (uid 2000) or root (uid 0).
 *
 * Shizuku removed `Shizuku.newProcess` in 13.1.1 and points everyone at its User
 * Service API instead: the app supplies a class, Shizuku loads it from the app's
 * own APK in a process of its own making, and hands the app a binder to it. So
 * this class is instantiated there, not here, which has three consequences worth
 * spelling out:
 *
 *  * it may only touch framework classes — `Context` APIs like
 *    `registerReceiver` do not work in that process;
 *  * it is constructed reflectively, so both the no-argument constructor and the
 *    `Context` one Shizuku v13 looks for first are declared;
 *  * it is reached through a raw binder transaction, hand-written below rather
 *    than generated from AIDL, because one `run a command` method does not need a
 *    build-feature and an `.aidl` file of its own.
 *
 * What it does is deliberately small: run one command, return its exit code, its
 * stdout and its stderr. Commands are passed as an argument array to
 * [ProcessBuilder], never to a shell, so nothing an app could put in a package
 * name or an argument is interpreted as shell syntax. Every caller passes a
 * literal `cmd` line and this app's own package name.
 *
 * Nothing here reports success on its own behalf: the exit code and the output go
 * back verbatim, and the caller verifies the change by reading the setting again.
 */
class ShizukuCommandService : Binder {

    /** Shizuku v13 tries a `Context` constructor first; the context is not used. */
    @Suppress("unused", "UNUSED_PARAMETER")
    constructor(context: Context?) : super()

    /** Older Shizuku versions construct the service with no arguments. */
    constructor() : super()

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean =
        when (code) {
            TRANSACTION_RUN_COMMAND -> {
                runCommand(data, reply)
                true
            }

            // Shizuku's own "this service is being replaced" call, at the
            // transaction code its documentation states. The process belongs to
            // Shizuku, so ending it is the only way to give it back.
            TRANSACTION_DESTROY -> {
                reply?.writeNoException()
                System.exit(0)
                true
            }

            else -> super.onTransact(code, data, reply, flags)
        }

    /** Shizuku calls `asBinder` when the instance it built is not already one. */
    fun asBinder(): IBinder = this

    private fun runCommand(data: Parcel, reply: Parcel?) {
        data.enforceInterface(DESCRIPTOR)
        val count = data.readInt().coerceIn(0, MAX_ARGUMENTS)
        val command = Array(count) { data.readString().orEmpty() }
        val outcome = execute(command)
        reply?.apply {
            writeNoException()
            writeInt(outcome.exitCode)
            writeString(outcome.output)
            writeString(outcome.errorText)
        }
    }

    private fun execute(command: Array<String>): Outcome {
        if (command.isEmpty()) return Outcome(-1, "", "no command was sent")
        return try {
            val process = ProcessBuilder(command.toList()).start()
            // Both streams are read to end-of-file before the exit code is taken.
            // Sequential reading can in principle deadlock on a process that
            // fills one pipe while the other is being drained; `cmd` writes a few
            // hundred bytes at most, and the alternative is a thread per call in
            // a process this app does not own.
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val errorText = process.errorStream.bufferedReader().use { it.readText() }.trim()
            Outcome(process.waitFor(), output, errorText)
        } catch (error: Throwable) {
            Outcome(-1, "", error.message ?: error.javaClass.name)
        }
    }

    private class Outcome(
        val exitCode: Int,
        val output: String,
        val errorText: String,
    )

    companion object {
        /** Must match what the caller writes with `writeInterfaceToken`. */
        const val DESCRIPTOR = "com.shakeit.background.IShizukuCommandService"

        const val TRANSACTION_RUN_COMMAND = IBinder.FIRST_CALL_TRANSACTION + 1

        /** Shizuku's destroy code, as documented in the Shizuku-API README. */
        const val TRANSACTION_DESTROY = 16777115

        /** A guard against a corrupt or hostile parcel, not a real limit. */
        private const val MAX_ARGUMENTS = 32
    }
}
