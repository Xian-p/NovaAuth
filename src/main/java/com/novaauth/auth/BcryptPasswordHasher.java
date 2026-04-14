package com.novaauth.auth;

import com.novaauth.lib.jbcrypt.BCrypt;

import java.util.Arrays;

public final class BcryptPasswordHasher implements PasswordHasher {

    private final int cost;

    public BcryptPasswordHasher(int cost) {
        this.cost = cost;
    }

    @Override
    public String hash(char[] password) {
        try {
            String pw = new String(password);
            return BCrypt.hashpw(pw, BCrypt.gensalt(cost));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Override
    public boolean verify(char[] password, String hash) {
        try {
            String pw = new String(password);
            return BCrypt.checkpw(pw, hash);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}