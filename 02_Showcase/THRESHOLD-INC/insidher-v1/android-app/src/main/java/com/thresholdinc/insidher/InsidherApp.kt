package com.thresholdinc.insidher

import android.app.Application
import android.util.Log
import com.thresholdinc.insidher.auth.DeviceKeyStore
import com.thresholdinc.insidher.data.AppPrefs
import com.thresholdinc.insidher.data.ThreadRepository
import com.thresholdinc.insidher.net.BackendClient
import com.thresholdinc.insidher.work.PollWorker
import java.util.UUID

class InsidherApp : Application() {

    lateinit var prefs: AppPrefs
        private set
    lateinit var keyStore: DeviceKeyStore
        private set
    lateinit var repository: ThreadRepository
        private set

    @Volatile
    var backendClient: BackendClient? = null
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        keyStore = DeviceKeyStore()
        repository = ThreadRepository()
        ensureDeviceRegistered()
        PollWorker.schedule(this)
    }

    fun ensureDeviceRegistered() {
        try {
            keyStore.ensureKey()
            val id = prefs.deviceId ?: UUID.randomUUID().toString().also { prefs.deviceId = it }
            val client = BackendClient(
                baseUrl = prefs.backendUrl,
                deviceId = id,
                keyStore = keyStore,
            )
            backendClient = client
            // Best-effort register; offline ok for UI
            try {
                val pub = keyStore.publicKeySpkiBase64()
                client.registerDevice(pub, deviceName = android.os.Build.MODEL)
                Log.d(TAG, "device registered: $id")
            } catch (e: Exception) {
                Log.w(TAG, "register deferred: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "init failed", e)
        }
    }

    fun rebuildClient() {
        val id = prefs.deviceId ?: return
        backendClient = BackendClient(
            baseUrl = prefs.backendUrl,
            deviceId = id,
            keyStore = keyStore,
        )
    }

    companion object {
        private const val TAG = "InsidherApp"
    }
}
