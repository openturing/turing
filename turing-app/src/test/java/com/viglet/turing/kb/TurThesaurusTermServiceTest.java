package com.viglet.turing.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.kb.TurThesaurusTermDto;
import com.viglet.turing.persistence.dto.kb.TurThesaurusTermRelationDto;
import com.viglet.turing.persistence.dto.kb.TurThesaurusTermVariationDto;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurThesaurusRelationType;
import com.viglet.turing.persistence.model.kb.TurThesaurusTerm;
import com.viglet.turing.persistence.repository.kb.TurThesaurusTermRepository;
import com.viglet.turing.sn.kb.TurMicrothesaurusDictionaryService;

/**
 * T669 / §XL (Block AQ) — unit tests for the thesaurus term CRUD service: label
 * required, blank variations skipped, relations require a type + target, and the
 * created relations point back at the owning term.
 */
@ExtendWith(MockitoExtension.class)
class TurThesaurusTermServiceTest {

    @Mock
    private TurThesaurusTermRepository repository;

    @Mock
    private TurMicrothesaurusDictionaryService dictionaryService;

    @InjectMocks
    private TurThesaurusTermService service;

    private final TurMicrothesaurus mt = new TurMicrothesaurus();

    private TurThesaurusTermDto dto(String label, List<TurThesaurusTermVariationDto> variations,
            List<TurThesaurusTermRelationDto> relations) {
        return new TurThesaurusTermDto(null, label, true, 0, null, null, null, null,
                variations, relations);
    }

    @Test
    void createRejectsBlankLabel() {
        assertThatThrownBy(() -> service.create(mt, dto(" ", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label is required");
        verify(repository, never()).save(any());
    }

    @Test
    void createSkipsBlankVariationsAndMapsGoodOnes() {
        when(repository.save(any(TurThesaurusTerm.class))).thenAnswer(i -> i.getArgument(0));

        TurThesaurusTermDto result = service.create(mt, dto("Pneumonia", List.of(
                new TurThesaurusTermVariationDto("pneumonia", 100.0, false, true,
                        Locale.forLanguageTag("pt")),
                new TurThesaurusTermVariationDto("  ", 100.0, false, true, null)),
                null));

        assertThat(result.variations()).hasSize(1);
        assertThat(result.variations().get(0).surfaceForm()).isEqualTo("pneumonia");
    }

    @Test
    void createRejectsRelationWithoutTarget() {
        assertThatThrownBy(() -> service.create(mt, dto("Doença", null, List.of(
                new TurThesaurusTermRelationDto(null, TurThesaurusRelationType.RELATED, null, " ")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type and a target");
    }

    @Test
    void createWiresRelationBackToOwningTerm() {
        when(repository.save(any(TurThesaurusTerm.class))).thenAnswer(i -> i.getArgument(0));

        service.create(mt, dto("Doença respiratória", null, List.of(
                new TurThesaurusTermRelationDto(null, TurThesaurusRelationType.RELATED, null,
                        "target-id"))));

        // The saved term owns a relation pointing at the target and back at itself.
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(term ->
                term.getRelations().size() == 1
                        && term.getRelations().iterator().next().getTurThesaurusTerm() == term
                        && "target-id".equals(
                                term.getRelations().iterator().next().getTargetTermId())));
    }

    @Test
    void listRootsDelegatesToTheRootFinder() {
        when(repository.findByTurMicrothesaurusAndParentTermIdIsNull(mt)).thenReturn(List.of());
        assertThat(service.listRoots(mt)).isEmpty();
        verify(repository).findByTurMicrothesaurusAndParentTermIdIsNull(mt);
    }
}
