package com.viglet.turing.exchange.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProvidersField;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;

import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class TurSNSiteExportTest {

    @Test
    void shouldHydrateSpotlightsAndMergeProvidersBeforeExportAll() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteSpotlightRepository spotlightRepository = mock(TurSNSiteSpotlightRepository.class);
        TurSNSiteMergeProvidersRepository mergeProvidersRepository = mock(TurSNSiteMergeProvidersRepository.class);
        TurSNSiteExportFileService exportFileService = mock(TurSNSiteExportFileService.class);

        TurSNSiteExport turSNSiteExport = new TurSNSiteExport(siteRepository, spotlightRepository,
                mergeProvidersRepository, exportFileService);

        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        site.setName("Site 1");

        TurSNSiteSpotlight spotlight = new TurSNSiteSpotlight();
        TurSNSiteSpotlightTerm term = new TurSNSiteSpotlightTerm();
        TurSNSiteSpotlightDocument document = new TurSNSiteSpotlightDocument();
        spotlight.setTurSNSiteSpotlightTerms(new HashSet<>(Set.of(term)));
        spotlight.setTurSNSiteSpotlightDocuments(new HashSet<>(Set.of(document)));

        TurSNSiteMergeProviders mergeProvider = new TurSNSiteMergeProviders();
        TurSNSiteMergeProvidersField overwrittenField = new TurSNSiteMergeProvidersField();
        mergeProvider.setOverwrittenFields(new HashSet<>(Set.of(overwrittenField)));

        when(siteRepository.findAll()).thenReturn(List.of(site));
        when(spotlightRepository.findByTurSNSite(any(Sort.class), eq(site))).thenReturn(List.of(spotlight));
        when(mergeProvidersRepository.findByTurSNSite(site)).thenReturn(List.of(mergeProvider));
        when(exportFileService.exportSNSitesToZip(any())).thenReturn(Path.of("site-export.zip"));

        HttpServletResponse response = mock(HttpServletResponse.class);
        StreamingResponseBody responseBody = turSNSiteExport.exportAll(response);

        assertThat(responseBody).isNotNull();
        assertThat(site.getTurSNSiteSpotlights()).containsExactlyInAnyOrder(spotlight);
        assertThat(site.getTurSNSiteMergeProviders()).containsExactlyInAnyOrder(mergeProvider);
        assertThat(spotlight.getTurSNSiteSpotlightTerms()).hasSize(1);
        assertThat(spotlight.getTurSNSiteSpotlightDocuments()).hasSize(1);
        assertThat(mergeProvider.getOverwrittenFields()).hasSize(1);

        verify(spotlightRepository).findByTurSNSite(any(Sort.class), eq(site));
        verify(mergeProvidersRepository).findByTurSNSite(site);
        verify(exportFileService).exportSNSitesToZip(List.of(site));
    }
}
