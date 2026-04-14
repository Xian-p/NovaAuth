package com.novaauth.commands;

import com.novaauth.NovaAuthPlugin;
import com.novaauth.auth.AuthService;
import org.bukkit.command.*;

public final class ReloadCommand implements CommandExecutor {

    private final Object lock = new Object();
    private final Runnable reloadAction;
    private final AuthService auth;

    public ReloadCommand(NovaAuthPlugin plugin, Runnable reloadAction, AuthService auth) {
        this.reloadAction = reloadAction;
        this.auth = auth;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        synchronized (lock) {
            reloadAction.run();
        }
        sender.sendMessage(auth.messages().msg("reload_success", "&aNovaAuth reloaded."));
        return true;
    }
}