package com.viglet.turing.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.kb.TurThesaurusDraft;
import com.viglet.turing.persistence.dto.kb.TurThesaurusDraft.TurThesaurusDraftTerm;
import com.viglet.turing.persistence.dto.kb.TurThesaurusDraft.TurThesaurusDraftVariation;
import com.viglet.turing.persistence.dto.kb.TurThesaurusGenerationRequest;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBase;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurThesaurusRelationType;
import com.viglet.turing.persistence.model.kb.TurThesaurusTerm;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;
import com.viglet.turing.sn.kb.TurMicrothesaurusDictionaryService;

/**
 * T675 / §XL (Block AQ) — LLM-assisted generation orchestration: the draft is
 * sanitised (dangling / self references dropped), generation never persists, and
 * an accepted draft round-trips through the shipped T673 authority-file importer
 * so its BT hierarchy / RT relations / variations land correctly in the domain.
 */
@ExtendWith(MockitoExtension.class)
class TurThesaurusGenerationServiceTest {

    @Mock
    private TurMicrothesaurusRepository microthesaurusRepository;
    @Mock
    private TurMicrothesaurusDictionaryService dictionaryService;

    private TurThesaurusHierarchyGenerator generator;
    private TurThesaurusGenerationService service;

    private TurThesaurusDraft nextDraft;

    @BeforeEach
    void setUp() {
        generator = request -> nextDraft;
        TurAuthorityFileImportService importService =
                new TurAuthorityFileImportService(microthesaurusRepository, dictionaryService);
        service = new TurThesaurusGenerationService(generator, importService);
    }

    private TurThesaurusDraftTerm term(String id, String label, String broader, List<String> related,
            List<TurThesaurusDraftVariation> variations) {
        return new TurThesaurusDraftTerm(id, label, null, broader, related, variations);
    }

    private TurThesaurusDraft educationDraft() {
        return new TurThesaurusDraft("Doenças", null, "pt", "MEDICINE", List.of(
                term("t1", "Doença", null, List.of(), List.of()),
                term("t2", "Doença respiratória", "t1", List.of(), List.of()),
                term("t3", "Pneumonia", "t2", List.of("t4"),
                        List.of(new TurThesaurusDraftVariation("pneumonias", false, false))),
                term("t4", "Gripe", "t1", List.of(), List.of())));
    }

    @Test
    void generateRejectsBlankDomainOrLanguage() {
        assertThatThrownBy(() -> service.generate(
                new TurThesaurusGenerationRequest("  ", "pt", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.generate(
                new TurThesaurusGenerationRequest("MEDICINE", "  ", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateSanitizesDanglingAndSelfReferences() {
        nextDraft = new TurThesaurusDraft("X", null, "pt", "GENERAL", List.of(
                term("t1", "Root", null, List.of(), List.of()),
                // broader points to a missing id → dropped; related has a dangling + self ref
                term("t2", "Child", "ghost", List.of("t1", "missing", "t2"), List.of())));

        TurThesaurusDraft result = service.generate(
                new TurThesaurusGenerationRequest("GENERAL", "pt", null, null));

        TurThesaurusDraftTerm child = result.terms().stream()
                .filter(t -> t.id().equals("t2")).findFirst().orElseThrow();
        assertThat(child.broader()).isNull();
        assertThat(child.related()).containsExactly("t1");
    }

    @Test
    void generateDropsTermsWithoutLabelAndNeverPersists() {
        nextDraft = new TurThesaurusDraft("X", null, "pt", "GENERAL", List.of(
                term("t1", "Kept", null, List.of(), List.of()),
                term("t2", "  ", null, List.of(), List.of())));

        TurThesaurusDraft result = service.generate(
                new TurThesaurusGenerationRequest("GENERAL", "pt", null, null));

        assertThat(result.terms()).extracting(TurThesaurusDraftTerm::id).containsExactly("t1");
        verify(microthesaurusRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void materializeRoundTripsThroughAuthorityFileImporter() {
        nextDraft = educationDraft();
        when(microthesaurusRepository.save(any(TurMicrothesaurus.class)))
                .thenAnswer(i -> i.getArgument(0));

        TurKnowledgeBase kb = new TurKnowledgeBase();
        kb.setId("kb1");
        service.materialize(kb, educationDraft());

        ArgumentCaptor<TurMicrothesaurus> captor = ArgumentCaptor.forClass(TurMicrothesaurus.class);
        verify(microthesaurusRepository).save(captor.capture());
        TurMicrothesaurus saved = captor.getValue();

        assertThat(saved.getLanguage()).isEqualTo(Locale.forLanguageTag("pt"));
        assertThat(saved.getDomain()).isEqualTo("MEDICINE");
        assertThat(saved.getTurThesaurusTerms()).hasSize(4);

        TurThesaurusTerm doenca = byExternalId(saved, "t1");
        TurThesaurusTerm respiratoria = byExternalId(saved, "t2");
        TurThesaurusTerm pneumonia = byExternalId(saved, "t3");
        TurThesaurusTerm gripe = byExternalId(saved, "t4");

        // BT spine: broader → parentTermId
        assertThat(respiratoria.getParentTermId()).isEqualTo(doenca.getId());
        assertThat(pneumonia.getParentTermId()).isEqualTo(respiratoria.getId());
        assertThat(gripe.getParentTermId()).isEqualTo(doenca.getId());

        // RT: Pneumonia --RELATED--> Gripe
        assertThat(pneumonia.getRelations()).anySatisfy(rel -> {
            assertThat(rel.getType()).isEqualTo(TurThesaurusRelationType.RELATED);
            assertThat(rel.getTargetTermId()).isEqualTo(gripe.getId());
        });

        // Variations: the label is always emitted, plus the explicit one.
        assertThat(pneumonia.getVariations()).extracting(v -> v.getSurfaceForm())
                .contains("Pneumonia", "pneumonias");

        verify(dictionaryService).evictAll();
    }

    private TurThesaurusTerm byExternalId(TurMicrothesaurus microthesaurus, String externalId) {
        return microthesaurus.getTurThesaurusTerms().stream()
                .filter(t -> externalId.equals(t.getExternalId()))
                .findFirst().orElseThrow(() -> new AssertionError("no term with externalId " + externalId));
    }
}
