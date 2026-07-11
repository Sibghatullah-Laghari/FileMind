package com.recall.core;

import java.sql.*;

/**
 * SQLite metadata store.
 * Persists file metadata and activity history.
 * Uses a single shared connection with WAL mode enabled to support
 * concurrent reads during background write operations.
 */
public class MetadataDB {

    private static Connection conn;

    public static void init(String dbPath) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

        try (Statement stmt = conn.createStatement()) {
            // Enable WAL mode for improved concurrent read/write performance.
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");
            stmt.execute("PRAGMA cache_size=-8000");  // Configure an 8 MB page cache.

            // Create the file metadata table if it does not already exist.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS files (
                    path           TEXT PRIMARY KEY,
                    last_modified  INTEGER,
                    file_size      INTEGER,
                    file_type      TEXT,
                    suggested_name TEXT
                )
            """);

            // Create indexes to improve lookup performance.
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_files_type ON files(file_type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_files_mod ON files(last_modified)");
        }

        // Initialize the activity history table (maintains a 3-day rolling history).
        ActivityHistory.createTable(conn);
    }

    public static Connection getConnection() {
        return conn;
    }

    public static void upsert(String path, long modified, long size, String type, String suggestedName) {
        String sql = """
            INSERT OR REPLACE INTO files
              (path, last_modified, file_size, file_type, suggested_name)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.setLong(2, modified);
            ps.setLong(3, size);
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

    /**
     * Closes the database connection if it is currently open.
     */
    public static void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException ignored) {
        }
    }
}
