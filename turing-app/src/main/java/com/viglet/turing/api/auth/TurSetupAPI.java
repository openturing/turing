package com.viglet.turing.api.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.service.auth.TurAdminPasswordService;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * First-access setup endpoint. Allows setting the admin password when it has
 * not been configured yet. Once the admin password is set, all endpoints in
 * this controller return 403 Forbidden — including when it was set headlessly
 * from {@code TURING_ADMIN_PASSWORD} on startup.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.1
 */
@RestController
@RequestMapping("/api/setup")
@Tag(name = "Setup", description = "First-access setup")
public class TurSetupAPI {

    private final TurAdminPasswordService turAdminPasswordService;

    public TurSetupAPI(TurAdminPasswordService turAdminPasswordService) {
        this.turAdminPasswordService = turAdminPasswordService;
    }

    public record SetupStatus(boolean required) {}

    public record SetupRequest(String password) {}

    @GetMapping
    public ResponseEntity<SetupStatus> status() {
        return ResponseEntity.ok(new SetupStatus(turAdminPasswordService.isSetupRequired()));
    }

    @PostMapping
    public ResponseEntity<Void> setAdminPassword(@RequestBody SetupRequest request) {
        if (!turAdminPasswordService.isSetupRequired()) {
            return ResponseEntity.status(403).build();
        }
        turAdminPasswordService.setPassword(request.password());
        return ResponseEntity.ok().build();
    }
}
