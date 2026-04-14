package com.novaauth.auth;

import com.novaauth.NovaAuthPlugin;
import com.novaauth.config.Messages;
import com.novaauth.config.NovaAuthConfig;
import com.novaauth.storage.UserRecord;
import com.novaauth.storage.UserRepository;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {

    private final NovaAuthPlugin plugin;
    private final UserRepository repo;
    private final PasswordHasher hasher;

    private volatile NovaAuthConfig config;
    private volatile Messages messages;

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AuthStatus> status = new ConcurrentHashMap<>();

    private final Map<UUID, Location> preAuthLocation = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockedUntilEpochSeconds = new ConcurrentHashMap<>();

    private BukkitTask reminderTask;
    private BukkitTask effectsTask;

    public AuthService(NovaAuthPlugin plugin,
                       NovaAuthConfig config,
                       Messages messages,
                       UserRepository repo,
                       PasswordHasher hasher) {
        this.plugin = plugin;
        this.repo = repo;
        this.config = config;
        this.messages = messages;
        this.hasher = hasher;

        restartBackgroundTasks();
    }

    public NovaAuthConfig config() { return config; }
    public Messages messages() { return messages; }

    public void reload(NovaAuthConfig config, Messages messages) {
        this.config = config;
        this.messages = messages;
        restartBackgroundTasks();
    }

    public void shutdown() {
        stopBackgroundTasks();
        for (BukkitTask task : timeoutTasks.values()) {
            if (task != null) task.cancel();
        }
        timeoutTasks.clear();
    }

    public boolean isAuthenticated(Player player) {
        return authenticated.contains(player.getUniqueId());
    }

    public AuthStatus authStatus(Player player) {
        return status.getOrDefault(player.getUniqueId(), AuthStatus.UNKNOWN);
    }

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();

        authenticated.remove(uuid);
        attempts.remove(uuid);

        status.put(uuid, AuthStatus.UNKNOWN);
        preAuthLocation.put(uuid, player.getLocation().clone());

        // Teleport to limbo/spawn immediately
        teleportUnauthenticated(player);

        // DB lookup async; then decide register/login prompt or session auto-login
        repo.findByUuid(uuid).whenComplete((opt, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            if (err != null) {
                status.put(uuid, AuthStatus.UNKNOWN);
                player.sendMessage(messages.msg("must_login", "&cPlease login with &e/login <password>&c."));
                scheduleTimeoutKick(player);
                return;
            }

            if (opt.isEmpty()) {
                status.put(uuid, AuthStatus.NOT_REGISTERED);
                player.sendMessage(messages.msg("must_register", "&cYou are not registered. Use &e/register <password> <password>&c."));
                scheduleTimeoutKick(player);
                return;
            }

            status.put(uuid, AuthStatus.REGISTERED);

            UserRecord rec = opt.get();
            if (isSessionValid(player, rec)) {
                setAuthenticated(player);
                player.sendMessage(messages.msg("session_auto_login", "&aSession restored. You have been logged in."));
                touchLoginMeta(player);
            } else {
                player.sendMessage(messages.msg("must_login", "&cPlease login with &e/login <password>&c."));
                scheduleTimeoutKick(player);
            }
        }));
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        authenticated.remove(uuid);
        preAuthLocation.remove(uuid);
        status.remove(uuid);

        BukkitTask task = timeoutTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    public CompletableFuture<AuthResult> register(Player player, char[] password, char[] confirm) {
        UUID uuid = player.getUniqueId();

        if (isAuthenticated(player)) return CompletableFuture.completedFuture(AuthResult.ALREADY_AUTHENTICATED);

        int min = config.passwordMinLength();
        if (password.length < min) return CompletableFuture.completedFuture(AuthResult.PASSWORD_TOO_SHORT);
        if (!Arrays.equals(password, confirm)) return CompletableFuture.completedFuture(AuthResult.PASSWORD_MISMATCH);

        return repo.findByUuid(uuid).thenCompose(opt -> {
            if (opt.isPresent()) return CompletableFuture.completedFuture(AuthResult.ALREADY_REGISTERED);

            String hash = hasher.hash(password);
            long now = Instant.now().getEpochSecond();
            String ip = ip(player);

            UserRecord rec = new UserRecord(uuid, player.getName(), hash, now, now, ip);
            return repo.createUser(rec).thenApply(ok -> ok ? AuthResult.SUCCESS : AuthResult.ERROR);
        });
    }

    public CompletableFuture<AuthResult> login(Player player, char[] password) {
        UUID uuid = player.getUniqueId();

        if (isAuthenticated(player)) return CompletableFuture.completedFuture(AuthResult.ALREADY_AUTHENTICATED);

        long now = Instant.now().getEpochSecond();
        Long lockedUntil = lockedUntilEpochSeconds.get(uuid);
        if (lockedUntil != null && lockedUntil > now) {
            return CompletableFuture.completedFuture(AuthResult.LOCKED_OUT);
        }

        return repo.findByUuid(uuid).thenApply(opt -> {
            if (opt.isEmpty()) return AuthResult.NOT_REGISTERED;

            UserRecord rec = opt.get();
            boolean ok = hasher.verify(password, rec.passwordHash());
            if (!ok) {
                int a = attempts.merge(uuid, 1, Integer::sum);
                if (a >= config.maxLoginAttempts()) {
                    lockedUntilEpochSeconds.put(uuid, now + config.lockoutSeconds());
                    attempts.remove(uuid);
                }
                return AuthResult.INVALID_PASSWORD;
            }

            attempts.remove(uuid);
            lockedUntilEpochSeconds.remove(uuid);
            return AuthResult.SUCCESS;
        });
    }

    public CompletableFuture<AuthResult> changePassword(Player player, char[] oldPw, char[] newPw, char[] confirm) {
        UUID uuid = player.getUniqueId();

        if (!isAuthenticated(player)) return CompletableFuture.completedFuture(AuthResult.NOT_AUTHENTICATED);

        int min = config.passwordMinLength();
        if (newPw.length < min) return CompletableFuture.completedFuture(AuthResult.PASSWORD_TOO_SHORT);
        if (!Arrays.equals(newPw, confirm)) return CompletableFuture.completedFuture(AuthResult.PASSWORD_MISMATCH);

        return repo.findByUuid(uuid).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(AuthResult.NOT_REGISTERED);

            UserRecord rec = opt.get();
            if (!hasher.verify(oldPw, rec.passwordHash())) return CompletableFuture.completedFuture(AuthResult.INVALID_PASSWORD);

            String newHash = hasher.hash(newPw);
            return repo.updatePassword(uuid, newHash).thenApply(ok -> ok ? AuthResult.SUCCESS : AuthResult.ERROR);
        });
    }

    public void logout(Player player) {
        UUID uuid = player.getUniqueId();
        authenticated.remove(uuid);

        if (status.getOrDefault(uuid, AuthStatus.UNKNOWN) == AuthStatus.UNKNOWN) {
            status.put(uuid, AuthStatus.REGISTERED);
        }

        teleportUnauthenticated(player);
        scheduleTimeoutKick(player);
    }

    public CompletableFuture<Boolean> unregisterByName(String name) {
        return repo.findByName(name).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(false);
            UUID uuid = opt.get().uuid();
            authenticated.remove(uuid);
            status.remove(uuid);
            preAuthLocation.remove(uuid);
            attempts.remove(uuid);
            lockedUntilEpochSeconds.remove(uuid);
            return repo.deleteByUuid(uuid);
        });
    }

    public void onLoginOrRegisterSuccess(Player player) {
        setAuthenticated(player);
        touchLoginMeta(player);
    }

    private void touchLoginMeta(Player player) {
        long now = Instant.now().getEpochSecond();
        repo.updateLoginMeta(player.getUniqueId(), player.getName(), now, ip(player));
    }

    private void setAuthenticated(Player player) {
        UUID uuid = player.getUniqueId();

        authenticated.add(uuid);
        status.put(uuid, AuthStatus.REGISTERED);

        BukkitTask task = timeoutTasks.remove(uuid);
        if (task != null) task.cancel();

        clearUnauthEffects(player);

        if (config.returnToLocationAfterLogin()) {
            Location loc = preAuthLocation.remove(uuid);
            if (loc != null && loc.getWorld() != null) {
                player.teleport(loc);
            }
        }
    }

    private void scheduleTimeoutKick(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask existing = timeoutTasks.remove(uuid);
        if (existing != null) existing.cancel();

        long seconds = config.kickTimeoutSeconds();
        if (seconds <= 0) return;

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (isAuthenticated(player)) return;
            player.kickPlayer(messages.msg("kicked_timeout", "&cYou took too long to login."));
        }, seconds * 20L);

        timeoutTasks.put(uuid, task);
    }

    private boolean isSessionValid(Player player, UserRecord rec) {
        if (!config.sessionEnabled()) return false;
        if (rec.lastLoginEpochSeconds() == null) return false;

        long now = Instant.now().getEpochSecond();
        long age = now - rec.lastLoginEpochSeconds();
        if (age < 0 || age > config.sessionDurationSeconds()) return false;

        if (config.sessionIpCheck()) {
            String ip = ip(player);
            if (ip == null || rec.lastIp() == null) return false;
            return ip.equals(rec.lastIp());
        }
        return true;
    }

    private void teleportUnauthenticated(Player player) {
        preAuthLocation.putIfAbsent(player.getUniqueId(), player.getLocation().clone());

        Location target = null;

        if (config.limboEnabled()) {
            World w = Bukkit.getWorld(config.limboWorld());
            if (w != null) {
                if (config.limboUseWorldSpawn()) {
                    target = w.getSpawnLocation();
                } else {
                    target = new Location(
                            w,
                            config.limboX(), config.limboY(), config.limboZ(),
                            config.limboYaw(), config.limboPitch()
                    );
                }
            } else {
                Bukkit.getLogger().warning("[NovaAuth] Limbo enabled but world not found: " + config.limboWorld());
            }
        }

        if (target == null && config.teleportToSpawnOnJoin()) {
            target = player.getWorld().getSpawnLocation();
        }

        if (target != null) {
            player.teleport(target);
        }
    }

    public long lockoutRemainingSeconds(Player player) {
        Long until = lockedUntilEpochSeconds.get(player.getUniqueId());
        if (until == null) return 0;
        long now = Instant.now().getEpochSecond();
        return Math.max(0, until - now);
    }

    private static String ip(Player player) {
        if (player.getAddress() == null || player.getAddress().getAddress() == null) return null;
        return player.getAddress().getAddress().getHostAddress();
    }

    /* ---------------------------
       Effects + Reminders
       --------------------------- */

    private void restartBackgroundTasks() {
        stopBackgroundTasks();
        startBackgroundTasks();
    }

    private void startBackgroundTasks() {
        if (config.effectsEnabled()) {
            long interval = config.effectsReapplyIntervalTicks();
            effectsTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickEffects, 1L, interval);
        }

        if (config.reminderEnabled()) {
            long intervalTicks = config.reminderIntervalSeconds() * 20L;
            reminderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickReminders, intervalTicks, intervalTicks);
        }
    }

    private void stopBackgroundTasks() {
        if (effectsTask != null) {
            effectsTask.cancel();
            effectsTask = null;
        }
        if (reminderTask != null) {
            reminderTask.cancel();
            reminderTask = null;
        }
    }

    private void tickEffects() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isAuthenticated(p)) continue;
            applyUnauthEffects(p);
        }
    }

    private void tickReminders() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isAuthenticated(p)) continue;

            String text;
            AuthStatus st = authStatus(p);
            if (st == AuthStatus.NOT_REGISTERED) {
                text = messages.msg("must_register", "&cYou are not registered. Use &e/register <password> <password>&c.");
            } else {
                text = messages.msg("must_login", "&cPlease login with &e/login <password>&c.");
            }

            if (config.reminderActionbar()) {
                // text already contains legacy section color codes after ChatColor translation
                p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
            } else {
                p.sendMessage(text);
            }
        }
    }

    private void applyUnauthEffects(Player p) {
        if (!config.effectsEnabled()) return;

        int freeze = config.effectsFreezeTicks();
        if (freeze > 0) {
            p.setFreezeTicks(freeze);
        }

        int dur = (int) Math.max(40, config.effectsReapplyIntervalTicks() + 40);

        if (config.effectsBlindness()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, dur, 0, true, false, false));
        }
        if (config.effectsSlowness()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 10, true, false, false));
        }
        if (config.effectsJumpDisable()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur, 250, true, false, false));
        }
    }

    private void clearUnauthEffects(Player p) {
        p.setFreezeTicks(0);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }
}
