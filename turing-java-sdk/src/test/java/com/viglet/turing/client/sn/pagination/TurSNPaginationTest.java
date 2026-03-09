package com.viglet.turing.client.sn.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchPaginationBean;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;

class TurSNPaginationTest {

    @Test
    void shouldFindPagesByTypeAndNumberAndFallbacks() {
        TurSNSiteSearchPaginationBean previous = page(TurSNPaginationType.PREVIOUS, "Prev", "/p=1", 1);
        TurSNSiteSearchPaginationBean current = page(TurSNPaginationType.CURRENT, "2", "/p=2", 2);
        TurSNSiteSearchPaginationBean next = page(TurSNPaginationType.NEXT, "Next", "/p=3", 3);

        TurSNPagination pagination = new TurSNPagination(List.of(previous, current, next));

        assertThat(pagination.getAllPages()).hasSize(3);
        assertThat(pagination.getCurrentPage()).isPresent();
        assertThat(pagination.getCurrentPage().orElseThrow().getPageNumber()).isEqualTo(2);
        assertThat(pagination.getPreviousPage().orElseThrow().getPageNumber()).isEqualTo(1);
        assertThat(pagination.getNextPage().orElseThrow().getPageNumber()).isEqualTo(3);
        assertThat(pagination.getLastPage().orElseThrow().getPageNumber()).isEqualTo(2);
        assertThat(pagination.getFirstPage().orElseThrow().getPageNumber()).isEqualTo(2);
        assertThat(pagination.findByPageNumber(3).getLabel()).isEqualTo("Next");
        assertThat(pagination.getPageNumberList()).containsExactly(1, 2, 3);
    }

    @Test
    void shouldReturnEmptyWhenTypeIsNotFound() {
        TurSNPagination pagination = new TurSNPagination(List.of(page(TurSNPaginationType.PAGE, "1", "/p=1", 1)));

        assertThat(pagination.findByType("invalid")).isEmpty();
    }

    private TurSNSiteSearchPaginationBean page(TurSNPaginationType type, String text, String href, int pageNumber) {
        TurSNSiteSearchPaginationBean bean = new TurSNSiteSearchPaginationBean();
        bean.setType(type);
        bean.setText(text);
        bean.setHref(href);
        bean.setPage(pageNumber);
        return bean;
    }
}
