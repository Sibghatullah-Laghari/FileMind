package com.recall.core;

import java.sql.*;

/**
 * SQLite metadata store.
 * Stores file metadata + activity history.
 * Single connection — all calls are from background threads so we use
 * WAL mode for safe concurrent reads.
 */
public class MetadataDB {

    private static Connection conn;

    public static void init(String dbPath) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

        try (Statement stmt = conn.createStatement()) {
            // WAL mode: allows reads while writes in progress
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA cache_size=-8000");  // 8 MB page cache

            // File metadata table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS files (
                    path          TEXT PRIMARY KEY,
                    last_modified INTEGER,
                    file_size     INTEGER,
                    file_type     TEXT,
                    suggested_name TEXT
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_files_type ON files(file_type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_files_mod  ON files(last_modified)");
        }

        // Activity history table (3-day rolling)
        ActivityHistory.createTable(conn);
    }

    public static Connection getConnection() { return conn; }

    public static void upsert(String path, long modified, long size, String type, String suggestedName) {
        String sql = """
            INSERT OR REPLACE INTO files
              (path, last_modified, file_size, file_type, suggested_name)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.setLong  (2, modified);
            ps.setLong  (3, size);
            ps.setString(4, type);
            ps.setString(5, suggestedName);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB UPSERT] " + e.getMessage());
        }
    }

    public static void delete(String path) {
        String sql = "DELETE FROM files WHERE path = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB DELETE] " + e.getMessage());
        }
    }

    public static void close() {
        try { if (conn != null && !conn.isClosed()) conn.close(); }
        catch (SQLException ignored) {}
    }
}