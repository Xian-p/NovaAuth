package com.novaauth.storage;

import com.novaauth.NovaAuthPlugin;
import com.novaauth.config.NovaAuthConfig;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class SqliteUserRepository implements UserRepository {

    private final NovaAuthPlugin plugin;
    private final Executor executor;
    private volatile File dbFile;

    public SqliteUserRepository(NovaAuthPlugin plugin, NovaAuthConfig config, Executor executor) {
        this.plugin = plugin;
        this.executor = executor;
        this.dbFile = new File(plugin.getDataFolder(), config.databaseFile());
        init();
    }

    public void reload(NovaAuthConfig config) {
        this.dbFile = new File(plugin.getDataFolder(), config.databaseFile());
        init();
    }

    private String jdbcUrl() {
        return "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    private void init() {
        plugin.getDataFolder().mkdirs();

        try (Connection con = DriverManager.getConnection(jdbcUrl());
             Statement st = con.createStatement()) {

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                  uuid TEXT PRIMARY KEY,
                  name TEXT NOT NULL,
                  password_hash TEXT NOT NULL,
                  registered_at INTEGER NOT NULL,
                  last_login INTEGER,
                  last_ip TEXT
                );
            """);

            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_name ON users(name);");

        } catch (SQLException e) {
            Bukkit.getLogger().severe("[NovaAuth] Failed to init SQLite: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<Optional<UserRecord>> findByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = DriverManager.getConnection(jdbcUrl());
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT uuid,name,password_hash,registered_at,last_login,last_ip FROM users WHERE uuid=?"
                 )) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(map(rs));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<UserRecord>> findByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = DriverManager.getConnection(jdbcUrl());
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT uuid,name,password_hash,registered_at,last_login,last_ip FROM users WHERE LOWER(name)=LOWER(?)"
                 )) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(map(rs));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> createUser(UserRecord record) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = DriverManager.getConnection(jdbcUrl());
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO users(uuid,name,password_hash,registered_at,last_login,last_ip) VALUES (?,?,?,?,?,?)"
                 )) {
                ps.setString(1, record.uuid().toString());
                ps.setString(2, record.name());
                ps.setString(3, record.passwordHash());
                ps.setLong(4, record.registeredAtEpochSeconds());
                if (record.lastLoginEpochSeconds() == null) ps.setNull(5, Types.INTEGER);
                else ps.setLong(5, record.lastLoginEpochSeconds());
                ps.setString(6, record.lastIp());
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> updatePassword(UUID uuid, String newHash) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = DriverManager.getConnection(jdbcUrl());
                 PreparedStatement ps = con.prepareStatement(
                         "UPDATE users SET password_hash=? WHERE uuid=?"
                 )) {
                ps.setString(1, newHash);
                ps.setString(2, uuid.toString());
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> updateLoginMeta(UUID uuid, String name, long lastLoginEpochSeconds, String ip) {
        return CompletableFuture.runAsync(() -> {
            try (Connection con = DriverManager.getConnection(jdbcUrl());
                 PreparedStatement ps = con.prepareStatement(
                         "UPDATE users SET name=?, last_login=?, last_ip=? WHERE uuid=?"
                 )) {
                ps.setString(1, name);
                ps.setLong(2, lastLoginEpochSeconds);
                ps.setString(3, ip);
                ps.setString(4, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> deleteByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = DriverManager.getConnection(jdbcUrl());
                 PreparedStatement ps = con.prepareStatement(
                         "DELETE FROM users WHERE uuid=?"
                 )) {
                ps.setString(1, uuid.toString());
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private static UserRecord map(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String name = rs.getString("name");
        String hash = rs.getString("password_hash");
        long reg = rs.getLong("registered_at");

        long lastLoginRaw = rs.getLong("last_login");
        Long lastLogin = rs.wasNull() ? null : lastLoginRaw;

        String ip = rs.getString("last_ip");
        return new UserRecord(uuid, name, hash, reg, lastLogin, ip);
    }
}