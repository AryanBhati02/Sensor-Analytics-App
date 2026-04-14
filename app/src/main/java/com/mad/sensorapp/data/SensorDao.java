package com.mad.sensorapp.data;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface SensorDao {

    @Insert
    void insert(SensorReading reading);

    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC LIMIT 500")
    LiveData<List<SensorReading>> getAllReadings();

    @Query("SELECT * FROM sensor_readings WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    List<SensorReading> getSession(String sessionId);

    @Query("SELECT DISTINCT sessionId FROM sensor_readings ORDER BY timestamp DESC")
    LiveData<List<String>> getAllSessionIds();

    /** Session summaries for history screen */
    @Query("SELECT sessionId, COUNT(*) as count, MIN(timestamp) as firstTs, MAX(timestamp) as lastTs " +
           "FROM sensor_readings GROUP BY sessionId ORDER BY firstTs DESC")
    List<SessionInfo> getSessionSummaries();

    @Query("SELECT MIN(x) FROM sensor_readings WHERE sensorType = :type")
    float getMin(String type);

    @Query("SELECT MAX(x) FROM sensor_readings WHERE sensorType = :type")
    float getMax(String type);

    @Query("SELECT AVG(x) FROM sensor_readings WHERE sensorType = :type")
    float getAvg(String type);

    @Query("DELETE FROM sensor_readings WHERE sessionId = :sessionId")
    void deleteSession(String sessionId);

    @Query("DELETE FROM sensor_readings")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM sensor_readings WHERE sessionId = :sessionId")
    int getSessionCount(String sessionId);
}
