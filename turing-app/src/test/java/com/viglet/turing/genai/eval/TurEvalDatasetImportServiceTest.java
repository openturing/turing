/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.eval.TurEvalDatasetImportService.Format;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalDatasetRow;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository;

/**
 * T596 / §XXXIII.11 — dataset import parsing (JSON / JSONL / CSV / OpenAI-Evals),
 * column mapping, the CSV parser, and JSONL export.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurEvalDatasetImportServiceTest {

    @Mock
    private TurEvalDatasetRepository datasetRepository;
    @Mock
    private TurEvalDatasetRowRepository datasetRowRepository;

    private TurEvalDatasetImportService service() {
        when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new TurEvalDatasetImportService(datasetRepository, datasetRowRepository);
    }

    private static List<TurEvalDatasetRow> rowsOf(TurEvalDataset d) {
        return List.copyOf(d.getRows());
    }

    @Test
    void importsJsonArray() {
        String json = "[{\"turns\":[\"hi\"],\"referenceAnswer\":\"ref\","
                + "\"expectedOutcome\":\"CAPTURED\"}]";
        TurEvalDataset d = service().importDataset("ds", Format.JSON, json, Map.of());
        List<TurEvalDatasetRow> rows = rowsOf(d);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSeedTurnsJson()).contains("hi");
        assertThat(rows.get(0).getReferenceAnswer()).isEqualTo("ref");
        assertThat(rows.get(0).getExpectedOutcome()).isEqualTo(TurAgentEvalExpectedOutcome.CAPTURED);
    }

    @Test
    void importsJsonl() {
        String jsonl = "{\"turns\":[\"a\"]}\n{\"turns\":[\"b\"]}";
        assertThat(rowsOf(service().importDataset("ds", Format.JSONL, jsonl, Map.of()))).hasSize(2);
    }

    @Test
    void importsCsvWithColumnMapping() {
        String csv = "question,answer\n\"Hello, world\",Hi there";
        Map<String, String> mapping = Map.of("question", "turns", "answer", "referenceAnswer");
        List<TurEvalDatasetRow> rows = rowsOf(service().importDataset("ds", Format.CSV, csv, mapping));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSeedTurnsJson()).contains("Hello, world");
        assertThat(rows.get(0).getReferenceAnswer()).isEqualTo("Hi there");
    }

    @Test
    void importsOpenAiEvals() {
        String jsonl = "{\"input\":[{\"role\":\"user\",\"content\":\"Q\"}],\"ideal\":\"A\"}";
        List<TurEvalDatasetRow> rows =
                rowsOf(service().importDataset("ds", Format.OPENAI_EVALS, jsonl, Map.of()));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSeedTurnsJson()).contains("Q");
        assertThat(rows.get(0).getReferenceAnswer()).isEqualTo("A");
    }

    @Test
    void csvParserHandlesQuotedCommasAndEscapedQuotes() {
        List<String[]> table = TurEvalDatasetImportService.parseCsv("a,b\n\"x,y\",\"he said \"\"hi\"\"\"");
        assertThat(table).hasSize(2);
        assertThat(table.get(1)).containsExactly("x,y", "he said \"hi\"");
    }

    @Test
    void exportJsonlEmitsOneLinePerRow() {
        TurEvalDatasetRow row = new TurEvalDatasetRow();
        row.setSeedTurnsJson("[\"hi\"]");
        row.setReferenceAnswer("ref");
        row.setExpectedOutcome(TurAgentEvalExpectedOutcome.ANY);
        when(datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc("d1"))
                .thenReturn(List.of(row));

        String jsonl = new TurEvalDatasetImportService(datasetRepository, datasetRowRepository)
                .exportJsonl("d1");

        assertThat(jsonl.strip().split("\\R")).hasSize(1);
        assertThat(jsonl).contains("\"referenceAnswer\":\"ref\"").contains("\"turns\":[\"hi\"]");
    }
}
