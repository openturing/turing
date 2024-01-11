package com.viglet.turing.onstartup.auth;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.auth.TurPrivilege;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
public class TurRoleOnStartup {
    private final TurPrivilegeRepository turPrivilegeRepository;

    private final TurRoleRepository turRoleRepository;

    @Inject
    public TurRoleOnStartup(TurPrivilegeRepository turPrivilegeRepository, TurRoleRepository turRoleRepository) {
        this.turPrivilegeRepository = turPrivilegeRepository;
        this.turRoleRepository = turRoleRepository;
    }

    @Transactional
    public void createDefaultRows() {
        TurPrivilege readPrivilege
                = createPrivilegeIfNotFound("READ_PRIVILEGE");
        TurPrivilege writePrivilege
                = createPrivilegeIfNotFound("WRITE_PRIVILEGE");

        List<TurPrivilege> adminPrivileges = Arrays.asList(
                readPrivilege, writePrivilege);
        createRoleIfNotFound("ROLE_ADMIN", adminPrivileges);
        createRoleIfNotFound("ROLE_USER", Collections.singletonList(readPrivilege));

    }
    @Transactional
    public TurPrivilege createPrivilegeIfNotFound(String name) {

        TurPrivilege privilege = turPrivilegeRepository.findByName(name);
        if (privilege == null) {
            privilege = new TurPrivilege(name);
            turPrivilegeRepository.save(privilege);
        }
        return privilege;
    }

    @Transactional
    public void createRoleIfNotFound(
            String name, Collection<TurPrivilege> privileges) {

        TurRole role = turRoleRepository.findByName(name);
        if (role == null) {
            role = new TurRole(name);
            role.setTurPrivileges(privileges);
            turRoleRepository.save(role);



        }
    }
}
