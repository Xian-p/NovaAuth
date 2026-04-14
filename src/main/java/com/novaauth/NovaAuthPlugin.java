package com.novaauth;

import com.novaauth.auth.AuthService;
import com.novaauth.auth.BcryptPasswordHasher;
import com.novaauth.commands.*;
import com.novaauth.config.Messages;
import com.novaauth.config.NovaAuthConfig;
import com.novaauth.listeners.AuthListener;
import com.novaauth.storage.SqliteUserRepository;
import com.novaauth.storage.UserRepository;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NovaAuthPlugin extends JavaPlugin {

    private ExecutorService dbExecutor;

    private NovaAuthConfig novaConfig;
    private Messages messages;

    private UserRepository userRepository;
    private AuthService authService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "NovaAuth-DB");
            t.setDaemon(true);
            return t;
        });

        reloadNovaAuth();

        // commands
        getCommand("register").setExecutor(new RegisterCommand(this, authService));
        getCommand("login").setExecutor(new LoginCommand(this, authService));
        getCommand("logout").setExecutor(new LogoutCommand(authService));
        getCommand("changepassword").setExecutor(new ChangePasswordCommand(this, authService));
        getCommand("unregister").setExecutor(new UnregisterCommand(this, authService));
        getCommand("novaauthreload").setExecutor(new ReloadCommand(this, this::reloadNovaAuth, authService));
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);

        if (authService != null) authService.shutdown();

        if (dbExecutor != null) {
            dbExecutor.shutdownNow();
            dbExecutor = null;
        }
    }

    public void reloadNovaAuth() {
        reloadConfig();

        this.novaConfig = new NovaAuthConfig(getConfig());
        this.messages = new Messages(novaConfig);

        if (this.userRepository == null) {
            this.userRepository = new SqliteUserRepository(this, novaConfig, dbExecutor);
        } else if (this.userRepository instanceof SqliteUserRepository sqlite) {
            sqlite.reload(novaConfig);
        }

        if (this.authService == null) {
            this.authService = new AuthService(
                    this,
                    novaConfig,
                    messages,
                    userRepository,
                    new BcryptPasswordHasher(novaConfig.passwordBcryptCost())
            );
        } else {
            this.authService.reload(novaConfig, messages);
        }

        // listeners (re-register to ensure config changes apply cleanly)
        HandlerList.unregisterAll(this);
        getServer().getPluginManager().registerEvents(new AuthListener(this, authService), this);
    }
}