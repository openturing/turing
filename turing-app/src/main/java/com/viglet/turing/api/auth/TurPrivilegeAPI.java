package com.viglet.turing.api.auth;

import com.viglet.turing.persistence.model.auth.TurPrivilege;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for listing available privileges.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
@Secured("ROLE_ADMIN")
@RestController
@RequestMapping("/api/v2/privilege")
@Tag(name = "Privilege", description = "Privilege API")
public class TurPrivilegeAPI {

    private final TurPrivilegeRepository turPrivilegeRepository;

    public TurPrivilegeAPI(TurPrivilegeRepository turPrivilegeRepository) {
        this.turPrivilegeRepository = turPrivilegeRepository;
    }

    @GetMapping
    public List<TurPrivilege> list() {
        return turPrivilegeRepository.findAll();
    }
}
