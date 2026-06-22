package com.laker.admin.infrastructure.security.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EasyPasswordHasherTest {

    private final EasyPasswordHasher passwordHasher = new EasyPasswordHasher();

    @Test
    void hashShouldUseBCryptAndVerifyPassword() {
        String encoded = passwordHasher.hash("easynext");

        assertThat(encoded).startsWith("$2");
        assertThat(passwordHasher.matches("easynext", encoded)).isTrue();
        assertThat(passwordHasher.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void hashShouldUseCost12() {
        String encoded = passwordHasher.hash("easynext");

        assertThat(encoded).startsWith("$2a$12$");
    }

    @Test
    void matchesShouldReturnFalseForInvalidHash() {
        assertThat(passwordHasher.matches("easynext", "not-a-bcrypt-hash")).isFalse();
    }

    @Test
    void hashShouldRejectPasswordLongerThanBCryptLimit() {
        String longPassword = "a".repeat(73);

        assertThatThrownBy(() -> passwordHasher.hash(longPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72");
        assertThat(passwordHasher.matches(longPassword, passwordHasher.hash("easynext"))).isFalse();
    }
}
