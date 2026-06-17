package com.viglet.turing.sn.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;
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

    private TurSNJobAttributeSpec field(String name) {
        return TurSNJobAttributeSpec.builder()
                .name(name).type(TurSEFieldType.STRING).facet(true).build();
    }

    private TurSNManifestDiff cleanDiff() {
        return new TurSNManifestDiff(null, true, List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void provisionCreatesSiteSeedsTemplateAndAddsFields() {
        Locale ptBr = LocaleUtils.toLocale("pt_BR");
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setId("se1");

        TurSNSiteManifest manifest = new TurSNSiteManifest(
                "courses", "Course catalog", "se1", List.of("pt_BR"), List.of(field("mensalidade")));

        when(turSNSiteRepository.findByNameIgnoreCase("courses")).thenReturn(Optional.empty());
        when(turSEInstanceRepository.findById("se1")).thenReturn(Optional.of(seInstance));
        // template.createSNSite (mocked) would have persisted the seed locale, so
        // the convergence check sees it as present.
        when(turSNSiteLocaleRepository.existsByTurSNSiteAndLanguage(any(), eq(ptBr))).thenReturn(true);
        when(turSNManifestPlanner.diff(eq(manifest), any(TurSNSite.class))).thenReturn(cleanDiff());
        when(turSNFieldProvisioner.ensureField(any(TurSNSite.class), eq(manifest.fields().get(0))))
                .thenReturn(true);

        TurSNManifestResult result = service.provision(manifest);

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

        TurSNSiteManifest manifest = new TurSNSiteManifest(
                "courses", null, null, null, List.of(field("mensalidade")));

        when(turSNSiteRepository.findByNameIgnoreCase("courses")).thenReturn(Optional.of(existing));
        when(turSNSiteRepository.findByIdNoCache(any())).thenReturn(Optional.of(existing));
        when(turSNManifestPlanner.diff(eq(manifest), eq(existing))).thenReturn(cleanDiff());
        when(turSNFieldProvisioner.ensureField(eq(existing), eq(manifest.fields().get(0))))
                .thenReturn(false);

        TurSNManifestResult result = service.provision(manifest);

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
        TurSNSiteManifest manifest = new TurSNSiteManifest(" ", null, "se1", null, null);
        assertThrows(IllegalArgumentException.class, () -> service.provision(manifest));
    }

    @Test
    void provisionRejectsNewSiteWithoutSeInstance() {
        TurSNSiteManifest manifest = new TurSNSiteManifest("courses", null, null, null, null);
        when(turSNSiteRepository.findByNameIgnoreCase("courses")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.provision(manifest));
    }

    @Test
    void provisionRejectsFieldWithoutType() {
        TurSNJobAttributeSpec bad = TurSNJobAttributeSpec.builder().name("x").build();
        TurSNSiteManifest manifest = new TurSNSiteManifest("courses", null, "se1", null, List.of(bad));
        assertThrows(IllegalArgumentException.class, () -> service.provision(manifest));
    }

    @Test
    void provisionRejectsUndeclaredBreakingChange() {
        TurSNSite existing = new TurSNSite();
        existing.setName("courses");
        TurSNSiteManifest manifest = new TurSNSiteManifest(
                "courses", null, null, "2", null, List.of(field("mensalidade")), null);

        TurSNManifestFieldChange breaking = new TurSNManifestFieldChange(
                "mensalidade", "type", "STRING", "INT", false);
        TurSNManifestDiff diff = new TurSNManifestDiff(
                "2", true, List.of(), List.of(breaking), List.of(), List.of());

        when(turSNSiteRepository.findByNameIgnoreCase("courses")).thenReturn(Optional.of(existing));
        when(turSNSiteRepository.findByIdNoCache(any())).thenReturn(Optional.of(existing));
        when(turSNManifestPlanner.diff(eq(manifest), eq(existing))).thenReturn(diff);

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
        TurSNJobAttributeSpec spec = field("mensalidade");
        TurSNSiteManifest manifest = new TurSNSiteManifest(
                "courses", null, null, "2", null, List.of(spec),
                List.of(new TurSNFieldMigration("mensalidade", TurSNManifestMigrationAction.RECREATE)));

        TurSNManifestFieldChange breaking = new TurSNManifestFieldChange(
                "mensalidade", "type", "STRING", "INT", true);
        TurSNManifestDiff diff = new TurSNManifestDiff(
                "2", true, List.of(), List.of(breaking), List.of(), List.of());

        when(turSNSiteRepository.findByNameIgnoreCase("courses")).thenReturn(Optional.of(existing));
        when(turSNSiteRepository.findByIdNoCache(any())).thenReturn(Optional.of(existing));
        when(turSNManifestPlanner.diff(eq(manifest), eq(existing))).thenReturn(diff);
        when(turSNFieldProvisioner.recreateField(eq(existing), eq(spec))).thenReturn(true);

        TurSNManifestResult result = service.provision(manifest);

        assertEquals(List.of("mensalidade"), result.fieldsMigrated());
        assertTrue(result.fieldsCreated().isEmpty());
        assertTrue(result.fieldsSkipped().isEmpty());
        assertEquals("2", result.schemaVersion());
        // Migrated field is converged by recreate, not re-created by ensureField.
        verify(turSNFieldProvisioner, never()).ensureField(eq(existing), eq(spec));
        verify(eventPublisher).publishEvent(any(SnSiteIndexInvalidatedEvent.class));
    }

    @Test
    void planDelegatesToPlannerWithoutMutating() {
        TurSNSiteManifest manifest = new TurSNSiteManifest(
                "courses", null, "se1", "3", List.of("pt_BR"), List.of(field("mensalidade")), null);
        TurSNManifestDiff diff = new TurSNManifestDiff(
                "3", false, List.of("mensalidade"), List.of(), List.of(), List.of("pt_BR"));
        when(turSNManifestPlanner.diff(manifest)).thenReturn(diff);

        TurSNManifestDiff result = service.plan(manifest);

        assertEquals(diff, result);
        verify(turSNFieldProvisioner, never()).ensureField(any(), any());
        verify(turSNFieldProvisioner, never()).recreateField(any(), any());
    }
}
