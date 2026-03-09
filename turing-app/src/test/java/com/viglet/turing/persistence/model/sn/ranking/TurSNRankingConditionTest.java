package com.viglet.turing.persistence.model.sn.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TurSNRankingConditionTest {

    private TurSNRankingCondition condition;

    @BeforeEach
    void setUp() {
        condition = new TurSNRankingCondition();
    }

    @Test
    void shouldSetAndGetAllProperties() {
        TurSNRankingExpression expression = new TurSNRankingExpression();

        condition.setId("condition-id");
        condition.setAttribute("documentType");
        condition.setCondition(2);
        condition.setValue("news");
        condition.setTurSNRankingExpression(expression);

        assertThat(condition.getId()).isEqualTo("condition-id");
        assertThat(condition.getAttribute()).isEqualTo("documentType");
        assertThat(condition.getCondition()).isEqualTo(2);
        assertThat(condition.getValue()).isEqualTo("news");
        assertThat(condition.getTurSNRankingExpression()).isEqualTo(expression);
    }

    @Test
    void shouldExposeDefaultValuesOnNoArgsConstructor() {
        assertThat(condition.getId()).isNull();
        assertThat(condition.getAttribute()).isNull();
        assertThat(condition.getCondition()).isZero();
        assertThat(condition.getValue()).isNull();
        assertThat(condition.getTurSNRankingExpression()).isNull();
        assertThat(condition.getCreatedBy()).isNull();
        assertThat(condition.getCreationDate()).isNull();
        assertThat(condition.getLastModifiedBy()).isNull();
        assertThat(condition.getLastModifiedDate()).isNull();
    }
}
