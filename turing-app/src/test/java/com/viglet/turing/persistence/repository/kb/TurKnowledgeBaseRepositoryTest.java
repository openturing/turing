package com.viglet.turing.persistence.repository.kb;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.repository.RepositoryContractAssertions;

class TurKnowledgeBaseRepositoryTest {
    @Test
    void shouldFollowJpaRepositoryContract() {
        RepositoryContractAssertions.assertJpaRepositoryContract(TurKnowledgeBaseRepository.class);
    }
}
