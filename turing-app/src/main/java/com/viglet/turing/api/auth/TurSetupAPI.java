package com.viglet.turing.api.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * First-access setup endpoint. Allows setting the admin password when it has
 * not been configured yet. Once the admin password is set, all endpoints in
 * this controller return 403 Forbidden.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.1
 */
@RestController
@RequestMapping("/api/setup")
@Tag(name = "Setup", description = "First-access setup")
public class TurSetupAPI {

    private static final String ADMIN = "admin";
    private static final int PASSWORD_MINIMUM_SIZE = 6;

    private final TurUserRepository turUserRepository;
    private final PasswordEncoder passwordEncoder;

    public TurSetupAPI(TurUserRepository turUserRepository, PasswordEncoder passwordEncoder) {
        this.turUserRepository = turUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record SetupStatus(boolean required) {}

    public record SetupRequest(String password) {}

    @GetMapping
    public ResponseEntity<SetupStatus> status() {
        return ResponseEntity.ok(new SetupStatus(isSetupRequired()));
    }

    @PostMapping
    public ResponseEntity<Void> setAdminPassword(@RequestBody SetupRequest request) {
        if (!isSetupRequired()) {
            return ResponseEntity.status(403).build();
        }
        if (!StringUtils.hasText(request.password())
                || request.password().trim().length() < PASSWORD_MINIMUM_SIZE) {
            return ResponseEntity.badRequest().build();
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
                    .lastLogin(java.time.Instant.now())
                    .build();
        }
        admin.setPassword(passwordEncoder.encode(request.password().trim()));
        turUserRepository.save(admin);
        return ResponseEntity.ok().build();
    }

    private boolean isSetupRequired() {
        TurUser admin = turUserRepository.findByUsername(ADMIN);
        return admin == null || !StringUtils.hasText(admin.getPassword());
    }
}
