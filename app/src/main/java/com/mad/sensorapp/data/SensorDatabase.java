package com.mad.sensorapp.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {SensorReading.class}, version = 1, exportSchema = false)
public abstract class SensorDatabase extends RoomDatabase {

    public abstract SensorDao sensorDao();

    private static volatile SensorDatabase INSTANCE;

    public static SensorDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SensorDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            SensorDatabase.class,
                            "sensor_db"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
