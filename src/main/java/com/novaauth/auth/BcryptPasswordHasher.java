package com.novaauth.auth;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Arrays;

/**
 * BCrypt password hasher.
 *
 * IMPORTANT:
 * - In Maven, relocation via maven-shade-plugin happens at PACKAGE time, not at COMPILE time.
 * - Therefore, source code must reference org.mindrot.jbcrypt.BCrypt.
 * - The shade plugin will relocate the bytecode reference into com.novaauth.lib.jbcrypt.BCrypt
 *   inside the shaded jar.
 */
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
