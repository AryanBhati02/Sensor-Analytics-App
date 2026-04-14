package com.mad.sensorapp.data;

/**
 * Room POJO for session summary queries.
 * Field names match the SQL column aliases exactly.
 */
public class SessionInfo {
    public String sessionId;
    public int    count;
    public long   firstTs;
    public long   lastTs;

    public SessionInfo() {}

    public SessionInfo(String sessionId, int count, long firstTs, long lastTs) {
        this.sessionId = sessionId;
        this.count     = count;
        this.firstTs   = firstTs;
        this.lastTs    = lastTs;
    }
}
