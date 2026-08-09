package com.reportai.auth;

/** Baseline password policy for first-party accounts. */
public final class PasswordPolicy {
    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH
                || password.chars().anyMatch(Character::isWhitespace)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("password does not meet the security policy");
        }
    }
}
