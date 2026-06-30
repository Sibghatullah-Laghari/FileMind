package com.recall.core;

import java.sql.*;

public class MetadataDB {
    private static Connection conn;

    public static void init(String dbPath) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS files (
                    path TEXT PRIMARY KEY,
                    last_modified INTEGER,
                    file_size INTEGER,
                    file_type TEXT
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS activity (
                    path TEXT PRIMARY KEY,
                    opened_at INTEGER NOT NULL
                )
            """);
        }
    }

    public static void upsert(String path, long modified, long size, String type) {
        String sql = "INSERT OR REPLACE INTO files (path, last_modified, file_size, file_type) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.setLong(2, modified);
            pstmt.setLong(3, size);
            pstmt.setString(4, type);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void delete(String path) {
        String sql = "DELETE FROM files WHERE path = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void close() {
        try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
    }

    // Getter for connection (so ActivityHistory and SearchUI can use it)
    public static Connection getConnection() {
        return conn;
    }
}