package com.viglet.turing.persistence.repository.integration;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.repository.RepositoryContractAssertions;

class TurIntegrationInstanceRepositoryTest {
    @Test
    void shouldFollowJpaRepositoryContract() {
        RepositoryContractAssertions.assertJpaRepositoryContract(TurIntegrationInstanceRepository.class);
    }
}
