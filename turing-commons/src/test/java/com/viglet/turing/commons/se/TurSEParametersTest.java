package com.viglet.turing.commons.se;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;

class TurSEParametersTest {

    @Test
    void shouldMapSearchParamsWithoutPostOverrides() {
        TurSNSearchParams search = new TurSNSearchParams();
        search.setQ("cloud");
        search.setFq(List.of("type:article"));
        search.setFqAnd(List.of("lang:en"));
        search.setFqOr(List.of("tag:ai"));
        search.setFqOp(TurSNFilterQueryOperator.AND);
        search.setP(2);
        search.setSort("newest");
        search.setRows(20);
        search.setGroup("type");
        search.setNfpr(0);
        search.setFl(List.of("title", "url"));

        TurSEParameters parameters = new TurSEParameters(search);

        assertThat(parameters.getQuery()).isEqualTo("cloud");
        assertThat(parameters.getCurrentPage()).isEqualTo(2);
        assertThat(parameters.getSort()).isEqualTo("newest");
        assertThat(parameters.getRows()).isEqualTo(20);
        assertThat(parameters.getGroup()).isEqualTo("type");
        assertThat(parameters.getAutoCorrectionDisabled()).isZero();
        assertThat(parameters.getFieldList()).containsExactly("title", "url");
        assertThat(parameters.getTurSNFilterParams().getDefaultValues()).containsExactly("type:article");
        assertThat(parameters.getTurSNFilterParams().getAnd()).containsExactly("lang:en");
        assertThat(parameters.getTurSNFilterParams().getOr()).containsExactly("tag:ai");
        assertThat(parameters.getTurSNFilterParams().getOperator()).isEqualTo(TurSNFilterQueryOperator.AND);
    }

    @Test
    void shouldOverrideFromPostParamsWhenPresent() {
        TurSNSearchParams search = new TurSNSearchParams();
        search.setQ("base");
        search.setSort("relevance");
        search.setRows(10);

        TurSNSitePostParamsBean post = new TurSNSitePostParamsBean();
        post.setQuery("override");
        post.setSort("oldest");
        post.setRows(5);
        post.setPage(4);
        post.setGroup("site");
        post.setFieldList(List.of("id"));
        post.setFq(List.of("type:news"));
        post.setFqAnd(List.of("locale:en"));
        post.setFqOr(List.of("tag:ml"));
        post.setFqOperator(TurSNFilterQueryOperator.OR);

        TurSEParameters parameters = new TurSEParameters(search, post);

        assertThat(parameters.getQuery()).isEqualTo("override");
        assertThat(parameters.getSort()).isEqualTo("oldest");
        assertThat(parameters.getRows()).isEqualTo(5);
        assertThat(parameters.getCurrentPage()).isEqualTo(4);
        assertThat(parameters.getGroup()).isEqualTo("site");
        assertThat(parameters.getFieldList()).containsExactly("id");
        assertThat(parameters.getTurSNFilterParams().getDefaultValues()).containsExactly("type:news");
        assertThat(parameters.getTurSNFilterParams().getAnd()).containsExactly("locale:en");
        assertThat(parameters.getTurSNFilterParams().getOr()).containsExactly("tag:ml");
        assertThat(parameters.getTurSNFilterParams().getOperator()).isEqualTo(TurSNFilterQueryOperator.OR);
    }
}
