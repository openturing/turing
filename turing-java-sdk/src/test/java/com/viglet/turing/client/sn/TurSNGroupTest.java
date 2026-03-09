package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.viglet.turing.client.sn.pagination.TurSNPagination;

class TurSNGroupTest {

    @Test
    void shouldStoreAndExposeGroupMetadata() {
        TurSNGroup group = new TurSNGroup();
        TurSNDocumentList results = new TurSNDocumentList();
        TurSNPagination pagination = new TurSNPagination(Collections.emptyList());

        group.setName("news");
        group.setCount(10);
        group.setPage(2);
        group.setPageCount(4);
        group.setPageStart(6);
        group.setPageEnd(10);
        group.setLimit(5);
        group.setResults(results);
        group.setPagination(pagination);

        assertThat(group.getName()).isEqualTo("news");
        assertThat(group.getCount()).isEqualTo(10);
        assertThat(group.getPage()).isEqualTo(2);
        assertThat(group.getPageCount()).isEqualTo(4);
        assertThat(group.getPageStart()).isEqualTo(6);
        assertThat(group.getPageEnd()).isEqualTo(10);
        assertThat(group.getLimit()).isEqualTo(5);
        assertThat(group.getResults()).isSameAs(results);
        assertThat(group.getPagination()).isSameAs(pagination);
    }
}
