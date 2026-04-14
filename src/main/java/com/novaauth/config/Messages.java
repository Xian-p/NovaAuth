package com.novaauth.config;

import org.bukkit.ChatColor;

import java.util.Map;

public final class Messages {

    private final NovaAuthConfig config;

    public Messages(NovaAuthConfig config) {
        this.config = config;
    }

    public String raw(String key, String def) {
        return config.message(key, def);
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public String msg(String key, String def) {
        return color(config.prefix() + raw(key, def));
    }

    public String msg(String key, String def, Map<String, String> placeholders) {
        String text = config.prefix() + raw(key, def);
        if (placeholders != null) {
            for (var e : placeholders.entrySet()) {
                text = text.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return color(text);
    }
}