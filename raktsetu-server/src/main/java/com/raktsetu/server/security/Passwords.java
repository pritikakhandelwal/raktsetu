package com.raktsetu.server.security;

import java.security.SecureRandom;

/** Password rules, and one-time passwords for new or reset accounts. */
public final class Passwords {

    public static final int MIN_LENGTH = 10;
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    /**
     * Checks a chosen password: at least 10 characters, with at least one letter and one digit, and not the username.
     *
     * @throws IllegalArgumentException with a message people can act on
     */
    public static void check(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Choose a password of at least " + MIN_LENGTH + " characters");
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Use at least one letter and one number in your password");
        }
        if (password.equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("Your password cannot be your username");
        }
    }

    /** A random 12-character one-time password without easily confused characters. */
    public static String oneTime() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
