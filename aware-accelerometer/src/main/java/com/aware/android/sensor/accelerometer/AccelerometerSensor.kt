package com.aware.android.sensor.accelerometer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.aware.android.sensor.core.db.Engine
import com.aware.android.sensor.accelerometer.db.DbEngine
import com.aware.android.sensor.accelerometer.model.AccelerometerDevice
import com.aware.android.sensor.accelerometer.model.AccelerometerEvent
import java.util.TimeZone
import kotlin.collections.ArrayList
import kotlin.collections.toTypedArray

/**
 * Implementation of Aware accelerometer in kotlin as a standalone service.
 * Utilizes db.Engine to support different kinds of databases.
 *
 * @author  sercant
 * @date 17/02/2018
 */
class AccelerometerSensor : Service(), SensorEventListener {

    val TAG = "com.aware.sensor.aclm"

    companion object {
        internal var CONFIG: Accelerometer.AccelerometerConfig = Accelerometer.defaultConfig
    }

    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null

    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var LAST_VALUES: Array<Float>? = null
    private var LAST_TS: Long = 0
    private var LAST_SAVE: Long = 0

    private val dataBuffer = ArrayList<AccelerometerEvent>()

    private var dbEngine: Engine? = null

    override fun onCreate() {
        super.onCreate()

//        AUTHORITY = getAuthority()

        dbEngine = DbEngine.Builder(applicationContext)
                .setDatabaseName(CONFIG.databaseName)
                .setDatabaseType(CONFIG.databaseType)
                .setEncryptionKey(CONFIG.encryptionKey)
                .build()

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        sensorThread = HandlerThread(TAG)

        val sensorThread = sensorThread!!
        sensorThread.start()

        if (CONFIG.wakeLockEnabled) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
            wakeLock!!.acquire()
        }

        sensorHandler = Handler(sensorThread.looper)

        if (CONFIG.debug) Log.d(TAG, "Accelerometer service created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (mAccelerometer == null) {
            if (CONFIG.debug) Log.w(TAG, "This device does not have an accelerometer!")
            stopSelf()
        } else {
            saveAccelerometerDevice(mAccelerometer)

            mSensorManager!!.registerListener(this, mAccelerometer, CONFIG.frequency, sensorHandler)
            LAST_SAVE = System.currentTimeMillis()

            if (CONFIG.debug) Log.d(TAG, "Accelerometer service active: ${CONFIG.frequency} ms")
        }

        return Service.START_STICKY
    }

    private fun saveAccelerometerDevice(acc: Sensor?) {
        if (acc == null) return

        val device = AccelerometerDevice(CONFIG.deviceID, System.currentTimeMillis(), acc)

        dbEngine?.save(device)

        if (CONFIG.debug) Log.d(TAG, "Accelerometer device:" + device.toString())
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // We log current accuracy on the sensor changed event
    }

    override fun onSensorChanged(event: SensorEvent) {
        val currentTime = System.currentTimeMillis()

        if (CONFIG.enforceFrequency && currentTime < LAST_TS + CONFIG.frequency / 1000)
            return

        if (LAST_VALUES != null && CONFIG.threshold > 0 && Math.abs(event.values[0] - LAST_VALUES!![0]) < CONFIG.threshold
                && Math.abs(event.values[1] - LAST_VALUES!![1]) < CONFIG.threshold
                && Math.abs(event.values[2] - LAST_VALUES!![2]) < CONFIG.threshold) {
            return
        }

        LAST_VALUES = arrayOf(event.values[0], event.values[1], event.values[2])

        val data = AccelerometerEvent()
        data.timestamp = currentTime
        data.eventTimestamp = event.timestamp
        data.timezone = TimeZone.getDefault().rawOffset
        data.deviceId = CONFIG.deviceID
        data.x = event.values[0]
        data.y = event.values[1]
        data.z = event.values[2]
        data.accuracy = event.accuracy
        data.label = CONFIG.label

        CONFIG.sensorObserver?.onAccelerometerChanged(data)

        dataBuffer.add(data)
        LAST_TS = currentTime

        if (dataBuffer.size < CONFIG.bufferSize && currentTime < LAST_SAVE + CONFIG.bufferTimeout) {
            return
        }

        val dataBuffer = dataBuffer.toTypedArray()
        this.dataBuffer.clear()

        try {
            dbEngine?.save(dataBuffer)

            val accelerometerData = Intent(Accelerometer.ACTION_AWARE_ACCELEROMETER)
            sendBroadcast(accelerometerData)
        } catch (e: Exception) {
            if (CONFIG.debug) Log.d(TAG, e.message)
        }

        LAST_SAVE = currentTime
    }

    override fun onDestroy() {
        super.onDestroy()

        sensorHandler?.removeCallbacksAndMessages(null)
        mSensorManager?.unregisterListener(this, mAccelerometer)
        sensorThread?.quit()
        if (CONFIG.wakeLockEnabled) {
            wakeLock?.release()
        }
        dbEngine?.close()

        if (CONFIG.debug) Log.d(TAG, "Accelerometer service terminated...")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}