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

    /** Retention period for activity records: 3 days in milliseconds. */
    private static final long RETENTION_MS = 3L * 24 * 60 * 60 * 1000; // 3 days

    /**
     * Creates the 'activity' table and its index if they do not exist.
     * Should be called during database initialization (e.g., from MetadataDB.init()).
     *
     * @param conn the active database connection
     * @throws SQLException if a database access error occurs
     */
    public static void createTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS activity (
                    path        TEXT    NOT NULL,
                    opened_at   INTEGER NOT NULL,
                    open_count  INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY (path, opened_at)
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_activity_time ON activity(opened_at)");
        }
    }

    /**
     * Records that the user opened a file.
     * Called every time they double-click a search result.
     * The timestamp is rounded to the nearest hour to avoid excessive granularity
     * while preserving time-of-day information.
     *
     * @param conn the active database connection
     * @param path the absolute file path that was opened
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
            pruneOld(conn);
        } catch (SQLException e) {
            System.err.println("[HISTORY WRITE] " + e.getMessage());
        }
    }

    /**
     * Queries files opened within a specified time range, with optional time‑of‑day filtering.
     * Results are limited to the most recent 200 distinct files, ordered by last open time descending.
     *
     * @param conn           the active database connection
     * @param afterMs        lower bound of the time range in epoch milliseconds (inclusive)
     * @param beforeMs       upper bound of the time range in epoch milliseconds (inclusive);
     *                       use {@code 0} or {@code Long.MAX_VALUE} for no upper limit
     * @param todAfterHour   optional hour of day (0‑23) for lower time‑of‑day filter;
     *                       only files opened at or after this hour are included
     * @param todBeforeHour  optional hour of day (0‑23) for upper time‑of‑day filter;
     *                       only files opened at or before this hour are included
     * @return a list of distinct file paths that satisfy the criteria,
     *         ordered by the most recent opening time descending
     */
    public static List<String> query(
            Connection conn, long afterMs, long beforeMs,
            Integer todAfterHour, Integer todBeforeHour
    ) {
        List<String> results = new ArrayList<>();
        if (conn == null) return results;

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

                    // Apply time-of-day filter post-query
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

    /**
     * Retrieves the most recently opened files, intended for the "Recent" section on the home screen.
     *
     * @param conn   the active database connection
     * @param limit  maximum number of file paths to return
     * @return a list of distinct file paths, ordered by most recent opening time descending,
     *         up to the specified limit
     */
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

    /**
     * Deletes all activity records older than the retention period (3 days).
     * Called automatically after each write operation to keep the table size bounded.
     *
     * @param conn the active database connection
     * @throws SQLException if a database access error occurs
     */
    private static void pruneOld(Connection conn) throws SQLException {
        long cutoff = Instant.now().toEpochMilli() - RETENTION_MS;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM activity WHERE opened_at < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        }
    }
}