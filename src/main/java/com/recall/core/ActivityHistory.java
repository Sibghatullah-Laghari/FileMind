package com.recall.core;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Tracks which files the user has opened via FileMind.
 * Stored in the same SQLite DB as MetadataDB.
 * Retention: 3 days rolling window (auto-pruned on every write).
 *
 * Used for queries like:
 *   "files I worked on yesterday"
 *   "what was I doing 2 days ago"
 *   "files opened between 2am and 5pm"
 */
public class ActivityHistory {

    private static final long RETENTION_MS = 3L * 24 * 60 * 60 * 1000; // 3 days

    /** Call this from MetadataDB.init() to ensure the table exists */
    public static void createTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Table: path + timestamp bucket (hour), with open_count for aggregation
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS activity (
                    path        TEXT    NOT NULL,
                    opened_at   INTEGER NOT NULL,
                    open_count  INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY (path, opened_at)
                )
            """);
            // Index for fast time-range pruning/querying
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_time ON activity(opened_at)");
        }
    }

    /**
     * Record that the user opened a file.
     * Called every time they double-click a search result.
     */
    public static void recordOpen(Connection conn, String path) {
        if (conn == null || path == null) return;
        String sql = """
            INSERT INTO activity (path, opened_at, open_count) VALUES (?, ?, 1)
            ON CONFLICT(path, opened_at) DO UPDATE SET open_count = open_count + 1
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            // Round to hour bucket — avoids duplicate rows for same file opened
            // multiple times in same hour, while preserving time-of-day info
            long hourBucket = (Instant.now().toEpochMilli() / 3_600_000) * 3_600_000;
            ps.setLong(2, hourBucket);
            ps.executeUpdate();
            pruneOld(conn); // Auto-clean entries older than retention period
        } catch (SQLException e) {
            System.err.println("[HISTORY WRITE] " + e.getMessage());
        }
    }

    /**
     * Query files opened in a time range.
     * @param afterMs   epoch millis lower bound (inclusive)
     * @param beforeMs  epoch millis upper bound (inclusive), or Long.MAX_VALUE
     * @param todAfterHour   time-of-day lower bound (0-23), or null
     * @param todBeforeHour  time-of-day upper bound (0-23), or null
     */
    public static List<String> query(
            Connection conn, long afterMs, long beforeMs,
            Integer todAfterHour, Integer todBeforeHour
    ) {
        List<String> results = new ArrayList<>();
        if (conn == null) return results;

        // Get distinct files in time range, grouped by path, most recent first
        String sql = """
            SELECT DISTINCT path, MAX(opened_at) as last_open
            FROM activity
            WHERE opened_at >= ? AND opened_at <= ?
            GROUP BY path
            ORDER BY last_open DESC
            LIMIT 200
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, afterMs);
            ps.setLong(2, beforeMs == 0 ? Long.MAX_VALUE : beforeMs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String path = rs.getString("path");
                    long ts     = rs.getLong("last_open");

                    // Apply time-of-day filter post-query (not indexable)
                    if (todAfterHour != null || todBeforeHour != null) {
                        int hour = java.time.LocalDateTime
                                .ofInstant(Instant.ofEpochMilli(ts), java.time.ZoneId.systemDefault())
                                .getHour();
                        if (todAfterHour  != null && hour < todAfterHour)  continue;
                        if (todBeforeHour != null && hour > todBeforeHour) continue;
                    }
                    results.add(path);
                }
            }
        } catch (SQLException e) {
            System.err.println("[HISTORY QUERY] " + e.getMessage());
        }
        return results;
    }

    /** Most recently opened files — used for "Recent" section on home screen */
    public static List<String> recent(Connection conn, int limit) {
        List<String> results = new ArrayList<>();
        if (conn == null) return results;
        String sql = """
            SELECT DISTINCT path FROM activity
            ORDER BY opened_at DESC LIMIT ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(rs.getString("path"));
            }
        } catch (SQLException e) {
            System.err.println("[HISTORY RECENT] " + e.getMessage());
        }
        return results;
    }

    // Deletes records older than RETENTION_MS from current time
    private static void pruneOld(Connection conn) throws SQLException {
        long cutoff = Instant.now().toEpochMilli() - RETENTION_MS;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM activity WHERE opened_at < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        }
    }
}
