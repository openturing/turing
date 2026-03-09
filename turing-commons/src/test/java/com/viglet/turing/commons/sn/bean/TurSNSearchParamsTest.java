package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;

class TurSNSearchParamsTest {

    @Test
    void shouldExposeDefaultsAndSupportOverrides() {
        TurSNSearchParams params = new TurSNSearchParams();

        assertThat(params.getQ()).isEqualTo("*");
        assertThat(params.getP()).isEqualTo(1);
        assertThat(params.getSort()).isEqualTo("relevance");
        assertThat(params.getRows()).isEqualTo(-1);
        assertThat(params.getFqOp()).isEqualTo(TurSNFilterQueryOperator.NONE);
        assertThat(params.getFqiOp()).isEqualTo(TurSNFilterQueryOperator.NONE);
        assertThat(params.getNfpr()).isEqualTo(1);

        params.setQ("cloud");
        params.setP(3);
        params.setFq(List.of("type:article"));
        params.setFqAnd(List.of("lang:en"));
        params.setFqOr(List.of("tag:ai"));
        params.setFqOp(TurSNFilterQueryOperator.AND);
        params.setFqiOp(TurSNFilterQueryOperator.OR);
        params.setSort("newest");
        params.setRows(20);
        params.setLocale(Locale.US);
        params.setFl(List.of("title", "url"));
        params.setGroup("type");
        params.setNfpr(0);

        assertThat(params.getQ()).isEqualTo("cloud");
        assertThat(params.getP()).isEqualTo(3);
        assertThat(params.getFq()).containsExactly("type:article");
        assertThat(params.getFqAnd()).containsExactly("lang:en");
        assertThat(params.getFqOr()).containsExactly("tag:ai");
        assertThat(params.getFqOp()).isEqualTo(TurSNFilterQueryOperator.AND);
        assertThat(params.getFqiOp()).isEqualTo(TurSNFilterQueryOperator.OR);
        assertThat(params.getLocale()).isEqualTo(Locale.US);
        assertThat(params.getFl()).containsExactly("title", "url");
        assertThat(params.getGroup()).isEqualTo("type");
        assertThat(params.getNfpr()).isZero();
        assertThat(params.toString()).contains("cloud").contains("newest");
    }
}
