/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDto;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.agent.TurEvalDatasetRow;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository;
import com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository;
import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation.ExpectedFilter;
import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation.ExpectedRange;

/**
 * T601 / §XXXIII.16 — the NL→facet pack ⇄ eval-dataset migration: an imported
 * pack round-trips losslessly back into a pack, list filters to NL→facet
 * datasets only, a saved dataset delegates to the intact Block&nbsp;R scorer,
 * and non-NL→facet datasets are rejected.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurNLFacetDatasetServiceTest {

    @Mock
    private TurEvalDatasetRepository datasetRepository;
    @Mock
    private TurEvalDatasetRowRepository datasetRowRepository;
    @Mock
    private TurNLFacetEvalService evalService;

    private TurNLFacetDatasetService service() {
        return new TurNLFacetDatasetService(datasetRepository, datasetRowRepository, evalService);
    }

    private static TurNLFacetEvalPack samplePack() {
        TurNLFacetField price = new TurNLFacetField("price", TurSEFieldType.DOUBLE, false, "unit price");
        TurNLFacetField mode = new TurNLFacetField("mode", TurSEFieldType.TEXT, true, "delivery mode");
        TurNLFacetExpectation expect = new TurNLFacetExpectation(
                List.of(new ExpectedFilter("mode", "online")),
                List.of(new ExpectedRange("price", null, null, 20000d, null)),
                null);
        return new TurNLFacetEvalPack("catalog-pack", "products", "pt_BR",
                List.of(price, mode),
                List.of(new TurNLFacetEvalCase("cheap online", "online under 20k", expect)));
    }

    @Test
    void importPersistsNlFacetDatasetWithOneRowPerCase() {
        when(datasetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TurEvalDatasetDto dto = service().importPack(samplePack());

        assertThat(dto.name()).isEqualTo("catalog-pack");
        assertThat(dto.metadataJson()).contains("\"kind\":\"nl-facet\"")
                .contains("products").contains("pt_BR").contains("price");
        assertThat(dto.rows()).hasSize(1);
        assertThat(dto.rows().get(0).name()).isEqualTo("cheap online");
        assertThat(dto.rows().get(0).seedTurnsJson()).contains("online under 20k");
    }

    @Test
    void importRejectsEmptyPack() {
        assertThatThrownBy(() -> service().importPack(
                new TurNLFacetEvalPack("empty", "products", null, List.of(), List.of())))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void toPackRoundTripsLosslessly() {
        // Persist the pack, capture what would be saved, then read it back.
        TurEvalDataset[] saved = new TurEvalDataset[1];
        when(datasetRepository.save(any())).thenAnswer(inv -> {
            saved[0] = inv.getArgument(0);
            saved[0].setId("ds1");
            return saved[0];
        });
        service().importPack(samplePack());

        List<TurEvalDatasetRow> rows = List.copyOf(saved[0].getRows());
        when(datasetRepository.findById("ds1")).thenReturn(Optional.of(saved[0]));
        when(datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc("ds1")).thenReturn(rows);

        TurNLFacetEvalPack pack = service().toPack("ds1");
        assertThat(pack.index()).isEqualTo("products");
        assertThat(pack.locale()).isEqualTo("pt_BR");
        assertThat(pack.fields()).extracting(TurNLFacetField::name).containsExactly("price", "mode");
        assertThat(pack.cases()).hasSize(1);
        TurNLFacetEvalCase c = pack.cases().get(0);
        assertThat(c.name()).isEqualTo("cheap online");
        assertThat(c.query()).isEqualTo("online under 20k");
        assertThat(c.expect().filters()).extracting(ExpectedFilter::field).containsExactly("mode");
        assertThat(c.expect().ranges()).extracting(ExpectedRange::lte).containsExactly(20000d);
    }

    @Test
    void listReturnsOnlyNlFacetDatasets() {
        TurEvalDataset nlFacet = new TurEvalDataset();
        nlFacet.setId("a");
        nlFacet.setName("nl");
        nlFacet.setMetadataJson("{\"kind\":\"nl-facet\",\"index\":\"products\"}");
        TurEvalDataset chatFlow = new TurEvalDataset();
        chatFlow.setId("b");
        chatFlow.setName("chat");
        when(datasetRepository.findByOrderByNameAsc()).thenReturn(List.of(nlFacet, chatFlow));
        when(datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc("a")).thenReturn(List.of());

        List<TurEvalDatasetDto> list = service().list();
        assertThat(list).extracting(TurEvalDatasetDto::id).containsExactly("a");
    }

    @Test
    void runDelegatesToBlockRScorer() {
        TurEvalDataset nlFacet = new TurEvalDataset();
        nlFacet.setId("ds1");
        nlFacet.setName("nl");
        nlFacet.setMetadataJson("{\"kind\":\"nl-facet\",\"index\":\"products\",\"schema\":[]}");
        when(datasetRepository.findById("ds1")).thenReturn(Optional.of(nlFacet));
        when(datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc("ds1")).thenReturn(List.of());
        when(evalService.run(any())).thenReturn(
                new TurNLFacetEvalReport("nl", true, 0, 0, 1d, List.of(), null));

        TurNLFacetEvalReport report = service().run("ds1");
        assertThat(report.passed()).isTrue();
        verify(evalService).run(any(TurNLFacetEvalPack.class));
    }

    @Test
    void rejectsNonNlFacetDataset() {
        TurEvalDataset chatFlow = new TurEvalDataset();
        chatFlow.setId("b");
        when(datasetRepository.findById("b")).thenReturn(Optional.of(chatFlow));

        assertThatThrownBy(() -> service().toPack("b"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
