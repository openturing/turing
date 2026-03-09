package com.viglet.turing.client.sn.autocomplete;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNAutoCompleteQueryTest {

    @Test
    void shouldStoreQueryAndRows() {
        TurSNAutoCompleteQuery query = new TurSNAutoCompleteQuery();
        query.setQuery("mach");
        query.setRows(7);

        assertThat(query.getQuery()).isEqualTo("mach");
        assertThat(query.getRows()).isEqualTo(7);
    }
}
