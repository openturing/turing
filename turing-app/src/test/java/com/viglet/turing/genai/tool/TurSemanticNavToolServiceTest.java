package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

@ExtendWith(MockitoExtension.class)
class TurSemanticNavToolServiceTest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    private TurSemanticNavToolService service;

    @BeforeEach
    void setUp() {
        service = new TurSemanticNavToolService(
                turSNSiteRepository, turSNSiteLocaleRepository, turSNSiteFieldExtRepository);
    }

    // --- listSites Tests ---

    @Test
    void shouldReturnNoSitesMessage() {
        when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());

        String result = service.listSites("");

        assertThat(result).isEqualTo("No Semantic Navigation sites found.");
    }

    @Test
    void shouldListAllSites() {
        TurSNSite site1 = new TurSNSite();
        site1.setName("Enterprise");
        site1.setDescription("Enterprise site");

        TurSNSite site2 = new TurSNSite();
        site2.setName("Portal");
        site2.setDescription(null);

        TurSNSiteLocale locale1 = new TurSNSiteLocale();
        locale1.setLanguage(Locale.ENGLISH);

        when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site1, site2));
        when(turSNSiteLocaleRepository.findByTurSNSite(site1)).thenReturn(List.of(locale1));
        when(turSNSiteLocaleRepository.findByTurSNSite(site2)).thenReturn(Collections.emptyList());

        String result = service.listSites("");

        assertThat(result)
                .contains("site_name;description;locales")
                .contains("Enterprise;Enterprise site;en")
                .contains("Portal;;");
    }

    @Test
    void shouldFilterSitesByName() {
        TurSNSite site1 = new TurSNSite();
        site1.setName("Enterprise");
        site1.setDescription("desc");

        TurSNSite site2 = new TurSNSite();
        site2.setName("Portal");
        site2.setDescription("desc");

        when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site1, site2));
        when(turSNSiteLocaleRepository.findByTurSNSite(site1)).thenReturn(Collections.emptyList());

        String result = service.listSites("enterprise");

        assertThat(result).contains("Enterprise").doesNotContain("Portal");
    }

    @Test
    void shouldReturnNoSitesWhenFilterMatchesNone() {
        TurSNSite site = new TurSNSite();
        site.setName("Enterprise");

        when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));

        String result = service.listSites("nonexistent");

        assertThat(result).isEqualTo("No Semantic Navigation sites found.");
    }

    @Test
    void shouldListSitesWithNullFilter() {
        TurSNSite site = new TurSNSite();
        site.setName("Site1");
        site.setDescription("desc");

        when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(turSNSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        String result = service.listSites(null);

        assertThat(result).contains("Site1");
    }

    @Test
    void shouldListSitesWithMultipleLocales() {
        TurSNSite site = new TurSNSite();
        site.setName("MultiLang");
        site.setDescription("desc");

        TurSNSiteLocale localeEn = new TurSNSiteLocale();
        localeEn.setLanguage(Locale.ENGLISH);
        TurSNSiteLocale localePt = new TurSNSiteLocale();
        localePt.setLanguage(Locale.of("pt"));

        when(turSNSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(turSNSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(localeEn, localePt));

        String result = service.listSites("");

        assertThat(result).contains("en, pt");
    }

    // --- getSiteFields Tests ---

    @Test
    void shouldReturnSiteNotFound() {
        when(turSNSiteRepository.findByNameIgnoreCase("Missing")).thenReturn(Optional.empty());

        String result = service.getSiteFields("Missing");

        assertThat(result).isEqualTo("Site not found: Missing");
    }

    @Test
    void shouldReturnNoFieldsMessage() {
        TurSNSite site = new TurSNSite();
        site.setName("MySite");

        when(turSNSiteRepository.findByNameIgnoreCase("MySite")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());

        String result = service.getSiteFields("MySite");

        assertThat(result).contains("No enabled fields found for site: MySite");
    }

    @Test
    void shouldReturnFieldsList() {
        TurSNSite site = new TurSNSite();
        site.setName("MySite");

        TurSNSiteFieldExt field1 = new TurSNSiteFieldExt();
        field1.setName("title");
        field1.setType(TurSEFieldType.TEXT);
        field1.setFacet(0);

        TurSNSiteFieldExt field2 = new TurSNSiteFieldExt();
        field2.setName("category");
        field2.setType(TurSEFieldType.STRING);
        field2.setFacet(1);

        when(turSNSiteRepository.findByNameIgnoreCase("MySite")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(field1, field2));

        String result = service.getSiteFields("MySite");

        assertThat(result)
                .contains("field_name;type;facet")
                .contains("title;TEXT;no")
                .contains("category;STRING;yes");
    }

    // --- searchSite Tests ---

    @Test
    void shouldReturnErrorOrNoResultsForSearchWhenServerUnavailable() {
        // searchSite makes an HTTP call directly via HttpTurSNServer
        // When serverPort is 0 (default in unit test), it either gets a connection error
        // or returns "No results" depending on the environment
        String result = service.searchSite("Missing", "en", "test", 10, 1, null);

        assertThat(result).satisfiesAnyOf(
                r -> assertThat(r).contains("Error executing search"),
                r -> assertThat(r).contains("No results found"));
    }
}
