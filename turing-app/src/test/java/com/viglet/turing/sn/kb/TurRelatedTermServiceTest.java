package com.viglet.turing.sn.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.kb.TurRelatedTermSuggestionDto;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurSNSiteMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurThesaurusRelationType;
import com.viglet.turing.persistence.model.kb.TurThesaurusTerm;
import com.viglet.turing.persistence.model.kb.TurThesaurusTermRelation;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusConfigRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusRepository;
import com.viglet.turing.persistence.repository.kb.TurThesaurusTermRelationRepository;
import com.viglet.turing.persistence.repository.kb.TurThesaurusTermRepository;

/**
 * T678 / §XL (Block AQ) — related-concept suggestions from the microthesaurus RT
 * links: a query mentioning a controlled-vocabulary term returns that term's
 * associative ({@code RELATED}) neighbours, following the reciprocal edge in both
 * directions, with disabled neighbours and an opted-out site handled.
 */
@ExtendWith(MockitoExtension.class)
class TurRelatedTermServiceTest {

    @Mock
    private TurSNSiteMicrothesaurusRepository selectionRepository;
    @Mock
    private TurSNSiteMicrothesaurusConfigRepository configRepository;
    @Mock
    private TurMicrothesaurusRepository microthesaurusRepository;
    @Mock
    private TurThesaurusTermRepository termRepository;
    @Mock
    private TurThesaurusTermRelationRepository relationRepository;

    private TurRelatedTermService service;

    private final TurSNSite site = new TurSNSite();
    private final Locale pt = Locale.forLanguageTag("pt");

    private final TurThesaurusTerm pneumonia = term("p", "Pneumonia");
    private final TurThesaurusTerm gripe = term("g", "Gripe");
    private final TurThesaurusTerm tuberculose = term("tb", "Tuberculose");

    @BeforeEach
    void setUp() {
        site.setId("site1");
        TurMicrothesaurusDictionaryService dictionaryService =
                new TurMicrothesaurusDictionaryService(selectionRepository, configRepository,
                        microthesaurusRepository, termRepository);
        service = new TurRelatedTermService(dictionaryService, termRepository, relationRepository);
    }

    private TurThesaurusTerm term(String id, String label) {
        TurThesaurusTerm t = new TurThesaurusTerm();
        t.setId(id);
        t.setLabel(label);
        t.setEnabled(true);
        return t;
    }

    private TurThesaurusTermRelation related(TurThesaurusTerm source, String targetTermId) {
        TurThesaurusTermRelation rel = new TurThesaurusTermRelation();
        rel.setType(TurThesaurusRelationType.RELATED);
        rel.setTurThesaurusTerm(source);
        rel.setTargetTermId(targetTermId);
        return rel;
    }

    private void wireDictionary() {
        TurMicrothesaurus m = new TurMicrothesaurus();
        m.setId("m1");
        m.setLanguage(pt);
        TurSNSiteMicrothesaurus selection = new TurSNSiteMicrothesaurus();
        selection.setMicrothesaurusId("m1");
        selection.setEnabled(true);
        lenient().when(selectionRepository.findByTurSNSiteAndEnabledTrue(site))
                .thenReturn(List.of(selection));
        lenient().when(microthesaurusRepository.findById("m1")).thenReturn(Optional.of(m));
        lenient().when(termRepository.findByTurMicrothesaurus(m))
                .thenReturn(List.of(pneumonia, gripe, tuberculose));
    }

    @Test
    void suggestsRelatedTermsInBothDirections() {
        wireDictionary();
        // outgoing edge Pneumonia --RELATED--> Gripe
        when(relationRepository.findByTurThesaurusTermAndType(pneumonia,
                TurThesaurusRelationType.RELATED)).thenReturn(List.of(related(pneumonia, "g")));
        // incoming edge Tuberculose --RELATED--> Pneumonia (reciprocal stored on the other end)
        when(relationRepository.findByTargetTermId("p"))
                .thenReturn(List.of(related(tuberculose, "p")));
        when(termRepository.findById("p")).thenReturn(Optional.of(pneumonia));
        when(termRepository.findById("g")).thenReturn(Optional.of(gripe));
        when(termRepository.findById("tb")).thenReturn(Optional.of(tuberculose));

        List<TurRelatedTermSuggestionDto> result =
                service.suggest(site, pt, "tratamento de pneumonia");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).term()).isEqualTo("Pneumonia");
        assertThat(result.get(0).related()).containsExactly("Gripe", "Tuberculose");
    }

    @Test
    void skipsDisabledNeighbours() {
        wireDictionary();
        gripe.setEnabled(false);
        when(relationRepository.findByTurThesaurusTermAndType(pneumonia,
                TurThesaurusRelationType.RELATED)).thenReturn(List.of(related(pneumonia, "g")));
        when(relationRepository.findByTargetTermId("p")).thenReturn(List.of());
        when(termRepository.findById("p")).thenReturn(Optional.of(pneumonia));
        when(termRepository.findById("g")).thenReturn(Optional.of(gripe));

        List<TurRelatedTermSuggestionDto> result = service.suggest(site, pt, "pneumonia");

        assertThat(result).isEmpty();
    }

    @Test
    void termWithNoRelatedEdgesProducesNoSuggestion() {
        wireDictionary();
        when(relationRepository.findByTurThesaurusTermAndType(pneumonia,
                TurThesaurusRelationType.RELATED)).thenReturn(List.of());
        when(relationRepository.findByTargetTermId("p")).thenReturn(List.of());
        when(termRepository.findById("p")).thenReturn(Optional.of(pneumonia));

        assertThat(service.suggest(site, pt, "pneumonia")).isEmpty();
    }

    @Test
    void optedOutSiteYieldsEmpty() {
        when(selectionRepository.findByTurSNSiteAndEnabledTrue(site)).thenReturn(List.of());
        assertThat(service.suggest(site, pt, "pneumonia")).isEmpty();
    }

    @Test
    void blankQueryYieldsEmpty() {
        assertThat(service.suggest(site, pt, "  ")).isEmpty();
    }
}
