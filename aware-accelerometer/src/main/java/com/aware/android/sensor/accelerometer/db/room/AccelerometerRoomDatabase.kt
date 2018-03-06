package com.aware.android.sensor.accelerometer.db.room

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import com.commonsware.cwac.saferoom.SafeHelperFactory

/**
 * Room accelerometer database class.
 * Note that creating new instances of Room db is costly.
 *
 * @author  sercant
 * @date 23/02/2018
 */
@Database(entities = arrayOf(EventRoomEntity::class, DeviceRoomEntity::class), version = 6)
abstract class AccelerometerRoomDatabase : RoomDatabase() {

    abstract fun AccelerometerEventDao(): AccelerometerEventDao
    abstract fun AccelerometerDeviceDao(): AccelerometerDeviceDao

    companion object {
        var instance: AccelerometerRoomDatabase? = null
            private set

        fun init(context: Context, encryptionKey: String?, dbName: String) {
            synchronized(AccelerometerRoomDatabase::class) {
                if (instance != null) {
                    destroyInstance()
                }

                val builder = Room.databaseBuilder(context.applicationContext,
                        AccelerometerRoomDatabase::class.java, dbName)
                if (encryptionKey != null) {
                    builder.openHelperFactory(SafeHelperFactory(encryptionKey.toCharArray()))
                }

                instance = builder
                        // TODO (sercant): handle migrations!
                        .fallbackToDestructiveMigration()
                        .build()
            }
        }

        fun destroyInstance() {
            instance?.close()
            instance = null
        }
    }

    fun clearAllData() {
        AccelerometerEventDao().deleteAll()
        AccelerometerDeviceDao().deleteAll()
    }
}