package com.viglet.turing.persistence.mapper.sn.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.dto.sn.ranking.TurSNRankingConditionDto;
import com.viglet.turing.persistence.dto.sn.ranking.TurSNRankingExpressionDto;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;

/**
 * Tests for ranking mapper implementations (Expression and Condition).
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurSNRankingMappersTest {

    // ---- TurSNRankingExpressionMapper ----------------------------------------

    @Nested
    class ExpressionMapperTests {

        private TurSNRankingExpressionMapperImpl mapper;

        @BeforeEach
        void setUp() {
            mapper = new TurSNRankingExpressionMapperImpl();
        }

        private TurSNRankingExpression buildEntity() {
            TurSNRankingExpression entity = new TurSNRankingExpression();
            entity.setId("re-1");
            entity.setName("Boost Recent");
            entity.setDescription("Boost recent documents");
            entity.setWeight(1.5f);
            return entity;
        }

        @Test
        void shouldMapEntityToDto() {
            TurSNRankingExpression entity = buildEntity();

            TurSNRankingExpressionDto dto = mapper.toDto(entity);

            assertNotNull(dto);
            assertEquals("re-1", dto.getId());
            assertEquals("Boost Recent", dto.getName());
            assertEquals("Boost recent documents", dto.getDescription());
            assertEquals(1.5f, dto.getWeight());
        }

        @Test
        void shouldReturnNullWhenEntityIsNull() {
            assertNull(mapper.toDto(null));
        }

        @Test
        void shouldMapDtoToEntity() {
            TurSNRankingExpressionDto dto = new TurSNRankingExpressionDto();
            dto.setId("re-2");
            dto.setName("Penalize Old");
            dto.setDescription("Penalize old documents");
            dto.setWeight(0.5f);

            TurSNRankingExpression entity = mapper.toEntity(dto);

            assertNotNull(entity);
            assertEquals("re-2", entity.getId());
            assertEquals("Penalize Old", entity.getName());
            assertEquals(0.5f, entity.getWeight());
        }

        @Test
        void shouldReturnNullWhenDtoIsNull() {
            assertNull(mapper.toEntity(null));
        }

        @Test
        void shouldMapEntityListToDtoList() {
            List<TurSNRankingExpression> entities = List.of(buildEntity());

            List<TurSNRankingExpressionDto> dtos = mapper.toDtoList(entities);

            assertNotNull(dtos);
            assertEquals(1, dtos.size());
            assertEquals("re-1", dtos.get(0).getId());
        }

        @Test
        void shouldReturnNullForNullList() {
            assertNull(mapper.toDtoList(null));
        }

        @Test
        void shouldMapEntitySetToDtoSet() {
            Set<TurSNRankingExpression> entities = Set.of(buildEntity());

            Set<TurSNRankingExpressionDto> dtos = mapper.toDtoSet(entities);

            assertNotNull(dtos);
            assertEquals(1, dtos.size());
        }

        @Test
        void shouldReturnNullForNullSet() {
            assertNull(mapper.toDtoSet(null));
        }

        @Test
        void shouldHandleEntityWithNullFields() {
            TurSNRankingExpression entity = new TurSNRankingExpression();
            entity.setId("null-re");

            TurSNRankingExpressionDto dto = mapper.toDto(entity);

            assertNotNull(dto);
            assertEquals("null-re", dto.getId());
            assertNull(dto.getName());
            assertNull(dto.getDescription());
        }
    }

    // ---- TurSNRankingConditionMapper -----------------------------------------

    @Nested
    class ConditionMapperTests {

        private TurSNRankingConditionMapperImpl mapper;

        @BeforeEach
        void setUp() {
            mapper = new TurSNRankingConditionMapperImpl();
        }

        private TurSNRankingCondition buildEntity() {
            TurSNRankingCondition entity = new TurSNRankingCondition();
            entity.setId("rc-1");
            entity.setAttribute("publication_date");
            entity.setCondition(1);
            entity.setValue("2026-01-01");
            return entity;
        }

        @Test
        void shouldMapEntityToDto() {
            TurSNRankingCondition entity = buildEntity();

            TurSNRankingConditionDto dto = mapper.toDto(entity);

            assertNotNull(dto);
            assertEquals("rc-1", dto.getId());
            assertEquals("publication_date", dto.getAttribute());
            assertEquals(1, dto.getCondition());
            assertEquals("2026-01-01", dto.getValue());
        }

        @Test
        void shouldReturnNullWhenEntityIsNull() {
            assertNull(mapper.toDto(null));
        }

        @Test
        void shouldMapDtoToEntity() {
            TurSNRankingConditionDto dto = new TurSNRankingConditionDto();
            dto.setId("rc-2");
            dto.setAttribute("type");
            dto.setCondition(2);
            dto.setValue("article");

            TurSNRankingCondition entity = mapper.toEntity(dto);

            assertNotNull(entity);
            assertEquals("rc-2", entity.getId());
            assertEquals("type", entity.getAttribute());
            assertEquals(2, entity.getCondition());
            assertEquals("article", entity.getValue());
        }

        @Test
        void shouldReturnNullWhenDtoIsNull() {
            assertNull(mapper.toEntity(null));
        }

        @Test
        void shouldMapEntityListToDtoList() {
            List<TurSNRankingCondition> entities = List.of(buildEntity());

            List<TurSNRankingConditionDto> dtos = mapper.toDtoList(entities);

            assertNotNull(dtos);
            assertEquals(1, dtos.size());
            assertEquals("rc-1", dtos.get(0).getId());
        }

        @Test
        void shouldReturnNullForNullList() {
            assertNull(mapper.toDtoList(null));
        }

        @Test
        void shouldMapEntitySetToDtoSet() {
            Set<TurSNRankingCondition> entities = Set.of(buildEntity());

            Set<TurSNRankingConditionDto> dtos = mapper.toDtoSet(entities);

            assertNotNull(dtos);
            assertEquals(1, dtos.size());
        }

        @Test
        void shouldReturnNullForNullSet() {
            assertNull(mapper.toDtoSet(null));
        }

        @Test
        void shouldHandleEntityWithNullFields() {
            TurSNRankingCondition entity = new TurSNRankingCondition();
            entity.setId("null-rc");

            TurSNRankingConditionDto dto = mapper.toDto(entity);

            assertNotNull(dto);
            assertEquals("null-rc", dto.getId());
            assertNull(dto.getAttribute());
            assertNull(dto.getValue());
        }
    }
}
