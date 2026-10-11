package com.viglet.turing.lucene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSESynonymRule;
import com.viglet.turing.plugins.se.lucene.TurLuceneSynonymMapBuilder;
import com.viglet.turing.plugins.se.lucene.TurLuceneSynonymRegistry;

/**
 * T1066 — multi-word synonym terms expand at query time on the embedded Lucene
 * engine, in both directions of a REGULAR group.
 */
@ExtendWith(MockitoExtension.class)
class TurLuceneMultiWordSynonymTest {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    @Mock
    private TurSNSiteFieldExtRepository fieldExtRepository;
    @Mock
    private TurSNSiteLocaleRepository localeRepository;

    private final TurSNSite site = new TurSNSite();
    private TurLuceneQueryBuilder queryBuilder;

    @BeforeEach
    void setUp() {
        TurLuceneSynonymRegistry registry = new TurLuceneSynonymRegistry();
        registry.put("jobs_pt", TurLuceneSynonymMapBuilder.build(List.of(
                new TurSESynonymRule(TurSNSynonymType.REGULAR, null, List.of("home office", "remoto")),
                new TurSESynonymRule(TurSNSynonymType.REGULAR, null, List.of("carteira assinada", "clt")),
                new TurSESynonymRule(TurSNSynonymType.REGULAR, null, List.of("programador", "desenvolvedor"))))
                .map());
        queryBuilder = new TurLuceneQueryBuilder(fieldExtRepository, null, localeRepository, registry, null);

        when(fieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(
                TurSNSiteFieldExt.builder().name("title").type(TurSEFieldType.TEXT).build(),
                TurSNSiteFieldExt.builder().name("text").type(TurSEFieldType.TEXT).build()));
        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setLanguage(PT_BR);
        locale.setCore("jobs_pt");
        when(localeRepository.findByTurSNSite(any())).thenReturn(List.of(locale));
    }

    private static Document job(String id, String title, String text) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("text", text, Field.Store.YES));
        return doc;
    }

    private List<String> search(String q) throws Exception {
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ(q);
        Query query = queryBuilder.buildQuery(site,
                new TurSEParameters(params, new TurSNSitePostParamsBean()), PT_BR);
        try (Directory dir = new ByteBuffersDirectory()) {
            try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
                writer.addDocument(job("remote", "Desenvolvedor Java", "Vaga remoto para time de plataforma"));
                writer.addDocument(job("hybrid", "Analista de dados", "Modelo home office duas vezes por semana"));
                writer.addDocument(job("clt", "Assistente administrativo", "Contrato clt com benefícios"));
                writer.addDocument(job("loose", "Escritório central", "Atendimento no office da home page"));
            }
            try (DirectoryReader reader = DirectoryReader.open(dir)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                List<String> ids = new ArrayList<>();
                for (ScoreDoc hit : searcher.search(query, 10).scoreDocs) {
                    ids.add(searcher.storedFields().document(hit.doc).get("id"));
                }
                return ids;
            }
        }
    }

    @Test
    void multiWordTermFindsItsSingleWordSynonym() throws Exception {
        assertThat(search("home office")).contains("remote", "hybrid").doesNotContain("clt");
    }

    @Test
    void singleWordTermFindsItsMultiWordSynonymAsAPhrase() throws Exception {
        // "home" and "office" appear apart in "loose": the expansion is a phrase, not loose words.
        assertThat(search("remoto")).containsExactlyInAnyOrder("remote", "hybrid");
    }

    @Test
    void secondMultiWordGroupExpandsToo() throws Exception {
        assertThat(search("carteira assinada")).containsExactly("clt");
    }

    @Test
    void singleWordGroupsKeepWorking() throws Exception {
        assertThat(search("programador")).containsExactly("remote");
    }
}
