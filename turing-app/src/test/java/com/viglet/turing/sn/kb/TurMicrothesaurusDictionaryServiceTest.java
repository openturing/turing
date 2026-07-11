package com.viglet.turing.sn.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurSNSiteMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurSNSiteMicrothesaurusConfig;
import com.viglet.turing.persistence.model.kb.TurThesaurusRelationType;
import com.viglet.turing.persistence.model.kb.TurThesaurusTerm;
import com.viglet.turing.persistence.model.kb.TurThesaurusTermRelation;
import com.viglet.turing.persistence.model.kb.TurThesaurusTermVariation;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusConfigRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusRepository;
import com.viglet.turing.persistence.repository.kb.TurThesaurusTermRepository;

/**
 * T671 / §XL (Block AQ) — compilation of the per-(site, locale) recognition
 * dictionary: locale filtering, root→term path pre-computation, USE-synonym
 * resolution to the preferred term, and cache eviction.
 */
@ExtendWith(MockitoExtension.class)
class TurMicrothesaurusDictionaryServiceTest {

    @Mock
    private TurSNSiteMicrothesaurusRepository selectionRepository;
    @Mock
    private TurSNSiteMicrothesaurusConfigRepository configRepository;
    @Mock
    private TurMicrothesaurusRepository microthesaurusRepository;
    @Mock
    private TurThesaurusTermRepository termRepository;

    @InjectMocks
    private TurMicrothesaurusDictionaryService service;

    private final TurSNSite site = new TurSNSite();

    private TurThesaurusTerm term(String id, String label, String parentId) {
        TurThesaurusTerm t = new TurThesaurusTerm();
        t.setId(id);
        t.setLabel(label);
        t.setParentTermId(parentId);
        t.setEnabled(true);
        return t;
    }

    private TurMicrothesaurus tree(String id, Locale language) {
        TurMicrothesaurus m = new TurMicrothesaurus();
        m.setId(id);
        m.setLanguage(language);
        m.setDomain("EDUCATION");
        return m;
    }

    private void selectTree(TurMicrothesaurus m, boolean includeSynonyms, List<TurThesaurusTerm> terms) {
        TurSNSiteMicrothesaurus selection = new TurSNSiteMicrothesaurus();
        selection.setMicrothesaurusId(m.getId());
        selection.setEnabled(true);
        when(selectionRepository.findByTurSNSiteAndEnabledTrue(site)).thenReturn(List.of(selection));
        TurSNSiteMicrothesaurusConfig config = new TurSNSiteMicrothesaurusConfig();
        config.setIncludeSynonyms(includeSynonyms);
        lenient().when(configRepository.findByTurSNSite(site)).thenReturn(Optional.of(config));
        lenient().when(microthesaurusRepository.findById(m.getId())).thenReturn(Optional.of(m));
        lenient().when(termRepository.findByTurMicrothesaurus(m)).thenReturn(terms);
    }

    @Test
    void compilesPathAndRecognizesChildTerm() {
        TurMicrothesaurus m = tree("m1", Locale.forLanguageTag("pt"));
        TurThesaurusTerm root = term("d", "Doença", null);
        TurThesaurusTerm child = term("p", "Pneumonia", "d");
        selectTree(m, true, List.of(root, child));

        var dict = service.getDictionary(site, Locale.forLanguageTag("pt"));
        var found = dict.recognize("quadro de pneumonia grave");

        assertThat(found).extracting(TurRecognizedTerm::termId).containsExactly("p");
        assertThat(found.iterator().next().path()).containsExactly("Doença", "Pneumonia");
    }

    @Test
    void skipsMicrothesaurusWhenLocaleDiffers() {
        TurMicrothesaurus m = tree("m1", Locale.forLanguageTag("pt"));
        selectTree(m, true, List.of(term("p", "Pneumonia", null)));

        var dict = service.getDictionary(site, Locale.ENGLISH);
        assertThat(dict.recognize("pneumonia")).isEmpty();
    }

    @Test
    void useSynonymResolvesToPreferredTerm() {
        TurMicrothesaurus m = tree("m1", Locale.forLanguageTag("pt"));
        TurThesaurusTerm preferred = term("p", "Pneumonia", null);
        TurThesaurusTerm nonPreferred = term("x", "H1N1", null);
        TurThesaurusTermRelation use = new TurThesaurusTermRelation();
        use.setType(TurThesaurusRelationType.USE);
        use.setTargetTermId("p");
        use.setTurThesaurusTerm(nonPreferred);
        nonPreferred.setRelations(Set.of(use));
        selectTree(m, true, List.of(preferred, nonPreferred));

        var found = service.getDictionary(site, Locale.forLanguageTag("pt")).recognize("caso de h1n1");

        assertThat(found).extracting(TurRecognizedTerm::termId).containsExactly("p");
        assertThat(found.iterator().next().label()).isEqualTo("Pneumonia");
    }

    @Test
    void variationIsRecognised() {
        TurMicrothesaurus m = tree("m1", Locale.forLanguageTag("pt"));
        TurThesaurusTerm t = term("p", "Pneumonia", null);
        t.setVariations(List.of(new TurThesaurusTermVariation("pneumonite", Locale.forLanguageTag("pt"))));
        selectTree(m, true, List.of(t));

        assertThat(service.getDictionary(site, Locale.forLanguageTag("pt")).recognize("teve pneumonite"))
                .extracting(TurRecognizedTerm::termId).containsExactly("p");
    }

    @Test
    void evictAllForcesRecompile() {
        TurMicrothesaurus m = tree("m1", Locale.forLanguageTag("pt"));
        selectTree(m, true, List.of(term("p", "Pneumonia", null)));

        service.getDictionary(site, Locale.forLanguageTag("pt"));
        service.evictAll();
        service.getDictionary(site, Locale.forLanguageTag("pt"));

        // findByTurMicrothesaurus called twice => the cache was evicted and rebuilt.
        org.mockito.Mockito.verify(termRepository, org.mockito.Mockito.times(2))
                .findByTurMicrothesaurus(m);
    }
}
