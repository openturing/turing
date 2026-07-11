package com.viglet.turing.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.kb.TurMicrothesaurusDto;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBase;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusRepository;
import com.viglet.turing.sn.kb.TurMicrothesaurusDictionaryService;

/**
 * T669 / §XL (Block AQ) — unit tests for the microthesaurus CRUD service:
 * name/language/domain are all required, and the knowledge base is wired on the
 * created entity.
 */
@ExtendWith(MockitoExtension.class)
class TurMicrothesaurusServiceTest {

    @Mock
    private TurMicrothesaurusRepository repository;

    @Mock
    private TurSNSiteMicrothesaurusRepository siteSelectionRepository;

    @Mock
    private TurMicrothesaurusDictionaryService dictionaryService;

    @InjectMocks
    private TurMicrothesaurusService service;

    private final TurKnowledgeBase kb = new TurKnowledgeBase();

    @Test
    void createRejectsMissingLanguage() {
        assertThatThrownBy(() -> service.create(kb,
                new TurMicrothesaurusDto(null, "Bees", null, null, "EDUCATION", null, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("language is required");
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsBlankDomain() {
        assertThatThrownBy(() -> service.create(kb, new TurMicrothesaurusDto(
                null, "Bees", null, Locale.forLanguageTag("pt"), "  ", null, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("domain is required");
    }

    @Test
    void createPersistsWithKnowledgeBaseAndFields() {
        when(repository.save(any(TurMicrothesaurus.class))).thenAnswer(i -> i.getArgument(0));

        TurMicrothesaurusDto result = service.create(kb, new TurMicrothesaurusDto(
                null, "Apicultura", "trees", Locale.forLanguageTag("pt"), "EDUCATION", null, 0));

        assertThat(result.name()).isEqualTo("Apicultura");
        assertThat(result.language()).isEqualTo(Locale.forLanguageTag("pt"));
        assertThat(result.domain()).isEqualTo("EDUCATION");
    }
}
