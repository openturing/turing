package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;

class TurSNFilterParamsTest {

    @Test
    void shouldProvideBuilderDefaultsAndAllowMutations() {
        TurSNFilterParams defaults = TurSNFilterParams.builder().build();
        assertThat(defaults.getDefaultValues()).isEmpty();
        assertThat(defaults.getAnd()).isEmpty();
        assertThat(defaults.getOr()).isEmpty();

        TurSNFilterParams params = TurSNFilterParams.builder()
                .defaultValues(List.of("type:doc"))
                .and(List.of("lang:en"))
                .or(List.of("tag:ml"))
                .operator(TurSNFilterQueryOperator.AND)
                .itemOperator(TurSNFilterQueryOperator.OR)
                .build();

        assertThat(params.getDefaultValues()).containsExactly("type:doc");
        assertThat(params.getAnd()).containsExactly("lang:en");
        assertThat(params.getOr()).containsExactly("tag:ml");
        assertThat(params.getOperator()).isEqualTo(TurSNFilterQueryOperator.AND);
        assertThat(params.getItemOperator()).isEqualTo(TurSNFilterQueryOperator.OR);
    }
}
