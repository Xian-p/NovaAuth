package com.novaauth.storage;

import java.util.UUID;

public record UserRecord(
        UUID uuid,
        String name,
        String passwordHash,
        long registeredAtEpochSeconds,
        Long lastLoginEpochSeconds,
        String lastIp
) { }