package com.novaauth.commands;

import com.novaauth.NovaAuthPlugin;
import com.novaauth.auth.AuthResult;
import com.novaauth.auth.AuthService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Map;

public final class RegisterCommand implements CommandExecutor {

    private final NovaAuthPlugin plugin;
    private final AuthService auth;

    public RegisterCommand(NovaAuthPlugin plugin, AuthService auth) {
        this.plugin = plugin;
        this.auth = auth;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length < 2) {
            p.sendMessage(auth.messages().msg("register_usage", "&cUsage: /register <password> <passwordConfirm>"));
            return true;
        }

        char[] pw = args[0].toCharArray();
        char[] confirm = args[1].toCharArray();

        auth.register(p, pw, confirm).whenComplete((res, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;

            if (err != null) {
                p.sendMessage(auth.messages().msg("action_blocked", "&cAn error occurred."));
                return;
            }

            switch (res) {
                case ALREADY_AUTHENTICATED -> p.sendMessage(auth.messages().msg("already_authenticated", "&aYou are already authenticated."));
                case ALREADY_REGISTERED -> p.sendMessage(auth.messages().msg("already_registered", "&cYou are already registered. Use &e/login <password>&c."));
                case PASSWORD_TOO_SHORT -> p.sendMessage(auth.messages().msg("password_too_short", "&cPassword too short (min: {min}).",
                        Map.of("min", String.valueOf(auth.config().passwordMinLength()))));
                case PASSWORD_MISMATCH -> p.sendMessage(auth.messages().msg("passwords_do_not_match", "&cPasswords do not match."));
                case SUCCESS -> {
                    p.sendMessage(auth.messages().msg("registered_success", "&aRegistered successfully."));
                    auth.onLoginOrRegisterSuccess(p); // auto-login after register
                    p.sendMessage(auth.messages().msg("login_success", "&aLogged in successfully."));
                }
                default -> p.sendMessage(auth.messages().msg("action_blocked", "&cAn error occurred."));
            }
        }));

        return true;
    }
}