package com.mad.sensorapp.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sensor_readings")
public class SensorReading {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String sensorType;   // "ACCELEROMETER", "LIGHT", "PROXIMITY", "GYROSCOPE", "STEP"
    public float x, y, z;       // x=primary value, y/z for multi-axis
    public long timestamp;      // System.currentTimeMillis()
    public String sessionId;    // groups readings by recording session

    public SensorReading() {}

    public SensorReading(String sensorType, float x, float y, float z,
                         long timestamp, String sessionId) {
        this.sensorType = sensorType;
        this.x = x; this.y = y; this.z = z;
        this.timestamp = timestamp;
        this.sessionId = sessionId;
    }
}
