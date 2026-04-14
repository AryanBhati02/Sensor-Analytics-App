package com.mad.sensorapp.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SensorDao_Impl implements SensorDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SensorReading> __insertionAdapterOfSensorReading;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSession;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public SensorDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSensorReading = new EntityInsertionAdapter<SensorReading>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `sensor_readings` (`id`,`sensorType`,`x`,`y`,`z`,`timestamp`,`sessionId`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final SensorReading entity) {
        statement.bindLong(1, entity.id);
        if (entity.sensorType == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.sensorType);
        }
        statement.bindDouble(3, entity.x);
        statement.bindDouble(4, entity.y);
        statement.bindDouble(5, entity.z);
        statement.bindLong(6, entity.timestamp);
        if (entity.sessionId == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.sessionId);
        }
      }
    };
    this.__preparedStmtOfDeleteSession = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM sensor_readings WHERE sessionId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM sensor_readings";
        return _query;
      }
    };
  }

  @Override
  public void insert(final SensorReading reading) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfSensorReading.insert(reading);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteSession(final String sessionId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSession.acquire();
    int _argIndex = 1;
    if (sessionId == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, sessionId);
    }
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteSession.release(_stmt);
    }
  }

  @Override
  public void deleteAll() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteAll.release(_stmt);
    }
  }

  @Override
  public LiveData<List<SensorReading>> getAllReadings() {
    final String _sql = "SELECT * FROM sensor_readings ORDER BY timestamp DESC LIMIT 500";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"sensor_readings"}, false, new Callable<List<SensorReading>>() {
      @Override
      @Nullable
      public List<SensorReading> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSensorType = CursorUtil.getColumnIndexOrThrow(_cursor, "sensorType");
          final int _cursorIndexOfX = CursorUtil.getColumnIndexOrThrow(_cursor, "x");
          final int _cursorIndexOfY = CursorUtil.getColumnIndexOrThrow(_cursor, "y");
          final int _cursorIndexOfZ = CursorUtil.getColumnIndexOrThrow(_cursor, "z");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final List<SensorReading> _result = new ArrayList<SensorReading>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SensorReading _item;
            _item = new SensorReading();
            _item.id = _cursor.getLong(_cursorIndexOfId);
            if (_cursor.isNull(_cursorIndexOfSensorType)) {
              _item.sensorType = null;
            } else {
              _item.sensorType = _cursor.getString(_cursorIndexOfSensorType);
            }
            _item.x = _cursor.getFloat(_cursorIndexOfX);
            _item.y = _cursor.getFloat(_cursorIndexOfY);
            _item.z = _cursor.getFloat(_cursorIndexOfZ);
            _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            if (_cursor.isNull(_cursorIndexOfSessionId)) {
              _item.sessionId = null;
            } else {
              _item.sessionId = _cursor.getString(_cursorIndexOfSessionId);
            }
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public List<SensorReading> getSession(final String sessionId) {
    final String _sql = "SELECT * FROM sensor_readings WHERE sessionId = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (sessionId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, sessionId);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfSensorType = CursorUtil.getColumnIndexOrThrow(_cursor, "sensorType");
      final int _cursorIndexOfX = CursorUtil.getColumnIndexOrThrow(_cursor, "x");
      final int _cursorIndexOfY = CursorUtil.getColumnIndexOrThrow(_cursor, "y");
      final int _cursorIndexOfZ = CursorUtil.getColumnIndexOrThrow(_cursor, "z");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
      final List<SensorReading> _result = new ArrayList<SensorReading>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final SensorReading _item;
        _item = new SensorReading();
        _item.id = _cursor.getLong(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfSensorType)) {
          _item.sensorType = null;
        } else {
          _item.sensorType = _cursor.getString(_cursorIndexOfSensorType);
        }
        _item.x = _cursor.getFloat(_cursorIndexOfX);
        _item.y = _cursor.getFloat(_cursorIndexOfY);
        _item.z = _cursor.getFloat(_cursorIndexOfZ);
        _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        if (_cursor.isNull(_cursorIndexOfSessionId)) {
          _item.sessionId = null;
        } else {
          _item.sessionId = _cursor.getString(_cursorIndexOfSessionId);
        }
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public LiveData<List<String>> getAllSessionIds() {
    final String _sql = "SELECT DISTINCT sessionId FROM sensor_readings ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"sensor_readings"}, false, new Callable<List<String>>() {
      @Override
      @Nullable
      public List<String> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<String> _result = new ArrayList<String>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final String _item;
            if (_cursor.isNull(0)) {
              _item = null;
            } else {
              _item = _cursor.getString(0);
            }
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public List<SessionInfo> getSessionSummaries() {
    final String _sql = "SELECT sessionId, COUNT(*) as count, MIN(timestamp) as firstTs, MAX(timestamp) as lastTs FROM sensor_readings GROUP BY sessionId ORDER BY firstTs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfSessionId = 0;
      final int _cursorIndexOfCount = 1;
      final int _cursorIndexOfFirstTs = 2;
      final int _cursorIndexOfLastTs = 3;
      final List<SessionInfo> _result = new ArrayList<SessionInfo>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final SessionInfo _item;
        _item = new SessionInfo();
        if (_cursor.isNull(_cursorIndexOfSessionId)) {
          _item.sessionId = null;
        } else {
          _item.sessionId = _cursor.getString(_cursorIndexOfSessionId);
        }
        _item.count = _cursor.getInt(_cursorIndexOfCount);
        _item.firstTs = _cursor.getLong(_cursorIndexOfFirstTs);
        _item.lastTs = _cursor.getLong(_cursorIndexOfLastTs);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public float getMin(final String type) {
    final String _sql = "SELECT MIN(x) FROM sensor_readings WHERE sensorType = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final float _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getFloat(0);
      } else {
        _result = 0f;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public float getMax(final String type) {
    final String _sql = "SELECT MAX(x) FROM sensor_readings WHERE sensorType = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final float _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getFloat(0);
      } else {
        _result = 0f;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public float getAvg(final String type) {
    final String _sql = "SELECT AVG(x) FROM sensor_readings WHERE sensorType = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final float _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getFloat(0);
      } else {
        _result = 0f;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int getSessionCount(final String sessionId) {
    final String _sql = "SELECT COUNT(*) FROM sensor_readings WHERE sessionId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (sessionId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, sessionId);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
