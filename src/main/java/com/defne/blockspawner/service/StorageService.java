package com.defne.blockspawner.service;

import com.defne.blockspawner.model.SpawnerInstance;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StorageService {
    private final File dbFile;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "BlockSpawner-SQLite"));
    private String jdbcUrl;

    public StorageService(File dbFile) {
        this.dbFile = dbFile;
    }

    public void init() throws SQLException {
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS spawners (
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        owner TEXT NOT NULL,
                        level INTEGER NOT NULL,
                        PRIMARY KEY(world, x, y, z)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_chunk_lookup ON spawners(world, x, z)");
        }
    }

    public void upsertAsync(SpawnerInstance instance) {
        executor.execute(() -> {
            String sql = """
                    INSERT INTO spawners(world, x, y, z, type, owner, level)
                    VALUES(?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(world, x, y, z)
                    DO UPDATE SET type = excluded.type, owner = excluded.owner, level = excluded.level
                    """;
            try (Connection connection = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, instance.world());
                ps.setInt(2, instance.x());
                ps.setInt(3, instance.y());
                ps.setInt(4, instance.z());
                ps.setString(5, instance.typeId());
                ps.setString(6, instance.owner().toString());
                ps.setInt(7, instance.level());
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        });
    }

    public void deleteAsync(String world, int x, int y, int z) {
        executor.execute(() -> {
            try (Connection connection = DriverManager.getConnection(jdbcUrl);
                 PreparedStatement ps = connection.prepareStatement(
                         "DELETE FROM spawners WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        });
    }

    public List<SpawnerInstance> loadChunk(String world, int chunkX, int chunkZ) {
        List<SpawnerInstance> result = new ArrayList<>();
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT world, x, y, z, type, owner, level FROM spawners WHERE world = ? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ?")) {
            ps.setString(1, world);
            ps.setInt(2, minX);
            ps.setInt(3, maxX);
            ps.setInt(4, minZ);
            ps.setInt(5, maxZ);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new SpawnerInstance(
                            rs.getString("world"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z"),
                            rs.getString("type"),
                            UUID.fromString(rs.getString("owner")),
                            rs.getInt("level")
                    ));
                }
            }
        } catch (SQLException | IllegalArgumentException ignored) {
        }
        return result;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
