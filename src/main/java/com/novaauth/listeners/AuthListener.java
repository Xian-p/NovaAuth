package com.novaauth.listeners;

import com.novaauth.NovaAuthPlugin;
import com.novaauth.auth.AuthService;
import com.novaauth.config.NovaAuthConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

import java.util.Locale;
import java.util.Set;

public final class AuthListener implements Listener {

    private final NovaAuthPlugin plugin;
    private final AuthService auth;

    public AuthListener(NovaAuthPlugin plugin, AuthService auth) {
        this.plugin = plugin;
        this.auth = auth;
    }

    private NovaAuthConfig cfg() {
        return auth.config();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        auth.handleJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        auth.handleQuit(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!cfg().blockMovement()) return;

        Player p = e.getPlayer();
        if (auth.isAuthenticated(p)) return;

        if (e.getTo() == null) return;

        // Block actual movement; allow head rotation.
        if (e.getFrom().getX() != e.getTo().getX()
                || e.getFrom().getY() != e.getTo().getY()
                || e.getFrom().getZ() != e.getTo().getZ()) {
            e.setTo(e.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (auth.isAuthenticated(e.getPlayer())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(auth.messages().msg("action_blocked", "&cYou must login/register to do that."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (auth.isAuthenticated(p)) return;

        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) return;

        String label = msg.startsWith("/") ? msg.substring(1) : msg;
        label = label.split(" ")[0].trim();
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        label = label.toLowerCase(Locale.ROOT);

        Set<String> allow = cfg().allowedCommandsLowercase();
        if (allow.contains(label)) return;

        e.setCancelled(true);
        p.sendMessage(auth.messages().msg("command_blocked", "&cYou must login/register to use commands."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (auth.isAuthenticated(e.getPlayer())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(auth.messages().msg("action_blocked", "&cYou must login/register to do that."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractAtEntityEvent e) {
        if (auth.isAuthenticated(e.getPlayer())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(auth.messages().msg("action_blocked", "&cYou must login/register to do that."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (auth.isAuthenticated(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (auth.isAuthenticated(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (auth.isAuthenticated(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (auth.isAuthenticated(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInv(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (auth.isAuthenticated(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (auth.isAuthenticated(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (auth.isAuthenticated(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        // Allow plugin/server teleports that are part of NovaAuth flow (handled directly).
        // Block other causes to reduce bypasses.
        if (auth.isAuthenticated(e.getPlayer())) return;

        switch (e.getCause()) {
            case PLUGIN, COMMAND, UNKNOWN -> { /* allow */ }
            default -> e.setCancelled(true);
        }
    }
}