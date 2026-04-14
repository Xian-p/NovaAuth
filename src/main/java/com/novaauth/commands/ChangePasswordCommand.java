package com.novaauth.commands;

import com.novaauth.NovaAuthPlugin;
import com.novaauth.auth.AuthResult;
import com.novaauth.auth.AuthService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Map;

public final class ChangePasswordCommand implements CommandExecutor {

    private final NovaAuthPlugin plugin;
    private final AuthService auth;

    public ChangePasswordCommand(NovaAuthPlugin plugin, AuthService auth) {
        this.plugin = plugin;
        this.auth = auth;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length < 3) {
            p.sendMessage(auth.messages().msg("changepassword_usage", "&cUsage: /changepassword <old> <new> <newConfirm>"));
            return true;
        }

        char[] oldPw = args[0].toCharArray();
        char[] newPw = args[1].toCharArray();
        char[] confirm = args[2].toCharArray();

        auth.changePassword(p, oldPw, newPw, confirm).whenComplete((res, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (err != null) {
                p.sendMessage(auth.messages().msg("action_blocked", "&cAn error occurred."));
                return;
            }

            switch (res) {
                case NOT_AUTHENTICATED -> p.sendMessage(auth.messages().msg("not_authenticated", "&cYou must login first."));
                case PASSWORD_TOO_SHORT -> p.sendMessage(auth.messages().msg("password_too_short", "&cPassword too short (min: {min}).",
                        Map.of("min", String.valueOf(auth.config().passwordMinLength()))));
                case PASSWORD_MISMATCH -> p.sendMessage(auth.messages().msg("passwords_do_not_match", "&cPasswords do not match."));
                case INVALID_PASSWORD -> p.sendMessage(auth.messages().msg("old_password_incorrect", "&cOld password is incorrect."));
                case SUCCESS -> p.sendMessage(auth.messages().msg("changepassword_success", "&aPassword changed successfully."));
                default -> p.sendMessage(auth.messages().msg("action_blocked", "&cAn error occurred."));
            }
        }));

        return true;
    }
}
