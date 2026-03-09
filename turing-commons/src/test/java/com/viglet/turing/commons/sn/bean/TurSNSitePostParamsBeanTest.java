package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;

class TurSNSitePostParamsBeanTest {

    @Test
    void shouldExposeDefaultsAndMutableCollections() {
        TurSNSitePostParamsBean bean = new TurSNSitePostParamsBean();

        assertThat(bean.isPopulateMetrics()).isTrue();
        assertThat(bean.isDisableAutoComplete()).isFalse();
        assertThat(bean.getTargetingRules()).isEmpty();
        assertThat(bean.getTargetingRulesWithCondition()).isEmpty();
        assertThat(bean.getTargetingRulesWithConditionAND()).isEmpty();
        assertThat(bean.getTargetingRulesWithConditionOR()).isEmpty();

        bean.setUserId("u-1");
        bean.setSort("newest");
        bean.setQuery("ai");
        bean.setFq(List.of("type:article"));
        bean.setFqAnd(List.of("lang:en"));
        bean.setFqOr(List.of("tag:ml"));
        bean.setFqOperator(TurSNFilterQueryOperator.AND);
        bean.setFqItemOperator(TurSNFilterQueryOperator.OR);
        bean.setPage(2);
        bean.setRows(15);
        bean.setGroup("type");
        bean.setLocale("en-US");
        bean.setFieldList(List.of("title", "url"));
        bean.setDisableAutoComplete(true);
        bean.setTargetingRules(List.of("premium"));
        bean.setTargetingRulesWithCondition(Map.of("country", List.of("us")));
        bean.setTargetingRulesWithConditionAND(Map.of("role", List.of("admin")));
        bean.setTargetingRulesWithConditionOR(Map.of("segment", List.of("a")));

        assertThat(bean.getUserId()).isEqualTo("u-1");
        assertThat(bean.getSort()).isEqualTo("newest");
        assertThat(bean.getQuery()).isEqualTo("ai");
        assertThat(bean.getFq()).containsExactly("type:article");
        assertThat(bean.getFqAnd()).containsExactly("lang:en");
        assertThat(bean.getFqOr()).containsExactly("tag:ml");
        assertThat(bean.getFqOperator()).isEqualTo(TurSNFilterQueryOperator.AND);
        assertThat(bean.getFqItemOperator()).isEqualTo(TurSNFilterQueryOperator.OR);
        assertThat(bean.getPage()).isEqualTo(2);
        assertThat(bean.getRows()).isEqualTo(15);
        assertThat(bean.getGroup()).isEqualTo("type");
        assertThat(bean.getLocale()).isEqualTo("en-US");
        assertThat(bean.getFieldList()).containsExactly("title", "url");
        assertThat(bean.isDisableAutoComplete()).isTrue();
        assertThat(bean.getTargetingRules()).containsExactly("premium");
    }
}
