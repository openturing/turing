package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNSiteSearchQueryContextQueryTest {

    @Test
    void shouldStoreQueryStringAndSort() {
        TurSNSiteSearchQueryContextQuery queryContextQuery = new TurSNSiteSearchQueryContextQuery();
        queryContextQuery.setQueryString("enterprise search");
        queryContextQuery.setSort("newest");

        assertThat(queryContextQuery.getQueryString()).isEqualTo("enterprise search");
        assertThat(queryContextQuery.getSort()).isEqualTo("newest");
    }
}
