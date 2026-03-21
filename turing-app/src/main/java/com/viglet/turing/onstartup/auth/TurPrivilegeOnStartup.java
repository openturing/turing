package com.viglet.turing.onstartup.auth;

import com.viglet.turing.persistence.model.auth.TurPrivilege;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Ensures all privileges exist on every startup (not just first-time).
 * Also ensures ROLE_ADMIN has all privileges assigned.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
@Slf4j
@Component
@Transactional
public class TurPrivilegeOnStartup implements ApplicationRunner {

    private static final String[][] PRIVILEGE_DEFINITIONS = {
        {"READ_PRIVILEGE", "Legacy read privilege", "SYSTEM"},
        {"WRITE_PRIVILEGE", "Legacy write privilege", "SYSTEM"},

        {"LLM_VIEW", "View Language Model instances", "LLM"},
        {"LLM_CREATE", "Create Language Model instances", "LLM"},
        {"LLM_EDIT", "Edit Language Model instances", "LLM"},
        {"LLM_DELETE", "Delete Language Model instances", "LLM"},

        {"EMBEDDING_VIEW", "View Embedding Model instances", "EMBEDDING"},
        {"EMBEDDING_CREATE", "Create Embedding Model instances", "EMBEDDING"},
        {"EMBEDDING_EDIT", "Edit Embedding Model instances", "EMBEDDING"},
        {"EMBEDDING_DELETE", "Delete Embedding Model instances", "EMBEDDING"},

        {"STORE_VIEW", "View Embedding Store instances", "STORE"},
        {"STORE_CREATE", "Create Embedding Store instances", "STORE"},
        {"STORE_EDIT", "Edit Embedding Store instances", "STORE"},
        {"STORE_DELETE", "Delete Embedding Store instances", "STORE"},

        {"SE_VIEW", "View Search Engine instances", "SE"},
        {"SE_CREATE", "Create Search Engine instances", "SE"},
        {"SE_EDIT", "Edit Search Engine instances", "SE"},
        {"SE_DELETE", "Delete Search Engine instances", "SE"},

        {"SN_VIEW", "View Semantic Navigation sites", "SN"},
        {"SN_CREATE", "Create Semantic Navigation sites", "SN"},
        {"SN_EDIT", "Edit Semantic Navigation sites", "SN"},
        {"SN_DELETE", "Delete Semantic Navigation sites", "SN"},

        {"AI_AGENT_VIEW", "View AI Agent instances", "AI_AGENT"},
        {"AI_AGENT_CREATE", "Create AI Agent instances", "AI_AGENT"},
        {"AI_AGENT_EDIT", "Edit AI Agent instances", "AI_AGENT"},
        {"AI_AGENT_DELETE", "Delete AI Agent instances", "AI_AGENT"},

        {"INTENT_VIEW", "View Intent categories", "INTENT"},
        {"INTENT_CREATE", "Create Intent categories", "INTENT"},
        {"INTENT_EDIT", "Edit Intent categories", "INTENT"},
        {"INTENT_DELETE", "Delete Intent categories", "INTENT"},
    };

    private final TurPrivilegeRepository turPrivilegeRepository;
    private final TurRoleRepository turRoleRepository;

    public TurPrivilegeOnStartup(TurPrivilegeRepository turPrivilegeRepository,
                                 TurRoleRepository turRoleRepository) {
        this.turPrivilegeRepository = turPrivilegeRepository;
        this.turRoleRepository = turRoleRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<TurPrivilege> allPrivileges = new ArrayList<>();
        int created = 0;
        for (String[] def : PRIVILEGE_DEFINITIONS) {
            TurPrivilege privilege = turPrivilegeRepository.findByName(def[0]);
            if (privilege == null) {
                privilege = new TurPrivilege(def[0]);
                privilege.setDescription(def[1]);
                privilege.setCategory(def[2]);
                turPrivilegeRepository.save(privilege);
                created++;
            } else if (privilege.getDescription() == null || privilege.getCategory() == null) {
                privilege.setDescription(def[1]);
                privilege.setCategory(def[2]);
                turPrivilegeRepository.save(privilege);
            }
            allPrivileges.add(privilege);
        }

        if (created > 0) {
            log.info("Created {} new privileges.", created);
        }

        // Ensure ROLE_ADMIN has all privileges
        TurRole adminRole = turRoleRepository.findByName("ROLE_ADMIN");
        if (adminRole != null) {
            var existing = adminRole.getTurPrivileges();
            var existingNames = new java.util.HashSet<String>();
            for (var p : existing) {
                existingNames.add(p.getName());
            }
            var toAdd = allPrivileges.stream()
                    .filter(p -> !existingNames.contains(p.getName()))
                    .toList();
            if (!toAdd.isEmpty()) {
                var merged = new ArrayList<>(existing);
                merged.addAll(toAdd);
                adminRole.setTurPrivileges(merged);
                turRoleRepository.save(adminRole);
                log.info("Added {} privileges to ROLE_ADMIN.", toAdd.size());
            }
        }
    }
}
