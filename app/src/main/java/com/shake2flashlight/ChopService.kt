package com.shake2flashlight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ChopService : Service(), SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        const val ACTION_STOP = "com.shake2flashlight.STOP"
        const val CHANNEL_ID = "chop"

        /** Also called by the settings screen, so the channel exists before the service ever ran. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Gesto chop-chop", NotificationManager.IMPORTANCE_LOW)
            )
        }
        var running = false
            private set

        /** Single entry point for the UI and the tile: remembers the choice and starts/stops the service. */
        fun setEnabled(context: Context, on: Boolean) {
            Prefs.get(context).edit().putBoolean(Prefs.ENABLED, on).apply()
            val intent = Intent(context, ChopService::class.java)
            if (on) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
        }
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var sensors: SensorManager
    private lateinit var camera: CameraManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private var detector = ChopDetector()
    private var gravity: FloatArray? = null // set only when falling back to the raw accelerometer
    private var proximity: Sensor? = null
    private var covered = false
    private var cameraId: String? = null
    private var torchOn = false
    private val handler = Handler(Looper.getMainLooper())
    private val autoOff = Runnable { setTorch(false) }

    // Tracks the real torch state, so toggling still works after the quick-settings tile changes it.
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id != cameraId) return
            torchOn = enabled
            handler.removeCallbacks(autoOff)
            val minutes = prefs.getInt(Prefs.AUTO_OFF_MIN, 0)
            if (enabled && minutes > 0) handler.postDelayed(autoOff, minutes * 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        camera = getSystemService(CameraManager::class.java)
        cameraId = camera.cameraIdList.firstOrNull {
            camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        camera.registerTorchCallback(torchCallback, handler)

        // Without a wake lock the CPU sleeps with the screen off and sensor events stop.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shake2flashlight:chop")
        wakeLock.acquire()

        sensors = getSystemService(SensorManager::class.java)
        // Linear acceleration is a fused sensor that needs a gyroscope; budget phones only have the accelerometer.
        val motion = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { gravity = FloatArray(3) }
        motion?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        proximity = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        applySettings()
        prefs.registerOnSharedPreferenceChangeListener(this)

        running = true
        refreshTile()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.edit().putBoolean(Prefs.ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        handler.removeCallbacks(autoOff)
        sensors.unregisterListener(this)
        camera.unregisterTorchCallback(torchCallback)
        if (wakeLock.isHeld) wakeLock.release()
        refreshTile()
        super.onDestroy()
    }

    private fun refreshTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        TileService.requestListeningState(this, ComponentName(this, ChopTileService::class.java))
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
        if (key == Prefs.SENSITIVITY || key == Prefs.POCKET) applySettings()
    }

    private fun applySettings() {
        detector = ChopDetector.forSensitivity(prefs.getInt(Prefs.SENSITIVITY, 1))
        val prox = proximity ?: return
        if (prefs.getBoolean(Prefs.POCKET, true)) {
            sensors.registerListener(this, prox, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            sensors.unregisterListener(this, prox)
            covered = false
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_PROXIMITY) {
            covered = e.values[0] < e.sensor.maximumRange
            return
        }
        var (x, y, z) = e.values
        gravity?.let { g ->
            // Low-pass filter isolates gravity; subtracting it leaves the linear acceleration (Android sensor docs formula).
            g[0] = 0.8f * g[0] + 0.2f * x; g[1] = 0.8f * g[1] + 0.2f * y; g[2] = 0.8f * g[2] + 0.2f * z
            x -= g[0]; y -= g[1]; z -= g[2]
        }
        if (!detector.onSample(e.timestamp, x, y, z)) return
        android.util.Log.d("CHOP", "fire torchOn=$torchOn covered=$covered")
        if (covered) return
        if (prefs.getBoolean(Prefs.VIBRATE, true)) vibrate()
        setTorch(!torchOn)
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            else -> vibrator.vibrate(40)
        }
    }

    private fun setTorch(on: Boolean) {
        val id = cameraId ?: return
        try {
            camera.setTorchMode(id, on)
        } catch (_: CameraAccessException) {
            // Camera is in use by another app; nothing to toggle.
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?) = null

    private fun notification(): Notification {
        ensureChannel(this)
        val stop = PendingIntent.getService(
            this, 0, Intent(this, ChopService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Chop-chop ativo")
            .setContentText("Faça o movimento para ligar/desligar a lanterna")
            .setContentIntent(open)
            .addAction(0, "Parar", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
