package com.viglet.turing.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.kb.TurKnowledgeBaseDto;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBase;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBaseSource;
import com.viglet.turing.persistence.repository.kb.TurKnowledgeBaseRepository;

/**
 * T669 / §XL (Block AQ) — unit tests for the Knowledge Base CRUD service over a
 * mocked repository: required-name validation, the default source, and the
 * delete-missing contract.
 */
@ExtendWith(MockitoExtension.class)
class TurKnowledgeBaseServiceTest {

    @Mock
    private TurKnowledgeBaseRepository repository;

    @InjectMocks
    private TurKnowledgeBaseService service;

    @Test
    void createRejectsBlankName() {
        assertThatThrownBy(() -> service.create(
                new TurKnowledgeBaseDto(null, "  ", "d", null, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required");
        verify(repository, never()).save(any());
    }

    @Test
    void createDefaultsSourceToUserAndPersists() {
        when(repository.save(any(TurKnowledgeBase.class))).thenAnswer(i -> i.getArgument(0));

        TurKnowledgeBaseDto result = service.create(
                new TurKnowledgeBaseDto(null, "Education KB", "desc", null, 0));

        assertThat(result.name()).isEqualTo("Education KB");
        assertThat(result.source()).isEqualTo(TurKnowledgeBaseSource.USER);
        verify(repository).save(any(TurKnowledgeBase.class));
    }

    @Test
    void createHonoursAnExplicitSource() {
        when(repository.save(any(TurKnowledgeBase.class))).thenAnswer(i -> i.getArgument(0));

        TurKnowledgeBaseDto result = service.create(new TurKnowledgeBaseDto(
                null, "Seed", null, TurKnowledgeBaseSource.SYSTEM_SEED, 0));

        assertThat(result.source()).isEqualTo(TurKnowledgeBaseSource.SYSTEM_SEED);
    }

    @Test
    void updateReturnsEmptyWhenMissing() {
        when(repository.findById("nope")).thenReturn(Optional.empty());
        assertThat(service.update("nope", new TurKnowledgeBaseDto(null, "x", null, null, 0)))
                .isEmpty();
    }

    @Test
    void deleteReturnsFalseWhenMissing() {
        when(repository.existsById("nope")).thenReturn(false);
        assertThat(service.delete("nope")).isFalse();
        verify(repository, never()).deleteById(any());
    }

    @Test
    void toDtoCountsMicrothesauri() {
        TurKnowledgeBase kb = new TurKnowledgeBase();
        kb.setName("KB");
        kb.setTurMicrothesauri(java.util.Set.of(
                new com.viglet.turing.persistence.model.kb.TurMicrothesaurus(),
                new com.viglet.turing.persistence.model.kb.TurMicrothesaurus()));
        assertThat(TurKnowledgeBaseService.toDto(kb).microthesaurusCount()).isEqualTo(2);
    }
}
