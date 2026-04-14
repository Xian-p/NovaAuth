package com.novaauth.commands;

import com.novaauth.NovaAuthPlugin;
import com.novaauth.auth.AuthResult;
import com.novaauth.auth.AuthService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Map;

public final class LoginCommand implements CommandExecutor {

    private final NovaAuthPlugin plugin;
    private final AuthService auth;

    public LoginCommand(NovaAuthPlugin plugin, AuthService auth) {
        this.plugin = plugin;
        this.auth = auth;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length < 1) {
            p.sendMessage(auth.messages().msg("login_usage", "&cUsage: /login <password>"));
            return true;
        }

        char[] pw = args[0].toCharArray();

        auth.login(p, pw).whenComplete((res, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;

            if (err != null) {
                p.sendMessage(auth.messages().msg("action_blocked", "&cAn error occurred."));
                return;
            }

            switch (res) {
                case ALREADY_AUTHENTICATED -> p.sendMessage(auth.messages().msg("already_authenticated", "&aYou are already authenticated."));
                case NOT_REGISTERED -> p.sendMessage(auth.messages().msg("not_registered", "&cYou are not registered."));
                case LOCKED_OUT -> {
                    long remaining = auth.lockoutRemainingSeconds(p);
                    p.sendMessage(auth.messages().msg("locked_out", "&cToo many attempts. Try again in {seconds}s.",
                            Map.of("seconds", String.valueOf(remaining))));
                }
                case INVALID_PASSWORD -> {
                    p.sendMessage(auth.messages().msg("login_failed", "&cInvalid password."));
                    // optional kick if lockout reached
                    if (auth.config().kickOnMaxAttempts() && auth.lockoutRemainingSeconds(p) > 0) {
                        p.kickPlayer(auth.messages().msg("locked_out", "&cToo many attempts. Try again in {seconds}s.",
                                Map.of("seconds", String.valueOf(auth.lockoutRemainingSeconds(p)))));
                    }
                }
                case SUCCESS -> {
                    auth.onLoginOrRegisterSuccess(p);
                    p.sendMessage(auth.messages().msg("login_success", "&aLogged in successfully."));
                }
                default -> p.sendMessage(auth.messages().msg("action_blocked", "&cAn error occurred."));
            }
        }));

        return true;
    }
}