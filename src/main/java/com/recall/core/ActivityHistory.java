package com.recall.core;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public class ActivityHistory {

    public static void recordOpen(Connection conn, String path) {
        String sql = "INSERT OR REPLACE INTO activity (path, opened_at) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.setLong(2, Instant.now().toEpochMilli());
            ps.executeUpdate();
            pruneOld(conn);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static List<String> query(Connection conn, long afterMs, long beforeMs) {
        String sql = "SELECT path FROM activity WHERE opened_at BETWEEN ? AND ? ORDER BY opened_at DESC";
        List<String> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, afterMs);
            ps.setLong(2, beforeMs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(rs.getString("path"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return results;
    }

    private static void pruneOld(Connection conn) throws SQLException {
        long cutoff = Instant.now().minusSeconds(3 * 86_400).toEpochMilli();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM activity WHERE opened_at < ?")) {
            ps.setLong(1, cutoff);
            ps.executeUpdate();
        }
    }
}