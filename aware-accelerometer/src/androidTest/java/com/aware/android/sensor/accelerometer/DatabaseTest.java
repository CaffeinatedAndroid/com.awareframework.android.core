package com.aware.android.sensor.accelerometer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.aware.android.sensor.accelerometer.db.DbEngine;
import com.aware.android.sensor.accelerometer.model.AccelerometerDevice;
import com.aware.android.sensor.accelerometer.model.AccelerometerEvent;
import com.aware.android.sensor.core.db.Engine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseTest {

    private static final String ENCRYPTION_KEY = "custom_key";

    private Engine engine;
    private Engine encryptedEngine;

    @Before
    public void init() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        engine = new DbEngine.Builder(appContext)
                .setDbType(Engine.DatabaseType.NONE)
                .build();

        assertNull(engine);

        engine = new DbEngine.Builder(appContext)
                .setDbName("test.db")
                .setDbType(Engine.DatabaseType.ROOM)
                .build();

        assertNotNull(engine);

        encryptedEngine = new DbEngine.Builder(appContext)
                .setDbName("test_encrypted.db")
                .setDbType(Engine.DatabaseType.ROOM)
                .setDbKey(ENCRYPTION_KEY)
                .build();

        assertNotNull(engine);
    }

    @Test
    public void testInsertAllEvents() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();
        // Create some default events
        ArrayList<AccelerometerEvent> events = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            events.add(new AccelerometerEvent());
        }

        final AccelerometerEvent[] data_buffer = new AccelerometerEvent[events.size()];
        events.toArray(data_buffer);

        engine.removeAll().join();
        engine.save(data_buffer).join();
        List<AccelerometerEvent> data = engine.getAll(AccelerometerEvent.class);
        assertEquals(events.size(), data.size());
        engine.close();

        encryptedEngine.removeAll().join();
        encryptedEngine.save(data_buffer).join();
        List<AccelerometerEvent> data2 = encryptedEngine.getAll(AccelerometerEvent.class);
        assertEquals(events.size(), data2.size());
        encryptedEngine.close();
    }

    @Test
    public void testUpdateDevice() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        SensorManager mSensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        Sensor mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        AccelerometerDevice device = new AccelerometerDevice("", System.currentTimeMillis(), mAccelerometer);

        engine.removeAll().join();
        engine.save(device).join();

        List<AccelerometerDevice> savedDevice = engine.getAll(AccelerometerDevice.class);
        assertEquals(1, savedDevice.size());

        // TODO (sercant): we should also check if the entries are same here.
        String uuid = UUID.randomUUID().toString();
        device.setDeviceId(uuid);
        engine.save(device).join();

        savedDevice = engine.getAll(AccelerometerDevice.class);
        assertEquals(1, savedDevice.size());
        assertEquals(device.getDeviceId(), savedDevice.get(0).getDeviceId());
    }

    @After
    public void tearDown() {
        engine.close();
        encryptedEngine.close();
    }
}
