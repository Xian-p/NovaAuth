package com.novaauth.commands;

import com.novaauth.NovaAuthPlugin;
import com.novaauth.auth.AuthService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;

import java.util.Map;

public final class UnregisterCommand implements CommandExecutor {

    private final NovaAuthPlugin plugin;
    private final AuthService auth;

    public UnregisterCommand(NovaAuthPlugin plugin, AuthService auth) {
        this.plugin = plugin;
        this.auth = auth;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(auth.messages().msg("unregister_usage", "&cUsage: /unregister <player>"));
            return true;
        }

        String target = args[0];

        auth.unregisterByName(target).whenComplete((ok, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                sender.sendMessage(auth.messages().msg("action_blocked", "&cAn error occurred."));
                return;
            }
            if (Boolean.TRUE.equals(ok)) {
                sender.sendMessage(auth.messages().msg("unregister_success", "&aUnregistered {player}.",
                        Map.of("player", target)));
            } else {
                sender.sendMessage(auth.messages().msg("unregister_not_found", "&cPlayer not found/registered: {player}",
                        Map.of("player", target)));
            }
        }));

        return true;
    }
}