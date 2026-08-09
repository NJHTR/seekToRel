package com.reportai.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {
    @Test
    void acceptsAComplexLongPassword() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("CorrectHorseBatteryStaple!2026"));
    }

    @Test
    void rejectsPasswordsWithoutAllRequiredCharacterClasses() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("alllowercase123!"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("NoSpecialCharacter123"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("Short1!"));
    }
}
