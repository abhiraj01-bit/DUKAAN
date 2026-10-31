package com.dukaan.app.core.ai

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DeviceCapabilityManager"

/**
 * Inspects device RAM and memory pressure to safely guide on-device VLM execution.
 *
 * Requirements:
 * - Minimum total RAM: 4.0 GB (~3,800 MB reported).
 * - Minimum free RAM: 1.8 GB (1,800 MB) before loading or running Gemma 3n.
 * - Monitors ComponentCallbacks2 to release cached models on low memory or trim memory events.
 */
@Singleton
class DeviceCapabilityManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ComponentCallbacks2 {

    companion object {
        const val MIN_TOTAL_RAM_MB: Long = 3800L      // ~4 GB class devices
        const val MIN_FREE_RAM_FOR_VLM_MB: Long = 1800L // 1.8 GB free headroom required
        const val MODEL_STORAGE_REQUIRED_MB: Long = 1500L // 1.5 GB disk space
    }

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    private val _isLowMemory = MutableStateFlow(false)
    val isLowMemory: StateFlow<Boolean> = _isLowMemory.asStateFlow()

    private var onTrimMemoryListener: (() -> Unit)? = null

    init {
        context.registerComponentCallbacks(this)
    }

    /**
     * Checks if the device has the physical RAM capacity to run on-device VLM.
     */
    fun hasSufficientTotalRam(): Boolean {
        val totalMb = getTotalRamMb()
        val sufficient = totalMb >= MIN_TOTAL_RAM_MB
        Log.d(TAG, "Total device RAM: $totalMb MB (Required: $MIN_TOTAL_RAM_MB MB, Compatible: $sufficient)")
        return sufficient
    }

    /**
     * Checks if the device currently has enough free headroom to safely execute VLM without OOM killing.
     */
    fun hasSufficientFreeRam(): Boolean {
        val freeMb = getAvailableRamMb()
        val sufficient = freeMb >= MIN_FREE_RAM_FOR_VLM_MB && !_isLowMemory.value
        Log.d(TAG, "Available device RAM: $freeMb MB (Required: $MIN_FREE_RAM_FOR_VLM_MB MB, Safe: $sufficient)")
        return sufficient
    }

    /**
     * Checks if internal storage has enough free space to download and persist model weights.
     */
    fun hasSufficientStorageSpace(): Boolean {
        val usableSpaceMb = context.filesDir.usableSpace / (1024 * 1024)
        val sufficient = usableSpaceMb >= MODEL_STORAGE_REQUIRED_MB
        Log.d(TAG, "Usable storage: $usableSpaceMb MB (Required: $MODEL_STORAGE_REQUIRED_MB MB, Sufficient: $sufficient)")
        return sufficient
    }

    fun getTotalRamMb(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem / (1024 * 1024)
    }

    fun getAvailableRamMb(): Long {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem / (1024 * 1024)
    }

    fun isDeviceUnderMemoryPressure(): Boolean {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        return memoryInfo.lowMemory || _isLowMemory.value
    }

    fun setOnTrimMemoryListener(listener: () -> Unit) {
        this.onTrimMemoryListener = listener
    }

    // ─── ComponentCallbacks2 Lifecycle Hooks ───────────────────────────────

    override fun onTrimMemory(level: Int) {
        Log.w(TAG, "onTrimMemory received with level=$level")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            _isLowMemory.value = true
            onTrimMemoryListener?.invoke()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    override fun onLowMemory() {
        Log.w(TAG, "onLowMemory triggered by OS! Releasing AI model resources immediately.")
        _isLowMemory.value = true
        onTrimMemoryListener?.invoke()
    }
}

