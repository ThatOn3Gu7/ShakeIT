package com.shakeit.hardware

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.getSystemService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The real flashlight, and the single source of truth for whether it is on.
 *
 * Built on [CameraManager.setTorchMode], which drives the torch without opening
 * the camera and therefore needs no `CAMERA` permission. [torchOn] is published
 * from a [CameraManager.TorchCallback] rather than from whatever was last
 * requested, so the UI also follows the torch when the system or another app
 * changes it — for example when a camera app grabs the flash and the torch is
 * forced off.
 *
 * There is no API to *read* the current torch state, so [torchOn] starts false
 * and is corrected by the first callback; [start] registers that callback for
 * the lifetime of the process.
 */
class TorchController(context: Context) {

    private val cameraManager: CameraManager? = context.getSystemService()
    private val callbackHandler = Handler(Looper.getMainLooper())

    private val _torchOn = MutableStateFlow(false)

    /** Whether the flash is actually lit right now. */
    val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()

    /**
     * Whether this device has a usable flash at all. Resolved on first access
     * rather than at construction: asking the camera service costs a binder
     * round trip, and paying that during `Application.onCreate` would slow every
     * cold start for a question nobody asks until the first tap.
     */
    val available: Boolean
        get() = torchCameraId != null

    /** The flash unit's camera id. The camera list never changes, so resolve once. */
    private val torchCameraId: String? by lazy { resolveTorchCamera() }

    private var listening = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) _torchOn.value = enabled
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            // Another app opened the camera, or the flash faulted. Reporting
            // "off" here is what keeps the screen honest.
            if (cameraId == torchCameraId) _torchOn.value = false
        }
    }

    /**
     * Starts listening for the torch's real state. Registering costs nothing and
     * touches no camera, which is why it can happen as the process starts.
     */
    fun start() {
        if (listening) return
        cameraManager?.registerTorchCallback(torchCallback, callbackHandler)
        listening = true
    }

    /** Stops listening. The torch itself is left exactly as it is. */
    fun stop() {
        if (!listening) return
        cameraManager?.unregisterTorchCallback(torchCallback)
        listening = false
    }

    /**
     * Asks the hardware to change state.
     *
     * @return false when there is no flash or the camera service refused the
     *   request, in which case [torchOn] is left untouched — the UI follows the
     *   hardware, never a request that did not land.
     */
    fun setTorch(enabled: Boolean): Boolean {
        val manager = cameraManager ?: return false
        val cameraId = torchCameraId ?: run {
            Log.w(TAG, "no camera with a flash unit; torch unavailable")
            return false
        }
        return try {
            manager.setTorchMode(cameraId, enabled)
            // Published optimistically so a tap feels instant; the callback
            // confirms it, and overrules it if the hardware disagrees.
            _torchOn.value = enabled
            true
        } catch (error: CameraAccessException) {
            Log.w(TAG, "torch request rejected by the camera service", error)
            false
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "torch camera id is no longer valid", error)
            false
        } catch (error: IllegalStateException) {
            Log.w(TAG, "torch is owned by another client", error)
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "not allowed to drive the torch", error)
            false
        }
    }

    /** Flips the torch to the opposite of its real current state. */
    fun toggleTorch(): Boolean = setTorch(!torchOn.value)

    /**
     * Prefers the back camera's flash — that is the one people mean by
     * "flashlight" — and falls back to any camera with a flash unit.
     */
    private fun resolveTorchCamera(): String? {
        val manager = cameraManager ?: return null
        return try {
            val ids = manager.cameraIdList
            ids.firstOrNull { id -> hasFlash(manager, id) && facesBack(manager, id) }
                ?: ids.firstOrNull { id -> hasFlash(manager, id) }
        } catch (error: CameraAccessException) {
            Log.w(TAG, "could not list the cameras", error)
            null
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "could not list the cameras", error)
            null
        }
    }

    private fun hasFlash(manager: CameraManager, cameraId: String): Boolean =
        characteristicsOf(manager, cameraId)
            ?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

    private fun facesBack(manager: CameraManager, cameraId: String): Boolean =
        characteristicsOf(manager, cameraId)
            ?.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK

    private fun characteristicsOf(
        manager: CameraManager,
        cameraId: String,
    ): CameraCharacteristics? = try {
        manager.getCameraCharacteristics(cameraId)
    } catch (error: CameraAccessException) {
        Log.w(TAG, "could not read camera $cameraId", error)
        null
    } catch (error: IllegalArgumentException) {
        Log.w(TAG, "unknown camera $cameraId", error)
        null
    }

    private companion object {
        const val TAG = "TorchController"
    }
}
