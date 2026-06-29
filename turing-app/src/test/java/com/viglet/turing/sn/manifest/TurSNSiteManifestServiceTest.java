package com.viglet.turing.sn.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.LocaleUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldMigration;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletFieldType;
import com.viglet.core.manifest.VigletManifestDiff;
import com.viglet.core.manifest.VigletManifestFieldChange;
import com.viglet.core.manifest.VigletManifestMigrationAction;
import com.viglet.core.manifest.VigletManifestResult;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.domain.sn.SnSiteIndexInvalidatedEvent;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;
import com.viglet.turing.sn.template.TurSNTemplate;

@ExtendWith(MockitoExtension.class)
class TurSNSiteManifestServiceTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Mock
    private TurSEInstanceRepository turSEInstanceRepository;
    @Mock
    private TurSNTemplate turSNTemplate;
    @Mock
    private TurSNFieldProvisioner turSNFieldProvisioner;
    @Mock
    private TurSNManifestPlanner turSNManifestPlanner;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private TurSNSiteManifestService service;

    private VigletFieldSpec field(String name) {
        return VigletFieldSpec.builder()
                .name(name).type(VigletFieldType.STRING).facet(true).build();
    }

    /** The service maps a VigletFieldSpec to a fresh TurSNJobAttributeSpec (no equals), so match by name. */
    private static TurSNJobAttributeSpec named(String name) {
        return argThat(spec -> spec != null && name.equals(spec.getName()));
    }

    private VigletManifestDiff cleanDiff() {
        return new VigletManifestDiff(null, true, List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void provisionCreatesSiteSeedsTemplateAndAddsFields() {
        Locale ptBr = LocaleUtils.toLocale("pt_BR");
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setId("se1");

        VigletFieldManifest manifest = new VigletFieldManifest(
                "courses", "Course catalog", "se1", List.of("pt_BR"), List.of(field("mensalidade")));

        when(turSNSiteRepository.findByNameIgnoreCase("courses")).thenReturn(Optional.empty());
        when(turSEInstanceRepository.findById("se1")).thenReturn(Optional.of(seInstance));
        // template.createSNSite (mocked) would have persisted the seed locale, so
        // the convergence check sees it as present.
        when(turSNSiteLocaleRepository.existsByTurSNSiteAndLanguage(any(), eq(ptBr))).thenReturn(true);
        when(turSNManifestPlanner.diff(eq(manifest), any(TurSNSite.class))).thenReturn(cleanDiff());
        when(turSNFieldProvisioner.ensureField(any(TurSNSite.class), named("mensalidade")))
                .thenReturn(true);

        VigletManifestResult result = service.provision(manifest);

        assertTrue(result.siteCreated());
        assertEquals(List.of("mensalidade"), result.fieldsCreated());
        assertTrue(result.fieldsSkipped().isEmpty());
        assertTrue(result.localesCreated().contains("pt_BR"));
        verify(turSNTemplate).createSNSite(any(TurSNSite.class), eq("manifest"), eq(ptBr));
        verify(eventPublisher).publishEvent(any(SnSiteIndexInvalidatedEvent.class));
    }

    @Test
    void provisionIsIdempotentOnExistingSite() {
        TurSNSite existing = new TurSNSite();
        existing.setName("courses");

        VigletFieldManifest manifest = new VigletFieldManifest(
                "courses", null, null, null, List.of(field("mensalidade")));

        when(turSNSiteRepository.findByNameIgnoreCase("courses")).thenReturn(Optional.of(existing));
        when(turSNSiteRepository.findByIdWithGenAi(any())).thenReturn(Optional.of(existing));
        when(turSNManifestPlanner.diff(manifest, existing)).thenReturn(cleanDiff());
        when(turSNFieldProvisioner.ensureField(eq(existing), named("mensalidade")))
                .thenReturn(false);

        VigletManifestResult result = service.provision(manifest);

        assertFalse(result.siteCreated());
        assertTrue(result.fieldsCreated().isEmpty());
        assertEquals(List.of("mensalidade"), result.fieldsSkipped());
        assertTrue(result.localesCreated().isEmpty());
        verify(turSNTemplate, never()).createSNSite(any(), any(), any());
        verify(turSNTemplate, never()).createLocale(any(), any(), any());
        verify(turSEInstanceRepository, never()).findById(any());
    }

    @Test
    void provisionRejectsMissingName() {
        VigletFieldManifest manifest = new VigletFieldManifest(" ", null, "se1", null, null);
        assertThrows(IllegalArgumentException.class, () -> service.provision(manifest));
    }

    @Test
    void provisionRejectsNewSiteWithoutSeInstance() {
        VigletFieldManifest manifest = new VigletFieldManifest("courses", null, null, null, null);
        when(turSNSiteRepository.findByNameIgnoreCase("courses")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.provision(manifest));
    }

    @Test
    void provisionRejectsFieldWithoutType() {
        VigletFieldSpec bad = VigletFieldSpec.builder().name("x").build();
        VigletFieldManifest manifest = new VigletFieldManifest("courses", null, "se1", null, List.of(bad));
        assertThrows(IllegalArgumentException.class, () -> service.provision(manifest));
    }

    @Test
    void provisionRejectsUndeclaredBreakingChange() {
        TurSNSite existing = new TurSNSite();
        existing.setName("courses");
        VigletFieldManifest manifest = new VigletFieldManifest(
                "courses", null, null, "2", null, List.of(field("mensalidade")), null);

        VigletManifestFieldChange breaking = new VigletManifestFieldChange(
                "mensalidade", "type", "STRING", "INT", false);
        VigletManifestDiff diff = new VigletManifestDiff(
                "2", true, List.of(), List.of(breaking), List.of(), List.of());

        when(turSNSiteRepository.findByNameIgnoreCase("courses")).thenReturn(Optional.of(existing));
        when(turSNSiteRepository.findByIdWithGenAi(any())).thenReturn(Optional.of(existing));
        when(turSNManifestPlanner.diff(manifest, existing)).thenReturn(diff);

        assertThrows(TurSNManifestMigrationRequiredException.class, () -> service.provision(manifest));
        // Nothing mutated: no field create, no migration, no event.
        verify(turSNFieldProvisioner, never()).ensureField(any(), any());
        verify(turSNFieldProvisioner, never()).recreateField(any(), any());
        verify(eventPublisher, never()).publishEvent(any(SnSiteIndexInvalidatedEvent.class));
    }

    @Test
    void provisionAppliesDeclaredBreakingChangeByRecreate() {
        TurSNSite existing = new TurSNSite();
        existing.setName("courses");
        VigletFieldManifest manifest = new VigletFieldManifest(
                "courses", null, null, "2", null, List.of(field("mensalidade")),
                List.of(new VigletFieldMigration("mensalidade", VigletManifestMigrationAction.RECREATE)));

        VigletManifestFieldChange breaking = new VigletManifestFieldChange(
                "mensalidade", "type", "STRING", "INT", true);
        VigletManifestDiff diff = new VigletManifestDiff(
                "2", true, List.of(), List.of(breaking), List.of(), List.of());

        when(turSNSiteRepository.findByNameIgnoreCase("courses")).thenReturn(Optional.of(existing));
        when(turSNSiteRepository.findByIdWithGenAi(any())).thenReturn(Optional.of(existing));
        when(turSNManifestPlanner.diff(manifest, existing)).thenReturn(diff);
        when(turSNFieldProvisioner.recreateField(eq(existing), named("mensalidade"))).thenReturn(true);

        VigletManifestResult result = service.provision(manifest);

        assertEquals(List.of("mensalidade"), result.fieldsMigrated());
        assertTrue(result.fieldsCreated().isEmpty());
        assertTrue(result.fieldsSkipped().isEmpty());
        assertEquals("2", result.schemaVersion());
        // Migrated field is converged by recreate, not re-created by ensureField.
        verify(turSNFieldProvisioner, never()).ensureField(eq(existing), named("mensalidade"));
        verify(eventPublisher).publishEvent(any(SnSiteIndexInvalidatedEvent.class));
    }

    @Test
    void planDelegatesToPlannerWithoutMutating() {
        VigletFieldManifest manifest = new VigletFieldManifest(
                "courses", null, "se1", "3", List.of("pt_BR"), List.of(field("mensalidade")), null);
        VigletManifestDiff diff = new VigletManifestDiff(
                "3", false, List.of("mensalidade"), List.of(), List.of(), List.of("pt_BR"));
        when(turSNManifestPlanner.diff(manifest)).thenReturn(diff);

        VigletManifestDiff result = service.plan(manifest);

        assertEquals(diff, result);
        verify(turSNFieldProvisioner, never()).ensureField(any(), any());
        verify(turSNFieldProvisioner, never()).recreateField(any(), any());
    }
}
