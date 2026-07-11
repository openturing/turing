package com.viglet.turing.persistence.repository.kb;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.repository.RepositoryContractAssertions;

class TurThesaurusTermRelationRepositoryTest {
    @Test
    void shouldFollowJpaRepositoryContract() {
        RepositoryContractAssertions.assertJpaRepositoryContract(TurThesaurusTermRelationRepository.class);
    }
}
