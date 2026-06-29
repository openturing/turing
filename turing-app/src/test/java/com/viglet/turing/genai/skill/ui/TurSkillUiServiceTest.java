/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.skill.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.skill.TurSkillCatalogService;
import com.viglet.turing.genai.skill.TurSkillFileService;
import com.viglet.turing.persistence.model.skill.TurSkill;

@ExtendWith(MockitoExtension.class)
class TurSkillUiServiceTest {

    @Mock private TurSkillCatalogService catalogService;
    @Mock private TurSkillFileService fileService;

    private TurSkillUiService service() {
        return new TurSkillUiService(catalogService, fileService);
    }

    private static TurSkill skill(String id, String name) {
        TurSkill s = new TurSkill();
        s.setId(id);
        s.setName(name);
        return s;
    }

    private static final String MANIFEST = """
            { "components": [
                { "name": "rma_form", "description": "Collect return items",
                  "schema": { "type": "object", "properties": { "reason": { "type": "string" } } } },
                { "name": "label", "description": "Show a label" }
            ] }""";

    @Test
    void parsesComponentsAndQualifiesToolName() {
        when(fileService.readFile("s1", TurSkillUiService.MANIFEST_PATH))
                .thenReturn(Optional.of(MANIFEST));
        List<TurSkillUiComponent> out = service().listComponents(skill("s1", "Returns RMA"));
        assertThat(out).hasSize(2);
        assertThat(out.get(0).componentName()).isEqualTo("rma_form");
        assertThat(out.get(0).toolName()).isEqualTo("returns_rma__rma_form"); // sanitised skill name
        assertThat(out.get(0).schema()).contains("reason");
        assertThat(out.get(1).toolName()).isEqualTo("returns_rma__label");
        assertThat(out.get(1).schema()).isNull(); // no schema → null (TurClientTool defaults it)
    }

    @Test
    void noManifestYieldsEmpty() {
        when(fileService.readFile("s1", TurSkillUiService.MANIFEST_PATH)).thenReturn(Optional.empty());
        assertThat(service().listComponents(skill("s1", "X"))).isEmpty();
    }

    @Test
    void malformedManifestYieldsEmpty() {
        when(fileService.readFile("s1", TurSkillUiService.MANIFEST_PATH))
                .thenReturn(Optional.of("{ not json"));
        assertThat(service().listComponents(skill("s1", "X"))).isEmpty();
    }

    @Test
    void resultIsCachedPerSkill() {
        when(fileService.readFile("s1", TurSkillUiService.MANIFEST_PATH))
                .thenReturn(Optional.of(MANIFEST));
        TurSkillUiService svc = service();
        svc.listComponents(skill("s1", "Returns"));
        svc.listComponents(skill("s1", "Returns"));
        verify(fileService, times(1)).readFile("s1", TurSkillUiService.MANIFEST_PATH);
        svc.clearCache();
        svc.listComponents(skill("s1", "Returns"));
        verify(fileService, times(2)).readFile("s1", TurSkillUiService.MANIFEST_PATH);
    }

    @Test
    void listByIdResolvesViaCatalog() {
        lenient().when(catalogService.findById("s1")).thenReturn(Optional.of(skill("s1", "Returns")));
        when(fileService.readFile("s1", TurSkillUiService.MANIFEST_PATH))
                .thenReturn(Optional.of(MANIFEST));
        assertThat(service().listComponents("s1")).hasSize(2);
    }
}
