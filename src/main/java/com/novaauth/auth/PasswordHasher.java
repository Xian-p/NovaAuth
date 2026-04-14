package com.novaauth.auth;

public interface PasswordHasher {
    String hash(char[] password);
    boolean verify(char[] password, String hash);
}