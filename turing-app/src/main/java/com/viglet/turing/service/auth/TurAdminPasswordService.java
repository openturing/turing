package com.viglet.turing.service.auth;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.properties.TurConfigProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Single owner of the local {@code admin} password: the first-access setup
 * screen ({@code POST /api/setup}) and the headless startup bootstrap
 * ({@code TURING_ADMIN_PASSWORD}) both go through here so they share the same
 * validation, the same "only while unset" rule and the same encoder.
 *
 * <p>
 * The bootstrap never overwrites a password that is already configured — an
 * operator who set it through the UI keeps it, and a restart of a long-lived
 * container with the env var still present is a no-op.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurAdminPasswordService {

    public static final String ADMIN = "admin";
    public static final int PASSWORD_MINIMUM_SIZE = 6;

    private final TurUserRepository turUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final TurConfigProperties turConfigProperties;

    public TurAdminPasswordService(TurUserRepository turUserRepository, PasswordEncoder passwordEncoder,
            TurConfigProperties turConfigProperties) {
        this.turUserRepository = turUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.turConfigProperties = turConfigProperties;
    }

    /**
     * {@code true} while the {@code admin} account has no usable password, i.e.
     * the first-access setup is still pending.
     */
    public boolean isSetupRequired() {
        TurUser admin = turUserRepository.findByUsername(ADMIN);
        return admin == null || !StringUtils.hasText(admin.getPassword());
    }

    /**
     * Encodes and stores the {@code admin} password, creating the account when a
     * fresh install has not seeded it yet.
     *
     * @throws IllegalArgumentException when the password is blank or shorter than
     *                                  {@value #PASSWORD_MINIMUM_SIZE} characters
     *                                  (mapped to HTTP 400 by the global handler)
     */
    public void setPassword(String rawPassword) {
        String password = rawPassword == null ? null : rawPassword.trim();
        if (!StringUtils.hasText(password) || password.length() < PASSWORD_MINIMUM_SIZE) {
            throw new IllegalArgumentException(
                    "The admin password must be at least " + PASSWORD_MINIMUM_SIZE + " characters long.");
        }
        TurUser admin = turUserRepository.findByUsername(ADMIN);
        if (admin == null) {
            admin = TurUser.builder()
                    .username(ADMIN)
                    .firstName("Admin")
                    .lastName("Administrator")
                    .email("admin@localhost.local")
                    .realm("default")
                    .enabled(1)
                    .lastLogin(Instant.now())
                    .build();
        }
        admin.setPassword(passwordEncoder.encode(password));
        turUserRepository.save(admin);
    }

    /**
     * Applies {@code turing.admin.password} ({@code TURING_ADMIN_PASSWORD}) so a
     * container/CI deploy starts with a usable admin login and the setup screen is
     * skipped. Idempotent and boot-safe: a blank value, an already-configured
     * password or a too-short value only logs — never fails startup.
     */
    public void bootstrapFromConfiguration() {
        String configured = turConfigProperties.getAdmin().getPassword();
        if (!StringUtils.hasText(configured)) {
            return;
        }
        if (!isSetupRequired()) {
            log.info("TURING_ADMIN_PASSWORD ignored: the admin password is already configured. "
                    + "Change it in the console (or clear the stored password) instead.");
            return;
        }
        if (configured.trim().length() < PASSWORD_MINIMUM_SIZE) {
            log.warn("TURING_ADMIN_PASSWORD ignored: the admin password must be at least {} characters long. "
                    + "Falling back to the first-access setup screen.", PASSWORD_MINIMUM_SIZE);
            return;
        }
        setPassword(configured);
        log.info("Admin password set from TURING_ADMIN_PASSWORD — the first-access setup screen is skipped.");
    }
}
