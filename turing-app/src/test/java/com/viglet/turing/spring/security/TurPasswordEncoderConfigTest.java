package com.viglet.turing.spring.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class TurPasswordEncoderConfigTest {

    private final TurPasswordEncoderConfig config = new TurPasswordEncoderConfig();

    @Test
    void shouldReturnBCryptPasswordEncoderInstance() {
        PasswordEncoder encoder = config.passwordEncoder();

        assertNotNull(encoder);
        assertTrue(encoder instanceof BCryptPasswordEncoder);
    }

    @Test
    void shouldEncodeAndMatchPassword() {
        PasswordEncoder encoder = config.passwordEncoder();

        String rawPassword = "my-secret-password";
        String encoded = encoder.encode(rawPassword);

        assertNotNull(encoded);
        assertNotEquals(rawPassword, encoded);
        assertTrue(encoder.matches(rawPassword, encoded));
    }
}
