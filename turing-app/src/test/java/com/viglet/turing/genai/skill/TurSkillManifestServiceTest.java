/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.skill.TurSkillManifest.PersonaFragment;
import com.viglet.turing.genai.skill.TurSkillManifest.Vocab;
import com.viglet.turing.genai.skill.TurSkillManifestService.SignatureStatus;
import com.viglet.turing.genai.skill.TurSkillManifestService.ValidationResult;
import com.viglet.turing.persistence.model.skill.TurSkill;

/**
 * T116 / §IX.4 — unit coverage for manifest parsing, validation, signing and
 * the entity round-trip. No Spring context, no LLM.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurSkillManifestServiceTest {

    private final TurSkillManifestService service = new TurSkillManifestService();

    private TurSkillManifest sample() {
        return new TurSkillManifest(
                "scaffold-markdown", "1.2.0", "viglet", null,
                "Generate beat-by-beat markdown scaffolds.",
                new PersonaFragment("Structure as beats.", new Vocab(List.of("beat"), List.of())),
                null,
                List.of("scaffold.markdown", "scaffold.pickQuote"),
                Map.of("draft.md", "# {{title}}"),
                List.of("http@^1.0.0"));
    }

    @Test
    void parsesAManifestFromJson() {
        String json = """
                {
                  "name": "scaffold-markdown",
                  "version": "1.2.0",
                  "author": "viglet",
                  "description": "desc",
                  "personaFragment": { "instruction": "do it", "vocab": { "mandatory": ["beat"], "forbidden": [] } },
                  "tools": ["scaffold.markdown"],
                  "workspaceDefaults": { "draft.md": "# x" },
                  "dependencies": []
                }
                """;
        TurSkillManifest manifest = service.parse(json);
        assertThat(manifest.name()).isEqualTo("scaffold-markdown");
        assertThat(manifest.version()).isEqualTo("1.2.0");
        assertThat(manifest.tools()).containsExactly("scaffold.markdown");
        assertThat(manifest.personaFragment().instruction()).isEqualTo("do it");
        assertThat(manifest.personaFragment().vocab().mandatory()).containsExactly("beat");
    }

    @Test
    void toleratesUnknownFieldsForForwardCompatibility() {
        String json = """
                { "name": "x-skill", "version": "1.0.0", "futureField": { "nested": true } }
                """;
        TurSkillManifest manifest = service.parse(json);
        assertThat(manifest.name()).isEqualTo("x-skill");
    }

    @Test
    void parseRejectsMalformedJson() {
        assertThatThrownBy(() -> service.parse("{ not json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateAcceptsAWellFormedManifest() {
        ValidationResult result = service.validate(sample());
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.signatureStatus()).isEqualTo(SignatureStatus.UNSIGNED);
        assertThat(result.computedSignature()).startsWith("sha256:");
    }

    @Test
    void validateRejectsMissingNameAndVersion() {
        ValidationResult result = service.validate(new TurSkillManifest(
                null, null, null, null, null, null, null, null, null, null));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("name"));
        assertThat(result.errors()).anyMatch(e -> e.contains("version"));
    }

    @Test
    void validateRejectsNonKebabNameAndBadSemver() {
        ValidationResult result = service.validate(new TurSkillManifest(
                "Scaffold Markdown", "v1", "viglet", null, null, null, null, null, null, null));
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("kebab-case"));
        assertThat(result.errors()).anyMatch(e -> e.contains("semver"));
    }

    @Test
    void signatureIsDeterministicAndIndependentOfKeyOrderAndSignatureField() {
        TurSkillManifest a = sample();
        // Same content, but with a (stale) signature already set — must not change the digest.
        TurSkillManifest b = new TurSkillManifest(a.name(), a.version(), a.author(),
                "sha256:deadbeef", a.description(), a.personaFragment(), a.flowSnippet(),
                a.tools(), a.workspaceDefaults(), a.dependencies());
        assertThat(service.computeSignature(a)).isEqualTo(service.computeSignature(b));
    }

    @Test
    void validateFlagsTamperedSignatureAndAcceptsCorrectOne() {
        TurSkillManifest unsigned = sample();
        String good = service.computeSignature(unsigned);

        TurSkillManifest signed = new TurSkillManifest(unsigned.name(), unsigned.version(),
                unsigned.author(), good, unsigned.description(), unsigned.personaFragment(),
                unsigned.flowSnippet(), unsigned.tools(), unsigned.workspaceDefaults(),
                unsigned.dependencies());
        assertThat(service.validate(signed).signatureStatus()).isEqualTo(SignatureStatus.VALID);

        TurSkillManifest tampered = new TurSkillManifest(unsigned.name(), unsigned.version(),
                unsigned.author(), "sha256:0000", unsigned.description(), unsigned.personaFragment(),
                unsigned.flowSnippet(), unsigned.tools(), unsigned.workspaceDefaults(),
                unsigned.dependencies());
        assertThat(service.validate(tampered).signatureStatus()).isEqualTo(SignatureStatus.INVALID);
    }

    @Test
    void toEntitySelfSignsWhenManifestHasNoSignature() {
        TurSkill entity = service.toEntity(sample(), true);
        assertThat(entity.getSignature()).isEqualTo(service.computeSignature(sample()));
        assertThat(entity.isBuiltIn()).isTrue();
        assertThat(entity.getEnabled()).isEqualTo(1);
        assertThat(entity.getToolsJson()).contains("scaffold.markdown");
        assertThat(entity.getPersonaFragmentJson()).contains("beat");
    }

    @Test
    void entityRoundTripsBackToAnEquivalentManifest() {
        TurSkillManifest original = sample();
        TurSkill entity = service.toEntity(original, false);
        TurSkillManifest rehydrated = service.toManifest(entity);

        assertThat(rehydrated.name()).isEqualTo(original.name());
        assertThat(rehydrated.version()).isEqualTo(original.version());
        assertThat(rehydrated.tools()).containsExactlyElementsOf(original.tools());
        assertThat(rehydrated.workspaceDefaults()).containsAllEntriesOf(original.workspaceDefaults());
        assertThat(rehydrated.dependencies()).containsExactlyElementsOf(original.dependencies());
        assertThat(rehydrated.personaFragment().instruction())
                .isEqualTo(original.personaFragment().instruction());
    }
}
