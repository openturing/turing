package com.viglet.turing.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.kb.TurThesaurusSeedDto;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBase;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;
import com.viglet.turing.sn.kb.TurMicrothesaurusDictionaryService;

/**
 * T674 / §XL (Block AQ) — the seed library scans the bundled education
 * authority-files (pt/en/it) and imports a selected one into a knowledge base
 * through the real T673 importer.
 */
@ExtendWith(MockitoExtension.class)
class TurThesaurusSeedServiceTest {

    @Mock
    private TurMicrothesaurusRepository microthesaurusRepository;
    @Mock
    private TurMicrothesaurusDictionaryService dictionaryService;

    private TurThesaurusSeedService service;

    private final TurKnowledgeBase kb = new TurKnowledgeBase();

    @BeforeEach
    void setUp() {
        service = new TurThesaurusSeedService(
                new TurAuthorityFileImportService(microthesaurusRepository, dictionaryService));
    }

    @Test
    void listsBundledEducationSeedsForThreeLanguages() {
        List<TurThesaurusSeedDto> seeds = service.list();
        assertThat(seeds).hasSizeGreaterThanOrEqualTo(3);
        assertThat(seeds).allMatch(s -> "EDUCATION".equals(s.domain()));
        assertThat(seeds).extracting(TurThesaurusSeedDto::language)
                .contains("pt", "en", "it");
    }

    @Test
    void importsSelectedSeedIntoKnowledgeBase() {
        when(microthesaurusRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<TurMicrothesaurus> captor = ArgumentCaptor.forClass(TurMicrothesaurus.class);

        service.importSeed(kb, "educacao-pt");

        org.mockito.Mockito.verify(microthesaurusRepository).save(captor.capture());
        TurMicrothesaurus imported = captor.getValue();
        assertThat(imported.getName()).isEqualTo("Educação");
        assertThat(imported.getDomain()).isEqualTo("EDUCATION");
        assertThat(imported.getLanguage()).isEqualTo(Locale.forLanguageTag("pt"));
        assertThat(imported.getTurThesaurusTerms()).hasSize(12);
    }

    @Test
    void rejectsUnknownSeed() {
        assertThatThrownBy(() -> service.importSeed(kb, "does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown seed");
    }
}
