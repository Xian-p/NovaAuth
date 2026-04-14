package com.novaauth.storage;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface UserRepository {
    CompletableFuture<Optional<UserRecord>> findByUuid(UUID uuid);
    CompletableFuture<Optional<UserRecord>> findByName(String name);

    CompletableFuture<Boolean> createUser(UserRecord record);
    CompletableFuture<Boolean> updatePassword(UUID uuid, String newHash);
    CompletableFuture<Void> updateLoginMeta(UUID uuid, String name, long lastLoginEpochSeconds, String ip);
    CompletableFuture<Boolean> deleteByUuid(UUID uuid);
}