package com.viglet.turing.persistence.model.sn.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;

@ExtendWith(MockitoExtension.class)
class TurSNRankingExpressionTest {

    @Mock
    private TurSNSite turSNSite;

    private TurSNRankingExpression expression;

    @BeforeEach
    void setUp() {
        expression = new TurSNRankingExpression();
    }

    @Test
    void shouldSetAndGetAllProperties() {
        expression.setId("exp-id");
        expression.setName("Boost title");
        expression.setDescription("Increase relevance for title matches");
        expression.setWeight(1.5f);
        expression.setTurSNSite(turSNSite);

        assertThat(expression.getId()).isEqualTo("exp-id");
        assertThat(expression.getName()).isEqualTo("Boost title");
        assertThat(expression.getDescription()).isEqualTo("Increase relevance for title matches");
        assertThat(expression.getWeight()).isEqualTo(1.5f);
        assertThat(expression.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void shouldInitializeConditionsAsEmptySet() {
        assertThat(expression.getTurSNRankingConditions()).isNotNull().isEmpty();
    }

    @Test
    void shouldSetConditionsAndBackReferenceExpression() {
        TurSNRankingCondition first = new TurSNRankingCondition();
        first.setId("c1");
        TurSNRankingCondition second = new TurSNRankingCondition();
        second.setId("c2");
        Set<TurSNRankingCondition> conditions = new HashSet<>();
        conditions.add(first);
        conditions.add(second);

        expression.setTurSNRankingConditions(conditions);

        assertThat(expression.getTurSNRankingConditions()).hasSize(2);
        assertThat(first.getTurSNRankingExpression()).isSameAs(expression);
        assertThat(second.getTurSNRankingExpression()).isSameAs(expression);
    }

    @Test
    void shouldClearConditionsWhenNullIsProvided() {
        TurSNRankingCondition condition = new TurSNRankingCondition();
        Set<TurSNRankingCondition> conditions = new HashSet<>();
        conditions.add(condition);
        expression.setTurSNRankingConditions(conditions);

        expression.setTurSNRankingConditions(null);

        assertThat(expression.getTurSNRankingConditions()).isEmpty();
    }
}
