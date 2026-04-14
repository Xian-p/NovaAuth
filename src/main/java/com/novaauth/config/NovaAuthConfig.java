package com.novaauth.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class NovaAuthConfig {

    private final FileConfiguration cfg;

    public NovaAuthConfig(FileConfiguration cfg) {
        this.cfg = cfg;
    }

    public String prefix() {
        return cfg.getString("prefix", "&7[&bNovaAuth&7]&r ");
    }

    public String databaseFile() {
        return cfg.getString("database.file", "data.db");
    }

    public int passwordMinLength() {
        return Math.max(1, cfg.getInt("password.min-length", 6));
    }

    public int passwordBcryptCost() {
        int cost = cfg.getInt("password.bcrypt-cost", 12);
        return Math.min(15, Math.max(8, cost));
    }

    public boolean sessionEnabled() { return cfg.getBoolean("session.enabled", true); }
    public long sessionDurationSeconds() { return Math.max(0, cfg.getLong("session.duration-seconds", 1800)); }
    public boolean sessionIpCheck() { return cfg.getBoolean("session.ip-check", true); }

    public int maxLoginAttempts() { return Math.max(1, cfg.getInt("security.max-login-attempts", 5)); }
    public long lockoutSeconds() { return Math.max(0, cfg.getLong("security.lockout-seconds", 300)); }
    public boolean kickOnMaxAttempts() { return cfg.getBoolean("security.kick-on-max-attempts", true); }

    public long kickTimeoutSeconds() {
        return Math.max(0, cfg.getLong("authentication.kick-timeout-seconds", 60));
    }

    // Limbo
    public boolean limboEnabled() { return cfg.getBoolean("limbo.enabled", true); }
    public String limboWorld() { return cfg.getString("limbo.world", "auth"); }

    public boolean limboUseWorldSpawn() { return cfg.getBoolean("limbo.spawn.use-world-spawn", true); }
    public double limboX() { return cfg.getDouble("limbo.spawn.x", 0.5); }
    public double limboY() { return cfg.getDouble("limbo.spawn.y", 100.0); }
    public double limboZ() { return cfg.getDouble("limbo.spawn.z", 0.5); }
    public float limboYaw() { return (float) cfg.getDouble("limbo.spawn.yaw", 0.0); }
    public float limboPitch() { return (float) cfg.getDouble("limbo.spawn.pitch", 0.0); }

    // Restrictions
    public boolean blockMovement() { return cfg.getBoolean("restrictions.block-movement", true); }
    public boolean teleportToSpawnOnJoin() { return cfg.getBoolean("restrictions.teleport-to-spawn-on-join", true); }
    public boolean returnToLocationAfterLogin() { return cfg.getBoolean("restrictions.return-to-location-after-login", true); }
    public boolean hideUnauthenticated() { return cfg.getBoolean("restrictions.hide-unauthenticated", false); }

    public Set<String> allowedCommandsLowercase() {
        List<String> list = cfg.getStringList("restrictions.allow-commands");
        Set<String> out = new HashSet<>();
        for (String s : list) {
            if (s == null) continue;
            String v = s.trim().toLowerCase(Locale.ROOT);
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    // Effects
    public boolean effectsEnabled() { return cfg.getBoolean("effects.enabled", true); }
    public int effectsFreezeTicks() { return Math.max(0, cfg.getInt("effects.freeze-ticks", 140)); }
    public boolean effectsSlowness() { return cfg.getBoolean("effects.slowness", true); }
    public boolean effectsJumpDisable() { return cfg.getBoolean("effects.jump-disable", true); }
    public boolean effectsBlindness() { return cfg.getBoolean("effects.blindness", true); }
    public long effectsReapplyIntervalTicks() { return Math.max(1, cfg.getLong("effects.reapply-interval-ticks", 20)); }

    // Reminders
    public boolean reminderEnabled() { return cfg.getBoolean("reminder.enabled", true); }
    public long reminderIntervalSeconds() { return Math.max(1, cfg.getLong("reminder.interval-seconds", 5)); }
    public boolean reminderActionbar() { return cfg.getBoolean("reminder.actionbar", false); }

    public String message(String key, String def) {
        return cfg.getString("messages." + key, def);
    }
}