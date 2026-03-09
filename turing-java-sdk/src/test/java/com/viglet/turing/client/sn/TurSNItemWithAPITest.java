package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurSNItemWithAPITest {

    @Test
    void shouldReturnEmptyOptionalsWhenApiUrlIsNull() {
        TurSNItemWithAPI item = new TurSNItemWithAPI();

        assertThat(item.getApiURL()).isEmpty();
        assertThat(item.getQueryParams()).isEmpty();
    }

    @Test
    void shouldParseQueryParametersIncludingRepeatedKeys() {
        TurSNItemWithAPI item = new TurSNItemWithAPI();
        item.setApiURL("http://localhost/search?q=ai&fq=type:doc&fq=lang:en");

        TurSNQueryParamMap queryParams = item.getQueryParams().orElseThrow();

        assertThat(queryParams.get("q")).containsExactly("ai");
        assertThat(queryParams.get("fq")).containsExactly("type:doc", "lang:en");
    }
}
