package com.viglet.turing.persistence.repository.sn.merge;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.repository.RepositoryContractAssertions;

class TurSNSiteMergeProvidersFieldRepositoryTest {
    @Test
    void shouldFollowJpaRepositoryContract() {
        RepositoryContractAssertions.assertJpaRepositoryContract(TurSNSiteMergeProvidersFieldRepository.class);
    }
}
