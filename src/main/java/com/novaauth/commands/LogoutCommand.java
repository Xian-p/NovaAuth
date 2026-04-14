package com.novaauth.commands;

import com.novaauth.auth.AuthService;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class LogoutCommand implements CommandExecutor {

    private final AuthService auth;

    public LogoutCommand(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!auth.isAuthenticated(p)) {
            p.sendMessage(auth.messages().msg("not_authenticated", "&cYou must login first."));
            return true;
        }

        auth.logout(p);
        p.sendMessage(auth.messages().msg("logout_success", "&aLogged out."));
        return true;
    }
}