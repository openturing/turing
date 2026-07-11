package com.viglet.turing.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.kb.TurKnowledgeBase;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurThesaurusRelationType;
import com.viglet.turing.persistence.model.kb.TurThesaurusTerm;
import com.viglet.turing.persistence.model.kb.TurThesaurusTermRelation;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;
import com.viglet.turing.sn.kb.TurMicrothesaurusDictionaryService;

/**
 * T673 / §XL (Block AQ) — the authority-file importer: BT/NT → parent spine,
 * RT → one deduped RELATED edge, U/UF → USE/USED_FOR, variations → surface forms,
 * ISO 639-2/B language mapping, and fidelity against the real 253-term sample.
 * Also asserts the reconstructed XSD accepts the real sample (contract fidelity).
 */
@ExtendWith(MockitoExtension.class)
class TurAuthorityFileImportServiceTest {

    @Mock
    private TurMicrothesaurusRepository microthesaurusRepository;
    @Mock
    private TurMicrothesaurusDictionaryService dictionaryService;

    @InjectMocks
    private TurAuthorityFileImportService service;

    private final TurKnowledgeBase kb = new TurKnowledgeBase();

    private static final String XML = """
            <authorityFile xmlns="https://turing.viglet.org/xsd/thesaurus/1.0">
              <name>APICULTURA</name>
              <terms>
                <term><id>1</id><name>Doença</name><enabled>true</enabled>
                  <variations><variation><name>Doença</name><weight>100.0</weight>
                    <case>ci</case><accent>as</accent>
                    <languages><language>por</language></languages></variation></variations>
                  <relations><relation><id>2</id><type>NT</type></relation></relations>
                </term>
                <term><id>2</id><name>Pneumonia</name><enabled>true</enabled>
                  <relations>
                    <relation><id>1</id><type>BT</type></relation>
                    <relation><id>3</id><type>RT</type></relation>
                    <relation><id>2</id><type>RT</type></relation>
                  </relations>
                </term>
                <term><id>3</id><name>Gripe</name><enabled>true</enabled>
                  <relations>
                    <relation><id>2</id><type>RT</type></relation>
                    <relation><id>4</id><type>U</type></relation>
                  </relations>
                </term>
                <term><id>4</id><name>Influenza</name><enabled>true</enabled>
                  <relations><relation><id>3</id><type>UF</type></relation></relations>
                </term>
              </terms>
            </authorityFile>
            """;

    private TurMicrothesaurus doImport(String xml) {
        when(microthesaurusRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<TurMicrothesaurus> captor = ArgumentCaptor.forClass(TurMicrothesaurus.class);
        service.importAuthorityFile(kb, xml.getBytes(StandardCharsets.UTF_8), "EDUCATION");
        verify(microthesaurusRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void mapsNameDomainAndLanguage() {
        TurMicrothesaurus m = doImport(XML);
        assertThat(m.getName()).isEqualTo("APICULTURA");
        assertThat(m.getDomain()).isEqualTo("EDUCATION");
        assertThat(m.getLanguage()).isEqualTo(Locale.forLanguageTag("pt"));
    }

    @Test
    void wiresBroaderNarrowerOntoParentSpine() {
        TurMicrothesaurus m = doImport(XML);
        Map<String, TurThesaurusTerm> byLabel = m.getTurThesaurusTerms().stream()
                .collect(Collectors.toMap(TurThesaurusTerm::getLabel, Function.identity()));
        TurThesaurusTerm doenca = byLabel.get("Doença");
        TurThesaurusTerm pneumonia = byLabel.get("Pneumonia");
        assertThat(doenca.getParentTermId()).isNull();
        assertThat(pneumonia.getParentTermId()).isEqualTo(doenca.getId());
    }

    @Test
    void deduplicatesSymmetricRelatedAndSkipsSelf() {
        TurMicrothesaurus m = doImport(XML);
        long relatedCount = m.getTurThesaurusTerms().stream()
                .flatMap(t -> t.getRelations().stream())
                .filter(r -> r.getType() == TurThesaurusRelationType.RELATED)
                .count();
        // term2 RT->3, term3 RT->2 (reciprocal), term2 RT->2 (self, skipped) => one edge.
        assertThat(relatedCount).isEqualTo(1);
    }

    @Test
    void mapsUseAndUsedFor() {
        TurMicrothesaurus m = doImport(XML);
        Set<TurThesaurusRelationType> types = m.getTurThesaurusTerms().stream()
                .flatMap(t -> t.getRelations().stream())
                .map(TurThesaurusTermRelation::getType).collect(Collectors.toSet());
        assertThat(types).contains(TurThesaurusRelationType.USE, TurThesaurusRelationType.USED_FOR);
    }

    @Test
    void variationCaseAndAccentFlagsMapped() {
        TurMicrothesaurus m = doImport(XML);
        TurThesaurusTerm doenca = m.getTurThesaurusTerms().stream()
                .filter(t -> t.getLabel().equals("Doença")).findFirst().orElseThrow();
        assertThat(doenca.getVariations()).hasSize(1);
        assertThat(doenca.getVariations().get(0).isCaseSensitive()).isFalse(); // ci
        assertThat(doenca.getVariations().get(0).isAccentSensitive()).isTrue(); // as
    }

    @Test
    void rejectsNonAuthorityFileXml() {
        assertThatThrownBy(() -> service.importAuthorityFile(kb,
                "<foo/>".getBytes(StandardCharsets.UTF_8), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importsRealSampleWithAllTerms() throws Exception {
        byte[] xml = getClass().getResourceAsStream("/kb/authority-file-sample.xml").readAllBytes();
        TurMicrothesaurus m = doImport(new String(xml, StandardCharsets.UTF_8));
        assertThat(m.getTurThesaurusTerms()).hasSize(253);
        assertThat(m.getName()).isEqualTo("APICULTURA");
        assertThat(m.getLanguage()).isEqualTo(Locale.forLanguageTag("pt"));
    }

    @Test
    void reconstructedXsdAcceptsTheRealSample() throws Exception {
        var schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(getClass().getResource("/kb/turing-thesaurus-1.0.xsd"));
        try (var in = getClass().getResourceAsStream("/kb/authority-file-sample.xml")) {
            schema.newValidator().validate(new javax.xml.transform.stream.StreamSource(in));
        }
        // No exception => the reconstructed contract validates the real export.
    }
}
