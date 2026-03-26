package com.viglet.turing.onstartup.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.auth.TurPrivilege;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;

import jakarta.transaction.Transactional;

@Component
public class TurRoleOnStartup {
    private static final String CATEGORY_EMBEDDING = "EMBEDDING";
    private static final String CATEGORY_STORE = "STORE";
    private static final String CATEGORY_AI_AGENT = "AI_AGENT";
    private static final String CATEGORY_INTENT = "INTENT";

    private final TurPrivilegeRepository turPrivilegeRepository;
    private final TurRoleRepository turRoleRepository;

    private static final String[][] PRIVILEGE_DEFINITIONS = {
        // name, description, category
        {"READ_PRIVILEGE", "Legacy read privilege", "SYSTEM"},
        {"WRITE_PRIVILEGE", "Legacy write privilege", "SYSTEM"},

        {"LLM_VIEW", "View Language Model instances", "LLM"},
        {"LLM_CREATE", "Create Language Model instances", "LLM"},
        {"LLM_EDIT", "Edit Language Model instances", "LLM"},
        {"LLM_DELETE", "Delete Language Model instances", "LLM"},

        {"EMBEDDING_VIEW", "View Embedding Model instances", CATEGORY_EMBEDDING},
        {"EMBEDDING_CREATE", "Create Embedding Model instances", CATEGORY_EMBEDDING},
        {"EMBEDDING_EDIT", "Edit Embedding Model instances", CATEGORY_EMBEDDING},
        {"EMBEDDING_DELETE", "Delete Embedding Model instances", CATEGORY_EMBEDDING},

        {"STORE_VIEW", "View Embedding Store instances", CATEGORY_STORE},
        {"STORE_CREATE", "Create Embedding Store instances", CATEGORY_STORE},
        {"STORE_EDIT", "Edit Embedding Store instances", CATEGORY_STORE},
        {"STORE_DELETE", "Delete Embedding Store instances", CATEGORY_STORE},

        {"SE_VIEW", "View Search Engine instances", "SE"},
        {"SE_CREATE", "Create Search Engine instances", "SE"},
        {"SE_EDIT", "Edit Search Engine instances", "SE"},
        {"SE_DELETE", "Delete Search Engine instances", "SE"},

        {"SN_VIEW", "View Semantic Navigation sites", "SN"},
        {"SN_CREATE", "Create Semantic Navigation sites", "SN"},
        {"SN_EDIT", "Edit Semantic Navigation sites", "SN"},
        {"SN_DELETE", "Delete Semantic Navigation sites", "SN"},

        {"AI_AGENT_VIEW", "View AI Agent instances", CATEGORY_AI_AGENT},
        {"AI_AGENT_CREATE", "Create AI Agent instances", CATEGORY_AI_AGENT},
        {"AI_AGENT_EDIT", "Edit AI Agent instances", CATEGORY_AI_AGENT},
        {"AI_AGENT_DELETE", "Delete AI Agent instances", CATEGORY_AI_AGENT},

        {"INTENT_VIEW", "View Intent categories", CATEGORY_INTENT},
        {"INTENT_CREATE", "Create Intent categories", CATEGORY_INTENT},
        {"INTENT_EDIT", "Edit Intent categories", CATEGORY_INTENT},
        {"INTENT_DELETE", "Delete Intent categories", CATEGORY_INTENT},
    };

    public TurRoleOnStartup(TurPrivilegeRepository turPrivilegeRepository, TurRoleRepository turRoleRepository) {
        this.turPrivilegeRepository = turPrivilegeRepository;
        this.turRoleRepository = turRoleRepository;
    }

    @Transactional
    public void createDefaultRows() {
        List<TurPrivilege> allPrivileges = new ArrayList<>();
        for (String[] def : PRIVILEGE_DEFINITIONS) {
            allPrivileges.add(createPrivilegeIfNotFound(def[0], def[1], def[2]));
        }

        createRoleIfNotFound("ROLE_ADMIN", allPrivileges);
        createRoleIfNotFound("ROLE_USER", Collections.singletonList(
                turPrivilegeRepository.findByName("READ_PRIVILEGE")));
    }

    @Transactional
    public TurPrivilege createPrivilegeIfNotFound(String name, String description, String category) {
        TurPrivilege privilege = turPrivilegeRepository.findByName(name);
        if (privilege == null) {
            privilege = new TurPrivilege(name);
            privilege.setDescription(description);
            privilege.setCategory(category);
            turPrivilegeRepository.save(privilege);
        } else if (privilege.getDescription() == null || privilege.getCategory() == null) {
            privilege.setDescription(description);
            privilege.setCategory(category);
            turPrivilegeRepository.save(privilege);
        }
        return privilege;
    }

    @Transactional
    public void createRoleIfNotFound(String name, Collection<TurPrivilege> privileges) {
        TurRole role = turRoleRepository.findByName(name);
        if (role == null) {
            role = new TurRole(name);
            role.setTurPrivileges(privileges);
            turRoleRepository.save(role);
        }
    }
}
